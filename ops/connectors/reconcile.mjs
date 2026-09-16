// 결제 대사(순수 함수): Toss 거래 ↔ 내부 payments + 포인트 원장. 불일치 목록 반환.
// tossTxns: [{ tossPaymentKey, amountKrw, status:'DONE'|'CANCELED'|'PARTIAL_CANCELED' }]
// payments: [{ paymentId, tossPaymentKey, amountKrw, kind:'point_purchase'|'subscription', status:'paid'|'refunded' }]
// ledger:   [{ type:'purchase'|'grant'|'consume'|'refund'|'expire', tossPaymentKey?, points }]
const isDone = (s) => s === 'DONE' || s === 'paid';
const isCanceled = (s) => s === 'CANCELED' || s === 'PARTIAL_CANCELED' || s === 'refunded';

export function reconcile({ tossTxns = [], payments = [], ledger = [] }) {
  const issues = [];
  const payByKey = new Map(payments.map((p) => [p.tossPaymentKey, p]));
  const tossKeys = new Set(tossTxns.map((t) => t.tossPaymentKey));

  const seen = new Set();
  for (const t of tossTxns) {
    if (seen.has(t.tossPaymentKey)) { issues.push({ type: 'duplicate_toss', key: t.tossPaymentKey }); continue; }
    seen.add(t.tossPaymentKey);
    const p = payByKey.get(t.tossPaymentKey);
    if (!p) { issues.push({ type: 'money_no_record', key: t.tossPaymentKey, amountKrw: t.amountKrw }); continue; } // 돈 O·내부기록 X
    if (isDone(t.status) && p.amountKrw !== t.amountKrw) issues.push({ type: 'amount_mismatch', key: t.tossPaymentKey, toss: t.amountKrw, internal: p.amountKrw });
    if (isCanceled(t.status) && p.status !== 'refunded') issues.push({ type: 'refund_mismatch', key: t.tossPaymentKey, tossStatus: t.status, internal: p.status });
  }
  // 내부엔 결제완료로 있는데 Toss 거래가 없음 (기록 O·돈 X)
  for (const p of payments) {
    if (p.status === 'paid' && !tossKeys.has(p.tossPaymentKey)) issues.push({ type: 'record_no_money', paymentId: p.paymentId, key: p.tossPaymentKey });
  }
  // 같은 결제키로 포인트가 2회 이상 지급됨 (중복 지급)
  const grants = new Map();
  for (const e of ledger) if ((e.type === 'purchase' || e.type === 'grant') && e.tossPaymentKey) grants.set(e.tossPaymentKey, (grants.get(e.tossPaymentKey) || 0) + 1);
  for (const [key, n] of grants) if (n > 1) issues.push({ type: 'double_grant', key, count: n });

  return issues;
}
