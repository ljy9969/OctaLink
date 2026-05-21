package com.unboundapex.octalink.ui.screens.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.PostCommentDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 글 단위 댓글 ViewModel — 카드 펼침 시 [observeFor] 로 listener 활성화.
 *
 * CommunityScreen 의 카드별로 인스턴스 1개씩 들고 있어도 비용 작음 (펼침 안 된 카드는
 * [observeFor] 호출 안 하니 Firestore listener 도 안 붙음).
 *
 * 카운트 배지([commentCounts]) 는 별도 [PostsViewModel] 에서 collectionGroup 한 번에 처리.
 */
class PostCommentsViewModel : ViewModel() {
    private val repo = RepositoryProvider.postComments

    private val _postId = MutableStateFlow<String?>(null)

    fun observeFor(postId: String?) {
        _postId.value = postId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments: StateFlow<List<PostCommentDoc>> =
        _postId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList())
                else repo.observeForPost(id)
            }
            .catch { e ->
                android.util.Log.e("OctaLink.PostComments", "comments flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState

    fun resetSubmitState() { _submitState.value = SubmitState.Idle }

    fun submit(
        postId: String,
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        body: String,
    ) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) {
            _submitState.value = SubmitState.Error("내용을 입력하세요.")
            return
        }
        _submitState.value = SubmitState.Submitting
        viewModelScope.launch {
            runCatching {
                repo.create(
                    postId = postId,
                    authorId = authorId,
                    authorName = authorName,
                    authorBelt = authorBelt,
                    body = trimmed.take(80),
                )
            }.onSuccess {
                _submitState.value = SubmitState.Done
                android.util.Log.i("OctaLink.PostComments", "submit ok postId=$postId id=${it.id}")
            }.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "댓글 작성 실패")
                android.util.Log.e("OctaLink.PostComments", "submit FAILED", e)
            }
        }
    }

    fun delete(postId: String, commentId: String) {
        viewModelScope.launch {
            runCatching { repo.delete(postId, commentId) }
                .onFailure { android.util.Log.e("OctaLink.PostComments", "delete FAILED", it) }
        }
    }

    /** 댓글 본문 수정. 100자 제한은 호출자(UI) + 여기 안전망 둘 다. */
    fun edit(postId: String, commentId: String, body: String) {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching { repo.update(postId, commentId, trimmed.take(80)) }
                .onFailure { android.util.Log.e("OctaLink.PostComments", "edit FAILED", it) }
        }
    }

    /** 댓글 좋아요 토글 — likedBy 에 본인 id 추가/제거. */
    fun toggleLike(postId: String, commentId: String, memberId: String) {
        viewModelScope.launch {
            runCatching { repo.toggleLike(postId, commentId, memberId) }
                .onFailure { android.util.Log.e("OctaLink.PostComments", "toggleLike FAILED", it) }
        }
    }

    sealed class SubmitState {
        data object Idle : SubmitState()
        data object Submitting : SubmitState()
        data object Done : SubmitState()
        data class Error(val message: String) : SubmitState()
    }
}
