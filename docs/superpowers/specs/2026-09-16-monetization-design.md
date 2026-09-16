# OctaLink 유료화(수익화) 정책 & 아키텍처 설계

- 날짜: 2026-09-16
- 상태: 승인됨(브레인스토밍) → 구현 플랜 대기
- 범위: OctaLink 앱의 수익화 정책 + 결제/포인트 시스템 아키텍처, 그리고 ops 자율운영의 결제 대사(Phase 2)

## 1. 개요 / 목표

OctaLink는 **하이브리드 수익화**를 채택한다.

- **관장(B2B)**: 각 체육관 **채널(지점)별 관리 SW 구독** (회원 수 티어).
- **회원(B2C)**: 유료 기능을 **포인트 선결제 → 사용 시 차감**.

목표: (a) 명확한 과금 규칙, (b) 만료·환불·대사가 정확한 데이터 구조, (c) ops payments 에이전트가 대사할 수 있는 불변 원장, (d) 베타 단계에 맞춘 config 주도 가격.

결제 수단: **Toss Payments 직접(3자결제)** — 구독은 정기결제(빌링키), 포인트는 일반결제. (스토어 인앱결제 정책 리스크는 §7 참고.)

## 2. 정책 규칙 (확정)

### 2.1 관장 구독 (채널별)
- 채널(지점)마다 개별 구독. 회원 수 구간 티어.
- 기본 티어(예시, config): `Free`(~15명, 0원) / `Basic`(~50명, 29,000원/월) / `Pro`(무제한, 59,000원/월).
- **티어 상향 규칙**: 채널의 활성 회원 수가 현재 티어 상한을 초과하면, **신규 회원 추가를 차단하고 업그레이드를 유도**한다(기본안). 자동 상향은 하지 않는다(관장 동의 필요).

### 2.2 회원 포인트 (유료 기능)
- 유료 기능과 **고정 단가**(예시, config):
  - AI 쉐도우 코치 (세션당): **10P**
  - AI 맞춤 루틴 (생성당): **20P**
  - 교류전 매칭 (매칭당): **30P**
- 무료: 6각형 실력 시각화, 기본 프로필 등.

### 2.3 포인트 종류 & 생애주기
- **유상 포인트**: 결제로 취득. **환불 가능**(미사용분). 사용 유효기간 **1년**.
  - 유효기간 1년은 "사용 기한"이며, **미사용 유상 잔액의 환불 청구권은 상사소멸시효(5년) 내 유지**한다(소멸 전 사전통지). §7-2.
- **무상 포인트(보너스)**: 프로모션/패키지 보너스로 지급. **환불 불가**. 유효기간 **지급 후 1주**.
- **소비 순서**: **만료 임박 순**으로 차감 → 보너스(1주)가 먼저 소진되고 유상(1년)은 최대한 보존.

### 2.4 가격/포인트 (config 기본값)
포인트 환율: **100원 = 1P** (숫자를 작게 유지해 사용 부담 완화).
- 패키지: `5,000원 = 50P` / `10,000원 = 110P(+10% 보너스)` / `30,000원 = 360P(+20% 보너스)`.
  - 보너스분(10P, 60P)은 **무상 포인트**로 지급(1주 만료).
- 모든 금액·단가·티어는 `pointCatalog`/`pointPackages`/`subscriptionTiers`에서 조정.

## 3. 데이터 모델 (Firestore, 프로젝트 octalink-28088)

### 3.1 카탈로그(설정성)
- `subscriptionTiers/{tierId}`: `{ name, maxMembers|null, priceKrw }`
- `pointCatalog/{serviceKey}`: `{ points }` — 예: `aiShadowCoach:{points:10}`, `aiCustomRoutine:{points:20}`, `exchangeMatch:{points:30}`
- `pointPackages/{pkgId}`: `{ krw, paidPoints, bonusPoints }`

