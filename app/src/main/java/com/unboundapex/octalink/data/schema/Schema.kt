package com.unboundapex.octalink.data.schema

import com.unboundapex.octalink.data.Belt
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
 * 권한 역할 — 3단계 계층.
 * - MASTER (관장): 전권. 스킬 점수 검토/확정 + 회원 가입 승인 + 권한 부여 등 최상위 권한
 * - COACH (코치, 부관리자): 일상 운영. 회원 코멘트 작성 + 출결 검토 + 토너먼트 관리 + 공지 작성
 *   추가로 스킬 점수 **입력(제안)** 가능 — 관장 검토 후 확정되는 워크플로
 * - MEMBER (회원): 본인 출결/프로필/커뮤니티 글 작성. 평가/공지/관리 작업 불가
 */
enum class Role { MASTER, COACH, MEMBER }

/**
 * 스킬 점수 검토 상태.
 * 코치 입력 → PROPOSED → 관장 검토 → APPROVED (또는 REJECTED 후 코치 재입력)
 */
enum class SkillScoreStatus { PROPOSED, APPROVED, REJECTED }

/** 운영진(관장 + 코치). 일상 운영 작업 권한이 있는지. */
val Role.isStaff: Boolean get() = this == Role.MASTER || this == Role.COACH

/** 관장 단독. 스킬 평가 + 회원 가입 승인 등 최상위 권한이 있는지. */
val Role.isMaster: Boolean get() = this == Role.MASTER

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
    val createdAt: Instant,
    val updatedAt: Instant,
)

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

/** 관장 한 줄 코멘트. 회원 본인 + 관장만 read */
data class CommentDoc(
    val id: String,
    val toMemberId: String,
    val byMasterId: String,
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
}
