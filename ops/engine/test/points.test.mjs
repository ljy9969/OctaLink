import { test } from 'node:test';
import assert from 'node:assert/strict';
import { consume, expire, refundable, balance, requiredTier } from '../../connectors/points.mjs';

const now = new Date('2026-09-16T00:00:00Z');
// 보너스(1주 뒤 만료)와 유상(1년 뒤 만료) 로트
const lots = () => ([
  { id: 'paid1', type: 'paid', remaining: 50, expiresAt: '2027-09-16T00:00:00Z', createdAt: '2026-09-16T00:00:00Z' },
  { id: 'bonus1', type: 'bonus', remaining: 10, expiresAt: '2026-09-23T00:00:00Z', createdAt: '2026-09-16T00:00:00Z' },
]);

test('consume: 만료 임박(보너스) 먼저 차감', () => {
  const r = consume(lots(), 15, now);
  assert.equal(r.ok, true);
  assert.deepEqual(r.deductions, [{ lotId: 'bonus1', type: 'bonus', amount: 10 }, { lotId: 'paid1', type: 'paid', amount: 5 }]);
  assert.equal(balance(r.lots, now).total, 45); // 60 - 15
});

test('consume: 잔액 부족 시 실패(차감 없음)', () => {
  const r = consume(lots(), 100, now);
  assert.equal(r.ok, false);
  assert.equal(r.reason, 'insufficient');
  assert.equal(r.short, 40);
});

test('activeLots/balance: 만료된 로트는 제외', () => {
  const later = new Date('2026-09-30T00:00:00Z'); // 보너스 만료 후
  const b = balance(lots(), later);
  assert.equal(b.bonus, 0);
  assert.equal(b.paid, 50);
});

test('expire: 지난 보너스 로트를 0으로', () => {
  const later = new Date('2026-09-30T00:00:00Z');
  const r = expire(lots(), later);
  assert.deepEqual(r.expired, [{ lotId: 'bonus1', type: 'bonus', amount: 10 }]);
  assert.equal(r.lots.find((l) => l.id === 'bonus1').remaining, 0);
  assert.equal(r.lots.find((l) => l.id === 'paid1').remaining, 50);
});

test('refundable: 미사용 유상만(무상 제외)', () => {
  assert.equal(refundable(lots(), now), 50);
});

test('requiredTier: 회원 수 구간별 티어', () => {
  const tiers = [
    { id: 'free', maxMembers: 15, priceKrw: 0 },
    { id: 'basic', maxMembers: 50, priceKrw: 29000 },
    { id: 'pro', maxMembers: null, priceKrw: 59000 },
  ];
  assert.equal(requiredTier(10, tiers).id, 'free');
  assert.equal(requiredTier(15, tiers).id, 'free');
  assert.equal(requiredTier(16, tiers).id, 'basic');
  assert.equal(requiredTier(500, tiers).id, 'pro');
});