### 3.2 구독
- `gyms/{gymId}/private/subscription`: `{ tierId, status: active|past_due|canceled, currentMembers, tossBillingKey, startedAt, nextBillingAt }`

### 3.3 포인트 로트 & 원장 (회원별)
- `pointLots/{memberId}/lots/{lotId}`: `{ type: paid|bonus, initial, remaining, expiresAt, createdAt, sourcePaymentId? }`
  - 로트 = 구매/지급 1건 단위. 만료·환불을 로트 단위로 정확히 처리.
- `pointLedger/{memberId}/entries/{entryId}`: **불변 원장**
  - `{ ts, type: purchase|grant|consume|refund|expire, points(+/-), lotId, service?, tossPaymentKey?, balanceAfter }`
  - payments 커넥터가 대사하는 **진실원본**.
- `members/{memberId}/wallet`(캐시): `{ paidBalance, bonusBalance, updatedAt }` — 빠른 조회용, 원장에서 파생.

### 3.4 결제
- `payments/{paymentId}`: `{ subjectId(gymId|memberId), kind: point_purchase|subscription, amountKrw, tossPaymentKey, tossOrderId, status: paid|canceled|refunded, ts }`
  - `tossOrderId` 유니크 → 멱등 지급.

**설계 근거**: "로트 + 불변 원장" 구조로 ① 만료(1년/1주)를 로트별 자동 처리, ② 유상만 환불(무상 제외)을 정확히 분리, ③ 원장 불변성으로 대사·감사 신뢰성 확보.

## 4. 흐름

### 4.1 구독 결제 (관장 · Toss 정기결제)
1. 관장이 웹 대시보드에서 티어 선택 → Toss **빌링키 발급**(카드 등록) → `subscription.tossBillingKey` 저장.
2. 매월 청구일에 서버 크론이 빌링키로 자동 청구 → `payments`(kind=subscription) 기록 + `nextBillingAt` 갱신.
3. 실패 → `past_due` 재시도 → grace 후 기능 제한(읽기전용 등).

### 4.2 포인트 구매 (회원 · Toss 일반결제)
1. 패키지 선택 → Toss 결제 → **서버 웹훅에서 결제 승인 검증(서명)**.
2. 검증 성공 시 원자적으로: `pointLots`에 유상 로트(+보너스 로트, 1주 만료) 생성 + `pointLedger` purchase/grant 기록 + `payments` 기록 + `wallet` 갱신.
3. **멱등성**: `tossOrderId` 중복이면 재지급하지 않는다.

### 4.3 포인트 차감 (사용)
1. 회원이 유료 기능 호출 → 서버가 **트랜잭션**으로: 총잔액 ≥ 단가 확인 → 만료 임박 순 로트에서 차감 → `ledger consume` 기록 → `wallet` 갱신 → 기능 제공.
2. 잔액 부족 → 구매 유도(기능 미제공). 동시성 안전(트랜잭션).

### 4.4 환불 / 만료
- **환불**: 대상 = **미사용 유상 로트만**. Toss 부분취소 → 로트 `remaining→0`+refunded 표시 + `ledger refund` + `payments.status=refunded`. 무상 로트 제외. 청약철회 7일 우선, 이후 미사용분은 요청 시 환불(수수료 정책 config).
- **만료**: 서버 크론이 `expiresAt` 지난 로트의 remaining을 0으로 + `ledger expire`. 유상 로트는 **소멸 전 사전통지**(알림).

### 4.5 대사 (ops · Phase 2)
- `connectors/toss.mjs`: Toss 결제/정산 조회 API. `TOSS_PAYMENTS_SECRET` 없으면 **no-op**.
- payments 에이전트(매일): **Toss 정산 ↔ Firestore `payments`+`pointLedger`** 대사.
  - 불일치 탐지: (a) 돈 들어옴·포인트 미지급, (b) 포인트 지급·결제 없음, (c) 중복 결제/지급, (d) 환불 금액 불일치, (e) 구독 청구 누락/중복.
  - gated 리포트 → `approvals/` 승인. 자금 이동(환불 실행 등)은 사람 승인.

