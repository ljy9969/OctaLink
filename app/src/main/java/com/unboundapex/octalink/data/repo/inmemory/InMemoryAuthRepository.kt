package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.repo.AuthRepository
import com.unboundapex.octalink.data.repo.KakaoIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 개발용 mock Auth. 앱 시작 시 자동으로 "이지연(CREATOR)" 으로 로그인된 상태 유지.
 *
 * 실제 카카오 SDK 통합 전까지 현재 UX (이지연 즉시 세션) 보존.
 * [signInWithKakao] 호출 시 새 카카오 ID 발급 → 신규 가입 시나리오 시뮬레이션 가능.
 */
class InMemoryAuthRepository(
    initialUid: String? = MOCK_CREATOR_UID,
) : AuthRepository {
    private val _currentUid = MutableStateFlow(initialUid)
    override val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    private var fakeKakaoCounter = 0

    override suspend fun signInWithKakao(): Result<KakaoIdentity> {
        fakeKakaoCounter++
        val id = "kakao:mock-${System.currentTimeMillis()}-$fakeKakaoCounter"
        _currentUid.value = id
        return Result.success(
            KakaoIdentity(
                authProviderId = id,
                displayName = "",
                phoneNumber = null,
            )
        )
    }

    override suspend fun signOut() {
        _currentUid.value = null
    }

    /** 디버그/테스트용 — 명시적으로 uid 전환. */
    fun setCurrentUid(uid: String?) {
        _currentUid.value = uid
    }

    companion object {
        const val MOCK_CREATOR_UID = "kakao:mock-jeeyeon"
        const val MOCK_MASTER_UID = "kakao:mock-pasi"
    }
}
