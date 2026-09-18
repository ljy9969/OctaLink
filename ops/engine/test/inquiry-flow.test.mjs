import { test } from 'node:test';
import assert from 'node:assert/strict';
import { draftPendingInquiries } from '../inquiry-flow.mjs';

const inqs = [
  { id: 'a', category: 'QUESTION', text: 'q1', authorId: 'u', authorName: 'n' },
  { id: 'b', category: 'BUG', text: 'q2', authorId: 'u', authorName: 'n' },
];

test('draftPendingInquiries: CEO 승인분만 saveDraft, 미승인은 skip', async () => {
  const saved = [];
  const r = await draftPendingInquiries({
    opsRoot: 'x',
    fetchFn: async () => ({ ok: true, inquiries: inqs }),
    draftFn: async (inq) => `draft-${inq.id}`,
    reviewFn: async (inq, draft) => inq.id === 'a'
      ? { approved: true, answer: `${draft}-approved` }
      : { approved: false, answer: '', reason: 'nope' },
    saveDraftFn: async ({ inquiryId, draftAnswer }) => { saved.push([inquiryId, draftAnswer]); },
  });
  assert.equal(r.drafted, 1);
  assert.equal(r.skipped, 1);
  assert.deepEqual(saved, [['a', 'draft-a-approved']]); // CEO 수정본이 저장됨
});

test('draftPendingInquiries: CEO answer 비면 저장 안 함(무조건 검토 게이트)', async () => {
  const saved = [];
  const r = await draftPendingInquiries({
    opsRoot: 'x',
    fetchFn: async () => ({ ok: true, inquiries: [inqs[0]] }),
    draftFn: async () => 'd',
    reviewFn: async () => ({ approved: true, answer: '' }), // 승인했지만 본문 없음
    saveDraftFn: async (x) => { saved.push(x); },
  });
  assert.equal(r.drafted, 0);
  assert.equal(r.skipped, 1);
  assert.equal(saved.length, 0);
});

test('draftPendingInquiries: CEO 파싱 실패(null)면 skip', async () => {
  const r = await draftPendingInquiries({
    opsRoot: 'x',
    fetchFn: async () => ({ ok: true, inquiries: [inqs[0]] }),
    draftFn: async () => 'd',
    reviewFn: async () => null,
    saveDraftFn: async () => { throw new Error('저장하면 안 됨'); },
  });
  assert.equal(r.drafted, 0);
  assert.equal(r.skipped, 1);
});

test('draftPendingInquiries: pending 없거나 자격증명 없으면 0건', async () => {
  const r = await draftPendingInquiries({ opsRoot: 'x', fetchFn: async () => ({ ok: false, reason: 'no cred', inquiries: [] }) });
  assert.equal(r.drafted, 0);
});
