# 교류전 일정 조율 재설계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 두 대전자가 가능한 일정을 3개씩 제시 → 서버가 교집합을 자동 매칭 → 운영진이 정확한 시간·장소를 확정하는 플로우로 교류전 일정을 재설계한다.

**Architecture:** 승인(APPROVED) 후 두 대전자가 `proposeDuelSlots` 로 슬롯(날짜+시간대)을 제출한다. 양측 제출 시 서버가 교집합 중 가장 이른 슬롯으로 `MATCHED` 전이. 운영진이 `scheduleDuel`(재정의)로 시간+장소를 채우면 `SCHEDULED`. 모든 쓰기는 Cloud Functions 콜러블(rules 는 exchangeMatches 클라 쓰기 차단).

**Tech Stack:** Kotlin / Jetpack Compose / Material3 (compose-bom 2024.10) · Firebase Cloud Functions (TS, Node22, region asia-northeast3) · Firestore.

## Global Constraints

- 함수 리전: `asia-northeast3`. 콜러블은 `onCall({ region: "asia-northeast3" }, …)`.
- 슬롯 인코딩: `"yyyy-MM-dd|BAND"`, BAND ∈ `MORNING|AFTERNOON|EVENING`. 날짜는 오늘(KST) 이후만.
- 시간대 정렬: `MORNING < AFTERNOON < EVENING`. 매칭 선택 = 날짜 asc → 시간대 asc 중 첫 번째.
- 표시 포맷: 날짜 `YY/MM/DD (요일)`, 시간 `오전/오후 h:mm`(Locale.KOREAN "a h:mm"), 시간대 `오전/오후/저녁`.
- 검증 방식(이 코드베이스 관행): 단위테스트 하니스 없음 → **컴파일(gradle/tsc) + 에뮬 수동 E2E**. 각 태스크는 빌드 통과 후 커밋.
- 커밋 메시지 말미: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

### Task 1: Kotlin 스키마 — MATCHED 상태 · 슬롯 필드 · DUEL_SCHEDULED 타입 · 읽기 매핑

**Files:**
- Modify: `app/src/main/java/com/unboundapex/octalink/data/schema/Schema.kt`
- Modify: `app/src/main/java/com/unboundapex/octalink/data/repo/firestore/ExchangeMatchDocMapping.kt`

**Interfaces:**
- Produces: `ExchangeMatchStatus.MATCHED`; `ExchangeMatchDoc.requesterSlots: List<String>`, `.opponentSlots: List<String>`, `.scheduledBand: String?`; `NotificationType.DUEL_SCHEDULED`.

- [ ] **Step 1: ExchangeMatchStatus 에 MATCHED 추가**

`Schema.kt` 의 enum 을 아래로 교체:
```kotlin
enum class ExchangeMatchStatus { REQUESTED, APPROVED, MATCHED, SCHEDULED, COMPLETED, REJECTED, CANCELLED }
```

- [ ] **Step 2: ExchangeMatchDoc 에 슬롯·밴드 필드 추가**

`ExchangeMatchDoc` data class 에서 `scheduledDate`/`scheduledTime`/`place` 근처에 필드 추가:
```kotlin
    val scheduledBand: String? = null,
    val requesterSlots: List<String> = emptyList(),
    val opponentSlots: List<String> = emptyList(),
```
(기존 필드는 유지. 기본값이 있으므로 생성자 호출부 영향 없음.)

- [ ] **Step 3: NotificationType 에 DUEL_SCHEDULED 추가**

`Schema.kt` 의 `NotificationType` enum, `DUEL_REQUESTED(...)` 항목 바로 다음에 추가:
```kotlin
    DUEL_SCHEDULED(
        displayName = "교류전 일정",
        description = "교류전 일정이 매칭/확정됐을 때",
        channelId = "octalink_duel_scheduled",
        channelName = "교류전 일정",
        channelDescription = "교류전 일정 매칭·확정 알림",
    ),
```

- [ ] **Step 4: 읽기 매핑에 새 필드 반영**

`ExchangeMatchDocMapping.kt` 의 `toExchangeMatchDoc()` 반환 객체에 추가(기존 `scheduledDate`/`place` 라인 근처):
```kotlin
        scheduledBand = getString("scheduledBand"),
        requesterSlots = (get("requesterSlots") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        opponentSlots = (get("opponentSlots") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :app:assembleDebug -q`
