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
     */
    val notificationPrefs: Map<String, Boolean> = emptyMap(),
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
    /** 도장 전역 설정 — 주간 미션 등 단일 doc 들이 들어가는 컬렉션. */
    const val GYM_SETTINGS = "gymSettings"
    /** [GYM_SETTINGS] 내 주간 미션 doc 의 고정 ID (싱글톤). */
    const val WEEKLY_MISSION_DOC_ID = "weeklyMission"
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
