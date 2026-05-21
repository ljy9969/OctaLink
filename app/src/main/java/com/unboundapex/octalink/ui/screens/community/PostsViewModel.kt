package com.unboundapex.octalink.ui.screens.community

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.media.ImageUploader
import com.unboundapex.octalink.data.media.VideoUploader
import com.unboundapex.octalink.data.repo.RepositoryProvider
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import com.unboundapex.octalink.data.schema.PostVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * CommunityScreen 의 글 목록 + 작성 + 좋아요 워크플로.
 *
 * 정렬: NOTICE 우선(상단 고정) → 그 다음 createdAt DESC. composite index 회피용 client-side sort.
 *
 * 작성 진행 상태([WriteState]) 는 다이얼로그 UI 에서 progress / error 표시용.
 */
class PostsViewModel : ViewModel() {
    private val posts = RepositoryProvider.posts
    private val postComments = RepositoryProvider.postComments
    private val members = RepositoryProvider.members

    /**
     * 멘션 picker 가 노출할 회원 목록 — APPROVED 만. 본인 제외는 호출자(UI) 책임.
     * 12명 규모 비공개 베타 가정 — 페이지네이션 / 검색 인덱스 불필요.
     */
    val approvedMembers: StateFlow<List<com.unboundapex.octalink.data.schema.MemberDoc>> =
        members.observeByStatus(com.unboundapex.octalink.data.schema.MembershipStatus.APPROVED)
            .catch { e ->
                android.util.Log.e("OctaLink.Posts", "approvedMembers flow error", e)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 피드 정렬 + 가시성 필터.
     * - PUBLIC: 모두에게 노출
     * - PENDING_APPROVAL: 작성자 본인 + 멘션된 회원만 노출 (Firestore rules 도 동일 강제)
     * - REJECTED: 작성자 본인 + 멘션된 회원만 (정리/재게시 결정 컨텍스트)
     *
     * NOTICE 우선 → createdAt DESC.
     */
    fun sortedPostsFor(currentMemberId: String?): kotlinx.coroutines.flow.Flow<List<PostDoc>> =
        posts.observeAll()
            .map { list ->
                list
                    .filter { p ->
                        when (p.visibility) {
                            PostVisibility.PUBLIC -> true
                            else -> currentMemberId != null &&
                                (p.authorId == currentMemberId || currentMemberId in p.mentionedMemberIds)
                        }
                    }
                    .sortedWith(
                        compareByDescending<PostDoc> { it.tag == PostTag.NOTICE }
                            .thenByDescending { it.createdAt }
                    )
            }
            .catch { e ->
                android.util.Log.e("OctaLink.Posts", "sortedPosts flow error", e)
                emit(emptyList())
            }

    /**
     * 호환용 — 가시성 필터 없는 전체 정렬 피드. 신규 코드는 [sortedPostsFor] 사용 권장.
     * (PENDING/REJECTED 도 보이므로 Firestore rules read 권한이 없는 doc 은 자동으로 client 에서 빠짐)
     */
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

    /** Map<postId, 댓글 수> — 카드의 "댓글 N" 배지. 모든 글 댓글 한 listener 로 집계. */
    val commentCounts: StateFlow<Map<String, Int>> = postComments.observeCommentCounts()
        .catch { e ->
            android.util.Log.e("OctaLink.Posts", "commentCounts flow error", e)
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _writeState = MutableStateFlow<WriteState>(WriteState.Idle)
    val writeState: StateFlow<WriteState> = _writeState.asStateFlow()

    fun resetWriteState() { _writeState.value = WriteState.Idle }

    /**
     * 글 작성. 이미지 또는 영상 첨부가 있으면 Firebase Storage 업로드 후 download URL 을 doc 에 저장.
     * 미디어는 상호 배타 — UI 가 동시에 둘 다 선택 못 하게 강제, VM 도 안전망으로 imageUri 우선.
     *
     * 일일 업로드 제한 — 계정당 이미지 1건/하루, 영상 1건/하루 (독립 카운트). KST 기준.
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
        videoUri: Uri? = null,
        mentionedMemberIds: List<String> = emptyList(),
    ) {
        if (title.isBlank()) {
            _writeState.value = WriteState.Error("제목을 입력하세요.")
            return
        }
        if (body.isBlank()) {
            _writeState.value = WriteState.Error("본문을 입력하세요.")
            return
        }
        // 동시 첨부 차단 — UI 도 mutual exclusion 강제하지만 안전망.
        if (imageUri != null && videoUri != null) {
            _writeState.value = WriteState.Error("이미지와 영상을 동시에 첨부할 수 없습니다.")
            return
        }
        // 일일 미디어 업로드 제한 — 계정당 이미지 1건/하루, 영상 1건/하루 (독립).
        // sortedPosts 는 CommunityScreen 가 구독 중이라 최신 값 보유. KST 기준 날짜 비교.
        // 클라이언트 사이드 체크만 — 같은 순간 두 번 빠르게 submit 시도 등의 race 는 허용 (작은 도장 가정).
        val todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"))
        if (imageUri != null) {
            val alreadyUploadedToday = sortedPosts.value.any { post ->
                post.authorId == authorId &&
                    post.imageUrl != null &&
                    post.createdAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate() == todayKst
            }
            if (alreadyUploadedToday) {
                _writeState.value = WriteState.Error("오늘 이미지 업로드 한도(1건)를 초과했습니다.\n내일 다시 시도해주세요.")
                return
            }
        }
        if (videoUri != null) {
            val alreadyUploadedToday = sortedPosts.value.any { post ->
                post.authorId == authorId &&
                    post.videoUrl != null &&
                    post.createdAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDate() == todayKst
            }
            if (alreadyUploadedToday) {
                _writeState.value = WriteState.Error("오늘 영상 업로드 한도(1건)를 초과했습니다.\n내일 다시 시도해주세요.")
                return
            }
        }
        _writeState.value = WriteState.Uploading
        viewModelScope.launch {
            runCatching {
                val imageUrl = imageUri?.let { ImageUploader.uploadPostImage(context, it) }
                val videoUrl = videoUri?.let { VideoUploader.uploadPostVideo(context, it) }
                posts.create(
                    authorId = authorId,
                    authorName = authorName,
                    authorBelt = authorBelt,
                    title = title.trim().take(80),
                    body = body.trim().take(2000),
                    tag = tag,
                    imageUrl = imageUrl,
                    videoUrl = videoUrl,
                    mentionedMemberIds = mentionedMemberIds,
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

    /**
     * 글 수정 — title/body/tag 변경. 이미지/작성자/시각은 불변.
     * 권한(작성자 본인 또는 운영진) 은 Firestore rules 에서 검증.
     */
    fun updatePost(
        postId: String,
        title: String,
        body: String,
        tag: PostTag,
    ) {
        if (title.isBlank()) {
            _writeState.value = WriteState.Error("제목을 입력하세요.")
            return
        }
        if (body.isBlank()) {
            _writeState.value = WriteState.Error("본문을 입력하세요.")
            return
        }
        _writeState.value = WriteState.Uploading
        viewModelScope.launch {
            runCatching {
                posts.update(
                    postId = postId,
                    title = title.trim().take(80),
                    body = body.trim().take(2000),
                    tag = tag,
                )
            }.onSuccess {
                _writeState.value = WriteState.Done
                android.util.Log.i("OctaLink.Posts", "updatePost success: id=$postId")
            }.onFailure { e ->
                _writeState.value = WriteState.Error(e.message ?: "글 수정 실패")
                android.util.Log.e("OctaLink.Posts", "updatePost FAILED", e)
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

    /** 멘션된 회원의 공개 승인. UI 의 "공개 승인" 버튼 onClick. */
    fun approveMention(postId: String, memberId: String) {
        viewModelScope.launch {
            runCatching { posts.approveMention(postId, memberId) }
                .onFailure { android.util.Log.e("OctaLink.Posts", "approveMention FAILED", it) }
        }
    }

    /** 멘션된 회원의 공개 거부 — 글이 REJECTED 로 잠금. */
    fun rejectMention(postId: String, memberId: String) {
        viewModelScope.launch {
            runCatching { posts.rejectMention(postId, memberId) }
                .onFailure { android.util.Log.e("OctaLink.Posts", "rejectMention FAILED", it) }
        }
    }
}

sealed class WriteState {
    data object Idle : WriteState()
    data object Uploading : WriteState()
    data object Done : WriteState()
    data class Error(val message: String) : WriteState()
}