Expected: `BUILD_OK` (에러 없음). 채널은 `NotificationType.values()` 순회로 자동 등록되므로 추가 작업 불필요.

- [ ] **Step 6: 커밋**
```bash
git add app/src/main/java/com/unboundapex/octalink/data/schema/Schema.kt app/src/main/java/com/unboundapex/octalink/data/repo/firestore/ExchangeMatchDocMapping.kt
git commit -m "교류전 스키마: MATCHED 상태 + 슬롯 필드 + DUEL_SCHEDULED 타입"
```

---

### Task 2: 서버 — proposeDuelSlots 신규 · scheduleDuel 재정의 · DUEL_SCHEDULED 등록

**Files:**
- Modify: `functions/src/index.ts`

**Interfaces:**
- Consumes: `loadApprovedCaller`, `isGymStaff`, `sendNotificationTo`, `HttpsError`, `admin` (기존 정의).
- Produces: 콜러블 `proposeDuelSlots(matchId, slots[])`, `scheduleDuel(matchId, time, place)`; `NotificationTypeKey` 에 `"DUEL_SCHEDULED"`.

- [ ] **Step 1: DUEL_SCHEDULED 를 3개 레코드에 등록**

`NotificationTypeKey` 유니온의 `| "DUEL_REQUESTED";` 를 아래로 교체:
```ts
  | "DUEL_REQUESTED"
  | "DUEL_SCHEDULED";
```
`DEFAULT_ENABLED` 에 `DUEL_REQUESTED: true,` 다음 줄 추가: `  DUEL_SCHEDULED: true,`
`CHANNEL_ID` 에 `DUEL_REQUESTED: "octalink_duel",` 다음 줄 추가: `  DUEL_SCHEDULED: "octalink_duel_scheduled",`

- [ ] **Step 2: 밴드 정렬 헬퍼 추가**

`requestDuel` 위(교류전 섹션 헬퍼 `isGymStaff` 다음)에 추가:
```ts
const DUEL_BANDS = ["MORNING", "AFTERNOON", "EVENING"];
function duelBandOrder(b: string): number {
  const i = DUEL_BANDS.indexOf(b);
  return i < 0 ? 99 : i;
}
```

- [ ] **Step 3: proposeDuelSlots 콜러블 추가**

`approveDuel` 정의 다음(또는 `rejectDuel` 앞)에 추가:
```ts
export const proposeDuelSlots = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const matchId = (request.data?.matchId as string | undefined)?.trim();
  const slots = request.data?.slots as string[] | undefined;
  if (!matchId || !Array.isArray(slots) || slots.length < 1 || slots.length > 3) {
    throw new HttpsError("invalid-argument", "matchId 와 슬롯(1~3개)이 필요해요.");
  }
  const todayKst = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Seoul" });
  const norm: string[] = [];
  for (const s of slots) {
    const m = /^(\d{4}-\d{2}-\d{2})\|(MORNING|AFTERNOON|EVENING)$/.exec(s ?? "");
    if (!m) throw new HttpsError("invalid-argument", "슬롯 형식이 올바르지 않아요.");
    if (m[1] < todayKst) throw new HttpsError("invalid-argument", "오늘 이후 날짜만 제시할 수 있어요.");
    if (!norm.includes(s)) norm.push(s);
  }
  await loadApprovedCaller(uid);
  const ref = admin.firestore().doc(`exchangeMatches/${matchId}`);
  await admin.firestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const d = snap.data();
    if (!d) throw new HttpsError("not-found", "교류전을 찾을 수 없어요.");
    if (d.status !== "APPROVED") throw new HttpsError("failed-precondition", "승인 완료된 교류전만 일정을 제시할 수 있어요.");
    const isRequester = uid === d.requesterMemberId;
    const isOpponent = uid === d.opponentMemberId;
    if (!isRequester && !isOpponent) throw new HttpsError("permission-denied", "대전자만 일정을 제시할 수 있어요.");

    const updates: admin.firestore.DocumentData = { updatedAt: admin.firestore.FieldValue.serverTimestamp() };
    if (isRequester) updates.requesterSlots = norm;
    else updates.opponentSlots = norm;

    const reqSlots: string[] = isRequester ? norm : (d.requesterSlots ?? []);
    const oppSlots: string[] = isOpponent ? norm : (d.opponentSlots ?? []);
    if (reqSlots.length > 0 && oppSlots.length > 0) {
      const common = reqSlots.filter((s) => oppSlots.includes(s));
      if (common.length > 0) {
        common.sort((a, b) => {
          const [da, ba] = a.split("|");
          const [db, bb] = b.split("|");
          return da === db ? duelBandOrder(ba) - duelBandOrder(bb) : (da < db ? -1 : 1);
        });
        const [date, band] = common[0].split("|");
        updates.scheduledDate = date;
        updates.scheduledBand = band;
        updates.status = "MATCHED";
      }
    }
    tx.update(ref, updates);
  });
  const after = (await ref.get()).data();
  if (after?.status === "MATCHED") {
    await sendNotificationTo(
      [after.requesterMemberId, after.opponentMemberId],
      "DUEL_SCHEDULED",
      "교류전 일정 매칭",
      `${after.requesterName ?? ""} vs ${after.opponentName ?? ""} — 일정이 맞춰졌어요. 운영진 확정을 기다려요.`,
    );
  }
  return { ok: true };
});
```

