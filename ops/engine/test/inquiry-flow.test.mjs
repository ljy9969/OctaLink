import { test } from 'node:test';
import assert from 'node:assert/strict';
import { draftPendingInquiries, overPromises } from '../inquiry-flow.mjs';

const q = (id, category, text = 'q') => ({ id, category, text, authorId: 'u', authorName: 'n' });

test('overPromises: 없는 조치/확정 일정 문구 감지', () => {
  assert.equal(overPromises('UI 팀에 개선 작업을 진행하도록 지시했습니다.'), true);
  assert.equal(overPromises('곧 업데이트될 예정이니 기다려 주세요.'), true);
  assert.equal(overPromises('이미 수정 완료했습니다.'), true);
  assert.equal(overPromises('소중한 의견 감사합니다. 검토해 개선을 고려하겠습니다.'), false); // 정상
});

test('draftPendingInquiries: CEO 승인분만 saveDraft, 미승인 skip', async () => {
  const saved = [];
  const r = await draftPendingInquiries({
    opsRoot: 'x',
    fetchFn: async () => ({ ok: true, inquiries: [q('a', 'QUESTION'), q('b', 'PRAISE')] }),
    draftFn: async (i) => `draft-${i.id}`,
    reviewFn: async (i, d) => i.id === 'a' ? { approved: true, answer: `${d}-ok` } : { approved: false, answer: '' },
    saveDraftFn: async ({ inquiryId, draftAnswer }) => { saved.push([inquiryId, draftAnswer]); },
    appendDevTaskFn: async () => true,
  });
  assert.equal(r.drafted, 1);
  assert.equal(r.skipped, 1);
  assert.deepEqual(saved, [['a', 'draft-a-ok']]);
});

test('draftPendingInquiries: 허위약속 답변은 승인돼도 저장 안 함(결정적 가드)', async () => {
  const saved = [];
  const r = await draftPendingInquiries({
    opsRoot: 'x',
    fetchFn: async () => ({ ok: true, inquiries: [q('a', 'IMPROVEMENT', '다크테마 개선')] }),
    draftFn: async () => 'd',
    reviewFn: async () => ({ approved: true, answer: 'UI 팀에 지시했습니다. 곧 반영될 예정입니다.', devTask: '다크테마 블럭 구분선 추가' }),
    saveDraftFn: async (x) => { saved.push(x); },
    appendDevTaskFn: async () => true,
  });
  assert.equal(r.drafted, 0);
  assert.equal(r.skipped, 1);
  assert.equal(saved.length, 0);
});

test('draftPendingInquiries: 개선/버그면 CEO devTask를 백로그로 발행', async () => {
  const devTasks = [];
  const r = await draftPendingInquiries({
    opsRoot: 'x',
    fetchFn: async () => ({ ok: true, inquiries: [q('a', 'IMPROVEMENT', '다크테마'), q('b', 'PRAISE')] }),
    draftFn: async () => 'd',
    reviewFn: async (i) => i.category === 'IMPROVEMENT'
      ? { approved: true, answer: '검토해 개선을 고려하겠습니다.', devTask: '다크테마 블럭 구분 개선' }
      : { approved: true, answer: '감사합니다.', devTask: '' },
    saveDraftFn: async () => {},
    appendDevTaskFn: async ({ inquiry, devTask }) => { devTasks.push([inquiry.id, devTask]); return true; },
  });
  assert.equal(r.drafted, 2);
  assert.equal(r.devTasks, 1);
  assert.deepEqual(devTasks, [['a', '다크테마 블럭 구분 개선']]); // 칭찬(b)은 devTask 없음
});

test('draftPendingInquiries: pending 없으면 0건', async () => {
  const r = await draftPendingInquiries({ opsRoot: 'x', fetchFn: async () => ({ ok: false, reason: 'no cred', inquiries: [] }) });
  assert.equal(r.drafted, 0);
});
