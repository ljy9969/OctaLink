package com.unboundapex.octalink.data.repo

import kotlinx.coroutines.flow.StateFlow

/**
 * 인증 추상화. 현재 [com.unboundapex.octalink.data.repo.inmemory.InMemoryAuthRepository]
 * 로 mock, 추후 KakaoAuthRepository 로 교체.
 *
 * 카카오 OAuth → Firebase Custom Token → Firebase Auth signInWithCustomToken 흐름의
 * 진입점을 인터페이스 1개로 묶는다. UI/ViewModel 은 [currentUid] StateFlow 만 본다.
 */
interface AuthRepository {
    /** 현재 로그인된 사용자의 authProviderId (예: "kakao:1234567"). 미로그인 시 null. */
    val currentUid: StateFlow<String?>

    /**
     * 카카오 OAuth 시작 → 토큰 교환 → Firebase signIn 까지 1-shot.
     * mock 구현은 즉시 [KakaoIdentity] 반환.
     */
    suspend fun signInWithKakao(): Result<KakaoIdentity>

    suspend fun signOut()
}

/**
 * 카카오 OAuth 결과. [authProviderId] 는 "kakao:{kakaoUserId}" 형식.
 * 이 ID 가 Firestore [com.unboundapex.octalink.data.schema.MemberDoc.authProviderId] 에 저장됨.
 */
data class KakaoIdentity(
    val authProviderId: String,
    val displayName: String,
    val phoneNumber: String? = null,
)
