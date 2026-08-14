# 교류전 일정 조율 재설계 — 양측 슬롯 제시 → 자동 매칭 → 운영진 확정

## Context

현재 교류전(결투) 일정은 양측 승인(APPROVED) 후 **운영진 한 명이 단일 날짜/시간/장소를
확정**하면 그대로 SCHEDULED가 된다. 문제: 한쪽이 정한 일정을 상대 대전자가 못 맞출 수 있는데
협의 여지가 없다.

목표: 두 대전자가 **각자 가능한 일정을 3개씩 제시**하고, 겹치는 게 있으면 **자동 매칭**, 없으면
**다시 제시**하게 한다. 매칭된 뒤에는 **운영진이 앱에서 정확한 시간·장소를 기록**해 확정한다.

## 설계 결정 (확정)

- **제시 주체**: 두 대전자 본인(requester / opponent). 승인은 기존대로 상대+양측 운영진 3자 유지.
- **슬롯 단위**: 날짜 + 시간대(오전 MORNING / 오후 AFTERNOON / 저녁 EVENING). 날짜는 오늘(KST) 이후만.
- **매칭 단위**: 날짜 + 시간대가 모두 같으면 교집합. 여러 개면 가장 이른 것(날짜 asc, 그다음 시간대
  MORNING < AFTERNOON < EVENING).
- **확정**: 매칭 후 운영진이 정확한 시간 + 장소(양측 체육관 중)를 앱에서 기록.

## 상태머신

```
REQUESTED --(상대+양측 운영진 3자 승인)--> APPROVED
APPROVED  --(양측 슬롯 제시 & 교집합 존재)--> MATCHED       (교집합 없으면 APPROVED 유지 = 재제시)
MATCHED   --(운영진: 정확 시간 + 장소 기록)--> SCHEDULED
SCHEDULED --(운영진: 결과 기록)--> COMPLETED
어느 단계든: 참가자/운영진 거부 --> REJECTED(상대/운영진) / CANCELLED(요청자)
```

`MATCHED`가 신규 상태. (ExchangeMatchStatus enum 에 추가 — 클라·서버 동기화)

## 데이터 모델 — `ExchangeMatchDoc` 추가 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `requesterSlots` | `List<String>` | 요청자가 낸 슬롯(≤3). 각 `"yyyy-MM-dd\|BAND"` |
| `opponentSlots`  | `List<String>` | 상대가 낸 슬롯(≤3). 동일 인코딩 |
| `scheduledBand`  | `String?` | 매칭된 시간대(MORNING/AFTERNOON/EVENING) |

기존 필드 재활용: `scheduledDate`(ISO, 매칭 날짜) / `scheduledTime`(확정 정확 시간, MATCHED까지 null) /
`place`(확정 장소, MATCHED까지 null). Kotlin `ExchangeMatchDoc` + `ExchangeMatchDocMapping`(읽기) 갱신.

## 서버 (functions/src/index.ts) — 모든 쓰기는 서버 전용(rules: exchangeMatches write=false 유지)

### 신규 `proposeDuelSlots(matchId, slots: string[])`
- 검증: 호출자 = 참가자(requesterMemberId 또는 opponentMemberId), `status == APPROVED`,
  슬롯 1~3개, 각 형식 `yyyy-MM-dd|BAND` + 날짜 ≥ 오늘(KST) + BAND 유효 + 리스트 내 중복 없음.
- 호출자가 요청자면 `requesterSlots`, 상대면 `opponentSlots`에 저장(덮어쓰기 = 재제시).
- **양측 모두 제시됐으면** 교집합 계산:
  - 있으면 가장 이른 (date, band) → `scheduledDate`+`scheduledBand` 세팅, `status=MATCHED`,
    양측 참가자에게 `DUEL_SCHEDULED` 알림("일정이 매칭됐어요").
  - 없으면 상태 변화 없음(APPROVED 유지). (선택) 상대에게 "일정을 다시 맞춰야 해요" 알림.
