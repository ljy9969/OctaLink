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
     * 현재 사용자의 표시 이름 — provider 가 알고 있는 nickname.
     * Kakao 구현은 Firebase Auth user 의 displayName (Cloud Function 이 가입 시 설정) 에서 읽어옴.
     * 앱 재시작 시 [signInWithKakao] 코루틴 없이도 SignupScreen prefill 에 사용 가능.
     */
    val currentDisplayName: StateFlow<String?>

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
 *
 * 비즈앱 인증 후 가능한 동의 항목 전체 반영. 권한이 없거나 사용자 미동의 시 해당 필드 null.
 *  - displayName: Kakao `name` (실명) 우선, 없으면 `profile.nickname` fallback
 *  - phoneNumber: "+82 10-1234-5678" 원시 형식 — UI prefill 시 숫자만 추출
 *  - gender: "MALE" / "FEMALE" (Kakao Gender enum.name)
 *  - ageRange: "AGE_20_29" 등 Kakao AgeRange enum.name
 *  - birthday: "MMDD" (예: "1130")
 *  - birthyear: "YYYY" (예: "1995")
 */
data class KakaoIdentity(
    val authProviderId: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val gender: String? = null,
    val ageRange: String? = null,
    val birthday: String? = null,
    val birthyear: String? = null,
)
