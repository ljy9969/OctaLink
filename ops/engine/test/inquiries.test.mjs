import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fetchPending, setStatus, postAnswer, saveDraft } from '../../connectors/inquiries.mjs';

test('fetchPending: 자격증명·readerFn 없으면 no-op', async () => {
  const r = await fetchPending({});
  assert.equal(r.ok, false);
  assert.deepEqual(r.inquiries, []);
});

test('fetchPending: readerFn 주입 → 필드 정규화', async () => {
  const readerFn = async () => [
    { id: 'a', gymId: 'g', category: 'QUESTION', text: 'q', authorId: 'u', authorName: 'n', extra: 'ignored' },
  ];
  const r = await fetchPending({ readerFn });
  assert.equal(r.ok, true);
  assert.deepEqual(r.inquiries, [{ id: 'a', gymId: 'g', category: 'QUESTION', text: 'q', authorId: 'u', authorName: 'n' }]);
});

test('postAnswer / setStatus: writerFn 주입 실행', async () => {
  let posted = null; let statused = null;
  await postAnswer({ writerFn: async () => { posted = true; }, inquiryId: 'a', answer: 'x' });
  await setStatus({ writerFn: async () => { statused = true; }, inquiryId: 'a', status: 'DRAFTED' });
  assert.equal(posted, true);
  assert.equal(statused, true);
});

test('postAnswer: 자격증명·writerFn 없으면 no-op', async () => {
  const r = await postAnswer({ inquiryId: 'a', answer: 'x' });
  assert.equal(r.ok, false);
});

test('saveDraft: writerFn 주입 실행 / 자격증명 없으면 no-op', async () => {
  let called = false;
  await saveDraft({ writerFn: async () => { called = true; }, inquiryId: 'a', draftAnswer: 'x' });
  assert.equal(called, true);
  const r = await saveDraft({ inquiryId: 'a', draftAnswer: 'x' });
  assert.equal(r.ok, false);
});
