// 포인트 도메인 순수 함수 (Firestore 비의존 → 단위테스트 용이).
// lot: { id, type:'paid'|'bonus', remaining, expiresAt(ISO), createdAt(ISO) }
// 규칙: 소비는 만료 임박 순(보너스 1주가 유상 1년보다 먼저 소진). 환불은 미사용 '유상'만.

const ms = (d) => +new Date(d);

// 활성(미만료·잔량>0) 로트만, 만료 임박 순 정렬.
export function activeLots(lots, now = new Date()) {
  const t = ms(now);
  return lots.filter((l) => l.remaining > 0 && ms(l.expiresAt) > t)
    .sort((a, b) => ms(a.expiresAt) - ms(b.expiresAt));
}

export function balance(lots, now = new Date()) {
  let paid = 0, bonus = 0;
  for (const l of activeLots(lots, now)) (l.type === 'paid' ? (paid += l.remaining) : (bonus += l.remaining));
  return { paid, bonus, total: paid + bonus };
}

// cost를 만료 임박 순으로 차감. 부족하면 {ok:false}. 성공 시 갱신된 lots + 차감 내역.
export function consume(lots, cost, now = new Date()) {
  if (!(cost > 0) || !Number.isFinite(cost)) return { ok: false, reason: 'invalid_cost' };
  const act = activeLots(lots, now);
  const total = act.reduce((s, l) => s + l.remaining, 0);
  if (total < cost) return { ok: false, reason: 'insufficient', short: cost - total };
  const byId = new Map(lots.map((l) => [l.id, { ...l }]));
  let left = cost; const deductions = [];
  for (const l of act) {
    if (left <= 0) break;
    const take = Math.min(l.remaining, left);
    byId.get(l.id).remaining -= take;
    deductions.push({ lotId: l.id, type: l.type, amount: take });
    left -= take;
  }
  return { ok: true, deductions, lots: [...byId.values()] };
}

// 만료 처리: 잔량>0인데 expiresAt 지난 로트 → remaining 0으로. 만료된 내역 반환.
export function expire(lots, now = new Date()) {
  const t = ms(now); const expired = [];
  const out = lots.map((l) => {
    if (l.remaining > 0 && ms(l.expiresAt) <= t) { expired.push({ lotId: l.id, type: l.type, amount: l.remaining }); return { ...l, remaining: 0 }; }
    return l;
  });
  return { expired, lots: out };
}

// 환불 가능액 = 미사용 '유상' 로트 잔량 합(무상 제외).
export function refundable(lots, now = new Date()) {
  return activeLots(lots, now).filter((l) => l.type === 'paid').reduce((s, l) => s + l.remaining, 0);
}

// 회원 수 → 필요한 구독 티어. tiers: [{id, maxMembers|null, priceKrw}]. 초과 시 상위, 최상위 무제한(maxMembers null).
export function requiredTier(memberCount, tiers) {
  const sorted = [...tiers].sort((a, b) => (a.maxMembers ?? Infinity) - (b.maxMembers ?? Infinity));
  for (const t of sorted) if (t.maxMembers == null || memberCount <= t.maxMembers) return t;
  return sorted[sorted.length - 1];
}
