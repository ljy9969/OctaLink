// Toss Payments 조회 어댑터(조회 전용 — 자금 이동 없음). 대사·검증용.
// TOSS_PAYMENTS_SECRET 없으면 no-op(안전). 인증: Basic base64(secret + ':').
function authHeader(secret) { return 'Basic ' + Buffer.from(`${secret}:`).toString('base64'); }
async function errText(res) { try { return (await res.text()).slice(0, 150); } catch { return ''; } }

// 결제 단건 조회(대사/웹훅 검증용).
export async function getPayment({ secret, paymentKey, transport = globalThis.fetch, apiBase = 'https://api.tosspayments.com/v1' }) {
  if (!secret) return { ok: false, reason: 'TOSS_PAYMENTS_SECRET 없음 (조회 skip)' };
  const res = await transport(`${apiBase}/payments/${encodeURIComponent(paymentKey)}`, { headers: { Authorization: authHeader(secret) } });
  if (!res.ok) throw new Error(`Toss payment ${res.status}: ${await errText(res)}`);
  return { ok: true, payment: await res.json() };
}

// 특정 일자 정산 조회 → 대사 입력용 거래 목록으로 정규화.
export async function listSettlements({ secret, date, page = 1, size = 100, transport = globalThis.fetch, apiBase = 'https://api.tosspayments.com/v1' }) {
  if (!secret) return { ok: false, reason: 'TOSS_PAYMENTS_SECRET 없음 (조회 skip)', txns: [] };
  const res = await transport(`${apiBase}/settlements?date=${encodeURIComponent(date)}&page=${page}&size=${size}`, { headers: { Authorization: authHeader(secret) } });
  if (!res.ok) throw new Error(`Toss settlements ${res.status}: ${await errText(res)}`);
  const j = await res.json();
  const rows = Array.isArray(j) ? j : (j.settlements || j.data || []);
  const txns = rows.map((r) => ({
    tossPaymentKey: r.paymentKey || r.tossPaymentKey,
    amountKrw: r.amount ?? r.totalAmount ?? r.amountKrw,
    status: r.status || 'DONE',
  }));
  return { ok: true, txns };
}
