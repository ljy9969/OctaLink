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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.unboundapex.octalink.data.schema.PostDoc
import com.unboundapex.octalink.data.schema.PostTag
import com.unboundapex.octalink.data.schema.isStaff
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import java.time.Duration
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
                PostCard(
                    post = p,
                    myMemberId = session.member?.id,
                    canDelete = session.member?.id == p.authorId || session.role.isStaff,
                    onToggleLike = {
                        session.member?.id?.let { postsVm.toggleLike(p.id, it) }
                    },
                    onDelete = { postsVm.delete(p.id) },
                )
            }
        }
    }

    dialogTag?.let { initialTag ->
        WritePostDialog(
            initialTag = initialTag,
            isStaff = session.role.isStaff,
            writeState = writeState,
            onSubmit = { title, body, tag, imageUri ->
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
                )
            },
            onDismiss = {
                dialogTag = null
                postsVm.resetWriteState()
            },
        )

        // 작성 성공 시 다이얼로그 자동 종료
        LaunchedEffect(writeState) {
            if (writeState is WriteState.Done) {
                dialogTag = null
                postsVm.resetWriteState()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Post 카드
// ─────────────────────────────────────────────────────────────────────────────

private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val seoul = ZoneId.of("Asia/Seoul")

private fun relativeTime(createdAt: Instant): String {
    val diff = Duration.between(createdAt, Instant.now())
    return when {
        diff.toMinutes() < 1 -> "방금"
        diff.toMinutes() < 60 -> "${diff.toMinutes()}분 전"
        diff.toHours() < 24 -> "${diff.toHours()}시간 전"
        diff.toDays() < 7 -> "${diff.toDays()}일 전"
        else -> createdAt.atZone(seoul).format(timeFormatter)
    }
}

@Composable
private fun PostCard(
    post: PostDoc,
    myMemberId: String?,
    canDelete: Boolean,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
) {
    val likedByMe = myMemberId != null && myMemberId in post.likedBy
    // title 이 tag 라벨(공지/질문/팁/기록)과 동일하면 chip 과 중복이라 표시하지 않음
    val showTitle = post.title.isNotBlank() &&
        post.title.trim() != post.tag.label()
    PosseCard(leftStripeColor = post.authorBelt.ringColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TagBadge(post.tag)
            Spacer(Modifier.weight(1f))
            Text(
                post.authorName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                relativeTime(post.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Text(post.body, style = MaterialTheme.typography.bodyLarge)
        if (post.imageUrl != null) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
            if (canDelete) {
                Text(
                    "삭제",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 글 작성 다이얼로그
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WritePostDialog(
    initialTag: PostTag,
    isStaff: Boolean,
    writeState: WriteState,
    onSubmit: (title: String, body: String, tag: PostTag, imageUri: Uri?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf(initialTag) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) imageUri = uri }

    val isUploading = writeState is WriteState.Uploading

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Text(
                if (initialTag == PostTag.NOTICE) "공지 작성" else "글 작성",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                // 카테고리 칩 — 공지 작성 진입이면 NOTICE 고정 비활성, 일반 진입이면 RECORD/TIP/QUESTION 선택
                val tagChoices = if (initialTag == PostTag.NOTICE && isStaff) {
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
                    label = { Text("제목 (선택)") },
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
                ImagePickerRow(
                    imageUri = imageUri,
                    enabled = !isUploading,
                    onPick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClear = { imageUri = null },
                )
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
                Text(
                    "게시",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSubmit(title, body, tag, imageUri) }
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
