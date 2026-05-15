package com.unboundapex.octalink.ui.screens.community

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.media.ImageUploader
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * CommunityScreen 의 글 목록 + 작성 + 좋아요 워크플로.
 *
 * 정렬: NOTICE 우선(상단 고정) → 그 다음 createdAt DESC. composite index 회피용 client-side sort.
 *
 * 작성 진행 상태([WriteState]) 는 다이얼로그 UI 에서 progress / error 표시용.
 */
class PostsViewModel : ViewModel() {
    private val posts = RepositoryProvider.posts

    val sortedPosts: StateFlow<List<PostDoc>> = posts.observeAll()
        .map { list ->
            list.sortedWith(
                compareByDescending<PostDoc> { it.tag == PostTag.NOTICE }
                    .thenByDescending { it.createdAt }
            )
        }
        .catch { e ->
            android.util.Log.e("OctaLink.Posts", "sortedPosts flow error", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _writeState = MutableStateFlow<WriteState>(WriteState.Idle)
    val writeState: StateFlow<WriteState> = _writeState.asStateFlow()

    fun resetWriteState() { _writeState.value = WriteState.Idle }

    /**
     * 글 작성. 이미지 첨부가 있으면 Firebase Storage 업로드 후 download URL 을 doc 에 저장.
     * 이미지 압축/사이즈 검증은 [ImageUploader] 가 처리.
     */
    fun submitPost(
        context: Context,
        authorId: String,
        authorName: String,
        authorBelt: Belt,
        title: String,
        body: String,
        tag: PostTag,
        imageUri: Uri?,
    ) {
        if (body.isBlank()) {
            _writeState.value = WriteState.Error("본문을 입력하세요.")
            return
        }
        _writeState.value = WriteState.Uploading
        viewModelScope.launch {
            runCatching {
                val imageUrl = imageUri?.let { ImageUploader.uploadPostImage(context, it) }
                posts.create(
                    authorId = authorId,
                    authorName = authorName,
                    authorBelt = authorBelt,
                    title = title.trim().take(80),
                    body = body.trim().take(2000),
                    tag = tag,
                    imageUrl = imageUrl,
                )
            }.onSuccess {
                _writeState.value = WriteState.Done
                android.util.Log.i("OctaLink.Posts", "submitPost success: id=${it.id}")
            }.onFailure { e ->
                _writeState.value = WriteState.Error(e.message ?: "글 작성 실패")
                android.util.Log.e("OctaLink.Posts", "submitPost FAILED", e)
            }
        }
    }

    fun toggleLike(postId: String, memberId: String) {
        viewModelScope.launch {
            runCatching { posts.toggleLike(postId, memberId) }
                .onFailure { android.util.Log.e("OctaLink.Posts", "toggleLike FAILED", it) }
        }
    }

    fun delete(postId: String) {
        viewModelScope.launch {
            runCatching { posts.delete(postId) }
                .onFailure { android.util.Log.e("OctaLink.Posts", "delete FAILED", it) }
        }
    }
}

sealed class WriteState {
    data object Idle : WriteState()
    data object Uploading : WriteState()
    data object Done : WriteState()
    data class Error(val message: String) : WriteState()
}