- [ ] **Step 4: scheduleDuel 를 확정 단계로 재정의**

기존 `export const scheduleDuel = onCall(...) { ... });` 전체를 아래로 교체:
```ts
export const scheduleDuel = onCall({ region: "asia-northeast3" }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Login required");
  const uid = request.auth.uid;
  const data = request.data ?? {};
  const matchId = (data.matchId as string | undefined)?.trim();
  const time = (data.time as string | undefined)?.trim();
  const place = (data.place as string | undefined)?.trim();
  if (!matchId || !time || !place) {
    throw new HttpsError("invalid-argument", "matchId/time/place required");
  }
  const caller = await loadApprovedCaller(uid);
  const ref = admin.firestore().doc(`exchangeMatches/${matchId}`);
  const snap = await ref.get();
  const d = snap.data();
  if (!d) throw new HttpsError("not-found", "교류전을 찾을 수 없어요.");
  if (!isGymStaff(caller, d.requesterGymId) && !isGymStaff(caller, d.opponentGymId)) {
    throw new HttpsError("permission-denied", "운영진만 확정할 수 있어요.");
  }
  if (d.status !== "MATCHED") {
    throw new HttpsError("failed-precondition", "매칭된 교류전만 확정할 수 있어요.");
  }
  await ref.update({
    scheduledTime: time,
    place,
    status: "SCHEDULED",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  await sendNotificationTo(
    [d.requesterMemberId, d.opponentMemberId],
    "DUEL_SCHEDULED",
    "교류전 일정 확정",
    `${d.requesterName ?? ""} vs ${d.opponentName ?? ""} — ${d.scheduledDate} ${time} @ ${place}`,
  );
  return { ok: true };
});
```

- [ ] **Step 5: 빌드 + 배포**

Run: `cd functions && npm run build`
Expected: tsc 에러 없음.
Run: `cd .. && firebase deploy --only functions:proposeDuelSlots,functions:scheduleDuel`
Expected: 두 함수 Successful create/update.

- [ ] **Step 6: 커밋**
```bash
git add functions/src/index.ts
git commit -m "교류전 서버: proposeDuelSlots(자동매칭) + scheduleDuel 확정단계 재정의 + DUEL_SCHEDULED"
```

---

### Task 3: 클라이언트 — proposeDuelSlots 추가 (추가 전용, 빌드 초록 유지)

`scheduleDuel` 시그니처 변경은 UI 호출부와 함께 바꿔야 컴파일되므로 Task 4 로 미룬다. 이 태스크는 **순수 추가**만 해서 빌드가 깨지지 않게 한다.

**Files:**
- Modify: `app/src/main/java/com/unboundapex/octalink/data/repo/ExchangeMatchRepository.kt`
- Modify: `app/src/main/java/com/unboundapex/octalink/data/repo/firestore/FirestoreExchangeMatchRepository.kt`
- Modify: `app/src/main/java/com/unboundapex/octalink/ui/screens/exchange/ExchangeViewModel.kt`

**Interfaces:**
- Produces: `ExchangeMatchRepository.proposeDuelSlots(matchId, slots)`; `ExchangeViewModel.propose(id, slots)`.

- [ ] **Step 1: 인터페이스에 proposeDuelSlots 추가**

`ExchangeMatchRepository.kt` 의 `scheduleDuel` 라인 위에 추가(기존 scheduleDuel 은 그대로 둠):
```kotlin
    suspend fun proposeDuelSlots(matchId: String, slots: List<String>)
```

