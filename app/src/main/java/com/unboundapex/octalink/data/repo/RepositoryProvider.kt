package com.unboundapex.octalink.data.repo

import android.content.Context
import com.unboundapex.octalink.data.repo.firestore.FirestoreAttendanceRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreCommentRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreExchangeMatchRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreGymRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreMemberRepository
import com.unboundapex.octalink.data.repo.firestore.FirestorePostCommentRepository
import com.unboundapex.octalink.data.repo.firestore.FirestorePostRepository
import com.unboundapex.octalink.data.repo.firestore.FirestorePublicProfileRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreSkillScoreRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreTournamentRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreWeeklyMissionRepository
import com.unboundapex.octalink.data.repo.firestore.FirestoreWeeklyRoutineRepository
import com.unboundapex.octalink.data.repo.kakao.KakaoAuthRepository

/**
 * Repository 싱글톤 컨테이너. 앱 시작 시 [init] 1회 호출.
 *
 * **현재 활성: Phase 2** — 실제 카카오 OAuth + Firestore.
 *   - [KakaoAuthRepository] : Kakao SDK → Cloud Function `kakaoSignIn` → Firebase Custom Token
 *   - [FirestoreMemberRepository] : `members/{uid}` 컬렉션 매핑
 *
 * **Phase 1 (mock) 으로 되돌리려면** 아래 두 줄을 `InMemoryAuthRepository()` /
 * `InMemoryMemberRepository()` 로 교체 + import 변경. mock 단계는
 * [com.unboundapex.octalink.data.repo.inmemory.MockSeed] 가 시드 데이터 자동 주입.
 *
 * 의존성이 커지면 Hilt 도입 검토.
 */
object RepositoryProvider {
    lateinit var auth: AuthRepository
        private set
    lateinit var members: MemberRepository
        private set
    lateinit var gyms: GymRepository
        private set
    lateinit var publicProfiles: PublicProfileRepository
        private set
    lateinit var exchangeMatches: ExchangeMatchRepository
        private set
    lateinit var attendance: AttendanceRepository
        private set
    lateinit var posts: PostRepository
        private set
    lateinit var postComments: PostCommentRepository
        private set
    lateinit var comments: CommentRepository
        private set
    lateinit var skillScores: SkillScoreRepository
        private set
    lateinit var tournaments: TournamentRepository
        private set
    lateinit var weeklyMission: WeeklyMissionRepository
        private set
    lateinit var weeklyRoutine: WeeklyRoutineRepository
        private set

    @Volatile
    private var initialized: Boolean = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            auth = KakaoAuthRepository(context.applicationContext)
            members = FirestoreMemberRepository()
            gyms = FirestoreGymRepository()
            publicProfiles = FirestorePublicProfileRepository()
            exchangeMatches = FirestoreExchangeMatchRepository()
            attendance = FirestoreAttendanceRepository()
            posts = FirestorePostRepository()
            postComments = FirestorePostCommentRepository()
            comments = FirestoreCommentRepository()
            skillScores = FirestoreSkillScoreRepository()
            tournaments = FirestoreTournamentRepository()
            weeklyMission = FirestoreWeeklyMissionRepository()
            weeklyRoutine = FirestoreWeeklyRoutineRepository()

            initialized = true
        }
    }
}
