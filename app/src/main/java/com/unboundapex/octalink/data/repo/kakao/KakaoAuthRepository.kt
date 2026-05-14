package com.unboundapex.octalink.data.repo.kakao

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.functions
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.ScopeInfo
import com.kakao.sdk.user.model.User
import com.unboundapex.octalink.data.repo.AuthRepository
import com.unboundapex.octalink.data.repo.KakaoIdentity
import com.unboundapex.octalink.data.util.PiiMask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "OctaLink.KakaoAuth"

/**
 * 카카오 비즈앱 검수 통과 시 사용자에게 추가 요청할 OAuth scope ID 목록.
 *
 * Kakao Developer Console → 내 애플리케이션 → 카카오 로그인 → 동의 항목 페이지에서
 * 각 항목의 ID 를 확인 가능. 콘솔에서 검수 완료된 항목만 실제 응답에 반환되며, 미검수
 * 항목은 scope 로 요청해도 OAuth 거절 처리됨.
 */
private val BIZ_SCOPES = listOf(
    "name",          // 이름 (실명) — 비즈앱 검수 필요
    "phone_number",  // 전화번호 — 비즈앱 검수 필요
    "account_email", // 카카오계정(이메일)
    "gender",        // 성별
    "age_range",     // 연령대
    "birthday",      // 생일
    "birthyear",     // 출생연도
)

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

    private val _currentDisplayName = MutableStateFlow(firebaseAuth.currentUser?.displayName)
    override val currentDisplayName: StateFlow<String?> = _currentDisplayName.asStateFlow()

    init {
        // Firebase Auth 상태 변경(앱 재시작 시 자동 복원 포함) → uid/displayName Flow 갱신.
        // displayName 은 Cloud Function `kakaoSignIn` 이 사용자 생성/갱신 시 Kakao nickname 으로 세팅.
        firebaseAuth.addAuthStateListener { auth ->
            _currentUid.value = auth.currentUser?.uid
            _currentDisplayName.value = auth.currentUser?.displayName
        }
    }

    override suspend fun signInWithKakao(): Result<KakaoIdentity> = runCatching {
        // 1) 카카오 OAuth 토큰 획득 — 카톡 앱 우선, 없으면 웹 계정 로그인.
        Log.d(TAG, "[1/4] Kakao OAuth 시작")
        val kakaoTalkAvailable = UserApiClient.instance.isKakaoTalkLoginAvailable(context)
        Log.d(TAG, "[1/4] kakaoTalkAvailable=$kakaoTalkAvailable")
        val oAuthToken = if (kakaoTalkAvailable) {
            awaitKakaoLogin { cb ->
                UserApiClient.instance.loginWithKakaoTalk(context, callback = cb)
            }
        } else {
            awaitKakaoLogin { cb ->
                UserApiClient.instance.loginWithKakaoAccount(context, callback = cb)
            }
        }
        Log.d(TAG, "[1/4] accessToken 획득 ${PiiMask.id(oAuthToken.accessToken)}")

        // 1b) 비즈 동의 항목 보충 — 초기 로그인은 기본 scope (e.g. profile_nickname) 만 가져오므로
        // `name` / `phone_number` 등 비즈 검수 항목은 명시적으로 추가 동의를 받아야 함.
        // 현재 agree 상태를 조회 후 누락 항목만 loginWithNewScopes 로 요청 (이미 동의됨이면 skip).
        ensureBizScopes()

        // 2) Cloud Function 으로 카카오 accessToken → Firebase Custom Token 교환
        Log.d(TAG, "[2/4] Cloud Function kakaoSignIn 호출")
        val callResult = functions
            .getHttpsCallable("kakaoSignIn")
            .call(mapOf("accessToken" to oAuthToken.accessToken))
            .await()
        @Suppress("UNCHECKED_CAST")
        val data = callResult.data as Map<String, Any>
        val customToken = data["customToken"] as String
        Log.d(TAG, "[2/4] customToken 수신 ${PiiMask.id(customToken)}")

        // 3) Firebase Auth 에 Custom Token 으로 로그인
        Log.d(TAG, "[3/4] signInWithCustomToken")
        firebaseAuth.signInWithCustomToken(customToken).await()
        Log.d(TAG, "[3/4] Firebase signed in. uid=${PiiMask.id(firebaseAuth.currentUser?.uid)}")

        // 4) 카카오 사용자 정보 조회 — SessionViewModel 의 가입 폼 prefill + MemberDoc 저장 용도
        Log.d(TAG, "[4/4] Kakao user.me")
        val user = awaitKakaoMe()
        val acct = user.kakaoAccount
        val realName = acct?.name
        val nickname = acct?.profile?.nickname
        val rawPhone = acct?.phoneNumber
        // PII 는 PiiMask 거쳐 마스킹된 형태로만 logcat 노출. release 빌드는 `<redacted>`.
        Log.d(
            TAG,
            "[4/4] name=${PiiMask.name(realName)}, nickname=${PiiMask.name(nickname)}, " +
                "phoneNumber=${PiiMask.phone(rawPhone)} " +
                "(isNull=${rawPhone == null}, isBlank=${rawPhone?.isBlank()})",
        )

        KakaoIdentity(
            authProviderId = firebaseAuth.currentUser!!.uid,
            // 비즈앱 동의 후 `name` (실명) 우선. 미동의 / 닉네임-only 환경 fallback.
            displayName = realName ?: nickname.orEmpty(),
            phoneNumber = acct?.phoneNumber,
            email = acct?.email,
            gender = acct?.gender?.name,
            ageRange = acct?.ageRange?.name,
            birthday = acct?.birthday,
            birthyear = acct?.birthyear,
        )
    }.onFailure { Log.e(TAG, "signInWithKakao 실패", it) }

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

    /**
     * [BIZ_SCOPES] 중 미동의 항목을 [UserApiClient.loginWithNewScopes] 로 추가 요청.
     * 모두 동의됨 / 또는 모두 미적용(콘솔 미등록) 이면 no-op.
     */
    private suspend fun ensureBizScopes() {
        Log.d(TAG, "[1b/4] ensureBizScopes 진입")
        val info = runCatching { awaitScopes() }
            .onFailure { Log.w(TAG, "[1b/4] scopes 조회 실패 — skip", it) }
            .getOrNull() ?: run {
                Log.w(TAG, "[1b/4] scopes() null — skip")
                return
            }
        val scopeList = info.scopes.orEmpty()
        Log.d(TAG, "[1b/4] 콘솔 등록 scope ${scopeList.size}건 — ${scopeList.map { "${it.id}(agreed=${it.agreed},using=${it.using})" }}")
        val agreed = scopeList.filter { it.agreed }.map { it.id }.toSet()
        val available = scopeList.map { it.id }.toSet()
        val missing = BIZ_SCOPES.filter { it in available && it !in agreed }
        val unavailable = BIZ_SCOPES.filterNot { it in available }
        Log.d(TAG, "[1b/4] agreed=$agreed, missing(요청예정)=$missing, biz_unavailable(콘솔미등록)=$unavailable")
        if (missing.isEmpty()) {
            Log.d(TAG, "[1b/4] 요청할 scope 없음 — skip (이미 동의됐거나 콘솔 미등록)")
            return
        }
        Log.d(TAG, "[1b/4] loginWithNewScopes(missing=$missing)")
        suspendCancellableCoroutine<OAuthToken> { cont ->
            UserApiClient.instance.loginWithNewScopes(context, missing) { token, error ->
                when {
                    error != null -> cont.resumeWithException(error)
                    token != null -> cont.resume(token)
                    else -> cont.resumeWithException(IllegalStateException("loginWithNewScopes returned null"))
                }
            }
        }
        Log.d(TAG, "[1b/4] 추가 동의 완료")
    }

    private suspend fun awaitScopes(): ScopeInfo = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.scopes { info, error ->
            when {
                error != null -> cont.resumeWithException(error)
                info != null -> cont.resume(info)
                else -> cont.resumeWithException(IllegalStateException("ScopeInfo null"))
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
