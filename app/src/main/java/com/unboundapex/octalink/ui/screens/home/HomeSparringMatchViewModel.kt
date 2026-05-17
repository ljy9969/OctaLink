package com.unboundapex.octalink.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 홈 "스파링 매치" 카드 데이터 — 이번 주(월~일, KST) 안에 추첨됐고 **아직 진행 중**인
 * 가장 최신 토너먼트 한 건.
 *
 * 필터:
 *  - `drawAt >= weekStart` — 이번 주 추첨만
 *  - `finishedAt == null` — 종료된 토너먼트는 "스파링 매치"가 아닌 히스토리 영역. 카드에서 제거.
 *
 * 매칭 doc 없으면 null → UI 는 placeholder 노출.
 */
data class HomeSparringMatch(
    /** 추첨 시각 — 카드 meta 의 날짜 부분. */
    val drawnAt: Instant,
    /** 토너먼트 제목 (예: "라이트급 화이트 벨트"). meta 의 뒤쪽 부분. */
    val title: String,
)

class HomeSparringMatchViewModel : ViewModel() {
    private val repo = RepositoryProvider.tournaments
    private val seoul = ZoneId.of("Asia/Seoul")
    private val weekStart: LocalDate =
        LocalDate.now(seoul).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val match: StateFlow<HomeSparringMatch?> =
        repo.observeAll()
            .map { tournaments ->
                tournaments.asSequence()
                    .filter { it.finishedAt == null } // 진행 중만
                    .filter { !it.drawAt.atZone(seoul).toLocalDate().isBefore(weekStart) }
                    .maxByOrNull { it.drawAt }
                    ?.let { t -> HomeSparringMatch(drawnAt = t.drawAt, title = t.title) }
            }
            .catch { e ->
                android.util.Log.e("OctaLink.HomeSparring", "match flow error", e)
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
