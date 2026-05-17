package com.unboundapex.octalink.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.WeeklyMissionDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 도장 주간 미션 — 홈 카드 표시 + 운영진 작성/수정 다이얼로그 공용.
 *
 * 실시간 구독으로 한 운영진이 수정하면 모든 회원 홈 카드 즉시 갱신.
 */
class WeeklyMissionViewModel : ViewModel() {
    private val repo = RepositoryProvider.weeklyMission

    val mission: StateFlow<WeeklyMissionDoc?> =
        repo.observe()
            .catch { e ->
                android.util.Log.e("OctaLink.WeeklyMission", "observe error", e)
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun resetSaveState() { _saveState.value = SaveState.Idle }

    fun save(text: String, byMemberName: String) {
        val trimmed = text.trim().take(500)
        if (trimmed.isBlank()) {
            _saveState.value = SaveState.Error("미션 내용을 입력하세요.")
            return
        }
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching { repo.update(trimmed, byMemberName) }
                .onSuccess {
                    _saveState.value = SaveState.Done
                    android.util.Log.i("OctaLink.WeeklyMission", "save success")
                }
                .onFailure { e ->
                    _saveState.value = SaveState.Error(e.message ?: "주간 미션 저장 실패")
                    android.util.Log.e("OctaLink.WeeklyMission", "save FAILED", e)
                }
        }
    }
}

sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data object Done : SaveState()
    data class Error(val message: String) : SaveState()
}
