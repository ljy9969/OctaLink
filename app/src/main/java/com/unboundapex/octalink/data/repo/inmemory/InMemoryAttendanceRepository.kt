package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.repo.AttendanceRepository
import com.unboundapex.octalink.data.schema.AttendanceDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * 출석 in-memory 저장소. Phase 1 토글 시 사용.
 *
 * 시드: 오늘 날짜로 6명 출석 (AttendanceScreen 의 기존 mock 명단과 매칭) — 가입 후 메인 진입
 * 즉시 동료 출석 카드가 풍성하게 보이도록.
 */
class InMemoryAttendanceRepository(
    seed: List<AttendanceDoc> = MockAttendanceSeed.todayMockAttendance(),
) : AttendanceRepository {
    private val _attendance = MutableStateFlow(seed)

    override fun observeByDate(classDate: LocalDate): Flow<List<AttendanceDoc>> =
        _attendance.map { list -> list.filter { it.classDate == classDate } }

    override fun observeByMember(memberId: String): Flow<List<AttendanceDoc>> =
        _attendance.map { list ->
            list.filter { it.memberId == memberId }
                .sortedByDescending { it.classDate }
        }

    override fun observeSince(classDate: LocalDate): Flow<List<AttendanceDoc>> =
        _attendance.map { list -> list.filter { !it.classDate.isBefore(classDate) } }

    override suspend fun checkIn(
        memberId: String,
        classDefId: String,
        classDate: LocalDate,
    ): AttendanceDoc {
        val docId = "$memberId-$classDate"
        val now = Instant.now()
        val existing = _attendance.value.firstOrNull {
            it.memberId == memberId && it.classDate == classDate
        }
        val doc = (existing ?: AttendanceDoc(
            id = docId,
            memberId = memberId,
            classDefId = classDefId,
            classDate = classDate,
            checkInAt = now,
        )).copy(checkInAt = now)

        _attendance.value = if (existing == null) {
            _attendance.value + doc
        } else {
            _attendance.value.map { if (it.id == existing.id) doc else it }
        }
        return doc
    }

    override suspend fun cancelCheckIn(memberId: String, classDate: LocalDate) {
        _attendance.value = _attendance.value.filterNot {
            it.memberId == memberId && it.classDate == classDate
        }
    }

    override suspend fun setVerified(memberId: String, classDate: LocalDate, verified: Boolean) {
        _attendance.value = _attendance.value.map {
            if (it.memberId == memberId && it.classDate == classDate) it.copy(verified = verified)
            else it
        }
    }
}

/** 시연용 시드. MockSeed 회원들에 매핑되는 오늘 출석 6건. */
internal object MockAttendanceSeed {
    fun todayMockAttendance(): List<AttendanceDoc> {
        val today = LocalDate.now()
        val baseTime = Instant.now()
        // MockSeed.kt 의 회원 id 와 매핑 (memberPool 의 id 형식 `pool-{xxx}`)
        return listOf(
            "pool-l01" to 0L,    // 박정호
            "pool-m01" to 60L,   // 김상혁
            "pool-f02" to 120L,  // 정유진
            "pool-f03" to 180L,  // 신예린
            "pool-l02" to 240L,  // 한도윤
            "pool-l03" to 300L,  // 최민서
        ).map { (memberId, offsetSec) ->
            AttendanceDoc(
                id = "$memberId-$today",
                memberId = memberId,
                classDefId = "default",
                classDate = today,
                checkInAt = baseTime.minusSeconds(3600 + offsetSec),
            )
        }
    }
}
