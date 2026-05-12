package com.unboundapex.octalink.data.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.repo.KakaoIdentity
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.repo.SignupRequest
import com.unboundapex.octalink.data.repo.inmemory.InMemoryAuthRepository
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 현재 로그인 세션 — [com.unboundapex.octalink.data.repo.AuthRepository.currentUid] (uid) +
 * [com.unboundapex.octalink.data.repo.MemberRepository] (profile) 합성 결과.
 *
 * UI 는 [SessionState.phase] 로 분기:
 * - LOADING: 초기 uid resolve 대기 (구현상 In-memory 는 거의 즉시 통과)
 * - UNAUTHENTICATED: uid == null. 로그인 화면 노출.
 * - PENDING_SIGNUP: uid 있는데 MemberDoc 없음. 가입 폼 필요.
 * - AUTHENTICATED: MemberDoc 존재. [member.status] 로 PENDING/APPROVED/REJECTED 분기.
 */
data class SessionState(
    val phase: Phase = Phase.LOADING,
    /** 카카오 OAuth 식별자 — PENDING_SIGNUP 단계에서 가입 폼 제출 시 사용 */
    val authProviderId: String? = null,
    val member: MemberDoc? = null,
    /** 카카오 로그인 직후 받은 프로필 — SignupScreen 폼 prefill 용. AUTHENTICATED 진입 후엔 무의미 */
    val kakaoIdentity: KakaoIdentity? = null,
) {
    enum class Phase { LOADING, UNAUTHENTICATED, PENDING_SIGNUP, AUTHENTICATED }

    // 기존 화면 호환용 단축 접근자 — member 가 없으면 안전 기본값 반환
    val name: String get() = member?.name ?: ""
    val belt: Belt get() = member?.belt ?: Belt.WHITE
    val avatarId: String get() = member?.avatarId ?: "ryu"
    val role: Role get() = member?.role ?: Role.MEMBER
    val status: MembershipStatus get() = member?.status ?: MembershipStatus.PENDING
}

class SessionViewModel : ViewModel() {
    private val auth = RepositoryProvider.auth
    private val members = RepositoryProvider.members

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val uidWithMember = combine(auth.currentUid, auth.currentDisplayName) { uid, name ->
        uid to name
    }.flatMapLatest { (uid, displayName) ->
        if (uid == null) flowOf(Triple(null, null, null))
        else members.observeByAuthProviderId(uid).map { Triple(uid, displayName, it) }
    }

    init {
        viewModelScope.launch {
            uidWithMember
                .catch { e ->
                    // Firestore 권한 거부 / 네트워크 에러 등이 Flow 까지 올라와도 앱이 죽지 않게.
                    // 발생 시 UNAUTHENTICATED 로 리셋 → 사용자가 다시 로그인 시도 가능
                    android.util.Log.e("OctaLink.Session", "uidWithMember flow error", e)
                    _state.value = SessionState(phase = SessionState.Phase.UNAUTHENTICATED)
                }
                .collect { (uid, displayName, member) ->
                    // kakaoIdentity 결정 우선순위:
                    //   1. signInWithKakao() 가 set 한 기존 값 (phoneNumber 포함 가장 풍부)
                    //   2. AuthRepository.currentDisplayName 기반 fallback
                    //      (앱 재시작 시 Firebase Auth user.displayName 에서 복원)
                    val keepIdentity = _state.value.kakaoIdentity
                    val identity = keepIdentity ?: displayName?.takeIf { it.isNotBlank() }?.let {
                        KakaoIdentity(authProviderId = uid.orEmpty(), displayName = it)
                    }
                    _state.value = when {
                        uid == null -> SessionState(phase = SessionState.Phase.UNAUTHENTICATED)
                        member == null -> SessionState(
                            phase = SessionState.Phase.PENDING_SIGNUP,
                            authProviderId = uid,
                            kakaoIdentity = identity,
                        )
                        else -> SessionState(
                            phase = SessionState.Phase.AUTHENTICATED,
                            authProviderId = uid,
                            member = member,
                        )
                    }
                }
        }
    }

    fun signInWithKakao() {
        viewModelScope.launch {
            val result = auth.signInWithKakao()
            if (result.isFailure) {
                android.util.Log.e(
                    "OctaLink.Auth",
                    "signInWithKakao failed",
                    result.exceptionOrNull(),
                )
            } else {
                val identity = result.getOrNull()
                android.util.Log.i("OctaLink.Auth", "signInWithKakao success: $identity")
                // SignupScreen 폼 prefill 용 — flow 가 PENDING_SIGNUP 상태 emit 한 후
                // identity 가 도착해도 keepIdentity 로직이 다음 emission 까지 보존
                if (identity != null) {
                    _state.value = _state.value.copy(kakaoIdentity = identity)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    /**
     * 회원 탈퇴 — `members/{uid}.status = LEFT` 갱신 후 자동 로그아웃.
     * Cloud Function 실패 시에도 클라이언트는 signOut 으로 진행 (사용자 의지 우선).
     */
    fun leaveMembership() {
        val memberId = state.value.member?.id ?: return
        viewModelScope.launch {
            runCatching { members.leaveMembership(memberId) }
                .onFailure { android.util.Log.e("OctaLink.Auth", "leaveMembership failed", it) }
            auth.signOut()
        }
    }

    /**
     * 개발용 단축 로그인 — mock 인증 단계에서만 의미 있음. 실제 카카오 SDK 도입 시 제거.
     * In-memory Auth 가 아니면 무시.
     */
    fun debugSignInAsCreator() = debugSetUid(InMemoryAuthRepository.MOCK_CREATOR_UID)
    fun debugSignInAsMaster() = debugSetUid(InMemoryAuthRepository.MOCK_MASTER_UID)

    private fun debugSetUid(uid: String) {
        viewModelScope.launch {
            (auth as? InMemoryAuthRepository)?.setCurrentUid(uid)
        }
    }

    /** PENDING_SIGNUP 단계에서 가입 폼 제출. authProviderId 는 현재 상태에서 가져옴.
     *  카카오 동의 항목으로 받은 부가 정보(email/gender/ageRange/birthday/birthyear) 도 같이 영속화. */
    fun completeSignup(
        name: String,
        belt: Belt,
        weightClass: WeightClass,
        avatarId: String,
        phone: String? = null,
    ) {
        val authId = state.value.authProviderId ?: return
        val identity = state.value.kakaoIdentity
        viewModelScope.launch {
            members.signup(
                SignupRequest(
                    authProviderId = authId,
                    name = name,
                    belt = belt,
                    weightClass = weightClass,
                    avatarId = avatarId,
                    phone = phone,
                    email = identity?.email,
                    gender = identity?.gender,
                    ageRange = identity?.ageRange,
                    birthday = identity?.birthday,
                    birthyear = identity?.birthyear,
                )
            )
        }
    }

    fun updateAvatar(avatarId: String) {
        val memberId = state.value.member?.id ?: return
        viewModelScope.launch { members.updateProfile(memberId, avatarId = avatarId) }
    }

    fun updateBelt(belt: Belt) {
        val memberId = state.value.member?.id ?: return
        viewModelScope.launch { members.updateProfile(memberId, belt = belt) }
    }

    fun updateName(name: String) {
        val memberId = state.value.member?.id ?: return
        viewModelScope.launch { members.updateProfile(memberId, name = name) }
    }
}
