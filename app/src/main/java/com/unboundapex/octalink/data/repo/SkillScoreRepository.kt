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
 *   1. 코치 [propose] → 새 doc, status=PROPOSED (관장 검토 대기 — 코치 입력은 반드시 승인 경유)
 *   2. 관장 [directApprove] → 새 doc, status=APPROVED (제안 단계 생략 — 관장 직접 평가)
 *   3. 관장 [setStatus] → 코치 제안의 PROPOSED → APPROVED/REJECTED 전이 + reviewedByMasterId/At 기록
 *   4. 회원 프로필 차트는 [getCanonicalApproved] 가 결정 — APPROVED 중 evaluatedAt 최신.
 *      누가 입력했는지 무관, 가장 최근 평가가 현재 실력.
 *   5. 운영진 리뷰 큐는 [observePendingAcrossAllMembers] (collectionGroup)
 */
interface SkillScoreRepository {
    /** 특정 회원의 모든 스킬 점수 (status 무관, evaluatedAt DESC). 본인/운영진 read. */
    fun observeByMember(memberId: String): Flow<List<SkillScoreDoc>>

    /**
     * 회원 프로필 차트의 단일 원본 — APPROVED 중 evaluatedAt 최신. 없으면 null.
     * 작성 주체(관장 직접 평가 vs 코치 제안→승인)는 구분하지 않음 — 가장 최근 평가가 곧 현재 실력.
     * Flow 라서 콘솔에서 직접 점수 doc 을 삭제/수정해도 listener 가 emit → 차트 즉시 갱신.
     */
    fun observeCanonicalApproved(memberId: String): Flow<SkillScoreDoc?>

    /**
     * [observeCanonicalApproved] 의 단발 버전.
     * APPROVED 중 evaluatedAt 최신, 없으면 null. 작성 주체 구분 없음.
     */
    suspend fun getCanonicalApproved(memberId: String): SkillScoreDoc?

    /**
     * 관장(MASTER/CREATOR) 직접 평가 — 제안 단계 없이 곧장 status=APPROVED 로 생성.
     * id 는 Firestore 자동 생성, evaluatedAt = reviewedAt = 서버 timestamp.
     * Rules 가 `isMaster() && status == 'APPROVED' && reviewedByMasterId == auth.uid` 강제.
     *
     * @param byUserId 작성한 관장 본인의 uid (= reviewedByMasterId).
     */
    suspend fun directApprove(
        memberId: String,
        byUserId: String,
        skills: SkillSet,
    ): SkillScoreDoc

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

    /**
     * 회원의 모든 PROPOSED 점수를 일괄 REJECTED 처리. 관장 직접 평가 직후 호출하면
     * 검토 큐에 남은 코치 제안이 자동 정리되어 의도가 명확해짐.
     * Idempotent — 대상 없으면 no-op. 각 doc 은 기존 [setStatus] 와 동일 룰로 검증.
     */
    suspend fun rejectAllPending(memberId: String, byMasterId: String)
}
