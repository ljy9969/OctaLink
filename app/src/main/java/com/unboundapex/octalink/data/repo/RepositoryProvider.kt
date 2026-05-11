package com.unboundapex.octalink.data.repo

import android.content.Context
import com.unboundapex.octalink.data.repo.inmemory.InMemoryAuthRepository
import com.unboundapex.octalink.data.repo.inmemory.InMemoryMemberRepository

/**
 * Repository 싱글톤 컨테이너. 앱 시작 시 [init] 1회 호출.
 *
 * **Phase 1 (현재):** In-memory 구현 — 카카오 로그인 / Firestore 통합 미완 상태 보존용.
 * **Phase 2:** [com.unboundapex.octalink.data.repo.kakao.KakaoAuthRepository] +
 *             FirestoreMemberRepository 로 교체. 아래 분기 주석 참조.
 *
 * Phase 2 전환 시점: Cloud Function `kakaoSignIn` 배포 완료 + Firestore Security Rules 검증 통과 후.
 * 의존성이 커지면 Hilt 도입 검토.
 */
object RepositoryProvider {
    lateinit var auth: AuthRepository
        private set
    lateinit var members: MemberRepository
        private set

    @Volatile
    private var initialized: Boolean = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            // ── Phase 1: In-memory (현재 활성) ─────────────────────────────────────
            auth = InMemoryAuthRepository()
            members = InMemoryMemberRepository()

            // ── Phase 2: 실제 카카오 + Firestore (Cloud Function 배포 후 활성화) ───
            // 위 두 줄(InMemory) 을 주석 처리하고 아래 두 줄 활성화:
            // auth = com.unboundapex.octalink.data.repo.kakao.KakaoAuthRepository(context.applicationContext)
            // members = com.unboundapex.octalink.data.repo.firestore.FirestoreMemberRepository()
            //
            // Phase 2 활성화 전 체크리스트:
            //  1. functions/ 에서 `firebase deploy --only functions` 성공 (Blaze 플랜 필수)
            //  2. Firebase Console > Auth > 사용자 직접관리 가능 상태
            //  3. firestore.rules 배포 완료 (`firebase deploy --only firestore:rules`)
            //  4. 카카오 콘솔 키해시 등록 완료 (SDK 첫 호출 시 logcat 에 출력)

            initialized = true
        }
    }
}
