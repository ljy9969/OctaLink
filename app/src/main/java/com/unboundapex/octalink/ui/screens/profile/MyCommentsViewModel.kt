package com.unboundapex.octalink.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.CommentDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * ProfileScreen 의 "관장님 한 줄 코멘트" 카드 — 본인이 받은 코멘트 시계열.
 *
 * [memberId] 가 set 된 뒤에야 실제 구독 시작. SessionViewModel 의 member id 를 비동기로 받기 위함.
 */
class MyCommentsViewModel : ViewModel() {
    private val commentsRepo = RepositoryProvider.comments

    private val _memberId = MutableStateFlow<String?>(null)

    fun observeFor(memberId: String?) {
        _memberId.value = memberId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val myComments: StateFlow<List<CommentDoc>> =
        _memberId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else commentsRepo.observeByMember(id)
            }
            .catch { e ->
                android.util.Log.e("OctaLink.Comments", "myComments flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
