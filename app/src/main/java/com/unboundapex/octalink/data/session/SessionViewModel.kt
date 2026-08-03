package com.unboundapex.octalink.data.session

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.avatarFor
import com.unboundapex.octalink.data.repo.KakaoIdentity
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.repo.SignupRequest
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.util.PiiMask
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
    // 캐릭터는 성별 + 체급에서 자동 파생. MemberDoc.avatarId 는 가입 시점 스냅샷이라
    // 체급/성별 갱신 후에도 옛 값을 가질 수 있으므로 렌더 시점에서 재계산.
    val avatarId: String get() = member?.let {
        avatarFor(it.gender, it.weightClass).id
    } ?: "m_light"
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
                    // 멀티테넌트 앰비언트 gymId 갱신 — repository 쿼리 스코핑용. 회원 없으면 null.
                    com.unboundapex.octalink.data.SessionGym.gymId =
                        member?.gymId?.takeIf { it.isNotBlank() }
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

    fun signInWithKakao(activity: Activity) {
        // 카카오 SDK 호출 시작 즉시 LOADING — Kakao auth + Firebase signIn + Firestore snapshot 도착
        // 시점까지의 갭에서 LoginScreen 이 재노출되는 깜빡임을 차단. uid 갱신 후 init flow 가
        // PENDING_SIGNUP/AUTHENTICATED 로 자연스럽게 전환.
        // activity: 카카오 SDK 가 카톡 앱/웹 OAuth 띄울 Activity context. ApplicationContext 전달 시
        // "Calling startActivity() from outside of an Activity context" 런타임 예외 → 카톡 설치된
        // 실기기에서 로그인 버튼 무반응(보이는 화면 변화 없이 silent fail).
        _state.value = _state.value.copy(phase = SessionState.Phase.LOADING)
        viewModelScope.launch {
            val result = auth.signInWithKakao(activity)
            if (result.isFailure) {
                android.util.Log.e(
                    "OctaLink.Auth",
                    "signInWithKakao failed",
                    result.exceptionOrNull(),
                )
                // 실패 → 로그인 화면 복귀
                _state.value = _state.value.copy(phase = SessionState.Phase.UNAUTHENTICATED)
            } else {
                val identity = result.getOrNull()
                // PII 는 PiiMask 거쳐 마스킹. 원본 KakaoIdentity.toString 직접 출력 금지.
                android.util.Log.i(
                    "OctaLink.Auth",
                    "signInWithKakao success: uid=${PiiMask.id(identity?.authProviderId)}, " +
                        "name=${PiiMask.name(identity?.displayName)}, " +
                        "hasPhone=${identity?.phoneNumber?.isNotBlank() == true}, " +
                        "hasEmail=${identity?.email?.isNotBlank() == true}",
                )
                // SignupScreen 폼 prefill 용 — phase 는 그대로 LOADING 유지하다 uidWithMember 가
                // 다음 emission 으로 갱신
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
     * LEFT 상태에서 재가입 — Cloud Function 이 RoleAllowlist 재평가 후
     * APPROVED (allowlist) / PENDING (일반 회원) 으로 status 갱신. Firestore snapshot 이
     * member.status 변경을 emit 하면 PosseApp 라우팅이 자동으로 적절한 화면 (Approved/Pending) 으로 전환.
     */
    fun rejoinMembership() {
        val memberId = state.value.member?.id ?: return
        viewModelScope.launch {
            runCatching { members.rejoinMembership(memberId) }
                .onFailure { android.util.Log.e("OctaLink.Auth", "rejoinMembership failed", it) }
        }
    }

    /** PENDING_SIGNUP 단계에서 가입 폼 제출. authProviderId 는 현재 상태에서 가져옴.
     *  카카오 동의 항목으로 받은 부가 정보(email/gender/ageRange/birthday/birthyear) 도 같이 영속화.
     *  gender 는 카카오 비즈 검수 통과 시 자동 수집, 미통과 시 사용자 직접 선택값을
     *  [pickedGender] 로 전달 — 카카오 값이 우선 사용되고 없을 때만 fallback. */
    fun completeSignup(
        name: String,
        belt: Belt,
        weightClass: WeightClass,
        avatarId: String,
        joinDate: java.time.LocalDate,
        gymId: String,
        phone: String? = null,
        pickedGender: String? = null,
    ) {
        val authId = state.value.authProviderId ?: return
        val identity = state.value.kakaoIdentity
        viewModelScope.launch {
            members.signup(
                SignupRequest(
                    authProviderId = authId,
                    gymId = gymId,
                    name = name,
                    belt = belt,
                    weightClass = weightClass,
                    avatarId = avatarId,
                    joinDate = joinDate,
                    phone = phone,
                    email = identity?.email,
                    // 카카오 gender (비즈 검수 통과 시) 우선, 미수집 시 사용자 직접 선택값 사용
                    gender = identity?.gender ?: pickedGender,
                    ageRange = identity?.ageRange,
                    birthday = identity?.birthday,
                    birthyear = identity?.birthyear,
                )
            )
        }
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
