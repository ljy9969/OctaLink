package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.SkillSet
import com.unboundapex.octalink.data.schema.SkillScoreDoc
import com.unboundapex.octalink.data.schema.SkillScoreStatus
import kotlinx.coroutines.flow.Flow

/**
 * 스킬 점수 ([SkillScoreDoc]) 영속화 추상화.
 *
 * Firestore 경로: `members/{memberId}/skillScores/{scoreId}` — 평가 대상 회원 서브컬렉션.
 * 권한 (Security Rules):
 *  - read: 본인 + 운영진(`isStaff`)
 *  - create: 운영진만, status 는 항상 `PROPOSED` (감사 추적: 관장 본인 입력도 일단 PROPOSED 거쳐 확정)
 *  - update: 관장만, PROPOSED → APPROVED/REJECTED 전환. 점수 자체는 immutable.
 *  - delete: 관장만
 *
 * 워크플로:
 *   1. 코치/관장 [propose] → 새 doc, status=PROPOSED
 *   2. 관장 [approve]/[reject] → status 전이 + reviewedByMasterId/At 기록
 *   3. 회원 프로필은 가장 최근 APPROVED 스냅샷을 표시 ([observeLatestApproved])
 *   4. 운영진 리뷰 큐는 [observePendingAcrossAllMembers] (collectionGroup)
 */
interface SkillScoreRepository {
    /** 특정 회원의 모든 스킬 점수 (status 무관, evaluatedAt DESC). 본인/운영진 read. */
    fun observeByMember(memberId: String): Flow<List<SkillScoreDoc>>

    /**
     * 특정 회원의 가장 최근 APPROVED 점수. 차트/프로필 카드 표시용. null 이면 미평가 또는 모두 PROPOSED/REJECTED.
     * [observeByMember] 결과를 클라이언트 필터링해도 되지만, 차트만 필요할 땐 이쪽이 의도가 명확.
     */
    fun observeLatestApproved(memberId: String): Flow<SkillScoreDoc?>

    /**
     * 전 회원 PROPOSED 점수 큐 (관장 리뷰 대기 목록).
     * `collectionGroup("skillScores")` + `whereEqualTo("status", "PROPOSED")` — 평가일 ASC (오래된 것부터).
     * Rules: `match /{path=**}/skillScores/{scoreId}` recursive wildcard 로 별도 read 허용 필요.
     */
    fun observePendingAcrossAllMembers(): Flow<List<SkillScoreDoc>>

    /**
     * 운영진이 [memberId] 회원의 스킬 점수 제안. id 는 Firestore 자동 생성, evaluatedAt 은 서버 timestamp.
     * status 는 항상 `PROPOSED` 로 시작.
     *
     * @param byUserId 입력자 (request.auth.uid). Rules 가 일치 강제.
     */
    suspend fun propose(
        memberId: String,
        byUserId: String,
        skills: SkillSet,
    ): SkillScoreDoc

    /**
     * 관장이 PROPOSED 점수를 검토 결과로 전이. APPROVED 또는 REJECTED 만 허용.
     * Rules: PROPOSED 상태 doc 만 수정 가능 + 변경 필드는 status/reviewedByMasterId/reviewedAt 로 제한.
     *
     * @param newStatus [SkillScoreStatus.APPROVED] / [SkillScoreStatus.REJECTED]. PROPOSED 전달 시 예외.
     */
    suspend fun setStatus(
        memberId: String,
        scoreId: String,
        reviewedByMasterId: String,
        newStatus: SkillScoreStatus,
    )

    /** 점수 삭제 — 관장만 (Rules 강제). 오입력 정정 등. */
    suspend fun delete(memberId: String, scoreId: String)
}
