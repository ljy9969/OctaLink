package com.unboundapex.octalink.ui.screens.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
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
    val posts by postsVm.sortedPosts.collectAsState()
    val writeState by postsVm.writeState.collectAsState()
    val context = LocalContext.current
    var dialogTag by remember { mutableStateOf<PostTag?>(null) }
    var editingPost by remember { mutableStateOf<PostDoc?>(null) }

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
                val canModify = session.member?.id == p.authorId || session.role.isStaff
                PostCard(
                    post = p,
                    myMemberId = session.member?.id,
                    canEdit = canModify,
                    canDelete = canModify,
                    onToggleLike = {
                        session.member?.id?.let { postsVm.toggleLike(p.id, it) }
                    },
                    onEdit = { editingPost = p },
                    onDelete = { postsVm.delete(p.id) },
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
            editing = editingPost,
            onSubmit = { title, body, tag, imageUri, videoUri ->
                val editTarget = editingPost
                if (editTarget != null) {
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
private val postDateFmt = DateTimeFormatter.ofPattern("MM/dd(EEE)", java.util.Locale.KOREAN)
private val postTimeFmt = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)

private fun postTimestamp(createdAt: Instant): String {
    val z = createdAt.atZone(seoul)
    return "${z.format(postDateFmt)} ${z.format(postTimeFmt)}"
}

@Composable
private fun PostCard(
    post: PostDoc,
    myMemberId: String?,
    canEdit: Boolean,
    canDelete: Boolean,
    onToggleLike: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val likedByMe = myMemberId != null && myMemberId in post.likedBy
    // title 이 tag 라벨(공지/질문/팁/기록)과 동일하면 chip 과 중복이라 표시하지 않음
    val showTitle = post.title.isNotBlank() &&
        post.title.trim() != post.tag.label()
    PosseCard {
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
                Text(
                    post.authorName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    // 이름 너비만큼 벨트색 underline. 두께 2dp.
                    modifier = Modifier.drawBehind {
                        val strokePx = 2.dp.toPx()
                        drawLine(
                            color = post.authorBelt.ringColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokePx,
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
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else if (post.videoUrl != null) {
            Spacer(Modifier.height(8.dp))
            // 인라인 영상 재생 — AndroidView 로 [VideoView] 감싸기. MediaController 가 자체 재생/정지/seek UI 제공.
            // 16:9 고정 — 원본 비율 추출 비용 크고(MediaMetadataRetriever 별도 호출 필요), 30초 짧은 클립이라 letterbox 허용.
            // ExoPlayer/Media3 로의 업그레이드는 후속 (적응형 스트리밍/픽처-인-픽처 등 필요해질 때).
            PostVideoPlayer(
                videoUrl = post.videoUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PostVideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = false
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
            }
        },
        modifier = modifier.background(Color(0xFF1A1A1A)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 글 작성 다이얼로그
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WritePostDialog(
    initialTag: PostTag,
    isStaff: Boolean,
    writeState: WriteState,
    onSubmit: (title: String, body: String, tag: PostTag, imageUri: Uri?, videoUri: Uri?) -> Unit,
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
    var videoUri by remember(editing?.id) { mutableStateOf<Uri?>(null) }

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
        videoUri = null // 미디어는 상호 배타 — 새 이미지 픽 시 영상 해제.
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
            Column {
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
                        enabled = !isUploading && videoUri == null,
                        onPick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClear = {
                            imageUri = null
                            onResetError()
                        },
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
                Text(
                    if (isEdit) "저장" else "게시",
                    color = if (canSubmit) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = canSubmit) { onSubmit(title, body, tag, imageUri, videoUri) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        },
        dismissButton = {
            Text(
                "취소",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(enabled = !isUploading) { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    )
}

@Composable
private fun ImagePickerRow(
    imageUri: Uri?,
    enabled: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(60.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "이미지 변경",
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
            Text(
                "+ 이미지 첨부 (선택, 5MB 이하)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled) { onPick() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
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
            Text(
                "+ 영상 첨부 (선택, 1분 30초·100MB 이하)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f * alpha), RoundedCornerShape(6.dp))
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
