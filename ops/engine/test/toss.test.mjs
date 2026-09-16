import { test } from 'node:test';
import assert from 'node:assert/strict';
import { getPayment, listSettlements } from '../../connectors/toss.mjs';

const res = (body, ok = true, status = 200) => ({ ok, status, async json() { return body; }, async text() { return JSON.stringify(body); } });

test('시크릿 없으면 no-op', async () => {
  const r1 = await getPayment({ secret: '', paymentKey: 'x', transport: async () => { throw new Error('nope'); } });
  assert.equal(r1.ok, false);
  const r2 = await listSettlements({ secret: '', date: '2026-09-16', transport: async () => { throw new Error('nope'); } });
  assert.equal(r2.ok, false);
  assert.deepEqual(r2.txns, []);
});

test('listSettlements: Basic 인증 헤더 + 정규화', async () => {
  let url, auth;
  const transport = async (u, init) => { url = u; auth = init.headers.Authorization; return res([{ paymentKey: 'k1', amount: 5000, status: 'DONE' }]); };
  const r = await listSettlements({ secret: 'test_sk_123', date: '2026-09-16', transport });
  assert.match(url, /\/settlements\?date=2026-09-16/);
  assert.equal(auth, 'Basic ' + Buffer.from('test_sk_123:').toString('base64'));
  assert.deepEqual(r.txns, [{ tossPaymentKey: 'k1', amountKrw: 5000, status: 'DONE' }]);
});

test('getPayment: 단건 조회', async () => {
  const transport = async () => res({ paymentKey: 'k2', totalAmount: 10000, status: 'DONE' });
  const r = await getPayment({ secret: 's', paymentKey: 'k2', transport });
  assert.equal(r.ok, true);
  assert.equal(r.payment.paymentKey, 'k2');
});

test('non-2xx → throw', async () => {
  await assert.rejects(getPayment({ secret: 's', paymentKey: 'k', transport: async () => res({ message: 'bad' }, false, 404) }), /Toss payment 404/);
});
