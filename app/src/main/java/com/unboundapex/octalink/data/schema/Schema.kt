package com.unboundapex.octalink.data.schema

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.WeightClass
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * 백엔드 영속화 도메인 모델 (Firebase Firestore / 자체 서버 무관).
 *
 * 원칙:
 * - id 는 String (Firestore 자연 친화 + UUID/auth uid 호환)
 * - 시각은 [Instant] (UTC) — 표시할 때만 KST 변환
 * - 경기일/입관일 등 "날짜만" 의미 있는 값은 [LocalDate]
 * - 컬렉션 간 참조는 항상 id 만 (객체 임베드 ❌, 비정규화 시 별도 명시)
 *
 * 화면 전용 in-memory 모델([com.unboundapex.octalink.data.Member] 등)은 그대로 유지.
 * 이 패키지의 *Doc 클래스는 영속화/네트워크 직렬화 단계에서만 사용.
 */

/**
 * 권한 역할 — 4단계 계층.
 * - CREATOR (앱 제작자): 최상위 + 단독. **회원 역할 부여(코치 승격) 권한을 단독 보유**.
 *   MASTER의 모든 권한 자동 포함 (스킬 검토 / 회원 승인 / 운영진 작업)
 * - MASTER (관장): 운영 전권. 스킬 점수 검토/확정 + 회원 가입 승인.
 *   **단, 코치 승격은 CREATOR만 가능** (관장 계정 탈취 시 무차별 권한 상승 차단)
 * - COACH (코치, 부관리자): 일상 운영. 회원 코멘트 작성 + 출결 검토 + 토너먼트 관리 + 공지 작성
 *   추가로 스킬 점수 **입력(제안)** 가능 — 관장 검토 후 확정되는 워크플로
 * - MEMBER (회원): 본인 출결/프로필/커뮤니티 글 작성. 평가/공지/관리 작업 불가
 *
 * **CREATOR 격리의 이유:** 앱 제작자만이 코드(`RoleAllowlist.kt`) + Firebase Console 두 채널
 * 모두 접근 가능. 관장 모바일 계정이 탈취되더라도 무권한 사용자가 코치로 승격되는 공격 차단.
 */
enum class Role { CREATOR, MASTER, COACH, MEMBER }

/**
 * 스킬 점수 검토 상태.
 * 코치 입력 → PROPOSED → 관장 검토 → APPROVED (또는 REJECTED 후 코치 재입력)
 */
enum class SkillScoreStatus { PROPOSED, APPROVED, REJECTED }

/** 앱 제작자 단독. 회원 역할 부여(코치 승격) 등 최상위 권한. */
val Role.isCreator: Boolean get() = this == Role.CREATOR

/** 관장급 이상 (MASTER + CREATOR). 스킬 검토 + 회원 가입 승인 등 운영 최상위. */
val Role.isMaster: Boolean get() = this == Role.MASTER || this == Role.CREATOR

/** 운영진(관장 + 코치 + 창조자). 일상 운영 작업 권한이 있는지. */
val Role.isStaff: Boolean get() = this == Role.MASTER || this == Role.COACH || this == Role.CREATOR

/** 입관 신청 상태. PENDING → 관장 승인 → APPROVED, 거부 시 REJECTED */
enum class MembershipStatus { PENDING, APPROVED, REJECTED, SUSPENDED, LEFT }

