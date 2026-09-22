import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { draftPendingInquiries, overPromises, appendDevTask, openBacklog, markDone, archiveDone, backlogEntry, completeInquiry, sweepCompletions } from '../inquiry-flow.mjs';

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

test('openBacklog: 대기 항목만(완료 제외)', () => {
  const md = '# H\n\n## [개선] 문의 a (t)\n- 상태: 대기\n- dev 지시: x\n\n## [버그] 문의 b (t)\n- 상태: 완료\n- dev 지시: y';
  const out = openBacklog(md);
  assert.match(out, /문의 a/);
  assert.doesNotMatch(out, /문의 b/);
});

test('백로그 생애주기: append(누적·중복방지) → markDone → archiveDone', () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-bl-'));
  const at = new Date('2026-09-18T00:00:00Z');
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'IMPROVEMENT'), devTask: 'fix a', now: at });
  appendDevTask({ opsRoot: ops, inquiry: q('b', 'BUG'), devTask: 'fix b', now: at });
  assert.equal(appendDevTask({ opsRoot: ops, inquiry: q('a', 'IMPROVEMENT'), devTask: 'again', now: at }), false); // 중복 방지
  const p = join(ops, 'tasks', 'dev-backlog.md');
  assert.equal((openBacklog(readFileSync(p, 'utf8')).match(/dev 지시/g) || []).length, 2); // 대기 2건

  assert.equal(markDone({ opsRoot: ops, inquiryId: 'a' }), true);
  assert.doesNotMatch(openBacklog(readFileSync(p, 'utf8')), /문의 a/); // 대기에서 빠짐
  assert.match(openBacklog(readFileSync(p, 'utf8')), /문의 b/);

  assert.equal(archiveDone({ opsRoot: ops }), 1);
  assert.doesNotMatch(readFileSync(p, 'utf8'), /문의 a/); // 활성 백로그에서 제거
  assert.match(readFileSync(join(ops, 'tasks', 'dev-backlog-done.md'), 'utf8'), /문의 a/); // 아카이브 보관
});

test('backlogEntry: 문의 항목의 카테고리/원문/dev지시 파싱', () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-be-'));
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'IMPROVEMENT', '다크테마 구분 안 됨'), devTask: '블럭 구분선 추가', now: new Date('2026-09-18T00:00:00Z') });
  const md = readFileSync(join(ops, 'tasks', 'dev-backlog.md'), 'utf8');
  const e = backlogEntry(md, 'a');
  assert.equal(e.category, 'IMPROVEMENT');
  assert.equal(e.categoryLabel, '개선 제안');
  assert.equal(e.text, '다크테마 구분 안 됨');
  assert.equal(e.devTask, '블럭 구분선 추가');
  assert.equal(backlogEntry(md, 'zzz'), null); // 없는 id
});

test('completeInquiry: 완료 안내 초안 CEO 통과 → saveDraft + 백로그 아카이브', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ci-'));
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'IMPROVEMENT', '다크테마'), devTask: '블럭 구분 개선', now: new Date('2026-09-18T00:00:00Z') });
  const saved = [];
  const r = await completeInquiry({
    opsRoot: ops, inquiryId: 'a', router: {},
    draftFn: async (inq, devTask) => `완료: ${inq.text}/${devTask}`,
    reviewFn: async (inq, d) => ({ approved: true, answer: `${d} 반영했습니다.` }),
    saveDraftFn: async ({ inquiryId, draftAnswer }) => { saved.push([inquiryId, draftAnswer]); return { ok: true }; },
  });
  assert.equal(r.found, true);
  assert.equal(r.drafted, true);
  assert.equal(r.archived, 1);
  assert.deepEqual(saved, [['a', '완료: 다크테마/블럭 구분 개선 반영했습니다.']]);
  const p = join(ops, 'tasks', 'dev-backlog.md');
  assert.doesNotMatch(readFileSync(p, 'utf8'), /문의 a/); // 완료·아카이브됨
});

