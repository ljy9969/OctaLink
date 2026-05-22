package com.unboundapex.octalink.ui.screens.community

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.schema.PostCommentDoc
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import com.unboundapex.octalink.data.schema.isMaster
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CommunityScreen(
    sessionVm: SessionViewModel,
    postsVm: PostsViewModel = viewModel(),
) {
    val session by sessionVm.state.collectAsState()
    // visibility 필터링된 피드 — 본인 + 멘션된 PENDING/REJECTED 만 노출.
    val myId = session.member?.id
    val posts by remember(myId) { postsVm.sortedPostsFor(myId) }
        .collectAsState(initial = emptyList())
    val commentCounts by postsVm.commentCounts.collectAsState()
    val approvedMembers by postsVm.approvedMembers.collectAsState()
    val writeState by postsVm.writeState.collectAsState()
    val context = LocalContext.current
    var dialogTag by remember { mutableStateOf<PostTag?>(null) }
    var editingPost by remember { mutableStateOf<PostDoc?>(null) }
    // 펼친 글의 id 셋 — 스크롤 후 다시 보여도 상태 유지. 동시에 여러 글 펼치기 허용.
    var expandedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    PosseScreen(title = "Community", subtitle = "팀원들의 기록과 응원") {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 글 작성 진입점 — 모든 회원 일반 글, 운영진은 공지 작성 추가
            item {
                PosseCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComposeAction(
                            label = "+ 글 쓰기",
                            modifier = Modifier.weight(1f),
                            onClick = { dialogTag = PostTag.RECORD },
                        )
                        if (session.role.isStaff) {
                            ComposeAction(
                                label = "+ 공지 작성",
                                modifier = Modifier.weight(1f),
                                onClick = { dialogTag = PostTag.NOTICE },
                                bg = MaterialTheme.colorScheme.primary,
                                fg = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
            items(posts, key = { it.id }) { p ->
                // 수정: 작성자 본인 전용 — 운영진이라도 남의 글 본문 편집 금지(데이터 무결성).
                // 삭제: 작성자 본인 + 관장급(MASTER/CREATOR) — 모더레이션용. 코치는 제외.
                val isAuthor = session.member?.id == p.authorId
                val isExpanded = p.id in expandedPostIds
                val meMentioned = myId != null && myId in p.mentionedMemberIds
                PostCard(
                    post = p,
                    myMemberId = session.member?.id,
                    myMemberName = session.member?.name,
                    myMemberBelt = session.member?.belt,
                    commentCount = commentCounts[p.id] ?: 0,
                    isExpanded = isExpanded,
                    canEdit = isAuthor,
                    canDelete = isAuthor || session.role.isMaster,
                    canModerateComments = session.role.isMaster,
                    canApproveMention = meMentioned && myId in p.pendingApprovalFrom,
                    onToggleLike = {
                        session.member?.id?.let { postsVm.toggleLike(p.id, it) }
                    },
                    onToggleExpand = {
                        expandedPostIds = if (isExpanded) expandedPostIds - p.id
                        else expandedPostIds + p.id
                    },
                    onEdit = { editingPost = p },
                    onDelete = { postsVm.delete(p.id) },
                    onApproveMention = {
                        myId?.let { postsVm.approveMention(p.id, it) }
                    },
                    onRejectMention = {
                        myId?.let { postsVm.rejectMention(p.id, it) }
                    },
                )
            }
        }
    }

    // 작성 모드(dialogTag) 또는 수정 모드(editingPost) 둘 중 하나면 다이얼로그 표시
    val effectiveTag = editingPost?.tag ?: dialogTag
    if (effectiveTag != null) {
        WritePostDialog(
            initialTag = effectiveTag,
            isStaff = session.role.isStaff,
            writeState = writeState,
            approvedMembers = approvedMembers,
            myMemberId = myId,
            editing = editingPost,
            onSubmit = { title, body, tag, imageUri, videoUri, mentions ->
                val editTarget = editingPost
                if (editTarget != null) {
                    // 수정 모드 — 멘션 변경은 v1 미지원 (편집은 본문만).
                    postsVm.updatePost(
                        postId = editTarget.id,
                        title = title,
                        body = body,
                        tag = tag,
                    )
                } else {
                    val member = session.member ?: return@WritePostDialog
                    postsVm.submitPost(
                        context = context,
                        authorId = member.id,
                        authorName = member.name,
                        authorBelt = member.belt,
                        title = title,
                        body = body,
                        tag = tag,
                        imageUri = imageUri,
                        videoUri = videoUri,
                        mentionedMemberIds = mentions,
                    )
                }
            },
            onDismiss = {
                dialogTag = null
                editingPost = null
                postsVm.resetWriteState()
            },
            onResetError = { postsVm.resetWriteState() },
        )

        // 작성/수정 성공 시 다이얼로그 자동 종료
        LaunchedEffect(writeState) {
            if (writeState is WriteState.Done) {
                dialogTag = null
                editingPost = null
                postsVm.resetWriteState()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Post 카드
// ─────────────────────────────────────────────────────────────────────────────

private val seoul = ZoneId.of("Asia/Seoul")

/** 글 카드 본문 미리보기 줄 수. 초과분은 ellipsis 로 잘림 — 펼치기는 별도 상세 화면에서 처리. */
private const val BODY_PREVIEW_LINES = 2
// 날짜는 한국 요일 약자("토"), 시간은 AM/PM 강제 → 두 formatter 를 별도 Locale 로 구성.
private val postDateFmt = DateTimeFormatter.ofPattern("M/d(EEE)", java.util.Locale.KOREAN)
private val postTimeFmt = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)

private fun postTimestamp(createdAt: Instant): String {
    val z = createdAt.atZone(seoul)
    return "${z.format(postDateFmt)} ${z.format(postTimeFmt)}"
}

// YouTube 링크 → 썸네일 카드. 본문 안에 youtube.com/watch / youtu.be / shorts / embed URL 이
// 섞여 있으면 첫 번째 것만 잡아 11자 video id 를 돌려준다. id 만 있으면 hqdefault 썸네일과
// 원본 시청 URL 둘 다 deterministic 으로 만들 수 있다.
private val YOUTUBE_URL_REGEX = Regex(
    """(?:https?://)?(?:www\.|m\.)?(?:youtube\.com/(?:watch\?[^\s]*?v=|shorts/|embed/|v/)|youtu\.be/)([A-Za-z0-9_-]{11})""",
    RegexOption.IGNORE_CASE,
)

private fun extractYouTubeVideoId(text: String): String? =
    YOUTUBE_URL_REGEX.find(text)?.groupValues?.getOrNull(1)

@Composable
private fun YouTubeThumbnail(videoId: String) {
    val context = LocalContext.current
    val thumbUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    val watchUrl = "https://www.youtube.com/watch?v=$videoId"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        android.util.Log.w("OctaLink.Posts", "YouTube 외부 열기 실패 url=$watchUrl", it)
                    }
            },
    ) {
        AsyncImage(
            model = thumbUrl,
            contentDescription = "YouTube 영상 썸네일",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 가운데 ▶ 배지 — 클릭 시 YouTube 로 이동한다는 시각 단서. 빨강은 브랜드 색.
        Text(
            "▶",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC_FF_00_00))
                .padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PostCard(
    post: PostDoc,
    myMemberId: String?,
    myMemberName: String?,
    myMemberBelt: Belt?,
    commentCount: Int,
    isExpanded: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canModerateComments: Boolean,
    canApproveMention: Boolean,
    onToggleLike: () -> Unit,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApproveMention: () -> Unit,
    onRejectMention: () -> Unit,
) {
    val likedByMe = myMemberId != null && myMemberId in post.likedBy
    // title 이 tag 라벨(공지/질문/팁/기록)과 동일하면 chip 과 중복이라 표시하지 않음
    val showTitle = post.title.isNotBlank() &&
        post.title.trim() != post.tag.label()
    val isPending = post.visibility == com.unboundapex.octalink.data.schema.PostVisibility.PENDING_APPROVAL
    val isRejected = post.visibility == com.unboundapex.octalink.data.schema.PostVisibility.REJECTED
    PosseCard {
        // 비공개/거부 상태 배지 — 작성자 + 멘션 회원만 보는 글이라는 시각 신호.
        if (isPending || isRejected) {
            val (label, bg) = when {
                isPending -> "🔒 비공개 (멘션 승인 대기)" to Color(0xFFB45309) // amber 700
                else -> "⛔ 비공개 (멘션 거부됨)" to Color(0xFF9CA3AF)            // gray 400
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        // 본인이 멘션됐고 승인 대기 상태 → 승인/거부 액션 행.
        if (canApproveMention) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF7ED))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${post.authorName}님이 사진/영상을 담은 글에서 회원님을 멘션했어요.\n공개할까요?",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9A3412),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "공개",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF16A34A))
                        .clickable { onApproveMention() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "거부",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFDC2626))
                        .clickable { onRejectMention() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagBadge(post.tag)
            Spacer(Modifier.weight(1f))
            Text(
                postTimestamp(post.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                // 라이트 테마의 WHITE 벨트처럼 ringColor 가 배경과 거의 동색일 때 윤곽이 살아나도록
                // outline 한 줄 깔고 위에 컬러 stroke. drawBehind 라 레이아웃은 동일.
                val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                Text(
                    post.authorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.drawBehind {
                        val colorStrokePx = 3.dp.toPx()
                        val outlineStrokePx = colorStrokePx + 1.5.dp.toPx()
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = outlineStrokePx,
                        )
                        drawLine(
                            color = post.authorBelt.ringColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = colorStrokePx,
                        )
                    },
                )
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(6.dp))
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 본문 인라인 expand — 한 줄 초과면 "더 보기" 토글, 펼치면 "접기".
        //
        // `bodyOverflows` 는 *현재* 측정 결과를 그대로 반영 (sticky true 안 함). 본문이 수정되어
        // 짧아진 경우 stale true 가 남아 토글이 의미 없이 보이는 버그 방지. 펼친 상태에선
        // maxLines=MAX_VALUE 라 자연히 hasVisualOverflow=false 가 되므로 그 케이스만 reset 스킵.
        var bodyExpanded by remember(post.id) { mutableStateOf(false) }
        var bodyOverflows by remember(post.id) { mutableStateOf(false) }
        Text(
            post.body,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (bodyExpanded) Int.MAX_VALUE else BODY_PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!bodyExpanded) bodyOverflows = result.hasVisualOverflow
            },
        )
        if (bodyOverflows || bodyExpanded) {
            Text(
                if (bodyExpanded) "접기" else "더 보기",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { bodyExpanded = !bodyExpanded }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        // 본문에 YouTube 링크가 섞여 있으면 썸네일 카드로 렌더 — 탭하면 YouTube 앱/웹으로 이동.
        // 본문 텍스트는 그대로 두고 카드만 *추가* 한다 (URL 을 본문에서 제거하면 작성자가
        // 의도적으로 남긴 설명 문맥이 깨질 수 있음). 첫 번째 링크 하나만 표시.
        val youTubeId = remember(post.body) { extractYouTubeVideoId(post.body) }
        if (youTubeId != null) {
            Spacer(Modifier.height(8.dp))
            YouTubeThumbnail(videoId = youTubeId)
        }
        // 전체화면 미디어 뷰어 토글 — 이미지/영상 영역 탭 시 활성.
        var fullscreenOpen by remember(post.id) { mutableStateOf(false) }
        if (post.imageUrl != null) {
            Spacer(Modifier.height(8.dp))
            // Coil AsyncImage 가 fillMaxWidth + wrapContent height 조합에서 intrinsic 사이즈
            // 를 layout 에 반영 못해 작게 그려지는 이슈 회피 — 로드 성공 콜백으로 실제 가로/세로
            // 비율을 받아 aspectRatio 로 명시. 초기엔 16:9 placeholder 박스로 시작.
            var imageRatio by remember(post.imageUrl) { mutableStateOf(16f / 9f) }
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val s = state.painter.intrinsicSize
                    if (s.width > 0f && s.height > 0f && s.width.isFinite() && s.height.isFinite()) {
                        imageRatio = s.width / s.height
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { fullscreenOpen = true },
            )
        } else if (post.videoUrl != null) {
            Spacer(Modifier.height(8.dp))
            // 인라인 영상 재생 — AndroidView 로 PlayerView. 컨트롤러가 재생/정지/seek 제공.
            // 우상단 ⛶ 오버레이로 전체화면 진입 (PlayerView 자체 touch 영역과 충돌 회피).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                PostVideoPlayer(
                    videoUrl = post.videoUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                )
                Text(
                    "⛶",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { fullscreenOpen = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (fullscreenOpen) {
            FullscreenMediaDialog(
                imageUrl = post.imageUrl,
                videoUrl = post.videoUrl,
                onDismiss = { fullscreenOpen = false },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (if (likedByMe) "♥ " else "♡ ") + post.likedBy.size,
                style = MaterialTheme.typography.labelMedium,
                color = if (likedByMe) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = myMemberId != null) { onToggleLike() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
            // 댓글 카운트 — 좋아요 하트의 ♡/♥ 패턴과 시각 어휘 통일.
            // 이모지(💬) 는 OS 컬러 폰트라 tint 불가 → Material vector 아이콘으로 교체.
            // - 0건: 외곽선 아이콘 (ChatBubbleOutline) + 회색 — 저참여 상태
            // - 1건+: 채워진 아이콘 (ChatBubble) + 파란색 + 카운트 bold — 활동성 강조
            val hasComments = commentCount > 0
            val commentColor = if (hasComments) Color(0xFF1E88E5)
            else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (hasComments) Icons.Filled.ChatBubble
                    else Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "댓글",
                    tint = commentColor,
                    modifier = Modifier.size(16.dp),
                )
                // 접힘/펼침 시각 — 작은 텍스트 글리프(▸/▾) 대신 Material chevron 으로 교체.
                // 18dp 라 chat bubble(16dp) 보다 약간 커서 토글 어포던스 강조.
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                    else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = commentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "$commentCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = commentColor,
                    fontWeight = if (hasComments) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.weight(1f))
            // 수정/삭제 칩 색상은 TAG 칩(빨강/파랑/노랑/보라)과 충돌하지 않게 선택.
            // 수정 = 청록(teal), 삭제 = 진한 주황(deep orange) — destructive 톤이지만 NOTICE 빨강과 구분.
            if (canEdit) {
                Text(
                    "수정",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00897B))
                        .clickable { onEdit() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (canDelete) {
                Text(
                    "삭제",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFD84315))
                        .clickable { onDelete() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        // 펼친 상태 — 카드 하단에 댓글 섹션 (입력 + 리스트). 접힌 카드는 listener 안 붙음.
        if (isExpanded) {
            Spacer(Modifier.height(12.dp))
            PostCommentSection(
                postId = post.id,
                myMemberId = myMemberId,
                myMemberName = myMemberName,
                myMemberBelt = myMemberBelt ?: Belt.UNKNOWN,
                canModerate = canModerateComments,
            )
        }
    }
}

/**
 * 글 한 건의 댓글 영역 — 입력창 + 리스트. 펼친 PostCard 안에서만 호출됨.
 *
 * ViewModel 은 `key = "comments-{postId}"` 로 글마다 별도 인스턴스 — 다른 글 펼쳤다 접어도
 * listener 재구독 비용 최소화 (WhileSubscribed 5s).
 */
@Composable
private fun PostCommentSection(
    postId: String,
    myMemberId: String?,
    myMemberName: String?,
    myMemberBelt: Belt,
    canModerate: Boolean,
    commentsVm: PostCommentsViewModel = viewModel(key = "comments-$postId"),
) {
    androidx.compose.runtime.LaunchedEffect(postId) { commentsVm.observeFor(postId) }
    val comments by commentsVm.comments.collectAsState()
    val submitState by commentsVm.submitState.collectAsState()
    var input by remember(postId) { mutableStateOf("") }

    // 작성 성공 시 입력창 비우기.
    androidx.compose.runtime.LaunchedEffect(submitState) {
        if (submitState is PostCommentsViewModel.SubmitState.Done) {
            input = ""
            commentsVm.resetSubmitState()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 입력창 — 본인 로그인 + member doc 있을 때만 활성. (PENDING/UNAPPROVED 회원은 read-only)
        if (myMemberId != null && myMemberName != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // OutlinedTextField 는 내부 contentPadding 이 노출 안 돼 진짜 컴팩트화 불가
                // (top+bottom 16dp 가 강제). BasicTextField + 수동 border Box 로 직접 그려서
                // 40dp 고정 높이 달성.
                val borderColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = input,
                        onValueChange = { if (it.length <= 80) input = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(borderColor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (input.isEmpty()) {
                        Text(
                            "댓글 작성…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                val busy = submitState is PostCommentsViewModel.SubmitState.Submitting
                val submitEnabled = !busy && input.isNotBlank()
                Text(
                    text = if (busy) "..." else "등록",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (submitEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (submitEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        .clickable(enabled = submitEnabled) {
                            commentsVm.submit(
                                postId = postId,
                                authorId = myMemberId,
                                authorName = myMemberName,
                                authorBelt = myMemberBelt,
                                body = input,
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            (submitState as? PostCommentsViewModel.SubmitState.Error)?.let {
                Text(
                    it.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (comments.isEmpty()) {
            Text(
                "첫 댓글을 작성해보세요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            comments.forEach { c ->
                CommentRow(
                    comment = c,
                    myMemberId = myMemberId,
                    canDelete = c.authorId == myMemberId || canModerate,
                    onToggleLike = {
                        if (myMemberId != null) commentsVm.toggleLike(postId, c.id, myMemberId)
                    },
                    onEditSubmit = { newBody -> commentsVm.edit(postId, c.id, newBody) },
                    onDelete = { commentsVm.delete(postId, c.id) },
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: PostCommentDoc,
    myMemberId: String?,
    canDelete: Boolean,
    onToggleLike: () -> Unit,
    onEditSubmit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val isAuthor = myMemberId != null && comment.authorId == myMemberId
    var editing by remember(comment.id) { mutableStateOf(false) }
    var editBuffer by remember(comment.id) { mutableStateOf(comment.body) }
    val likedByMe = myMemberId != null && myMemberId in comment.likedBy

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                comment.authorName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                postTimestamp(comment.createdAt) + if (comment.updatedAt != null) " · 수정됨" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing) {
            // 인라인 수정 모드 — 본문 자리에 OutlinedTextField + 저장/취소.
            androidx.compose.material3.OutlinedTextField(
                value = editBuffer,
                onValueChange = { if (it.length <= 80) editBuffer = it },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Spacer(Modifier.weight(1f))
                Text(
                    "취소",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            editing = false
                            editBuffer = comment.body
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(4.dp))
                // 빈 문자열 / 원본과 동일이면 비활성. clickable.enabled 만으론 시각 신호가 없어
                // 배경색·텍스트색도 함께 흐려서 사용자에게 disabled 상태를 명확히 보여줌.
                val saveEnabled = editBuffer.isNotBlank() && editBuffer != comment.body
                Text(
                    "저장",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (saveEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (saveEnabled) Color(0xFF00897B)
                            else Color(0xFF00897B).copy(alpha = 0.4f)
                        )
                        .clickable(enabled = saveEnabled) {
                            onEditSubmit(editBuffer)
                            editing = false
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            Text(
                comment.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 좋아요 (♥ N) — primary 빨강 강조 (글 좋아요 패턴과 동일).
                Text(
                    text = (if (likedByMe) "♥ " else "♡ ") + comment.likedBy.size,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (likedByMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = myMemberId != null) { onToggleLike() }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.weight(1f))
                if (isAuthor) {
                    Text(
                        "수정",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00897B))
                            .clickable {
                                editing = true
                                editBuffer = comment.body
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (canDelete) {
                    Text(
                        "삭제",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFD84315))
                            .clickable { onDelete() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * PostCard 인라인 영상 플레이어 — Media3 [androidx.media3.exoplayer.ExoPlayer] + [androidx.media3.ui.PlayerView].
 *
 * VideoView 대비 장점: 적응형 스트리밍, fullscreen 토글, 재생 속도 조절, seek/scrub 정밀도, lifecycle 안전성.
 * LazyColumn 의 disposed item 이 ExoPlayer 인스턴스를 [DisposableEffect] 로 release — 메모리 leak 방지.
 *
 * 자동 재생 ❌ (`playWhenReady = false`): 데이터 비용 / 다중 카드 동시 재생 회피. 사용자가 명시적으로 ▶ 탭.
 */
/**
 * 글 작성 다이얼로그의 이미지 액션 chip — outlined + 옅은 색 배경 + 색 텍스트 외곽.
 * 회전/변경/제거 4종에 각기 다른 색 부여해 시각적 구분.
 */
@Composable
private fun ImageActionChip(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val bg = if (enabled) color.copy(alpha = 0.10f) else Color.Transparent
    val border = if (enabled) color.copy(alpha = 0.45f)
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PostVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    /** true 면 prev/-5s/+15s/next 버튼 숨김 (play/pause + 타임라인만). 전체화면 진입 시 사용. */
    compactControls: Boolean = false,
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = autoPlay
        }
    }
    androidx.compose.runtime.DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                controllerAutoShow = true
                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                if (compactControls) {
                    // 이전/다음(미디어 아이템 nav) 만 숨김. 5초전/15초후 는 일시정지 중 이동 위해 유지.
                    setShowPreviousButton(false)
                    setShowNextButton(false)
                }
            }
        },
        modifier = modifier.background(Color(0xFF1A1A1A)),
    )
}

/**
 * 전체화면 미디어 뷰어 — 이미지 또는 영상 한 쪽만 set. 검정 배경 + Fit content scale.
 * 배경 탭 또는 우상단 ✕ 로 닫힘. 영상은 PlayerView 인스턴스 자체 재생성.
 */
@Composable
private fun FullscreenMediaDialog(
    imageUrl: String?,
    videoUrl: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            // Activity 가 enableEdgeToEdge() 라 Dialog 도 edge-to-edge 로 전개. 내부 콘텐츠를
            // safeDrawing 인셋으로 직접 패딩해야 PlayerView 컨트롤이 네비 바에 안 잘림.
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                imageUrl != null -> AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                videoUrl != null -> PostVideoPlayer(
                    videoUrl = videoUrl,
                    autoPlay = true,
                    compactControls = true,
                    // Dialog 내부에선 WindowInsets / systemBarsPadding 가 0 반환 케이스 있어서
                    // 하드코딩 패딩으로 확실히 회피 — top 48dp(상태바) / bottom 64dp(네비바+여유).
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, bottom = 64.dp),
                )
            }
            // 닫기 X — 우상단. 상태바 영역 회피 위해 top 패딩 hard 48dp.
            Text(
                "✕",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp, bottom = 16.dp, start = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            // 좌측 하단 액션 — 다운로드 / 카카오톡 공유 / 인스타 공유. 이미지에만 노출.
            // Material vector 아이콘 원형 버튼 — 이모지+텍스트보다 컴팩트.
            if (imageUrl != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MediaActionIconButton(
                        icon = androidx.compose.material.icons.Icons.Outlined.FileDownload,
                        contentDescription = "저장",
                    ) {
                        scope.launch {
                            val ok = saveImageToGallery(context, imageUrl)
                            android.widget.Toast.makeText(
                                context,
                                if (ok) "갤러리에 저장됨" else "저장 실패",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    MediaActionIconButton(
                        icon = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = "카카오톡 공유",
                    ) {
                        scope.launch {
                            val ok = shareImageToApp(context, imageUrl, "com.kakao.talk")
                            if (!ok) android.widget.Toast.makeText(
                                context, "카카오톡 공유 실패 — 앱 설치 여부 확인",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    MediaActionIconButton(
                        icon = androidx.compose.material.icons.Icons.Outlined.PhotoCamera,
                        contentDescription = "인스타 공유",
                    ) {
                        scope.launch {
                            val ok = shareImageToApp(context, imageUrl, "com.instagram.android")
                            if (!ok) android.widget.Toast.makeText(
                                context, "인스타 공유 실패 — 앱 설치 여부 확인",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.3f),
                androidx.compose.foundation.shape.CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Firebase Storage URL → 캐시 파일. 다운로드 실패 시 null. */
private suspend fun downloadImageToCache(
    context: android.content.Context,
    url: String,
): java.io.File? = withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "shared_images").apply { mkdirs() }
        val ext = url.substringAfterLast('.', "jpg").substringBefore('?').ifBlank { "jpg" }
        val file = java.io.File(dir, "share_${System.currentTimeMillis()}.$ext")
        java.net.URL(url).openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    }.onFailure {
        android.util.Log.w("OctaLink.Share", "image download 실패 url=$url", it)
    }.getOrNull()
}

/** MediaStore Pictures/OctaLink 에 이미지 저장. */
private suspend fun saveImageToGallery(
    context: android.content.Context,
    url: String,
): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
    val cached = downloadImageToCache(context, url) ?: return@withContext false
    runCatching {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "OctaLink_${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OctaLink")
            }
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { output ->
            cached.inputStream().use { input -> input.copyTo(output) }
        } ?: return@runCatching false
        true
    }.onFailure {
        android.util.Log.w("OctaLink.Share", "MediaStore 저장 실패", it)
    }.getOrDefault(false)
}

/** 지정 앱 패키지(카카오톡/인스타)로 ACTION_SEND. 앱 미설치 시 false. */
private suspend fun shareImageToApp(
    context: android.content.Context,
    url: String,
    packageName: String,
): Boolean {
    val cached = downloadImageToCache(context, url) ?: return false
    return runCatching {
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, cached)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            setPackage(packageName)
            addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        context.startActivity(intent)
        true
    }.onFailure {
        android.util.Log.w("OctaLink.Share", "외부 앱 공유 실패 pkg=$packageName", it)
    }.getOrDefault(false)
}

// ─────────────────────────────────────────────────────────────────────────────
// 글 작성 다이얼로그
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WritePostDialog(
    initialTag: PostTag,
    isStaff: Boolean,
    writeState: WriteState,
    /** 멘션 picker 후보 — APPROVED 회원. 작성 모드에서만 사용. */
    approvedMembers: List<com.unboundapex.octalink.data.schema.MemberDoc> = emptyList(),
    /** 본인 member id — picker 후보에서 본인 제외 + 본인 멘션 차단. */
    myMemberId: String? = null,
    onSubmit: (title: String, body: String, tag: PostTag, imageUri: Uri?, videoUri: Uri?, mentionedMemberIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
    /** 이미지 교체/제거 등 사용자 액션 시 stale 에러 메시지 초기화용. */
    onResetError: () -> Unit,
    /** 수정 모드일 때 원본 글. null 이면 작성 모드. */
    editing: PostDoc? = null,
) {
    val isEdit = editing != null
    // editing 인스턴스가 바뀔 때마다 초기값 재설정 (다이얼로그 첫 진입 시점 prefill).
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var body by remember(editing?.id) { mutableStateOf(editing?.body.orEmpty()) }
    var tag by remember(editing?.id) { mutableStateOf(editing?.tag ?: initialTag) }
    var imageUri by remember(editing?.id) { mutableStateOf<Uri?>(null) }
    // 사진 회전 — 0/90/180/270. 프리뷰 graphicsLayer 로 즉시 반영 + 업로드 직전 파일에 baking.
    var imageRotation by remember(editing?.id) { mutableStateOf(0) }
    var videoUri by remember(editing?.id) { mutableStateOf<Uri?>(null) }
    // 멘션 안내 인앱 배너 — 사진/영상 픽 직후 5초간 표시. Android Toast 는 LENGTH_LONG 도 ~3.5초고
    // 2줄 텍스트가 잘리는 케이스가 있어 Compose 배너로 교체.
    var mentionReminderVisible by remember { mutableStateOf(false) }
    LaunchedEffect(mentionReminderVisible) {
        if (mentionReminderVisible) {
            kotlinx.coroutines.delay(7000)
            mentionReminderVisible = false
        }
    }
    // 멘션된 회원 id — 작성 모드 전용 (편집 모드는 기존 멘션 유지).
    var mentionedIds by remember(editing?.id) { mutableStateOf<Set<String>>(emptySet()) }
    var pickerOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        // picker URI 권한이 콜백 직후 가장 신선 — 바로 app cache 로 복사해 안정적인 file:// 로 전환.
        // (업로드 시점까지 들고 있다 보면 권한 만료/리졸버 동작 차이로 openInputStream 가 null 을
        // 돌려주는 케이스 회피)
        val cached = runCatching { copyPickedMediaToCache(context, picked, "img") }
            .onFailure { android.util.Log.w("OctaLink.Posts", "picker URI cache 복사 실패", it) }
            .getOrNull()
        imageUri = cached ?: picked
        imageRotation = 0 // 새 이미지 픽 시 회전 초기화.
        videoUri = null // 미디어는 상호 배타 — 새 이미지 픽 시 영상 해제.
        mentionReminderVisible = true
        onResetError()
    }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        val cached = runCatching { copyPickedMediaToCache(context, picked, "vid") }
            .onFailure { android.util.Log.w("OctaLink.Posts", "picker video cache 복사 실패", it) }
            .getOrNull()
        videoUri = cached ?: picked
        imageUri = null
        mentionReminderVisible = true
        onResetError()
    }

    val isUploading = writeState is WriteState.Uploading

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            val titleText = when {
                isEdit && tag == PostTag.NOTICE -> "공지 수정"
                isEdit -> "글 수정"
                initialTag == PostTag.NOTICE -> "공지 작성"
                else -> "글 작성"
            }
            Text(titleText, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            // Box 로 Column 감싸기 — 멘션 안내 토스트를 정중앙에 align(Center) 로 띄움.
            // Popup 은 AlertDialog 보다 z-order 낮아 가려져서 in-dialog 오버레이로 처리.
            Box(modifier = Modifier.fillMaxWidth()) {
            // 컨텐츠 길어지면(미디어 + 멘션 칩 등) 다이얼로그 text 영역 안에서 스크롤.
            Column(
                modifier = Modifier.verticalScroll(
                    androidx.compose.foundation.rememberScrollState(),
                ),
            ) {
                // 카테고리 칩
                // - 공지 작성/수정: NOTICE 고정 (운영진 전용)
                // - 일반: RECORD/TIP/QUESTION 중 선택. 수정 시 현재 tag 유지하며 변경 허용.
                val tagChoices = if (tag == PostTag.NOTICE && isStaff) {
                    listOf(PostTag.NOTICE)
                } else {
                    listOf(PostTag.RECORD, PostTag.TIP, PostTag.QUESTION)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tagChoices.forEach { t ->
                        TagChipSelectable(
                            tag = t,
                            selected = tag == t,
                            onClick = { tag = t },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("제목") },
                    singleLine = true,
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(2000) },
                    label = { Text("본문") },
                    enabled = !isUploading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (isEdit) {
                    // 수정 모드: 기존 이미지 read-only 표시. 이미지 변경/제거는 후속 작업.
                    if (editing?.imageUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = editing.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "이미지 변경/제거는 글을 새로 작성해주세요",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // 이미지/영상 상호 배타 — 한쪽 picker 가 set 되면 다른 쪽은 disabled.
                    ImagePickerRow(
                        imageUri = imageUri,
                        rotation = imageRotation,
                        enabled = !isUploading && videoUri == null,
                        onPick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClear = {
                            imageUri = null
                            imageRotation = 0
                            onResetError()
                        },
                        onRotateLeft = { imageRotation = (imageRotation + 270) % 360 },
                        onRotateRight = { imageRotation = (imageRotation + 90) % 360 },
                    )
                    Spacer(Modifier.height(6.dp))
                    VideoPickerRow(
                        videoUri = videoUri,
                        enabled = !isUploading && imageUri == null,
                        onPick = {
                            pickVideo.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onClear = {
                            videoUri = null
                            onResetError()
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    // 멘션 picker 행 — 작성 모드 전용. 선택된 멘션은 칩 형태.
                    MentionPickerRow(
                        mentionedIds = mentionedIds,
                        candidates = approvedMembers.filter { it.id != myMemberId },
                        hasMedia = imageUri != null || videoUri != null,
                        enabled = !isUploading,
                        onOpenPicker = { pickerOpen = true },
                        onRemove = { id -> mentionedIds = mentionedIds - id },
                    )
                }
                if (writeState is WriteState.Error) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        writeState.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // 멘션 안내 토스트 — Column 위에 Box.align(Center) 로 다이얼로그 정중앙에 띄움.
            if (mentionReminderVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    // '@ 멘션' 만 노랑/볼드 강조 — 검정 배경 위 흰 텍스트 사이에서 눈에 띄게.
                    val mentionEmphasis = androidx.compose.ui.text.buildAnnotatedString {
                        append("사진 / 영상에 특정 회원이\n포함되어 있을 경우, ")
                        withStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        ) { append("'@ 멘션'") }
                        append("으로\n커뮤니티 게시 승인을 받아주세요!")
                    }
                    Text(
                        mentionEmphasis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            } // Box 닫기 — mentionReminderVisible 토스트 오버레이용
        },
        confirmButton = {
            if (isUploading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "업로드 중…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            } else {
                val canSubmit = title.isNotBlank() && body.isNotBlank()
                // 게시 = filled primary 버튼. 작성 완료 액션 시각 강조.
                val submitBg = if (canSubmit) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                Text(
                    if (isEdit) "저장" else "게시",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(submitBg)
                        .clickable(enabled = canSubmit) {
                            // 회전 적용 — 0 이면 no-op, 그 외엔 새 cache 파일로 baking 후 업로드.
                            val finalImage = imageUri?.let { src ->
                                if (imageRotation == 0) src
                                else rotateImageFile(context, src, imageRotation) ?: src
                            }
                            onSubmit(title, body, tag, finalImage, videoUri, mentionedIds.toList())
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        },
        dismissButton = {
            // 취소 = outlined 회색 버튼. 게시 옆에서 시각 무게 동일하게 맞춤.
            Text(
                "취소",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(enabled = !isUploading) { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        },
    )
    if (pickerOpen) {
        MentionPickerDialog(
            candidates = approvedMembers.filter { it.id != myMemberId },
            initiallySelected = mentionedIds,
            onConfirm = { picked ->
                mentionedIds = picked
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MentionPickerRow(
    mentionedIds: Set<String>,
    candidates: List<com.unboundapex.octalink.data.schema.MemberDoc>,
    hasMedia: Boolean,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val nameById = remember(candidates) { candidates.associateBy({ it.id }, { it.name }) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 토스트 강조 색과 정확히 동일 — Amber 300 (#FFD54F).
            Text(
                "@ 멘션",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFD54F),
            )
            Spacer(Modifier.width(8.dp))
            // 회원 멘션 picker 진입 — 보라(#7E57C2) 로 다른 액션 군과 분리. 멘션은 독립 개념.
            Text(
                "+ 회원 선택",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) Color(0xFF7E57C2)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) { onOpenPicker() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (mentionedIds.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // 선택된 멘션 칩 — FlowRow 로 다이얼로그 폭 초과 시 자동 줄바꿈.
            // Row 만 쓰면 4번째 이후 칩이 viewport 밖으로 잘려 보이지 않는 버그가 있었음.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                mentionedIds.forEach { id ->
                    val name = nameById[id] ?: id
                    Text(
                        "@$name ✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        // 좁은 잔여 폭에서 글자별 세로 wrap 막기 — Text 가 자연 폭을 요구해야
                        // FlowRow 가 "안 들어감" 판단하고 다음 줄로 chip 전체를 wrap 함.
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF6366F1))
                            .clickable(enabled = enabled) { onRemove(id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (hasMedia && mentionedIds.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "ⓘ 사진/영상 + 멘션 조합은 멘션된 회원 전원 승인 후 공개됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MentionPickerDialog(
    candidates: List<com.unboundapex.octalink.data.schema.MemberDoc>,
    initiallySelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initiallySelected) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, candidates) {
        if (query.isBlank()) candidates
        else candidates.filter { it.name.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("회원 선택", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("이름 검색…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(filtered, key = { it.id }) { m ->
                        val checked = m.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - m.id else selected + m.id
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (checked) "☑" else "☐",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                m.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "확인 (${selected.size}명)",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { onConfirm(selected) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            // AlertDialog 의 dismissButton 슬롯에 두 버튼을 함께 배치 — "선택 해제" 가 가장 왼쪽,
            // "취소" 는 그 우측. 두 버튼을 한 Row 로 묶어야 AlertDialog 의 좌측 정렬 영역에 함께 들어감.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val canClear = selected.isNotEmpty()
                Text(
                    "선택 해제",
                    // 확인(red primary) 옆 두 빨강 충돌 회피 — destructive 가 아닌 reset 성격이라
                    // 정보 액션 블루(#1E88E5, 내 참가 칩과 동일 톤) 사용.
                    color = if (canClear) Color(0xFF1E88E5)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable(enabled = canClear) { selected = emptySet() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Text(
                    "취소",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun ImagePickerRow(
    imageUri: Uri?,
    rotation: Int = 0,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onRotateLeft: () -> Unit = {},
    onRotateRight: () -> Unit = {},
) {
    if (imageUri != null) {
        Column {
            // 정사각 미리보기 박스 — 어떤 비율의 사진을 90/270 으로 돌려도 잘림 없이 fit.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation.toFloat() },
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 회전 두 chip 나란히 (스왑된 순서: ↻ → ↺). 우측에 변경/제거.
                ImageActionChip(
                    label = "↻ 회전",
                    color = Color(0xFF27AE60), // 그린
                    enabled = enabled,
                    onClick = onRotateRight,
                )
                Spacer(Modifier.width(6.dp))
                ImageActionChip(
                    label = "↺ 회전",
                    color = Color(0xFF7E57C2), // 보라
                    enabled = enabled,
                    onClick = onRotateLeft,
                )
                Spacer(Modifier.weight(1f))
                ImageActionChip(
                    label = "변경",
                    color = Color(0xFF1E88E5), // 블루
                    enabled = enabled,
                    onClick = onPick,
                )
                Spacer(Modifier.width(6.dp))
                ImageActionChip(
                    label = "제거",
                    color = Color(0xFFC8102E), // 빨강 (destructive)
                    enabled = enabled,
                    onClick = onClear,
                )
            }
        }
    } else {
        // primary red 와 충돌 회피 — 정보 액션 톤(blue #1E88E5, 내 참가 칩과 동일) 사용.
        Text(
            "+ 이미지 첨부 (선택, 5MB 이하)",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF1E88E5),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF1E88E5).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .clickable(enabled = enabled) { onPick() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/**
 * 원본 파일의 EXIF Orientation 태그를 읽어 회전 각도(0/90/180/270) 로 반환.
 * Coil/뷰어는 EXIF 를 자동 적용해 표시하지만 [android.graphics.BitmapFactory] 는 raw 픽셀만
 * 디코딩해서 EXIF 를 무시 — 회전 baking 시 사용자 추가 회전과 합산해 픽셀로 굳혀야 함.
 */
private fun readExifRotation(context: android.content.Context, src: Uri): Int {
    return runCatching {
        context.contentResolver.openInputStream(src)?.use { stream ->
            val exif = android.media.ExifInterface(stream)
            when (
                exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
}

/**
 * 사용자 선택 회전 도(90/180/270) 를 실제 비트맵 회전 + JPEG 재인코딩으로 적용,
 * 새 cache 파일 Uri 반환. 0 이면 src 그대로 (원본 EXIF respect). 디코딩/인코딩 실패 시 null.
 *
 * 회전 계산: EXIF orientation + 사용자 회전을 합산해 픽셀에 굳힘. JPEG 출력엔 EXIF 태그가
 * 없으므로 (Bitmap.compress 가 자동 첨부 안 함) 뷰어가 추가 회전 없이 그대로 표시.
 *
 * **OOM 방지** — 12MP+ 원본을 그대로 디코딩하면 ~50MB + createBitmap rotation 2배 = ~100MB
 * 로 heap 압박. inSampleSize 로 긴 변 ~2400px 까지만 다운샘플 후 회전.
 */
private fun rotateImageFile(context: android.content.Context, src: Uri, degrees: Int): Uri? {
    val tag = "OctaLink.Rotate"
    if (degrees == 0) return src // 회전 안 함 — 원본 EXIF 그대로 (Coil 가 자동 적용)
    val exifRotation = readExifRotation(context, src)
    val totalRotation = (((degrees + exifRotation) % 360) + 360) % 360
    android.util.Log.d(tag, "user=$degrees exif=$exifRotation total=$totalRotation")

    // 1) 차원만 먼저 — inSampleSize 계산용
    val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(src)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, boundsOpts)
        }
    }.onFailure { android.util.Log.w(tag, "차원 읽기 실패", it); return null }
    if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
        android.util.Log.w(tag, "차원 0 — src 디코딩 불가")
        return null
    }
    val maxEdge = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
    var sample = 1
    while (maxEdge / sample > 2400) sample *= 2
    android.util.Log.d(tag, "원본 ${boundsOpts.outWidth}x${boundsOpts.outHeight} sample=$sample")

    // 2) 실제 디코드 (다운샘플)
    val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = runCatching {
        context.contentResolver.openInputStream(src)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }.getOrNull()
    if (bitmap == null) {
        android.util.Log.w(tag, "decodeStream 실패")
        return null
    }

    // 3) 회전 — OOM 명시 catch
    val matrix = android.graphics.Matrix().apply { postRotate(totalRotation.toFloat()) }
    val rotated = try {
        android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (oom: OutOfMemoryError) {
        android.util.Log.w(tag, "createBitmap OOM (sample=$sample)", oom)
        bitmap.recycle()
        return null
    } catch (t: Throwable) {
        android.util.Log.w(tag, "createBitmap 실패", t)
        bitmap.recycle()
        return null
    }

    // 4) JPEG 인코딩
    val dir = java.io.File(context.cacheDir, "post_uploads").apply { mkdirs() }
    val out = java.io.File(dir, "rotated_${System.currentTimeMillis()}.jpg")
    val ok = runCatching {
        out.outputStream().use {
            rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
        }
        true
    }.onFailure { android.util.Log.w(tag, "JPEG 인코딩 실패", it) }
        .getOrDefault(false)
    if (rotated != bitmap) bitmap.recycle()
    rotated.recycle()
    if (ok) android.util.Log.i(tag, "rotation OK → ${out.absolutePath} (${out.length()} bytes)")
    return if (ok) android.net.Uri.fromFile(out) else null
}

/**
 * picker 가 돌려준 `content://media/picker/...` URI 를 즉시 app cache 디렉토리로 복사하고
 * 결과 파일의 Uri 를 반환. picker URI 의 임시 권한은 콜백 직후 가장 신선하므로 이 시점에
 * 바이트를 떠두면 이후 업로드(Dispatchers.IO) 가 안정적으로 read 가능.
 *
 * 복사 파일은 `cacheDir/post_uploads/` 에 쌓임. 별도 cleanup 안 함 — Android OS 가 캐시 압박 시
 * 자동 회수.
 *
 * @param kind 파일명 prefix — "img"/"vid" 로 디버깅 시 캐시 식별 편의.
 */
private fun copyPickedMediaToCache(context: android.content.Context, src: Uri, kind: String): Uri? {
    val dir = java.io.File(context.cacheDir, "post_uploads").apply { mkdirs() }
    val out = java.io.File(dir, "${kind}_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.bin")
    val ok = context.contentResolver.openInputStream(src)?.use { input ->
        out.outputStream().use { sink -> input.copyTo(sink) }
        true
    } ?: false
    if (!ok) {
        out.delete()
        return null
    }
    return Uri.fromFile(out)
}

/**
 * 영상 첨부 picker UI — [ImagePickerRow] 와 동일 패턴.
 * 다른 미디어가 이미 선택돼 있을 때 disabled — 안내 문구로 사용자에 명시.
 */
@Composable
private fun VideoPickerRow(
    videoUri: Uri?,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (videoUri != null) {
            // 영상 thumbnail 대신 fixed-size box 에 ▶ 아이콘 — MediaMetadataRetriever 로 첫 프레임 추출은
            // 비용 대비 가치 낮음 (다이얼로그 짧은 노출). 업로드 직전 미리보기 용도라 placeholder 면 충분.
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "영상 변경",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(enabled = enabled) { onPick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "제거",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(enabled = enabled) { onClear() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else {
            val alpha = if (enabled) 1f else 0.4f
            // 영상 첨부 — 이미지(blue) 와 구별해 그린(#27AE60, 스파링 태그 톤). 미디어 종류 즉시 식별.
            Text(
                "+ 영상 첨부 (선택, 1분 30초·100MB 이하)",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF27AE60).copy(alpha = alpha),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF27AE60).copy(alpha = 0.4f * alpha), RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) { onPick() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 공통 컴포저블
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComposeAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    bg: Color = MaterialTheme.colorScheme.surfaceVariant,
    fg: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun PostTag.label(): String = when (this) {
    PostTag.NOTICE -> "공지"
    PostTag.RECORD -> "기록"
    PostTag.TIP -> "팁"
    PostTag.QUESTION -> "질문"
}

private fun PostTag.colors(): Pair<Color, Color> = when (this) {
    PostTag.NOTICE -> Color(0xFFC8102E) to Color.White
    PostTag.RECORD -> Color(0xFF1E88E5) to Color.White
    PostTag.TIP -> Color(0xFFFBC02D) to Color(0xFF1A1A1A)
    PostTag.QUESTION -> Color(0xFF7B1FA2) to Color.White
}

@Composable
private fun TagBadge(tag: PostTag) {
    val (bg, fg) = tag.colors()
    Text(
        text = tag.label(),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun TagChipSelectable(
    tag: PostTag,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val (bg, fg) = tag.colors()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) bg else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            tag.label(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) fg else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
        )
    }
}
