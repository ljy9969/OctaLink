# 런북: 결제 대사 커넥터 (Toss)

수익화 정책·아키텍처: `docs/superpowers/specs/2026-09-16-monetization-design.md`.
이 커넥터는 **조회·대사 전용**(자금 이동 없음). 실제 결제/환불 실행은 앱 백엔드 + 사람 승인.

## 구성 (ops repo, 1단계 = 구현 완료)
- `connectors/toss.mjs` — Toss 조회 어댑터: `getPayment`(단건), `listSettlements`(일자 정산 → 정규화 txns). `TOSS_PAYMENTS_SECRET` 없으면 **no-op**.
- `connectors/points.mjs` — 순수함수: `consume`(만료 임박 순 차감)·`expire`·`refundable`(유상만)·`balance`·`requiredTier`.
- `connectors/reconcile.mjs` — `reconcile({tossTxns, payments, ledger})` → 불일치 목록(결정적).
- payments 에이전트(`agents/payments/skill.md`) — 대사 리포트(gated 승인).
- 가격/단가/티어 = `connectors/config.json`의 `monetization`(단일 출처, 조정 가능).

## 불일치 유형 (reconcile)
`money_no_record`(돈 O·기록 X) · `record_no_money`(기록 O·돈 X) · `amount_mismatch` · `refund_mismatch`(Toss 취소인데 내부 미환불) · `double_grant`(같은 결제키 2회 지급) · `duplicate_toss`.

## 활성화 (현재 미완 — 시크릿 없음)
1. **Toss Payments 가맹점 계약**(사업자등록 필요) → 시크릿 키 발급.
2. `ops/mcp/.env`에 `TOSS_PAYMENTS_SECRET=live_sk_...`(또는 test_sk_). git 제외 확인됨.
3. 앱 백엔드가 `payments`/`pointLedger`/`pointLots`를 Firestore에 기록(스펙 2·3단계)해야 대사할 데이터가 생김.
4. 시크릿·앱데이터 준비되면 payments 에이전트가 매일(cron 09:30) Toss 정산 ↔ 내부 기록 대사 → `approvals/`.

## 보안
- 시크릿은 **서버 전용**(클라 노출 금지). 웹훅은 **서명 검증** 필수. 카드번호 미저장(Toss 보관).

## 스토어 정책 주의
- 앱 내 디지털 재화는 구글/애플 인앱결제 원칙. Toss 직접결제는 **구독=웹 대시보드 결제로 우회** 권장(스펙 §7-1).
