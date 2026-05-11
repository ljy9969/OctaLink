package com.unboundapex.octalink.data.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
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
    private val uidWithMember = auth.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null to null)
        else members.observeByAuthProviderId(uid).map { uid to it }
    }

    init {
        viewModelScope.launch {
            uidWithMember.collect { (uid, member) ->
                _state.value = when {
                    uid == null -> SessionState(phase = SessionState.Phase.UNAUTHENTICATED)
                    member == null -> SessionState(
                        phase = SessionState.Phase.PENDING_SIGNUP,
                        authProviderId = uid,
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
        viewModelScope.launch { auth.signInWithKakao() }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
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

    /** PENDING_SIGNUP 단계에서 가입 폼 제출. authProviderId 는 현재 상태에서 가져옴. */
    fun completeSignup(
        name: String,
        belt: Belt,
        weightClass: WeightClass,
        avatarId: String,
        phone: String? = null,
    ) {
        val authId = state.value.authProviderId ?: return
        viewModelScope.launch {
            members.signup(
                SignupRequest(
                    authProviderId = authId,
                    name = name,
                    belt = belt,
                    weightClass = weightClass,
                    avatarId = avatarId,
                    phone = phone,
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
