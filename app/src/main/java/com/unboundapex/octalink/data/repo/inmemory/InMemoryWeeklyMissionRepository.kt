package com.unboundapex.octalink.data.repo.inmemory

import com.unboundapex.octalink.data.repo.WeeklyMissionRepository
import com.unboundapex.octalink.data.schema.WeeklyMissionDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** Mock 단계용. 시드 미션 1건 미리 들어가 있어 홈에서 빈 카드 안 보이게. */
class InMemoryWeeklyMissionRepository(
    seed: WeeklyMissionDoc? = WeeklyMissionDoc(
        text = "• 체육관 3회 출석\n• 스파링 1라운드 이상\n• 개인 영상 1개 업로드",
        updatedAt = Instant.now(),
        updatedByName = "관장 김파시",
    ),
) : WeeklyMissionRepository {
    private val _doc = MutableStateFlow(seed)
    override fun observe(): Flow<WeeklyMissionDoc?> = _doc.asStateFlow()

    override suspend fun update(text: String, byMemberName: String) {
        _doc.value = WeeklyMissionDoc(
            text = text,
            updatedAt = Instant.now(),
            updatedByName = byMemberName,
        )
    }
}
