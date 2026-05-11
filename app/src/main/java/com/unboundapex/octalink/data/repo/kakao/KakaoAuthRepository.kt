package com.unboundapex.octalink.data.repo.kakao

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.User
import com.unboundapex.octalink.data.repo.AuthRepository
import com.unboundapex.octalink.data.repo.KakaoIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 실제 카카오 OAuth + Firebase Custom Token 통합 [AuthRepository] 구현.
 *
 * 플로우:
 * 1. [UserApiClient.loginWithKakaoTalk] / [UserApiClient.loginWithKakaoAccount] → 카카오 accessToken
 * 2. Firebase Cloud Function `kakaoSignIn(accessToken)` 호출 → 서버측 토큰 검증 후 Custom Token 발급
 * 3. [FirebaseAuth.signInWithCustomToken] → Firebase 사용자 인증
 * 4. [UserApiClient.me] → 카카오 사용자 정보(닉네임 등) 조회
 *
 * [RepositoryProvider] 에서 [com.unboundapex.octalink.data.repo.inmemory.InMemoryAuthRepository]
 * 대신 이 클래스로 교체하면 실제 카카오 로그인 활성. Cloud Function 미배포 상태에선 2단계에서
 * 실패하므로 그 전까지는 InMemory 유지.
 *
 * **Custom Token uid 규약:** Cloud Function 이 `kakao:{kakaoUserId}` 형식으로 Firebase uid 발급.
 * 이 uid 가 [com.unboundapex.octalink.data.schema.MemberDoc.authProviderId] 와 매칭됨.
 */
class KakaoAuthRepository(
    private val context: Context,
) : AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val functions = Firebase.functions("asia-northeast3")

    private val _currentUid = MutableStateFlow(firebaseAuth.currentUser?.uid)
    override val currentUid: StateFlow<String?> = _currentUid.asStateFlow()

    init {
        // Firebase Auth 상태 변경(앱 재시작 시 자동 복원 포함) → uid Flow 갱신
        firebaseAuth.addAuthStateListener { auth ->
            _currentUid.value = auth.currentUser?.uid
        }
    }

    override suspend fun signInWithKakao(): Result<KakaoIdentity> = runCatching {
        // 1) 카카오 OAuth 토큰 획득 — 카톡 앱 우선, 없으면 웹 계정 로그인
        val oAuthToken = if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            awaitKakaoLogin { cb ->
                UserApiClient.instance.loginWithKakaoTalk(context, callback = cb)
            }
        } else {
            awaitKakaoLogin { cb ->
                UserApiClient.instance.loginWithKakaoAccount(context, callback = cb)
            }
        }

        // 2) Cloud Function 으로 카카오 accessToken → Firebase Custom Token 교환
        val callResult = functions
            .getHttpsCallable("kakaoSignIn")
            .call(mapOf("accessToken" to oAuthToken.accessToken))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = callResult.data as Map<String, Any>
        val customToken = data["customToken"] as String

        // 3) Firebase Auth 에 Custom Token 으로 로그인
        firebaseAuth.signInWithCustomToken(customToken).await()

        // 4) 카카오 사용자 정보 (닉네임) 조회 — SessionViewModel 의 가입 폼 prefill 용
        val user = awaitKakaoMe()

        KakaoIdentity(
            authProviderId = firebaseAuth.currentUser!!.uid,
            displayName = user.kakaoAccount?.profile?.nickname.orEmpty(),
            phoneNumber = user.kakaoAccount?.phoneNumber,
        )
    }

    override suspend fun signOut() {
        awaitKakaoLogout()
        firebaseAuth.signOut()
    }

    private suspend fun awaitKakaoLogin(
        invoke: ((OAuthToken?, Throwable?) -> Unit) -> Unit,
    ): OAuthToken = suspendCancellableCoroutine { cont ->
        invoke { token, error ->
            when {
                error != null -> cont.resumeWithException(error)
                token != null -> cont.resume(token)
                else -> cont.resumeWithException(IllegalStateException("Kakao OAuthToken null"))
            }
        }
    }

    private suspend fun awaitKakaoMe(): User = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.me { user, error ->
            when {
                error != null -> cont.resumeWithException(error)
                user != null -> cont.resume(user)
                else -> cont.resumeWithException(IllegalStateException("Kakao User null"))
            }
        }
    }

    private suspend fun awaitKakaoLogout(): Unit = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.logout {
            // 카카오 로그아웃 실패해도 Firebase 로그아웃은 진행 — 단순 cleanup 성격
            cont.resume(Unit)
        }
    }
}