- 한쪽만 제시된 상태면 저장만 하고 대기.

### `scheduleDuel` 재정의 → **확정 단계**
- 시그니처: `(matchId, time, place)` — 날짜는 매칭값 고정이므로 date 인자 제거.
- 검증: 운영진(양측 gym 중 staff), `status == MATCHED`, time/place 필수.
- `scheduledTime`+`place` 기록, `status=SCHEDULED`, 양측에 `DUEL_SCHEDULED` 알림("일정 확정").
- (과거 날짜 가드는 매칭 단계에서 이미 오늘 이후만 허용되므로 불필요)

`recordDuelResult`(운영진, SCHEDULED→COMPLETED)는 변경 없음.

### 알림
- 신규 타입 `DUEL_SCHEDULED` (채널 `octalink_duel_scheduled`) — 클라 `NotificationType` enum +
  서버 `NotificationTypeKey`/`DEFAULT_ENABLED`/`CHANNEL_ID` 동기화.

## 클라이언트

### Repository / ViewModel
- `ExchangeMatchRepository`: `proposeDuelSlots(matchId, slots)` 추가, `scheduleDuel(matchId, time, place)`
  로 시그니처 변경. Firestore 구현은 각 콜러블 호출.
- `ExchangeViewModel`: `propose(id, slots)` 추가, `schedule(id, time, place)` 로 변경.

### UI — `ExchangeScreen.DuelRow`
- **APPROVED + 내가 참가자**: "가능한 일정 제시" 버튼 → `ProposeSlotsDialog`.
  - 날짜(캘린더, 오늘 이후) + 시간대 칩(오전/오후/저녁)으로 슬롯 1~3개 추가/삭제.
  - 내 제시 슬롯 + 상대 제시 여부("상대 제시 완료/대기") 표시.
- **APPROVED + 양측 제시했으나 불일치**: "일정 불일치 — 다시 제시" 안내 + 재제시 버튼.
- **MATCHED + 운영진**: "장소·시간 확정" 버튼 → `FinalizeDialog`(매칭 날짜 읽기전용 + 타임피커
  + 장소 드롭다운[양측 체육관]).
- **MATCHED (그 외)**: "매칭됨 · 8/20(목) 저녁 · 확정 대기".
- **SCHEDULED**: "일정 확정 · 8/20(목) 오후 7:00 @ 강남점".
- statusLine / 슬롯·시간대 라벨 헬퍼(오전/오후/저녁, YY/MM/DD(요일)) 추가.

### 홈 배지 (`HomeExchangeViewModel`)
- 기존 규칙(REQUESTED/APPROVED 항상, SCHEDULED는 일정 당일까지)에 **MATCHED** 추가 —
  MATCHED도 scheduledDate 기준 일정 당일까지 표시.

## 범위 밖 / 메모
- iOS 포트(`OctaLink-iOS`) 동기화는 후속(스키마/상태/알림).
- 슬롯 최대 3개, 확정 시간은 밴드 강제 안 함(운영진 재량).
- Firestore 인덱스 변경 없음(단일 doc 접근). rules 변경 없음(쓰기 서버 전용 유지).

## 검증 (E2E)
1. 이지연 vs 이지예 APPROVED 상태에서 양측이 각각 3슬롯 제시.
2. 겹치는 (날짜+시간대) 있으면 자동 MATCHED + "매칭됨" 표시/알림. 없으면 APPROVED 유지 → 재제시로 성사.
3. 운영진(이지연 CREATOR / 이지예 코치)이 MATCHED 결투에 시간+장소 확정 → SCHEDULED, 양측 알림.
4. 홈 교류전 배지가 REQUESTED~일정 당일(APPROVED/MATCHED/SCHEDULED 포함) 유지, 날짜 지나면 해제.
5. 서버 로그: `firebase functions:log --only proposeDuelSlots,scheduleDuel`.
