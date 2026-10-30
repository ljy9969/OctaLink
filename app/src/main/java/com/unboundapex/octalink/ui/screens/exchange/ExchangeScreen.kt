package com.unboundapex.octalink.ui.screens.exchange

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unboundapex.octalink.data.dayLabelKor
import com.unboundapex.octalink.data.geo.GeoDistance
import com.unboundapex.octalink.data.repo.NearbyGym
import com.unboundapex.octalink.data.schema.ExchangeMatchDoc
import com.unboundapex.octalink.data.schema.ExchangeMatchStatus
import com.unboundapex.octalink.data.schema.GymDoc
import com.unboundapex.octalink.ui.components.PosseCard
import com.unboundapex.octalink.ui.components.PosseScreen
import com.unboundapex.octalink.ui.map.GymMapColors
import com.unboundapex.octalink.ui.map.KakaoGymMap
import com.unboundapex.octalink.ui.map.MapPin
import com.unboundapex.octalink.ui.map.PinKind

/** 반경 안 체육관 목록에서 처음에 보여줄 개수(나머지는 '더 보기'). */
private const val NEARBY_PREVIEW = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(
    memberId: String,
    myGymId: String,
    isStaff: Boolean,
    /** 신청 가능한(가입) 체육관 → 관원 신청 페이지. */
    onOpenGym: (String) -> Unit,
    vm: ExchangeViewModel = viewModel(),
) {
    LaunchedEffect(memberId, myGymId, isStaff) { vm.bind(memberId, myGymId, isStaff) }

    val myGym by vm.myGym.collectAsState()
    val memberGyms by vm.memberGyms.collectAsState()
    val radiusKm by vm.radiusKm.collectAsState()
    val nearby by vm.nearby.collectAsState()
    val myDuels by vm.myDuels.collectAsState()
    val gymDuels by vm.gymDuels.collectAsState()
    val allGyms by vm.allGyms.collectAsState()
    val message by vm.message.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    // 내 교류전 + (운영진) 우리 체육관 교류전 병합, id 중복 제거, 최신순.
    val duels = remember(myDuels, gymDuels) {
        (myDuels + gymDuels).associateBy { it.id }.values.sortedByDescending { it.createdAt }
    }
    // 주변 체육관 중 OctaLink 가입 체육관은 '신청 가능한 체육관'(파란 점)으로 따로 보여주므로 여기선 뺀다.
    val places = remember(nearby) {
        (nearby as? NearbyState.Loaded)?.places.orEmpty().filter { it.memberGymId == null }
    }
    var openPlaceId by remember { mutableStateOf<String?>(null) }
    val openPlace = places.firstOrNull { it.placeId == openPlaceId }
    var showAllNearby by remember(radiusKm) { mutableStateOf(false) }

    var proposeTarget by remember { mutableStateOf<ExchangeMatchDoc?>(null) }
    var scheduleTarget by remember { mutableStateOf<ExchangeMatchDoc?>(null) }
    var resultTarget by remember { mutableStateOf<ExchangeMatchDoc?>(null) }

    PosseScreen(title = "교류전", subtitle = "다른 체육관 관원과 결투") {
        // 지도(MapView)가 스크롤로 화면 밖에 나가도 다시 만들어지지 않게 Lazy 대신 일반 스크롤.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GymFinderCard(
                myGym = myGym,
                radiusKm = radiusKm,
                onRadius = vm::setRadius,
                memberGyms = memberGyms,
                places = places,
                onPinClick = { key ->
                    when {
                        key.startsWith(PIN_GYM) -> onOpenGym(key.removePrefix(PIN_GYM))
                        key.startsWith(PIN_PLACE) -> openPlaceId = key.removePrefix(PIN_PLACE)
                    }
                },
            )

            // ── 신청 가능한 체육관(가입) — 반경과 상관없이 전부 ──
            SectionHeader("신청 가능한 체육관", "반경과 상관없이 · ${memberGyms.size}곳")
            if (memberGyms.isEmpty()) Hint("아직 OctaLink를 쓰는 다른 체육관이 없어요.")
            memberGyms.forEach { row -> MemberGymItem(row, onClick = { onOpenGym(row.gym.id) }) }

            // ── 반경 안 체육관(아직 OctaLink 전) ──
            SectionHeader(
                "${radiusKm}km 안 체육관",
                if (nearby is NearbyState.Loaded) "${places.size}곳 · 가까운 순" else null,
            )
            when (val s = nearby) {
                NearbyState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Hint("주변 체육관을 찾고 있어요…")
                }
                NearbyState.NeedsLocation -> Hint("우리 체육관 위치가 등록되면 주변 체육관을 보여드려요.")
                is NearbyState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Hint(s.message) }
                    TextButton(onClick = vm::retryNearby) { Text("다시 시도") }
                }
                is NearbyState.Loaded -> if (places.isEmpty()) {
                    Hint(
                        if (radiusKm < RADIUS_OPTIONS_KM.last()) "${radiusKm}km 안에 교류할 체육관이 없어요. 반경을 넓혀 보세요."
                        else "${radiusKm}km 안에 교류할 체육관이 없어요.",
                    )
                } else {
                    PosseCard(padding = PaddingValues(0.dp)) {
                        Column {
                            val shown = if (showAllNearby) places else places.take(NEARBY_PREVIEW)
                            shown.forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                NearbyGymItem(p, onOpen = { openPlaceId = p.placeId }, onToggle = { vm.toggleInterest(p) })
                            }
                            if (!showAllNearby && places.size > NEARBY_PREVIEW) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                TextButton(onClick = { showAllNearby = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("더 보기 (${places.size - NEARBY_PREVIEW}곳)")
                                }
                            }
                        }
                    }
                }
            }

            // ── 내/우리 체육관 교류전 ──
            Spacer(Modifier.height(8.dp))
            Text("교류전 현황", style = MaterialTheme.typography.titleMedium)
            if (duels.isEmpty()) {
                Text("아직 교류전이 없어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            duels.forEach { d ->
                DuelRow(
                    d = d,
                    memberId = memberId,
                    myGymId = myGymId,
                    isStaff = isStaff,
                    onApprove = { vm.approve(d.id) },
                    onReject = { vm.reject(d.id) },
                    onPropose = { proposeTarget = d },
                    onSchedule = { scheduleTarget = d },
                    onResult = { resultTarget = d },
                )
            }
        }
    }

    openPlace?.let { p ->
        NearbyPlaceSheet(place = p, onDismiss = { openPlaceId = null }, onToggle = { vm.toggleInterest(p) })
    }

    proposeTarget?.let { d ->
        val mine = if (memberId == d.opponentMemberId) d.opponentSlots else d.requesterSlots
        ProposeSlotsDialog(
            initial = mine,
            onDismiss = { proposeTarget = null },
            onConfirm = { slots -> vm.propose(d.id, slots); proposeTarget = null },
        )
    }
    scheduleTarget?.let { d ->
        fun gymLabel(id: String) = allGyms.firstOrNull { it.id == id }?.label ?: id
        val placeOptions = listOf(gymLabel(d.requesterGymId), gymLabel(d.opponentGymId)).distinct()
        FinalizeDialog(
            matchedDate = d.scheduledDate,
            matchedBand = d.scheduledBand,
            placeOptions = placeOptions,
            onDismiss = { scheduleTarget = null },
            onConfirm = { time, place -> vm.schedule(d.id, time, place); scheduleTarget = null },
        )
    }
    resultTarget?.let { d ->
        ResultDialog(
            duel = d,
            onDismiss = { resultTarget = null },
            onConfirm = { winnerId, draw -> vm.recordResult(d.id, winnerId, draw); resultTarget = null },
        )
    }
}

