package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.schema.MemberDoc
import com.unboundapex.octalink.data.schema.MembershipStatus
import com.unboundapex.octalink.data.schema.Role
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 회원 ([MemberDoc]) 영속화 추상화. Firestore `members/{uid}` 컬렉션과 1:1 매핑 예정.
 *
 * 현재 in-memory mock. 변경 작업은 mock 구현 단에선 권한 검증 생략 — 호출자(ViewModel)가
 * 사전 권한 확인 필요. 실제 Firestore 환경에선 Security Rules 가 동일 검증을 강제.
 */
interface MemberRepository {
    fun observeAll(): Flow<List<MemberDoc>>
    fun observeByStatus(status: MembershipStatus): Flow<List<MemberDoc>>
    fun observeById(memberId: String): Flow<MemberDoc?>
    fun observeByAuthProviderId(authProviderId: String): Flow<MemberDoc?>

    /**
     * 신규 회원 가입. [com.unboundapex.octalink.data.RoleAllowlist] 매칭 시 즉시 APPROVED
     * + 사전 정의된 Role 부여. 그 외엔 MEMBER + PENDING (관장 승인 대기).
     *
     * 동일 [SignupRequest.authProviderId] 로 재호출 시 기존 MemberDoc 반환 (idempotent).
     */
    suspend fun signup(req: SignupRequest): MemberDoc

    /** PENDING → APPROVED 또는 REJECTED 등. 관장/창조자 권한 필요. */
    suspend fun setStatus(memberId: String, status: MembershipStatus)

    /** 회원 역할 변경 (창조자 단독 권한). */
    suspend fun setRole(memberId: String, role: Role)

    /**
     * 회원 프로필 변경. 권한 검증은 호출자 책임 (또는 Firestore Rules).
     *
     * 권한 모델:
     *  - [name], [avatarId], [weightClass]: 본인이 직접 변경 (체급은 본인만 — 관장 변경 불가)
     *  - [belt], [skills]: 관장이 회원에게 부여
     *
     * [weightClass] 변경 시 — 본인이 (gender, weightClass) 매핑으로 [avatarId] 도 함께 갱신해야 함.
     * 자동 동기화는 SessionViewModel 단에서 수행.
     */
    suspend fun updateProfile(
        memberId: String,
        name: String? = null,
        belt: Belt? = null,
        weightClass: WeightClass? = null,
        avatarId: String? = null,
        skills: SkillSet? = null,
    )

    /**
     * 회원 스킬 스냅샷(`member.skills`) 명시적 제거 — 모든 APPROVED skillScores 가 사라져
     * canonical 이 없는 상태에서 [assignSkills] 가 차트/AI 입력의 일관성을 위해 호출.
     *
     * `updateProfile(skills=null)` 은 의미상 "변경 없음" 이라 클리어용으로 쓸 수 없어 분리.
     * Firestore 구현은 `FieldValue.delete()` 로 필드 자체를 제거 → 클라이언트 매핑에서 null 로 읽힘.
     */
    suspend fun clearSkills(memberId: String)

    /**
     * 본인 FCM 디바이스 토큰 갱신 — [com.unboundapex.octalink.messaging.OctaLinkMessagingService.onNewToken]
     * 콜백에서 호출. self-only write 라 Firestore rules 의 `memberSelfEditableOnly` 화이트리스트 포함 필요.
     */
    suspend fun updateFcmToken(memberId: String, token: String?)

    /**
     * 본인 알림 ON/OFF 일괄 갱신 — ProfileScreen 토글에서 호출. 단일 호출로 전체 prefs 덮어쓰기.
     * key 는 [com.unboundapex.octalink.data.schema.NotificationType.name].
     */
    suspend fun updateNotificationPrefs(memberId: String, prefs: Map<String, Boolean>)

    /**
     * 본인 CLASS_REMINDER 슬롯 셋 일괄 갱신 — ProfileScreen 의 "수업 리마인더 설정" 다이얼로그에서 호출.
     * 각 항목은 `"MONDAY_19:30"` 형식 ([com.unboundapex.octalink.data.classSlotKey] 참조).
     * 비어있는 리스트 전달 시 알림 모두 해제.
     */
    suspend fun updateClassReminderSlots(memberId: String, slots: List<String>)

    /**
     * 본인 회원 탈퇴 — [com.unboundapex.octalink.data.schema.MembershipStatus.LEFT] 로 전환.
     * 도장 명단(MemberDoc) 자체 삭제는 CREATOR/MASTER 권한 (별도 요청).
     * Cloud Function `leaveMembership` 호출 (server-side audit trail).
     */
    suspend fun leaveMembership(memberId: String)

    /**
     * LEFT 상태에서 본인 재가입 — Role 재평가 (allowlist 매칭) 후 APPROVED 또는 PENDING.
     * Cloud Function `rejoinMembership` 호출. 이미 active 상태면 no-op.
     */
    suspend fun rejoinMembership(memberId: String)
}

/** 가입 폼 입력 결과 — 폼에서 사용자가 입력한 값 + 카카오 동의 항목에서 가져온 값 합산. */
data class SignupRequest(
    val authProviderId: String,
    /** 가입자가 드롭다운에서 선택한 소속 체육관 id. 서버 completeSignup 이 실재 여부 재검증. */
    val gymId: String,
    val name: String,
    val belt: Belt,
    val weightClass: WeightClass,
    val avatarId: String,
    /**
     * 체육관 입관일 (실제로 도장에 등록한 날). 앱 가입일(`MemberDoc.createdAt`)과 별개.
     * 이미 다니던 회원이 나중에 앱 가입하는 케이스 — 본인이 직접 과거 날짜로 입력.
     */
    val joinDate: LocalDate,
    val phone: String? = null,
    /** 카카오 동의 항목에서 가져온 부가 정보 — null 가능, 권한/미동의 시 비어옴 */
    val email: String? = null,
    val gender: String? = null,
    val ageRange: String? = null,
    val birthday: String? = null,
    val birthyear: String? = null,
)
