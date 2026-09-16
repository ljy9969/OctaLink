import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reconcile } from '../../connectors/reconcile.mjs';

test('일치하면 불일치 없음', () => {
  const issues = reconcile({
    tossTxns: [{ tossPaymentKey: 'k1', amountKrw: 5000, status: 'DONE' }],
    payments: [{ paymentId: 'p1', tossPaymentKey: 'k1', amountKrw: 5000, kind: 'point_purchase', status: 'paid' }],
    ledger: [{ type: 'purchase', tossPaymentKey: 'k1', points: 50 }],
  });
  assert.deepEqual(issues, []);
});

test('money_no_record: 돈 들어왔는데 내부 기록 없음', () => {
  const issues = reconcile({ tossTxns: [{ tossPaymentKey: 'k9', amountKrw: 10000, status: 'DONE' }], payments: [], ledger: [] });
  assert.equal(issues.some((i) => i.type === 'money_no_record' && i.key === 'k9'), true);
});

test('record_no_money: 내부 결제완료인데 Toss 거래 없음', () => {
  const issues = reconcile({ tossTxns: [], payments: [{ paymentId: 'p2', tossPaymentKey: 'k2', amountKrw: 5000, kind: 'point_purchase', status: 'paid' }], ledger: [] });
  assert.equal(issues.some((i) => i.type === 'record_no_money' && i.paymentId === 'p2'), true);
});

test('amount_mismatch: 금액 불일치', () => {
  const issues = reconcile({
    tossTxns: [{ tossPaymentKey: 'k3', amountKrw: 5000, status: 'DONE' }],
    payments: [{ paymentId: 'p3', tossPaymentKey: 'k3', amountKrw: 4000, status: 'paid' }], ledger: [],
  });
  assert.equal(issues.some((i) => i.type === 'amount_mismatch'), true);
});

test('refund_mismatch: Toss 취소인데 내부 미환불', () => {
  const issues = reconcile({
    tossTxns: [{ tossPaymentKey: 'k4', amountKrw: 5000, status: 'CANCELED' }],
    payments: [{ paymentId: 'p4', tossPaymentKey: 'k4', amountKrw: 5000, status: 'paid' }], ledger: [],
  });
  assert.equal(issues.some((i) => i.type === 'refund_mismatch'), true);
});

test('double_grant: 같은 결제키로 2회 지급', () => {
  const issues = reconcile({
    tossTxns: [{ tossPaymentKey: 'k5', amountKrw: 5000, status: 'DONE' }],
    payments: [{ paymentId: 'p5', tossPaymentKey: 'k5', amountKrw: 5000, status: 'paid' }],
    ledger: [{ type: 'purchase', tossPaymentKey: 'k5', points: 50 }, { type: 'purchase', tossPaymentKey: 'k5', points: 50 }],
  });
  assert.equal(issues.some((i) => i.type === 'double_grant' && i.count === 2), true);
});

test('duplicate_toss: Toss 거래 중복', () => {
  const issues = reconcile({
    tossTxns: [{ tossPaymentKey: 'k6', amountKrw: 5000, status: 'DONE' }, { tossPaymentKey: 'k6', amountKrw: 5000, status: 'DONE' }],
    payments: [{ paymentId: 'p6', tossPaymentKey: 'k6', amountKrw: 5000, status: 'paid' }], ledger: [],
  });
  assert.equal(issues.some((i) => i.type === 'duplicate_toss'), true);
});