- [ ] **Step 2: Firestore 구현에 proposeDuelSlots 추가**

`FirestoreExchangeMatchRepository.kt` 의 `scheduleDuel` override 위에 추가:
```kotlin
    override suspend fun proposeDuelSlots(matchId: String, slots: List<String>) {
        call("proposeDuelSlots", mapOf("matchId" to matchId, "slots" to slots))
    }
```

- [ ] **Step 3: 다른 구현체 확인**

Run: `grep -rln ": ExchangeMatchRepository" app/src/main/java/`
FirestoreExchangeMatchRepository 외 다른 구현체가 나오면 동일하게 `proposeDuelSlots` 추가(본문 no-op `{}` 가능). 없으면 skip.

- [ ] **Step 4: ViewModel 에 propose 추가**

`ExchangeViewModel.kt` 의 `schedule` 위에 추가(기존 schedule 은 그대로 둠):
```kotlin
    fun propose(id: String, slots: List<String>) =
        run("가능한 일정을 제시했어요.") { exRepo.proposeDuelSlots(id, slots) }
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :app:assembleDebug -q`
Expected: `BUILD_OK` (추가만 했으므로 깨질 곳 없음).

- [ ] **Step 6: 커밋**
```bash
git add app/src/main/java/com/unboundapex/octalink/data/repo/ExchangeMatchRepository.kt app/src/main/java/com/unboundapex/octalink/data/repo/firestore/FirestoreExchangeMatchRepository.kt app/src/main/java/com/unboundapex/octalink/ui/screens/exchange/ExchangeViewModel.kt
git commit -m "교류전 repo/VM: proposeDuelSlots 추가"
```

---

### Task 4: UI + scheduleDuel 시그니처 변경 (원자적 — 빌드 초록)

`scheduleDuel(date,time,place)` → `(time,place)` 변경과 UI 호출부 교체를 한 태스크에서 처리해 컴파일을 유지한다.

**Files:**
- Modify: `app/src/main/java/com/unboundapex/octalink/data/repo/ExchangeMatchRepository.kt`
- Modify: `app/src/main/java/com/unboundapex/octalink/data/repo/firestore/FirestoreExchangeMatchRepository.kt`
- Modify: `app/src/main/java/com/unboundapex/octalink/ui/screens/exchange/ExchangeViewModel.kt`
- Modify: `app/src/main/java/com/unboundapex/octalink/ui/screens/exchange/ExchangeScreen.kt`

**Interfaces:**
- Consumes: `ExchangeViewModel.propose(id, slots)`(Task 3), `vm.allGyms`; `ExchangeMatchStatus.MATCHED`; `dayLabelKor`, `schedDateLabel`, `schedTimeLabel`(기존).
- Produces: `ExchangeViewModel.schedule(id, time, place)`; 화면 동작(슬롯 제시/확정).

- [ ] **Step 0: scheduleDuel 시그니처 변경 (repo·VM)**

`ExchangeMatchRepository.kt`: `suspend fun scheduleDuel(matchId: String, date: String, time: String, place: String)` → `suspend fun scheduleDuel(matchId: String, time: String, place: String)`.
`FirestoreExchangeMatchRepository.kt`: 해당 override 를 교체:
```kotlin
    override suspend fun scheduleDuel(matchId: String, time: String, place: String) {
        call("scheduleDuel", mapOf("matchId" to matchId, "time" to time, "place" to place))
    }
```
`ExchangeViewModel.kt`: `schedule` 를 교체:
```kotlin
    fun schedule(id: String, time: String, place: String) =
        run("일정을 확정했어요.") { exRepo.scheduleDuel(id, time, place) }
```
(이 시점엔 ExchangeScreen 이 아직 구 signature 로 호출 → 아래 Step 5 까지 완료 후 한 번에 빌드.)

- [ ] **Step 1: 밴드·슬롯 라벨 헬퍼 추가**

파일 하단(`schedTimeLabel` 근처)에 추가:
```kotlin
private val DUEL_BANDS = listOf("MORNING", "AFTERNOON", "EVENING")
private fun bandLabel(b: String): String = when (b) {
    "MORNING" -> "오전"; "AFTERNOON" -> "오후"; "EVENING" -> "저녁"; else -> b
}
/** "yyyy-MM-dd" → "YY/MM/DD (요일)". schedDateLabel 재사용. */
private fun slotDateLabel(iso: String): String = schedDateLabel(iso)
```