/** 회원 마스터 레코드 */
data class MemberDoc(
    val id: String,
    val name: String,
    val belt: Belt,
    val weightClass: WeightClass,
    val avatarId: String,
    val role: Role = Role.MEMBER,
    val status: MembershipStatus = MembershipStatus.PENDING,
    val joinDate: LocalDate,
    val phone: String? = null,
    val authProviderId: String? = null,
    /** 카카오 비즈앱 동의 항목들 — 가입 시 1회 채워지고 이후 운영자가 수정/보강 */
    val email: String? = null,
    val gender: String? = null,        // "MALE" / "FEMALE" (Kakao SDK Gender enum.name)
    val ageRange: String? = null,      // "AGE_20_29" 등 Kakao SDK AgeRange enum.name
    val birthday: String? = null,      // "MMDD" 형식 (예: "1130")
    val birthyear: String? = null,     // "YYYY" 형식 (예: "1995")
    /** 6축 스킬 최신 스냅샷. null 이면 미평가. 관장이 [SkillSet] 으로 입력/갱신. */
    val skills: SkillSet? = null,
    /**
     * FCM 디바이스 토큰 — 푸시 알림 발송 대상. [OctaLinkMessagingService.onNewToken] 에서 자동 갱신.
     * null = 아직 토큰 미수신(앱 첫 실행 전) 또는 사용자 토글로 알림 일괄 OFF.
     */
    val fcmToken: String? = null,
    /**
     * 알림 종류별 ON/OFF — key 는 [NotificationType.name]. 키 누락 시 [NotificationType.defaultEnabled] 값 사용.
     * 본인이 ProfileScreen 에서 직접 토글. Firestore rules 의 self-editable 화이트리스트에 포함.
     *
     * 단, [NotificationType.CLASS_REMINDER] 는 이 map 의 값이 아닌 [classReminderSlots] 로 세분화 관리.
     * (회원마다 출석 의향 있는 요일/시간대가 다르므로 단순 boolean 으로 부족)
     */
    val notificationPrefs: Map<String, Boolean> = emptyMap(),
    /**
     * 회원이 30분 전 리마인더를 받고 싶은 특정 수업 슬롯 셋.
     * 각 항목은 `"MONDAY_19:30"` 형식의 `요일_HH:mm` key
     * (`com.unboundapex.octalink.data.classSlotKey` 헬퍼 사용).
     * 비어있으면 CLASS_REMINDER 알림 안 받음 — 기본값 (옵트인).
     */
    val classReminderSlots: List<String> = emptyList(),
    /**
     * AI 코치의 맞춤 루틴 베타 액세스 — Phase 1 비공개 테스트 선착순 12명만 true.
     *
     * `completeSignup` Cloud Function 이 신규 가입(앱 회원 doc 최초 생성) 시점에 카운트해서
     * 자동 부여 — 현재 grants 수 < 12 일 때만 새 회원에게 true. 12 도달 후 신규 회원은 false.
     *
     * **선착순 기준은 [createdAt] (서버 타임스탬프) 이지 [joinDate] (도장 입관일) 가 아님.**
     * joinDate 는 사용자 입력이라 백데이트 가능 → 분기 기준으로 부적합.
     *
     * CREATOR 는 이 필드와 무관하게 항상 접근 (역할 기반).
     */
    val aiRoutineBeta: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * 푸시 알림 종류 — 사용자가 ProfileScreen 에서 개별 ON/OFF 가능.
 * 각 enum 의 [channelId]/[channelName]/[channelDescription] 는 NotificationChannel 등록 시 사용.
 *
 * FCM payload 의 `type` 데이터 필드가 [name] 과 일치하면 해당 채널로 라우팅.
 * 채널 분리 사유: Android 8.0+ 부터 사용자가 OS 알림 설정에서 채널별로 끌 수 있고, 알림 톤/중요도 분리 가능.
 */
enum class NotificationType(
    val displayName: String,
    val description: String,
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
    val defaultEnabled: Boolean = true,
) {
    COMMENT(
        displayName = "한 줄 코멘트",
        description = "운영진이 내게 한 줄 코멘트를 남겼을 때",
        channelId = "octalink_comment",
        channelName = "한 줄 코멘트",
        channelDescription = "운영진이 보낸 한 줄 코멘트 알림",
    ),
    TOURNAMENT_DRAWN(
        displayName = "토너먼트 추첨",
        description = "이번 주 새 토너먼트가 정해졌을 때",
        channelId = "octalink_tournament",
        channelName = "토너먼트",
        channelDescription = "새 대진표 추첨 알림",
    ),
    NEW_NOTICE(
        displayName = "새 공지",
        description = "운영진이 커뮤니티에 새 공지를 작성했을 때",
        channelId = "octalink_notice",
        channelName = "공지",
        channelDescription = "운영진 공지 알림",
    ),
    SIGNUP_RESULT(
        displayName = "가입 승인 결과",
        description = "관장이 내 가입 신청을 승인/거부할 때",
        channelId = "octalink_signup",
        channelName = "가입 결과",
        channelDescription = "가입 승인/거부 결과 알림",
    ),
    CLASS_REMINDER(
        displayName = "수업 30분 전 리마인더",
        description = "체육관 영업일의 각 수업 시작 30분 전",
        channelId = "octalink_class",
        channelName = "수업 리마인더",
        channelDescription = "오늘 수업 시작 30분 전 리마인더",
        defaultEnabled = false, // 잦은 알림 — 옵트인 방식
    ),
    SKILL_UPDATED(
        displayName = "스킬 점수 갱신",
        description = "내 스킬 점수가 갱신되었을 때",
        channelId = "octalink_skill",
        channelName = "스킬 점수",
        channelDescription = "스킬 점수 갱신 알림",
    ),
    NEW_POST_COMMENT(
        displayName = "내 글에 댓글",
        description = "내가 작성한 커뮤니티 글에 다른 회원이 댓글을 달았을 때",
        channelId = "octalink_post_comment",
        channelName = "댓글",
        channelDescription = "내 글에 달린 댓글 알림",
    ),
    MENTION(
        displayName = "@멘션",
        description = "다른 회원이 글에서 나를 멘션했을 때 (사진/영상 첨부 시 공개 승인 요청 포함)",
        channelId = "octalink_mention",
        channelName = "@멘션 / 공개 승인",
        channelDescription = "글 멘션 및 사진/영상 공개 승인 요청",
    ),
    // 운영진(MASTER/CREATOR) 전용 — 검토 큐 신규 항목 알림. COACH 는 대상 아님(rules 가 isMaster() 강제).
    // 클라이언트는 모든 회원에게 채널을 등록하지만 서버가 MASTER/CREATOR 에게만 발송 → 일반 회원은 못 받음.
    // ProfileSettingsScreen 의 토글은 session.role.isMaster 일 때만 노출 (회원에게 무의미).
    NEW_SIGNUP_PENDING(
        displayName = "새 가입 신청 (운영진)",
        description = "신규 회원이 가입 신청을 했을 때 — 관장/창조자에게",
        channelId = "octalink_admin_signup_pending",
        channelName = "가입 신청 (운영진)",
        channelDescription = "신규 회원 가입 신청 — 승인 대기 알림",
    ),
    NEW_SKILL_PROPOSAL(
        displayName = "새 스킬 점수 검토 요청 (운영진)",
        description = "코치가 회원 스킬 점수를 제안했을 때 — 관장/창조자에게",
        channelId = "octalink_admin_skill_proposal",
        channelName = "스킬 검토 (운영진)",
        channelDescription = "코치 제안 스킬 점수 — 승인 대기 알림",
    ),
}

/** 정기 클래스 정의 (요일별 운영 슬롯 — 변경 빈도 낮음) */
data class ClassDefDoc(
    val id: String,
    val dayOfWeek: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val name: String,
    val active: Boolean = true,
)

/** 출석 한 건. (memberId, classDate, classDefId) 가 자연 키 */
data class AttendanceDoc(
    val id: String,
    val memberId: String,
    val classDefId: String,
    val classDate: LocalDate,
    val checkInAt: Instant,
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val verified: Boolean = false,
)

/**
 * 운영진 한 줄 코멘트. 회원 본인 + 운영진만 read.
 *
 * 비정규화 필드 [byMasterName] — 작성 시점 운영진 이름 스냅샷. 회원 표시 시 members join 회피.
 * 운영진이 이름 변경해도 옛 코멘트엔 반영 안 됨 (history 의도).
 *
 * [classDate] — 코멘트가 다루는 수업/관찰 일자. 기본 today, 운영진이 과거 일자 지정 가능.
 */
data class CommentDoc(
    val id: String,
    val toMemberId: String,
    val byMasterId: String,
    val byMasterName: String,
    val text: String,
    val classDate: LocalDate,
    val createdAt: Instant,
)

/** 6축 스킬 점수 스냅샷. 평가일 기준 누적 (history 차트용) */
data class SkillScoreDoc(
    val id: String,
    val memberId: String,
    /** 입력자(코치 또는 관장). PROPOSED 상태에선 코치 ID, APPROVED 후엔 최종 검토한 관장 ID */
    val byUserId: String,
    val striking: Float,
    val grappling: Float,
    val stamina: Float,
    val technique: Float,
    val mental: Float,
    val speed: Float,
    val evaluatedAt: Instant,
    /** 검토 상태. 코치 입력 시 PROPOSED, 관장 확정 시 APPROVED, 반려 시 REJECTED */
    val status: SkillScoreStatus = SkillScoreStatus.PROPOSED,
    /** APPROVED/REJECTED 시점에 관장이 남기는 메타데이터 (선택) */
    val reviewedByMasterId: String? = null,
    val reviewedAt: Instant? = null,
)

/** 커뮤니티 글 카테고리. NOTICE 는 운영진(`isStaff`) 만 작성 가능. */
enum class PostTag { NOTICE, RECORD, TIP, QUESTION }

/**
 * 글 노출 상태 — @멘션 + 미디어(사진/영상) 조합 시 프라이버시 보호용.
 *
 *  - [PUBLIC]: 일반 노출. 모든 APPROVED 회원이 피드에서 봄.
 *  - [PENDING_APPROVAL]: 사진/영상 + @멘션 조합으로 작성된 글, 멘션된 회원 전원 승인 전까지 비공개.
 *    피드엔 작성자 본인 + 멘션된 회원만 볼 수 있음.
 *  - [REJECTED]: 멘션된 회원이 1명이라도 거부함. 피드에서 숨김. 작성자가 사진 제거 후 재게시 가능 (TBD).
 */
enum class PostVisibility { PUBLIC, PENDING_APPROVAL, REJECTED }

/**
 * 커뮤니티 글 한 건. 모든 회원이 작성하지만 NOTICE 태그는 운영진만 사용 가능.
 *
 * 비정규화 필드(authorName/authorBelt) — 회원 정보 join 회피로 list 쿼리 단순화.
 * 회원이 이름/벨트 변경 시 기존 글에는 반영되지 않음 (history snapshot 의도).
 */
data class PostDoc(
    val id: String,
    val authorId: String,
    /** 비정규화 — 회원 이름. createdAt 시점 스냅샷 */
    val authorName: String,
    /** 비정규화 — 작성 시점 벨트 ([Belt.name]). 카드 좌측 스트라이프 색 */
    val authorBelt: Belt,
    val title: String,                  // 짧은 제목 (선택, 빈 문자열 허용)
    val body: String,                   // 본문
    val tag: PostTag,
    /** Firebase Storage download URL — null 이면 첨부 없음. [imageUrl] / [videoUrl] 은 상호 배타. */
    val imageUrl: String? = null,
    /** Firebase Storage download URL (mp4). null 이면 첨부 없음. */
    val videoUrl: String? = null,
    /** 좋아요 누른 회원 id 셋. 토글 시 arrayUnion / arrayRemove 사용 */
    val likedBy: List<String> = emptyList(),
    val createdAt: Instant,
    /** @멘션된 회원 id 목록. 작성 시 picker 로 추가. */
    val mentionedMemberIds: List<String> = emptyList(),
    /** 노출 상태 — 기본 PUBLIC. 미디어+멘션 조합이면 PENDING_APPROVAL 로 생성. */
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    /** PENDING_APPROVAL 상태에서 아직 승인 안 한 회원 id 목록. 모두 빠지면 PUBLIC 전이. */
    val pendingApprovalFrom: List<String> = emptyList(),
    /** 거부한 멘션 회원 id — REJECTED 상태 사유 추적. */
    val rejectedBy: List<String> = emptyList(),
)

/**
 * 글 한 건에 대한 댓글. `posts/{postId}/comments/{commentId}` 서브컬렉션.
 *
 * 비정규화 — authorName/authorBelt 는 createdAt 시점 스냅샷.
 * 권한 정책 ([PostDoc] 과 동일):
 *  - 수정: 작성자만
 *  - 삭제: 작성자 + 관장급(MASTER/CREATOR) 모더레이션
 */
data class PostCommentDoc(
    val id: String,
    val postId: String,                 // 부모 글 id (collectionGroup 쿼리 시 식별)
    val authorId: String,
    val authorName: String,             // 비정규화 — createdAt 스냅샷
    val authorBelt: Belt,               // 비정규화 — 좌측 벨트 점 색
    val body: String,
    val createdAt: Instant,
    /** 좋아요 누른 회원 id 셋. 토글 시 arrayUnion / arrayRemove 사용. */
    val likedBy: List<String> = emptyList(),
    /** 마지막 수정 시각 — 작성자가 수정하면 갱신. null 이면 미수정. */
    val updatedAt: Instant? = null,
)

/** 토너먼트 라운드 enum — TournamentDoc 로 그룹핑 */
enum class TournamentRound { EIGHT, FOUR, FINAL }

/** 토너먼트 상위 컨테이너. 추첨 시점 + 체급/벨트 그룹 메타 보관 */
data class TournamentDoc(
    val id: String,
    val title: String,
    val weightClass: WeightClass?,
    val beltGroup: Belt?,
    val drawAt: Instant,
    val finishedAt: Instant? = null,
    val champion: String? = null,
)

/** 토너먼트 내 한 매치. round1/round2/final 모두 동일 스키마 */
data class MatchDoc(
    val id: String,
    val tournamentId: String,
    val round: TournamentRound,
    val slotIndex: Int,
    val redMemberId: String?,
    val blueMemberId: String?,
    val winnerMemberId: String? = null,
    val resolvedAt: Instant? = null,
    val resolvedByMasterId: String? = null,
)

/**
 * Firestore 컬렉션 경로 규약. 백엔드 선택 후 Repository 구현체에서 참조.
 *
 * 멤버 종속 데이터 (출석/코멘트/스킬)는 서브컬렉션으로 두면 권한 룰이 단순해진다.
 *   members/{memberId}/attendance/{attendanceId}
 *   members/{memberId}/comments/{commentId}
 *   members/{memberId}/skillScores/{scoreId}
 *
 * 토너먼트는 단독 컬렉션 + matches 서브컬렉션:
 *   tournaments/{tournamentId}
 *   tournaments/{tournamentId}/matches/{matchId}
 */
object Collections {
    const val MEMBERS = "members"
    const val CLASS_DEFS = "classDefs"
    const val ATTENDANCE = "attendance"
    const val COMMENTS = "comments"
    const val SKILL_SCORES = "skillScores"
    const val TOURNAMENTS = "tournaments"
    const val MATCHES = "matches"
    const val POSTS = "posts"
    /** posts/{postId}/postComments/{commentId} — 글 댓글. members/{uid}/comments 와 이름 분리하여
     *  collectionGroup("postComments") 가 한 줄 코멘트와 섞이지 않도록 함. */
    const val POST_COMMENTS = "postComments"
    /** 도장 전역 설정 — 주간 미션 등 단일 doc 들이 들어가는 컬렉션. */
    const val GYM_SETTINGS = "gymSettings"
    /** [GYM_SETTINGS] 내 주간 미션 doc 의 고정 ID (싱글톤). */
    const val WEEKLY_MISSION_DOC_ID = "weeklyMission"

    /** AI 가 생성한 회원별 주간 보강 루틴. doc ID = ISO week key ("2026-W22"). */
    const val WEEKLY_ROUTINES = "weeklyRoutines"
}

/**
 * 도장의 이번 주 미션 — 홈 화면 카드에 모든 회원에게 동일하게 노출.
 * 운영진(코치+관장+창조자)만 작성/수정. Firestore rules 에서 isStaff 검증.
 *
 * 본문은 짧은 multi-line 문자열 가정 (불릿 기호 포함 가능). 길이/포맷은 클라이언트 책임.
 */
data class WeeklyMissionDoc(
    val text: String,
    val updatedAt: Instant,
    /** 마지막 수정자 운영진 이름 (비정규화 — display 용). */
    val updatedByName: String,
)

/**
 * AI 가 생성한 회원별 주간 보강 루틴.
 *
 * 경로: `members/{memberId}/weeklyRoutines/{yyyy-W##}` (예: `2026-W22`).
 *
 * 생성 정책:
 *  - Cloud Function `generateWeeklyRoutine` 가 주1회 (일요일 23시 KST batch) 또는 수동 호출 시 작성.
 *  - 같은 `weekId` 로 재호출 시 덮어쓰기 (idempotent).
 *  - LLM 입력: 6축 점수 + 최근 5건 한 줄 코멘트 + belt / 체급 / 입관기간 + 성별 + 사용자 선택 난이도.
 *  - LLM 출력: 드릴별 [RoutineDrill.youtubeQuery] (영문 검색어). 외부 매핑 호출 없음.
 *
 * 시각 요소: 클라이언트가 [RoutineDrill.youtubeQuery] 로 YouTube 검색 화면 진입.
 * Phase 4 에 자체 도장 GIF/일러스트 라이브러리 도입 예정 — 그때 RoutineDrill 에 추가 필드.
 */
data class WeeklyRoutineDoc(
    /** ISO week key — 예: "2026-W22". 일요일 23시 KST 기준 다음 주의 weekId 사용. */
    val weekId: String,
    val generatedAt: Instant,
    /** 하위 2축 자동 추출 — 헥사곤 차트에서 빨강 highlight 대상. enum 키 (`STRIKING` 등). */
    val focusSkills: List<String>,
    /** LLM 이 참고한 한 줄 코멘트 id 목록 — 상세 화면 "AI 가 참고한 정보" 섹션 노출용. */
    val referencedCommentIds: List<String>,
    val days: List<RoutineDay>,
    /** 회원이 한 주 끝나고 입력하는 텍스트 (선택). */
    val weeklyFeedback: String = "",
    /** 생성 시점 사용자 선택 난이도 — "BEGINNER" / "INTERMEDIATE" / "ADVANCED". 옛 doc 호환 위해 nullable. */
    val difficulty: String? = null,
)

/**
 * 요일 단위 묶음. 일별 총 운동량은 [drills] 의 `durationMin` 합산 — 30분 이하 보장 (Cloud Function
 * 검증 + 초과 시 LLM 재호출). day 는 `java.time.DayOfWeek.name` ("MONDAY" 등) 직렬화.
 */
data class RoutineDay(
    val day: String,
    /** 그날의 주제 — 예: "가드 패스 콤비 + 코어 보강". */
    val title: String,
    val drills: List<RoutineDrill>,
)

/**
 * 개별 드릴. 시각 자료는 [youtubeQuery] 로 YouTube 검색 진입 (Phase 1).
 * Phase 4 자체 GIF 라이브러리 도입 시 [gymGifUrl] 등 필드 추가 예정.
 */
data class RoutineDrill(
    /** 한국어 드릴 이름 — LLM 출력. */
    val koName: String,
    /** 영문 YouTube 검색어 — 예: "jiu jitsu shrimp drill", "muay thai teep technique". */
    val youtubeQuery: String,
    /** 짧은 설명 (2~3줄). */
    val desc: String,
    /** 세트 / 반복 표기 — 예: "3R × 5분, 휴식 1분". */
    val sets: String,
    /** 30분 제약 검증용 분 단위. */
    val durationMin: Int,
    /** 6축 enum 키 ("STRIKING" / "GRAPPLING" / "STAMINA" / "TECHNIQUE" / "MENTAL" / "SPEED"). */
    val targetAxis: String,
    /**
     * YouTube `search.list` 상위 결과의 video id (11자) — Cloud Function 에서 채움.
     * 있으면 클라이언트가 `img.youtube.com/vi/{id}/hqdefault.jpg` 썸네일 + 탭 시 watch URL 진입.
     * 검색 실패 / API quota 초과 시 null — 폴백으로 검색 페이지 진입.
     */
    val videoId: String? = null,
    /** 회원 피드백 (Phase 2 에서 작성됨). 초기엔 null/false. */
    val done: Boolean = false,
    val skipped: Boolean = false,
)
