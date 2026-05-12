package com.unboundapex.octalink.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unboundapex.octalink.data.Belt
import com.unboundapex.octalink.data.WeightClass
import com.unboundapex.octalink.data.session.SessionViewModel
import com.unboundapex.octalink.ui.components.AvatarPickerSheet
import com.unboundapex.octalink.ui.components.AvatarTile
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.data.avatarById

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

            // 개발용 단축 로그인 — 실제 카카오 SDK 통합 후 제거 예정
            Spacer(Modifier.height(32.dp))
            Text(
                "테스트 단축 로그인 (개발용)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebugLoginChip(
                    label = "이지연 (창조자)",
                    onClick = { sessionVm.debugSignInAsCreator() },
                )
                DebugLoginChip(
                    label = "김파시 (관장)",
                    onClick = { sessionVm.debugSignInAsMaster() },
                )
            }
        }
    }
}

@Composable
private fun DebugLoginChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * 카카오 로그인 완료(uid 발급) 했으나 MemberDoc 이 없는 상태 — 가입 폼.
 */
@Composable
fun SignupScreen(sessionVm: SessionViewModel) {
    val session by sessionVm.state.collectAsState()
    val kakaoIdentity = session.kakaoIdentity

    // 한글 IME 조합 보존을 위해 TextFieldValue 사용 (String 기반은 onValueChange 콜백 시
    // composition region 이 리셋되어 한글 첫 자모 입력이 무시되는 알려진 이슈가 있음)
    var nameValue by remember { mutableStateOf(TextFieldValue("")) }
    var phone by remember { mutableStateOf("") }
    var belt by remember { mutableStateOf(Belt.WHITE) }
    var weightClass by remember { mutableStateOf(WeightClass.LIGHT) }
    var avatarId by remember { mutableStateOf("ryu") }
    var pickerOpen by remember { mutableStateOf(false) }

    // 카카오에서 받은 nickname/phone 으로 prefill — 단, 사용자가 이미 입력 시작했으면 덮어쓰지 않음
    LaunchedEffect(kakaoIdentity?.displayName) {
        val nickname = kakaoIdentity?.displayName.orEmpty()
        if (nickname.isNotBlank() && nameValue.text.isEmpty()) {
            nameValue = TextFieldValue(nickname, selection = TextRange(nickname.length))
        }
    }
    LaunchedEffect(kakaoIdentity?.phoneNumber) {
        val raw = kakaoIdentity?.phoneNumber.orEmpty()
        if (raw.isNotBlank() && phone.isEmpty()) {
            // 카카오 phone_number 는 "+82 10-1234-5678" 형식 — 숫자만 추출해서 11자 cap
            phone = raw.filter { it.isDigit() }
                .let { if (it.startsWith("82")) "0" + it.drop(2) else it }
                .take(11)
        }
    }

    val avatar = avatarById(avatarId)
    val name = nameValue.text
    val canSubmit = name.isNotBlank()

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
                        ringColor = null,
                        modifier = Modifier.clickable { pickerOpen = true },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("캐릭터", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(avatar.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "탭해서 변경",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            PosseCard {
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it },
                    label = { Text("이름") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(11) },
                    label = { Text("연락처") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PosseCard {
                Text("벨트", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    items = Belt.values().toList(),
                    selected = belt,
                    label = { it.displayName },
                    onSelect = { belt = it },
                )
            }

            PosseCard {
                Text("체급", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    items = WeightClass.values().toList(),
                    selected = weightClass,
                    label = { it.displayName },
                    onSelect = { weightClass = it },
                )
            }

            SubmitButton(
                enabled = canSubmit,
                label = "가입 신청",
                onClick = {
                    sessionVm.completeSignup(
                        name = name.trim().take(20),
                        belt = belt,
                        weightClass = weightClass,
                        avatarId = avatarId,
                        phone = phone.ifBlank { null },
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

    if (pickerOpen) {
        AvatarPickerSheet(
            selectedId = avatarId,
            onDismiss = { pickerOpen = false },
            onSelect = {
                avatarId = it.id
                pickerOpen = false
            },
        )
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
            "카카오로 시작하기",
            style = MaterialTheme.typography.titleMedium,
            color = kakaoText,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            val isSel = item == selected
            Text(
                text = label(item),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
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
