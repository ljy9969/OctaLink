# 런북(개요): 결제사 가입 — payments 에이전트 실연결(Phase 2)

> 제가 대신 가입할 수 없습니다(사업자·정산계좌·KYC 필요). 사용자 본인이 수행.

1. 결제사 선택: 국내 **Toss Payments**(권장) 또는 **Stripe**.
2. 사업자 등록/개인 판매자 등록 + 정산 계좌 + 본인인증(KYC).
3. 대시보드에서 **Secret Key**(대사는 읽기 권한이면 충분) 발급.
4. `ops/mcp/.env`의 `TOSS_PAYMENTS_SECRET`(또는 `STRIPE_SECRET`)에 입력.
5. `ops/mcp/connectors.json`의 `payments.status`를 `enabled`로.
6. payments 에이전트는 **읽기·대사·이상감지 리포트**만; 환불 등 자금 이동은 항상 승인 게이트.