- [ ] **Step 2: statusLine 의 MATCHED/SCHEDULED 갱신**

`statusLine` 의 임시 MATCHED 라인(`"일정 매칭됨 — 확정 대기"`, Task 1에서 추가됨)과 SCHEDULED 라인을 아래 두 줄로 교체:
```kotlin
    ExchangeMatchStatus.MATCHED -> "일정 매칭 · ${schedDateLabel(d.scheduledDate)} ${d.scheduledBand?.let { bandLabel(it) } ?: ""} · 장소·시간 확정 대기"
    ExchangeMatchStatus.SCHEDULED -> "일정 확정 · ${schedDateLabel(d.scheduledDate)} ${d.scheduledBand?.let { bandLabel(it) } ?: ""} ${schedTimeLabel(d.scheduledTime)} @ ${d.place}"
```

- [ ] **Step 3: DuelRow 액션 조건·버튼 변경**

`DuelRow` 내부의 `canApprove`/`canReject`/`canSchedule`/`canResult` 계산부를 아래로 교체(canSchedule 을 MATCHED+staff 로, canPropose 신규):
```kotlin
    val canApprove = d.status == ExchangeMatchStatus.REQUESTED && (
        (iAmOpponent && !d.opponentApproved)
            || (staffOfReq && !d.requesterGymApproved)
            || (staffOfOpp && !d.opponentGymApproved)
    )
    val canReject = d.status == ExchangeMatchStatus.REQUESTED && (iAmParticipant || staffOfReq || staffOfOpp)
    val canPropose = d.status == ExchangeMatchStatus.APPROVED && iAmParticipant
    val canSchedule = d.status == ExchangeMatchStatus.MATCHED && (staffOfReq || staffOfOpp)
    val canResult = d.status == ExchangeMatchStatus.SCHEDULED && (staffOfReq || staffOfOpp)
```
그리고 REQUESTED 승인현황 블록 아래에 APPROVED 제시현황 안내 추가(같은 `Column` 내, `if (d.status == REQUESTED){…}` 다음):
```kotlin
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
```
액션 Row 에 제시 버튼 추가(기존 `if (canApprove) …` 들과 같은 Row 안, `onPropose`/`onSchedule` 콜백은 아래 Step 5 에서 배선):
```kotlin
                    if (canPropose) Button(onClick = onPropose) { Text("일정 제시") }
                    if (canSchedule) Button(onClick = onSchedule) { Text("장소·시간 확정") }
```
그리고 `DuelRow` 파라미터에 `onPropose: () -> Unit` 추가(`onSchedule` 는 기존 유지).

- [ ] **Step 4: ProposeSlotsDialog 컴포저블 추가**

파일에 추가(ScheduleDialog 근처):
```kotlin
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
```
(import 추가: `androidx.compose.runtime.mutableStateListOf`.)

- [ ] **Step 5: ScheduleDialog → FinalizeDialog 로 전환 + 호출부 배선**

`ScheduleDialog` 를 확정 전용으로 축소(날짜는 매칭값 읽기전용, 시간+장소만). 시그니처와 본문 교체:
```kotlin
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
```
호출부: `ExchangeScreen` 의 상태 변수와 렌더 블록 교체.
- `var scheduleTarget` 유지, `var proposeTarget by remember { mutableStateOf<ExchangeMatchDoc?>(null) }` 추가.
- `items(duels) { d -> DuelRow(..., onPropose = { proposeTarget = d }, onSchedule = { scheduleTarget = d }, ...) }`
- 다이얼로그 렌더:
```kotlin
    proposeTarget?.let { d ->
        val mine = if (memberId == d.opponentMemberId) d.opponentSlots else d.requesterSlots
        ProposeSlotsDialog(
            initial = mine,
            onDismiss = { proposeTarget = null },
            onConfirm = { slots -> vm.propose(d.id, slots); proposeTarget = null },
        )
    }
    scheduleTarget?.let { d ->
        fun gymLabel(id: String) = allGyms.firstOrNull { it.id == id }
            ?.let { it.name + (it.branch?.let { b -> " · $b" } ?: "") } ?: id
        val placeOptions = listOf(gymLabel(d.requesterGymId), gymLabel(d.opponentGymId)).distinct()
        FinalizeDialog(
            matchedDate = d.scheduledDate,
            matchedBand = d.scheduledBand,
            placeOptions = placeOptions,
            onDismiss = { scheduleTarget = null },
            onConfirm = { time, place -> vm.schedule(d.id, time, place); scheduleTarget = null },
        )
    }
```
(구 ScheduleDialog 및 그 date/isoDate 관련 코드는 제거.)

