import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { renderProposal, parseProposal, answerPending, applyApproved } from '../inquiry-flow.mjs';

const inq = { id: 'inq1', category: 'QUESTION', text: '출석 어떻게 하나요?', authorId: 'u1', authorName: '홍길동' };

test('renderProposal → parseProposal 왕복', () => {
  const md = renderProposal({ inquiry: inq, draft: '출석 화면에서 체크인하시면 됩니다.', now: new Date('2026-09-17T00:00:00Z') });
  assert.match(md, /카테고리: 문의/);
  assert.match(md, /상태: 대기/);
  const p = parseProposal(md);
  assert.equal(p.inquiryId, 'inq1');
  assert.equal(p.status, '대기');
  assert.equal(p.answer, '출석 화면에서 체크인하시면 됩니다.');
});

test('answerPending: pending 문의마다 approvals 파일 + DRAFTED 표시', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-inq-'));
  const statuses = [];
  const fetchFn = async () => ({ ok: true, inquiries: [inq] });
  const setStatusFn = async ({ inquiryId, status }) => { statuses.push([inquiryId, status]); };
  const draftFn = async (q) => `초안: ${q.text}`;
  const r = await answerPending({ opsRoot: ops, draftFn, fetchFn, setStatusFn, now: () => new Date('2026-09-17T00:00:00Z') });
  assert.equal(r.drafted, 1);
  assert.deepEqual(statuses, [['inq1', 'DRAFTED']]);
  const files = readdirSync(join(ops, 'approvals'));
  assert.deepEqual(files, ['inquiry-inq1.md']);
  assert.match(readFileSync(join(ops, 'approvals', 'inquiry-inq1.md'), 'utf8'), /초안: 출석/);
});

test('answerPending: 자격증명 없으면 0건', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-inq2-'));
  const fetchFn = async () => ({ ok: false, reason: 'no cred', inquiries: [] });
  const r = await answerPending({ opsRoot: ops, draftFn: async () => 'x', fetchFn });
  assert.equal(r.drafted, 0);
});

test('applyApproved: "승인"만 게시하고 .done 처리, "대기"는 건너뜀', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-inq3-'));
  const dir = join(ops, 'approvals'); mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'inquiry-a.md'), renderProposal({ inquiry: { id: 'a', category: 'QUESTION', text: 'q', authorId: 'u', authorName: 'n' }, draft: '답변A' }).replace('상태: 대기', '상태: 승인'));
  writeFileSync(join(dir, 'inquiry-b.md'), renderProposal({ inquiry: { id: 'b', category: 'BUG', text: 'q', authorId: 'u', authorName: 'n' }, draft: '답변B' })); // 대기 유지
  const posted = [];
  const postFn = async ({ inquiryId, answer }) => { posted.push([inquiryId, answer]); };
  const r = await applyApproved({ opsRoot: ops, postFn });
  assert.equal(r.applied, 1);
  assert.deepEqual(posted, [['a', '답변A']]);
  const files = readdirSync(dir).sort();
  assert.deepEqual(files, ['inquiry-a.done.md', 'inquiry-b.md']); // a는 done, b는 대기로 남음
});

test('applyApproved: "반려"는 게시 없이 폐기(.done)', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-inq4-'));
  const dir = join(ops, 'approvals'); mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'inquiry-c.md'), renderProposal({ inquiry: { id: 'c', category: 'PRAISE', text: 'q', authorId: 'u', authorName: 'n' }, draft: 'x' }).replace('상태: 대기', '상태: 반려'));
  const posted = [];
  const r = await applyApproved({ opsRoot: ops, postFn: async (a) => posted.push(a) });
  assert.equal(r.applied, 0);
  assert.equal(r.rejected, 1);
  assert.equal(posted.length, 0);
  assert.deepEqual(readdirSync(dir), ['inquiry-c.done.md']);
});
