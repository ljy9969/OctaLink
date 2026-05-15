package com.unboundapex.octalink.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.components.WeightClassInfoDialog
import com.unboundapex.octalink.data.avatarById
import com.unboundapex.octalink.data.avatarFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val kakaoYellow = Color(0xFFFEE500)
private val kakaoText = Color(0xFF3C1E1E)

/**
 * 비로그인 사용자 — 카카오 로그인 진입점.
 * (현재 [com.unboundapex.octalink.data.repo.inmemory.InMemoryAuthRepository] 가 mock 카카오
 * ID 를 즉시 발급. 실제 통합 후엔 카카오 SDK 로그인 액티비티 호출.)
 */
@Composable
fun LoginScreen(sessionVm: SessionViewModel) {
    PosseScreen(title = "OctaLink", subtitle = "회원 로그인") {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "🥊",
                style = MaterialTheme.typography.displayLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "체육관 회원이라면 카카오 계정으로 로그인 후 가입 신청해주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            KakaoSignInButton(onClick = { sessionVm.signInWithKakao() })
            Spacer(Modifier.height(12.dp))
            Text(
                "관장 승인 후 정상 이용 가능합니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 카카오 로그인 완료(uid 발급) 했으나 MemberDoc 이 없는 상태 — 가입 폼.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(sessionVm: SessionViewModel) {
    val session by sessionVm.state.collectAsState()
    val kakaoIdentity = session.kakaoIdentity

    // 카카오 자동 제공 값 (실명) — null/blank 아니면 read-only 로 고정.
    val kakaoName = kakaoIdentity?.displayName?.takeIf { it.isNotBlank() }
    val nameAutoFilled = kakaoName != null

    // 전화번호는 가입 폼 UI 에서 받지 않지만, 카카오 비즈앱 동의 항목으로 받아온 값이 있으면
    // Firestore members.{uid}.phone 에 그대로 저장. 사용자에게는 노출하지 않음 (조용히 보관).
    val kakaoPhone = kakaoIdentity?.phoneNumber?.takeIf { it.isNotBlank() }?.let { raw ->
        raw.filter { it.isDigit() }
            .let { if (it.startsWith("82")) "0" + it.drop(2) else it }
            .take(11)
            .takeIf { it.isNotEmpty() }
    }

    // 한글 IME 조합 보존을 위해 TextFieldValue 사용
    var nameValue by remember { mutableStateOf(TextFieldValue("")) }
    val kakaoGender = kakaoIdentity?.gender
    var belt by remember { mutableStateOf(Belt.UNKNOWN) }
    var weightClass by remember { mutableStateOf(WeightClass.LIGHT) }
    // 캐릭터는 성별 + 체급에서 자동 파생 (사용자 선택 X). belt 는 마스크 색만 변경.
    val avatarId = avatarFor(kakaoGender, weightClass).id

    // 입관일 (체육관 등록일) — 기본값 오늘. 이미 다니던 회원은 과거 날짜로 변경.
    val today = remember { LocalDate.now(ZoneId.of("Asia/Seoul")) }
    var joinDate by remember { mutableStateOf(today) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var weightInfoOpen by remember { mutableStateOf(false) }

    // 카카오 prefill (자동 — 이후 비활성 필드라 LaunchedEffect 한 번이면 충분)
    LaunchedEffect(kakaoName) {
        if (kakaoName != null && nameValue.text.isEmpty()) {
            nameValue = TextFieldValue(kakaoName, selection = TextRange(kakaoName.length))
        }
    }

    val avatar = avatarById(avatarId)
    val name = nameValue.text
    val canSubmit = name.isNotBlank()
    val joinDateLabel = remember(joinDate) {
        joinDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
    }

    PosseScreen(title = "Signup", subtitle = "체육관 회원 가입 신청") {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PosseCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarTile(
                        avatar = avatar,
                        size = 72.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("캐릭터", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(avatar.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "성별 · 체급에 따라 자동 부여",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 이름 — 카카오에서 받아왔으면 read-only (변조 방지).
            // 전화번호는 폼에서 받지 않음 — 카카오 비즈앱 동의 항목으로만 수집.
            PosseCard {
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { if (!nameAutoFilled) nameValue = it },
                    enabled = !nameAutoFilled,
                    label = { Text("이름")},
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 입관일 — 도장에 처음 등록한 날 (앱 가입일과 별개)
            PosseCard(modifier = Modifier.clickable { datePickerOpen = true }) {
                Text("도장 입관일", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(joinDateLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    "이미 다니던 회원은 실제 입관일로 변경하세요. 탭해서 선택.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

            // 벨트 — 정식 5단계 + Unknown 옵션. 색상은 BracketDrawScreen 과 일치 (벨트별 ringColor)
            PosseCard {
                Text("벨트", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Belt.values().forEach { b ->
                        BeltChip(
                            belt = b,
                            selected = belt == b,
                            onClick = { belt = b },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (belt == Belt.UNKNOWN) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "관장님이 가입 승인 시 정확한 벨트로 갱신해주십니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 체급 — BracketDrawScreen 의 그라데이션 칩과 일치 (페더 → 헤비 점진적 진색)
            PosseCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "체급",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "ⓘ 체급 안내",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1A1A1A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFBC02D))
                            .clickable { weightInfoOpen = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WeightClass.values().forEachIndexed { idx, wc ->
                        WeightChip(
                            label = wc.shortLabel,
                            selected = weightClass == wc,
                            gradient = weightGradient(idx),
                            onClick = { weightClass = wc },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SubmitButton(
                enabled = canSubmit,
                label = "가입 신청",
                onClick = {
                    android.util.Log.d(
                        "OctaLink.Signup",
                        "submit joinDate=$joinDate (today=$today, diff=${joinDate != today})",
                    )
                    sessionVm.completeSignup(
                        name = name.trim().take(20),
                        belt = belt,
                        weightClass = weightClass,
                        avatarId = avatarId,
                        joinDate = joinDate,
                        phone = kakaoPhone,
                    )
                },
            )

            Text(
                "신청 후 관장 승인 시 메인 화면 진입 가능. 관장은 운영 탭에서 승인 가능합니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }

    if (datePickerOpen) {
        // 입관일 picker — 오늘까지만 선택 가능.
        // 타임존은 KST(Asia/Seoul) 통일. OctaLink 사용자는 모두 한국이고 joinDate 의 의미는
        // "Korean local date" 이므로 millis ↔ LocalDate 변환 양쪽 모두 KST 기준.
        // selectableDates 의 today 비교도 KST 날짜로 보정 — UTC 비교 시 새벽 KST 시간대(0~9시)에
        // "오늘" 칸이 잘못 비활성되는 문제 방지.
        val seoulZone = ZoneId.of("Asia/Seoul")
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = joinDate.atStartOfDay(seoulZone)
                .toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val picked = Instant.ofEpochMilli(utcTimeMillis)
                        .atZone(seoulZone).toLocalDate()
                    return !picked.isAfter(LocalDate.now(seoulZone))
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clickable {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val picked = Instant.ofEpochMilli(millis)
                                    .atZone(seoulZone).toLocalDate()
                                android.util.Log.d(
                                    "OctaLink.Signup",
                                    "DatePicker confirm: millis=$millis, picked=$picked",
                                )
                                joinDate = picked
                            }
                            datePickerOpen = false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "확인",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clickable { datePickerOpen = false }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "취소",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (weightInfoOpen) {
        WeightClassInfoDialog(onDismiss = { weightInfoOpen = false })
    }
}

/**
 * 가입 신청 완료 후 관장 승인 대기 화면. 본인이 무엇을 해야 하는지 안내 + 로그아웃 옵션.
 */
@Composable
fun PendingApprovalScreen(sessionVm: SessionViewModel) {
    PosseScreen(title = "Pending", subtitle = "관장 승인 대기 중") {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⏳", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "가입 신청 완료",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "관장의 승인을 기다리는 중입니다. 승인 즉시 메인 화면으로 자동 이동합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            SubmitButton(
                enabled = true,
                label = "로그아웃",
                onClick = { sessionVm.signOut() },
                bg = MaterialTheme.colorScheme.surface,
                fg = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 자진 탈퇴(LEFT) 후 같은 카카오 계정으로 재로그인한 경우 — 재가입 CTA 제공.
 * 거부(REJECTED) / 정지(SUSPENDED) 와 달리 본인 의지로 복귀 가능하므로 [RejectedScreen] 과 분리.
 */
@Composable
fun LeftScreen(sessionVm: SessionViewModel) {
    PosseScreen(title = "Welcome back", subtitle = "탈퇴 후 재로그인") {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("👋", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text("다시 오셨군요", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "이전에 탈퇴 처리된 계정입니다. 재가입 시 과거 출석/스킬 기록은 그대로 복원됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(24.dp))
            SubmitButton(
                enabled = true,
                label = "재가입 신청",
                onClick = { sessionVm.rejoinMembership() },
            )
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                enabled = true,
                label = "로그아웃",
                onClick = { sessionVm.signOut() },
                bg = MaterialTheme.colorScheme.surface,
                fg = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun RejectedScreen(sessionVm: SessionViewModel) {
    PosseScreen(title = "Rejected", subtitle = "가입 거부됨") {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⛔", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text("가입 신청이 거부되었습니다", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "체육관 관장님께 문의해주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            SubmitButton(
                enabled = true,
                label = "로그아웃",
                onClick = { sessionVm.signOut() },
                bg = MaterialTheme.colorScheme.surface,
                fg = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun KakaoSignInButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(kakaoYellow)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "카카오톡으로 시작하기",
            style = MaterialTheme.typography.titleMedium,
            color = kakaoText,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ----- 칩 컴포저블 — BracketDrawScreen 의 칩 스타일과 일치 -----

private val chipShape = RoundedCornerShape(50)
private const val UNSELECTED_ALPHA = 0.45f

/** 체급 칩의 배경 그라데이션. 페더(밝은 오렌지/레드) → 헤비(딥 마룬). BracketDrawScreen 과 동일. */
private fun weightGradient(index: Int): Brush {
    val palette = listOf(
        Color(0xFFFF6B35) to Color(0xFFE53935),
        Color(0xFFE53935) to Color(0xFFC8102E),
        Color(0xFFC8102E) to Color(0xFFAD1A1A),
        Color(0xFFAD1A1A) to Color(0xFF7B1010),
        Color(0xFF7B1010) to Color(0xFF3E0606),
    )
    val (start, end) = palette[index.coerceIn(palette.indices)]
    return Brush.horizontalGradient(listOf(start, end))
}

@Composable
private fun WeightChip(
    label: String,
    selected: Boolean,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .height(32.dp)
            .alpha(if (selected) 1f else UNSELECTED_ALPHA)
            .clip(chipShape)
            .background(gradient)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor), chipShape)
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun beltTextColor(belt: Belt): Color = when (belt) {
    Belt.WHITE -> Color(0xFF111111)
    else -> Color.White
}

@Composable
private fun BeltChip(
    belt: Belt,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .height(32.dp)
            .alpha(if (selected) 1f else UNSELECTED_ALPHA)
            .clip(chipShape)
            .background(belt.ringColor)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor), chipShape)
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            belt.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = beltTextColor(belt),
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubmitButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    bg: Color = MaterialTheme.colorScheme.primary,
    fg: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val containerBg = if (enabled) bg else MaterialTheme.colorScheme.surfaceVariant
    val labelFg = if (enabled) fg else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = labelFg,
            fontWeight = FontWeight.Bold,
        )
    }
}
