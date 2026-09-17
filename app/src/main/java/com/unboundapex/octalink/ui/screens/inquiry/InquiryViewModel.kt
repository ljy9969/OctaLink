package com.unboundapex.octalink.ui.screens.inquiry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.InquiryCategory
import com.unboundapex.octalink.data.schema.InquiryDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * 1:1 문의(고객의 소리) 작성 + 내 문의 목록.
 *
 * 답변은 support 에이전트 초안 → 운영자 승인(ops·rules) 후 게시되며, 여기서는 읽기만 한다.
 * 작성 진행 상태([InquiryWriteState]) 는 폼 UI 의 진행/에러 표시용.
 */
class InquiryViewModel : ViewModel() {
    private val repo = RepositoryProvider.inquiries

    /** 내 문의 흐름 — 화면이 authorId 로 한 번 만들어 collect. */
    fun mine(authorId: String): Flow<List<InquiryDoc>> =
        repo.observeMine(authorId)
            .catch { e ->
                android.util.Log.e("OctaLink.Inquiry", "mine flow error", e)
                emit(emptyList())
            }

    private val _writeState = MutableStateFlow<InquiryWriteState>(InquiryWriteState.Idle)
    val writeState: StateFlow<InquiryWriteState> = _writeState.asStateFlow()

    fun resetWriteState() { _writeState.value = InquiryWriteState.Idle }

    fun submit(authorId: String, authorName: String, category: InquiryCategory, text: String) {
        if (text.isBlank()) {
            _writeState.value = InquiryWriteState.Error("내용을 입력해 주세요.")
            return
        }
        _writeState.value = InquiryWriteState.Sending
        viewModelScope.launch {
            runCatching {
                repo.create(
                    authorId = authorId,
                    authorName = authorName,
                    category = category,
                    text = text.trim().take(1000),
                )
            }.onSuccess {
                _writeState.value = InquiryWriteState.Done
                android.util.Log.i("OctaLink.Inquiry", "submit success: id=${it.id}")
            }.onFailure { e ->
                _writeState.value = InquiryWriteState.Error(e.message ?: "문의 전송에 실패했습니다.")
                android.util.Log.e("OctaLink.Inquiry", "submit FAILED", e)
            }
        }
    }
}

sealed class InquiryWriteState {
    data object Idle : InquiryWriteState()
    data object Sending : InquiryWriteState()
    data object Done : InquiryWriteState()
    data class Error(val message: String) : InquiryWriteState()
}
