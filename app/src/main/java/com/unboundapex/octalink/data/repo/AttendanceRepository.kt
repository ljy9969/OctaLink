package com.unboundapex.octalink.data.repo

import com.unboundapex.octalink.data.schema.AttendanceDoc
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 출석 ([AttendanceDoc]) 영속화 추상화.
 *
 * Firestore 경로: `members/{memberId}/attendance/{classDate}` — classDate 가 doc ID 라
 * **하루 1 체크인** 이 자연스럽게 idempotent (재호출 시 같은 doc 갱신).
 * 1일 다중 수업 지원이 필요해지면 doc ID 를 `{classDate}-{classDefId}` 로 확장.
 *
 * 동료 출석 조회는 Firestore `collectionGroup("attendance")` + `whereEqualTo("classDate", ...)`
 * 로 한 번에 — 각 회원 서브컬렉션을 N번 쿼리하지 않음.
 * (firestore.indexes.json 에 classDate single-field index 필요)
 */
interface AttendanceRepository {
    /**
     * 특정 날짜 전체 출석 — 출석 동료 표시 + 운영진 일자별 검토.
     * Security Rules: APPROVED 회원 누구나 read 가능 (동료 가시 필요).
     */
    fun observeByDate(classDate: LocalDate): Flow<List<AttendanceDoc>>

    /** 특정 회원 출석 기록 — 본인 통계 / 운영진 회원별 검토. */
    fun observeByMember(memberId: String): Flow<List<AttendanceDoc>>

    /**
     * 체크인. 같은 날 재호출은 idempotent (서버 시각으로 checkInAt 갱신).
     * 본인만 자기 체크인 가능 (Security Rules: `isSelf(uid)` + `memberId == uid`).
     */
    suspend fun checkIn(
        memberId: String,
        classDefId: String,
        classDate: LocalDate,
    ): AttendanceDoc

    /**
     * 체크인 취소 — doc 삭제. 본인 또는 운영진.
     * Security Rules: `isSelf(uid) || isStaff()`.
     */
    suspend fun cancelCheckIn(memberId: String, classDate: LocalDate)

    /**
     * 운영진 출결 검토 — verified 플래그 토글.
     * 운영진이 회원 출석을 확인했음을 기록 (체크인 자체는 회원이 직접 누른 기록이고,
     * verified 는 코치/관장이 실제 수업 참석을 확인했다는 메타).
     * Security Rules: `isStaff()` 만 허용.
     */
    suspend fun setVerified(memberId: String, classDate: LocalDate, verified: Boolean)
}