- [ ] **Step 6: 빌드 + 설치**

Run: `./gradlew :app:assembleDebug -q`
Expected: `BUILD_OK`.
Run: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: `Success`.

- [ ] **Step 7: 커밋**
```bash
git add app/src/main/java/com/unboundapex/octalink/data/repo/ExchangeMatchRepository.kt app/src/main/java/com/unboundapex/octalink/data/repo/firestore/FirestoreExchangeMatchRepository.kt app/src/main/java/com/unboundapex/octalink/ui/screens/exchange/ExchangeViewModel.kt app/src/main/java/com/unboundapex/octalink/ui/screens/exchange/ExchangeScreen.kt
git commit -m "교류전 UI: 슬롯 제시 다이얼로그 + 확정(FinalizeDialog) + scheduleDuel(time,place) + MATCHED 표시/액션"
```

---

### Task 5: 홈 교류전 배지에 MATCHED 반영

**Files:**
- Modify: `app/src/main/java/com/unboundapex/octalink/ui/screens/home/HomeExchangeViewModel.kt`

**Interfaces:**
- Consumes: `ExchangeMatchStatus.MATCHED`.

- [ ] **Step 1: when 분기에 MATCHED 추가**

`incomingCount` 의 `when (d.status)` 에서 SCHEDULED 분기를 MATCHED 와 공유하도록 교체:
```kotlin
                    ExchangeMatchStatus.REQUESTED, ExchangeMatchStatus.APPROVED -> true
                    ExchangeMatchStatus.MATCHED, ExchangeMatchStatus.SCHEDULED -> {
                        val sd = d.scheduledDate?.let {
                            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
                        }
                        sd != null && !sd.isBefore(today)
                    }
                    else -> false
```

- [ ] **Step 2: 빌드 + 커밋**

Run: `./gradlew :app:assembleDebug -q` → `BUILD_OK`.
```bash
git add app/src/main/java/com/unboundapex/octalink/ui/screens/home/HomeExchangeViewModel.kt
git commit -m "홈 교류전 배지: MATCHED 도 일정 당일까지 유지"
```

---

### Task 6: E2E 검증 (에뮬레이터)

**Files:** 없음(수동 검증).

- [ ] **Step 1: 양 기기 최신 설치**

Run: `for D in emulator-5554 RFCW41APZ1X; do adb -s $D install -r app/build/outputs/apk/debug/app-debug.apk; done`

- [ ] **Step 2: 기존 테스트 결투 상태 리셋(선택)**

APPROVED 결투 하나 필요. 없으면 이지연↔이지예로 신청→3자 승인까지 진행. (기존 `F3dWSAPKh0QAnWgoc0ys`가 있으면 관리자 스크립트로 status=APPROVED, requesterSlots/opponentSlots=[] 로 초기화.)

- [ ] **Step 3: 슬롯 제시 → 매칭 확인**

이지예(에뮬)·이지연(폰)에서 각각 교류전 → "일정 제시" → 겹치는 (날짜+시간대) 포함해 3개씩 제시. 양측 제시 후 상태가 `일정 매칭 · … · 확정 대기(MATCHED)` 로 바뀌는지 + 양측 알림 수신 확인. 불일치 케이스도 1회(겹치는 슬롯 없이 제시 → APPROVED 유지 + "불일치 다시 제시").

- [ ] **Step 4: 확정 → SCHEDULED**

운영진 계정(이지연 CREATOR / 이지예 코치)에서 MATCHED 결투 → "장소·시간 확정" → 타임피커 + 장소 드롭다운 → 확정. 상태 `일정 확정 · YY/MM/DD(요일) 시간대 오후 h:mm @ 장소(SCHEDULED)` + 알림 확인.

- [ ] **Step 5: 홈 배지 확인**

APPROVED/MATCHED 동안 홈 교류전 배지 유지, SCHEDULED 후에도 일정 날짜 전까지 유지, 날짜 지나면 해제.

- [ ] **Step 6: 서버 로그 확인**

Run: `firebase functions:log --only proposeDuelSlots,scheduleDuel`
Expected: 매칭/확정 로그 정상, 에러 없음.