/** 지도 점 key 접두사 — 가입 체육관(gymId) / 카카오 장소(placeId). */
private const val PIN_GYM = "gym:"
private const val PIN_PLACE = "place:"

/** 상대 체육관 찾기 — 반경 선택 + 카카오맵(반경 원·체육관 점) + 범례. */
@Composable
private fun GymFinderCard(
    myGym: GymDoc?,
    radiusKm: Int,
    onRadius: (Int) -> Unit,
    memberGyms: List<MemberGymRow>,
    places: List<NearbyGym>,
    onPinClick: (String) -> Unit,
) {
    PosseCard(padding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("우리 체육관에서", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                RadiusSelector(radiusKm, onRadius)
            }
            val mapModifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
            val lat = myGym?.lat
            val lng = myGym?.lng
            if (lat != null && lng != null) {
                val pins = remember(lat, lng, memberGyms, places) {
                    buildList {
                        places.forEach { add(MapPin(PIN_PLACE + it.placeId, it.lat, it.lng, PinKind.OTHER_GYM)) }
                        memberGyms.forEach { r ->
                            val g = r.gym
                            if (g.lat != null && g.lng != null) add(MapPin(PIN_GYM + g.id, g.lat, g.lng, PinKind.MEMBER_GYM))
                        }
                        add(MapPin("me", lat, lng, PinKind.MY_GYM))
                    }
                }
                KakaoGymMap(
                    centerLat = lat,
                    centerLng = lng,
                    radiusKm = radiusKm,
                    pins = pins,
                    onPinClick = onPinClick,
                    primary = MaterialTheme.colorScheme.primary,
                    modifier = mapModifier,
                )
            } else {
                Box(mapModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(
                        if (myGym == null) "체육관 정보를 불러오고 있어요…"
                        else "우리 체육관 위치가 아직 등록되지 않았어요.\n운영자에게 위치 등록을 요청해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(MaterialTheme.colorScheme.primary, "우리 체육관")
                LegendDot(GymMapColors.MemberBlue, "신청 가능")
                LegendDot(GymMapColors.OtherGray, "아직 OctaLink 전")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusSelector(selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        RADIUS_OPTIONS_KM.forEachIndexed { i, km ->
            SegmentedButton(
                selected = km == selected,
                onClick = { onSelect(km) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = RADIUS_OPTIONS_KM.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {},
                label = { Text("${km}km") },
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, 9)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dot(color: Color, sizeDp: Int = 10) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SectionHeader(title: String, meta: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (meta != null) {
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 신청 가능한(가입) 체육관 한 줄 — 누르면 관원 신청 페이지. */
@Composable
private fun MemberGymItem(row: MemberGymRow, onClick: () -> Unit) {
    PosseCard(
        modifier = Modifier.clickable(onClick = onClick),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(GymMapColors.MemberBlue)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.gym.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val district = row.gym.address?.split(" ")?.getOrNull(1)
                Text(
                    listOfNotNull(row.distanceM?.let { GeoDistance.label(it) } ?: "위치 미등록", district).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = GymMapColors.MemberBlue.copy(alpha = 0.16f),
                    contentColor = GymMapColors.MemberBlue,
                ),
            ) { Text("관원 보기") }
        }
    }
}

/** 반경 안 체육관(아직 OctaLink 전) 한 줄 — 누르면 상세 시트, 버튼은 교류 요청 토글. */
@Composable
private fun NearbyGymItem(place: NearbyGym, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(GymMapColors.OtherGray, 9)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                nearbyMeta(place),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        val small = Modifier.height(34.dp)
        val pad = PaddingValues(horizontal = 12.dp)
        if (place.requestedByMe) {
            FilledTonalButton(
                onClick = onToggle,
                modifier = small,
                contentPadding = pad,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { Text("요청함") }
        } else {
            OutlinedButton(onClick = onToggle, modifier = small, contentPadding = pad) { Text("교류 요청") }
        }
    }
}

private fun nearbyMeta(p: NearbyGym): String = buildString {
    append(p.sportLabel)
    append(" · ")
    append(GeoDistance.label(p.distanceM.toDouble()))
    // 우리 체육관 회원의 요청 수(전체·우리 체육관 구분은 상세 시트에서).
    if (p.myGymRequestCount > 0) append(" · 요청 ${p.myGymRequestCount}명")
}

/** 아직 OctaLink 전인 체육관 상세 — 교류 요청(익명 집계) · 카카오맵 장소 페이지. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyPlaceSheet(place: NearbyGym, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(place.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOf(place.sportLabel, place.district, "우리 체육관에서 ${GeoDistance.label(place.distanceM.toDouble())}")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (place.address.isNotBlank()) {
                Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "아직 OctaLink를 쓰지 않는 체육관이에요. 교류를 요청한 회원이 모이면 OctaLink 팀이 이 체육관에 소개할게요.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (place.requestCount > 0) {
                Text(
                    "교류 요청 ${place.requestCount}명 · 우리 체육관 ${place.myGymRequestCount}명",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            val big = Modifier
                .fillMaxWidth()
                .height(50.dp)
            if (place.requestedByMe) {
                OutlinedButton(onClick = onToggle, modifier = big) { Text("교류 요청 취소") }
            } else {
                Button(onClick = onToggle, modifier = big) { Text("교류 요청") }
            }
            TextButton(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(place.kakaoMapUrl))) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("카카오맵에서 보기") }
            Text(
                "요청한 회원 이름은 체육관에 전달되지 않아요. 다시 누르면 취소돼요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DuelRow(
    d: ExchangeMatchDoc,
    memberId: String,
    myGymId: String,
    isStaff: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPropose: () -> Unit,
    onSchedule: () -> Unit,
    onResult: () -> Unit,
) {
    // 내가 이 교류전에서 승인해야 할 주체인지 (상대 본인 / 우리 체육관 운영진).
    val iAmOpponent = memberId == d.opponentMemberId
    val iAmParticipant = iAmOpponent || memberId == d.requesterMemberId
    val staffOfReq = isStaff && myGymId == d.requesterGymId
    val staffOfOpp = isStaff && myGymId == d.opponentGymId
    val canApprove = d.status == ExchangeMatchStatus.REQUESTED && (
        (iAmOpponent && !d.opponentApproved)
            || (staffOfReq && !d.requesterGymApproved)
            || (staffOfOpp && !d.opponentGymApproved)
    )
    val canReject = d.status == ExchangeMatchStatus.REQUESTED && (iAmParticipant || staffOfReq || staffOfOpp)
    val canPropose = d.status == ExchangeMatchStatus.APPROVED && iAmParticipant
    val canSchedule = d.status == ExchangeMatchStatus.MATCHED && (staffOfReq || staffOfOpp)
    val canResult = d.status == ExchangeMatchStatus.SCHEDULED && (staffOfReq || staffOfOpp)

    PosseCard {
        Column {
            Text(
                "${d.requesterName}(${d.requesterGymId}) vs ${d.opponentName}(${d.opponentGymId})",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(2.dp))
            Text(statusLine(d), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (d.status == ExchangeMatchStatus.REQUESTED) {
                Text(
                    "승인: 상대 ${chk(d.opponentApproved)} · 요청측 운영진 ${chk(d.requesterGymApproved)} · 상대측 운영진 ${chk(d.opponentGymApproved)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (d.status == ExchangeMatchStatus.APPROVED) {
                val mine = if (iAmOpponent) d.opponentSlots else d.requesterSlots
                val oppDone = if (iAmOpponent) d.requesterSlots.isNotEmpty() else d.opponentSlots.isNotEmpty()
                val bothTried = d.requesterSlots.isNotEmpty() && d.opponentSlots.isNotEmpty()
                Text(
                    when {
                        bothTried -> "일정 불일치 — 다시 제시해 주세요."
                        mine.isNotEmpty() -> "내 일정 제시 완료 · 상대 ${if (oppDone) "완료" else "대기"}"
                        else -> "가능한 일정을 제시해 주세요."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canApprove || canReject || canPropose || canSchedule || canResult) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canApprove) Button(onClick = onApprove) { Text("승인") }
                    if (canPropose) Button(onClick = onPropose) { Text("일정 제시") }
                    if (canSchedule) Button(onClick = onSchedule) { Text("장소·시간 확정") }
                    if (canResult) Button(onClick = onResult) { Text("결과 입력") }
                    if (canReject) OutlinedButton(onClick = onReject) { Text("반려/취소") }
                }
            }
        }
    }
}

private fun chk(b: Boolean) = if (b) "✓" else "…"

private fun statusLine(d: ExchangeMatchDoc): String = when (d.status) {
    ExchangeMatchStatus.REQUESTED -> "요청됨 — 승인 대기"
    ExchangeMatchStatus.APPROVED -> "승인 완료 — 일정 대기"
    ExchangeMatchStatus.MATCHED -> "일정 매칭 · ${schedDateLabel(d.scheduledDate)} ${d.scheduledBand?.let { bandLabel(it) } ?: ""} · 장소·시간 확정 대기"
    ExchangeMatchStatus.SCHEDULED -> "일정 확정 · ${schedDateLabel(d.scheduledDate)} ${d.scheduledBand?.let { bandLabel(it) } ?: ""} ${schedTimeLabel(d.scheduledTime)} @ ${d.place}"
    ExchangeMatchStatus.COMPLETED -> if (d.isDraw) "종료 · 무승부" else "종료 · 승자 ${if (d.winnerMemberId == d.requesterMemberId) d.requesterName else d.opponentName}"
    ExchangeMatchStatus.REJECTED -> "반려됨"
    ExchangeMatchStatus.CANCELLED -> "취소됨"
}

/** 저장된 ISO 일정("yyyy-MM-dd") → 표시용 "YY/MM/DD (요일)". 파싱 실패 시 원본. */
private fun schedDateLabel(iso: String?): String {
    val d = iso?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return iso.orEmpty()
    return "%02d/%02d/%02d (%s)".format(d.year % 100, d.monthValue, d.dayOfMonth, dayLabelKor(d.dayOfWeek))
}

/** 저장된 "HH:mm" → 표시용 "오전/오후 h:mm". 파싱 실패 시 원본. */
private fun schedTimeLabel(hhmm: String?): String {
    val t = hhmm?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: return hhmm.orEmpty()
    return t.format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
}

private val DUEL_BANDS = listOf("MORNING", "AFTERNOON", "EVENING")
private fun bandLabel(b: String): String = when (b) {
    "MORNING" -> "오전"; "AFTERNOON" -> "오후"; "EVENING" -> "저녁"; else -> b
}
/** "yyyy-MM-dd" → "YY/MM/DD (요일)". schedDateLabel 재사용. */
private fun slotDateLabel(iso: String): String = schedDateLabel(iso)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposeSlotsDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val slots = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var pickDateMillis by remember { mutableStateOf<Long?>(null) }
    var pickBand by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }

    val pickDateIso = pickDateMillis?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
    }
    fun addSlot() {
        val iso = pickDateIso ?: return
        val band = pickBand ?: return
        val s = "$iso|$band"
        if (slots.size < 3 && s !in slots) slots.add(s)
        pickDateMillis = null; pickBand = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("가능한 일정 제시 (최대 3개)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { s ->
                    val (iso, band) = s.split("|")
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${slotDateLabel(iso)} ${bandLabel(band)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { slots.remove(s) }) { Text("삭제") }
                    }
                }
                if (slots.size < 3) {
                    Text("새 슬롯 추가", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(pickDateIso?.let { "📅  ${slotDateLabel(it)}" } ?: "📅  날짜 선택")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DUEL_BANDS.forEach { b ->
                            val sel = pickBand == b
                            OutlinedButton(
                                onClick = { pickBand = b },
                                colors = if (sel) androidx.compose.material3.ButtonDefaults.buttonColors() else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                            ) { Text(bandLabel(b)) }
                        }
                    }
                    TextButton(
                        onClick = { addSlot() },
                        enabled = pickDateIso != null && pickBand != null,
                    ) { Text("+ 슬롯 추가") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(slots.toList()) }, enabled = slots.isNotEmpty()) { Text("제시") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (showDate) {
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
        val minMillis = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val minYear = today.year
        val selectable = remember(minMillis) {
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
                override fun isSelectableYear(year: Int): Boolean = year >= minYear
            }
        }
        val state = rememberDatePickerState(selectableDates = selectable)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { pickDateMillis = state.selectedDateMillis; showDate = false }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinalizeDialog(
    matchedDate: String?,
    matchedBand: String?,
    placeOptions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,   // (time "HH:mm", place)
) {
    var hour by remember { mutableStateOf<Int?>(null) }
    var minute by remember { mutableStateOf<Int?>(null) }
    var place by remember { mutableStateOf(placeOptions.firstOrNull().orEmpty()) }
    var showTime by remember { mutableStateOf(false) }
    var placeOpen by remember { mutableStateOf(false) }

    val timeText = if (hour != null && minute != null) {
        java.time.LocalTime.of(hour!!, minute!!).format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
    } else ""
    val isoTime = if (hour != null && minute != null) "%02d:%02d".format(hour!!, minute!!) else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교류전 확정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "매칭 일정 · ${matchedDate?.let { schedDateLabel(it) } ?: ""} ${matchedBand?.let { bandLabel(it) } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (timeText.isBlank()) "🕐  정확한 시간 선택" else "🕐  $timeText")
                }
                ExposedDropdownMenuBox(expanded = placeOpen, onExpandedChange = { placeOpen = it }) {
                    OutlinedTextField(
                        value = place, onValueChange = {}, readOnly = true, label = { Text("장소") },
                        placeholder = { Text("체육관 선택") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placeOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = placeOpen, onDismissRequest = { placeOpen = false }) {
                        placeOptions.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { place = opt; placeOpen = false }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(isoTime, place) }, enabled = isoTime.isNotBlank() && place.isNotBlank()) { Text("확정") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
    if (showTime) {
        val state = rememberTimePickerState(is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { hour = state.hour; minute = state.minute; showTime = false }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("취소") } },
            text = { Column { TimePicker(state = state) } },
        )
    }
}

@Composable
private fun ResultDialog(duel: ExchangeMatchDoc, onDismiss: () -> Unit, onConfirm: (String?, Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교류전 결과") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("승자를 선택하세요.")
                Button(onClick = { onConfirm(duel.requesterMemberId, false) }, modifier = Modifier.fillMaxWidth()) { Text("${duel.requesterName} 승") }
                Button(onClick = { onConfirm(duel.opponentMemberId, false) }, modifier = Modifier.fillMaxWidth()) { Text("${duel.opponentName} 승") }
                OutlinedButton(onClick = { onConfirm(null, true) }, modifier = Modifier.fillMaxWidth()) { Text("무승부") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
