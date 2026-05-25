package com.unboundapex.octalink.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.WeeklyRoutineDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 주간 보강 루틴 — 홈 카드 + 상세 화면 공용 ViewModel.
 *
 *  - `routine`     : 현재 회원의 이번 주 루틴 실시간 (없으면 null).
 *  - `generate()`  : Cloud Function 수동 호출. 캐시가 있으면 그대로, force=true 면 재생성.
 *  - `setDrillState`: 드릴 done/skipped 토글.
 *
 * 회원 uid 는 [com.unboundapex.octalink.session.SessionStore] 에서 받아 매번 새로 세팅.
 * Repository 의 `observeCurrentWeek` 가 KST 기준 ISO 주키로 doc 을 잡아오므로,
 * 자정에 주가 바뀌어도 ViewModel 재구독 없이 다음 호출에서 자연스럽게 새 weekId 로 전환.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyRoutineViewModel : ViewModel() {
    private val repo = RepositoryProvider.weeklyRoutine

    private val _memberId = MutableStateFlow<String?>(null)
    fun bind(memberId: String?) { _memberId.value = memberId }

    val routine: StateFlow<WeeklyRoutineDoc?> =
        _memberId
            .flatMapLatest { id ->
                if (id.isNullOrBlank()) flowOf(null)
                else repo.observeCurrentWeek(id)
            }
            .catch { e ->
                android.util.Log.e(TAG, "observe error", e)
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _genState = MutableStateFlow<GenerateState>(GenerateState.Idle)
    val genState: StateFlow<GenerateState> = _genState.asStateFlow()

    fun resetGenState() { _genState.value = GenerateState.Idle }

    fun generate(difficulty: String, force: Boolean = false) {
        val id = _memberId.value ?: run {
            _genState.value = GenerateState.Error("로그인 정보가 없어요.")
            return
        }
        _genState.value = GenerateState.Loading
        viewModelScope.launch {
            runCatching { repo.generate(id, difficulty, force) }
                .onSuccess {
                    _genState.value = GenerateState.Done
                    android.util.Log.i(TAG, "generate success difficulty=$difficulty force=$force")
                }
                .onFailure { e ->
                    _genState.value = GenerateState.Error(e.message ?: "AI 루틴 생성 실패")
                    android.util.Log.e(TAG, "generate FAILED", e)
                }
        }
    }

    fun setDrillState(weekId: String, dayIndex: Int, drillIndex: Int, done: Boolean, skipped: Boolean) {
        val id = _memberId.value ?: return
        viewModelScope.launch {
            runCatching { repo.setDrillState(id, weekId, dayIndex, drillIndex, done, skipped) }
                .onFailure { e -> android.util.Log.e(TAG, "setDrillState FAILED", e) }
        }
    }

    private companion object {
        const val TAG = "OctaLink.WeeklyRoutine"
    }
}

sealed class GenerateState {
    data object Idle : GenerateState()
    data object Loading : GenerateState()
    data object Done : GenerateState()
    data class Error(val message: String) : GenerateState()
}