test('completeInquiry: CEO 미승인이면 초안 미저장(그래도 백로그는 완료·아카이브)', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ci2-'));
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'BUG', '버그'), devTask: 'fix', now: new Date('2026-09-18T00:00:00Z') });
  const saved = [];
  const r = await completeInquiry({
    opsRoot: ops, inquiryId: 'a', router: {},
    draftFn: async () => 'd',
    reviewFn: async () => ({ approved: false, answer: '', reason: '과장됨' }),
    saveDraftFn: async (x) => { saved.push(x); return { ok: true }; },
  });
  assert.equal(r.drafted, false);
  assert.equal(saved.length, 0);
  assert.equal(r.archived, 1); // 완료 처리는 진행
  assert.match(r.reason, /CEO 미승인/);
});

test('completeInquiry: router 없으면 완료 안내 생략(대기 유지), 백로그만 완료·아카이브', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ci3-'));
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'IMPROVEMENT'), devTask: 'x', now: new Date('2026-09-18T00:00:00Z') });
  const r = await completeInquiry({ opsRoot: ops, inquiryId: 'a', router: null });
  assert.equal(r.found, true);
  assert.equal(r.drafted, false);
  assert.equal(r.archived, 1);
  assert.match(r.reason, /router 없음/);
  // 아카이브에 '완료안내: 대기' 로 남아 다음 support 주기 sweep 대상.
  assert.match(readFileSync(join(ops, 'tasks', 'dev-backlog-done.md'), 'utf8'), /완료안내: 대기/);
});

test('sweepCompletions: 대기 개선건 완료안내 작성 → 완료 전환, 재실행 시 건너뜀', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-sw-'));
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'IMPROVEMENT', '다크테마'), devTask: '블럭 구분', now: new Date('2026-09-20T00:00:00Z') });
  markDone({ opsRoot: ops, inquiryId: 'a' });
  archiveDone({ opsRoot: ops }); // 완료안내: 대기 로 아카이브
  const saved = [];
  const opts = {
    opsRoot: ops, router: {},
    draftFn: async (inq, dt) => `완료:${inq.text}/${dt}`,
    reviewFn: async (inq, d) => ({ approved: true, answer: `${d} 반영` }),
    saveDraftFn: async ({ inquiryId, draftAnswer }) => { saved.push([inquiryId, draftAnswer]); return { ok: true }; },
  };
  const r1 = await sweepCompletions(opts);
  assert.equal(r1.drafted, 1);
  assert.deepEqual(saved, [['a', '완료:다크테마/블럭 구분 반영']]);
  assert.match(readFileSync(join(ops, 'tasks', 'dev-backlog-done.md'), 'utf8'), /완료안내: 완료/);
  const r2 = await sweepCompletions(opts); // 이미 완료 → 건너뜀
  assert.equal(r2.drafted, 0);
  assert.equal(saved.length, 1);
});

test('sweepCompletions: 마커 없는 레거시 항목은 건너뜀(재작성 안 함)', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-sw2-'));
  mkdirSync(join(ops, 'tasks'), { recursive: true });
  writeFileSync(join(ops, 'tasks', 'dev-backlog-done.md'),
    '# dev 백로그 — 완료 아카이브\n\n## [개선 제안] 문의 old (2026-09-01T00:00:00Z)\n- 상태: 완료\n- 원문: 옛건\n- dev 지시: x\n- 완료처리: 2026-09-01T00:00:00Z\n');
  const saved = [];
  const r = await sweepCompletions({ opsRoot: ops, router: {}, draftFn: async () => 'd', reviewFn: async () => ({ approved: true, answer: 'a' }), saveDraftFn: async (x) => { saved.push(x); return { ok: true }; } });
  assert.equal(r.drafted, 0);
  assert.equal(saved.length, 0);
});

test('sweepCompletions: router 없으면 대기 유지(발송 skip)', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-sw3-'));
  appendDevTask({ opsRoot: ops, inquiry: q('a', 'BUG', '버그'), devTask: 'fix', now: new Date('2026-09-20T00:00:00Z') });
  markDone({ opsRoot: ops, inquiryId: 'a' });
  archiveDone({ opsRoot: ops });
  const r = await sweepCompletions({ opsRoot: ops, router: null });
  assert.equal(r.drafted, 0);
  assert.match(readFileSync(join(ops, 'tasks', 'dev-backlog-done.md'), 'utf8'), /완료안내: 대기/);
});