## 5. 컴포넌트 경계 (이 repo, ops)

- `connectors/toss.mjs` — Toss API 어댑터(조회 전용): `listPayments`, `listSettlements`. 주입 transport. no-op 가드.
- `engine/points.mjs`(또는 connectors 내 순수 모듈) — **순수 함수**: `consume(lots, cost, now)`(만료순 차감), `expire(lots, now)`, `refundable(lots)`, `requiredTier(memberCount, tiers)`. Firestore 비의존 → 단위테스트 용이.
- `connectors/reconcile.mjs` — `reconcile({tossTxns, payments, ledger})` → 불일치 목록(순수 함수).
- payments 에이전트 `skill.md`/`verifier.md` — 대사 리포트 규칙(원장 근거·불일치·이상거래·자금 미이동).
- (앱 백엔드/UI는 이 스펙을 참조하되 별도 저장소에서 구현 — §6.)

## 6. 구현 단계

| 단계 | 범위 | 위치 | 상태 |
|---|---|---|---|
| **1. ops 대사** | `toss.mjs`(조회) + `points.mjs`(순수함수) + `reconcile.mjs` + payments 스킬/검증기 | 이 repo | **이번 구현 대상** (no-op 안전) |
| 2. 앱 백엔드 | Firestore 스키마 + 포인트 차감/환불/만료 + Toss 결제/빌링키/웹훅 | 앱 저장소(별도) | 스펙 참조 |
| 3. 앱 UI | 관장 구독 대시보드(웹) + 회원 포인트 구매/잔액/사용 UI | 앱 저장소(별도) | 스펙 참조 |

## 7. 컴플라이언스 / 리스크

1. **스토어 인앱결제 정책** — 구글 플레이/애플은 앱 내 디지털 재화를 자체 결제로 요구(수수료 15~30%). 한국은 인앱결제 강제방지법으로 3자결제(Toss) 허용이나, 구글은 병행 결제 제공+서비스 수수료를 요구한다. **완화책**: 구독은 웹 대시보드에서 결제(스토어 정책 밖), 포인트도 웹결제 유도를 우선 검토. (Toss 직접은 사용자 제품 결정 — 리스크 명시.)
2. **소비자법** — 유상 포인트 미사용분 환불 의무, 청약철회 7일, 소멸 전 통지. §4.4 반영. 유효기간(1년) 후에도 유상 미사용분 환불 청구권은 소멸시효 내 유지.
3. **PG 보안** — Toss 가맹점 계약(사업자등록) 필요. 웹훅 **서명 검증** 필수, 결제 secret은 **서버 전용**(클라 노출 금지, `ops/mcp/.env` gitignore). 카드번호 미저장(Toss 보관).
4. **세무** — 부가세 포함 표기, 관장 B2B 세금계산서/현금영수증.

## 8. 테스트 전략

- 순수 함수 node:test: `consume`(만료순·부족)·`expire`·`refundable`(유상만)·`requiredTier`·`reconcile`(각 불일치 케이스)·멱등(`tossOrderId` 중복).
- `toss.mjs`: 주입 transport로 no-op·조회·정산 파싱.
- 통합 시나리오: 구매→차감→환불 시 원장·잔액 정합성.

## 9. 활성화 의존성 / 범위 밖

- **활성화 의존성**: Toss 가맹점 계약(사업자등록) + `TOSS_PAYMENTS_SECRET`. 미보유 시 `toss.mjs`는 no-op 대기(Firebase/Instagram과 동일 패턴). **현재 시크릿 없음 → 1단계 커넥터는 no-op 상태로 빌드.**
- **범위 밖(이 스펙 아님)**: 앱 클라이언트 UI 구현, Toss SDK 실연동, 세금 신고 자동화, 광고(Meta Ads) 커넥터(별도 Phase 2 항목).
