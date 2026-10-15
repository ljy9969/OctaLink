import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { runCeo } from '../ceo-run.mjs';

test('CEO reads context, writes tasks/<agent>.md and state/ceo.json', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ceo-'));
  for (const a of ['research', 'social']) mkdirSync(join(ops, 'agents', a), { recursive: true });
  mkdirSync(join(ops, 'ceo'), { recursive: true });
  writeFileSync(join(ops, 'ceo', 'config.json'), JSON.stringify({ model: { provider: 'dryrun', name: 'dryrun' } }));
  writeFileSync(join(ops, 'ceo', 'ceo.md'), 'CEO');
  const router = { complete: async () => ({
    text: '{"priorities":["테스터 +6"],"tasks":{"research":"경쟁앱 3개 조사","social":"릴스 2건","ghost":"무시"}}',
    usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } }) };
  const r = await runCeo({ opsRoot: ops, router, now: () => new Date('2026-09-08T00:00:00Z') });
  assert.deepEqual(r.tasksWritten.sort(), ['research', 'social']);   // ghost(존재X) 제외
  assert.ok(existsSync(join(ops, 'tasks', 'research.md')));
  assert.match(readFileSync(join(ops, 'tasks', 'social.md'), 'utf8'), /릴스 2건/);
  assert.equal(JSON.parse(readFileSync(join(ops, 'state', 'ceo.json'), 'utf8')).priorities[0], '테스터 +6');
});

import { summarizeReports, reportDigest } from '../ceo-run.mjs';

const rep = (agent, iso, verdict, body) =>
  `# ${agent} 리포트 (${iso})\n\n- 검증: ${verdict}\n- 게이트: none\n- 비용: $0.0000\n\n## 산출물\n\n### 제목\n**굵게** ${body}\n`;

function makeReports() {
  const dir = mkdtempSync(join(tmpdir(), 'ops-rep-'));
  writeFileSync(join(dir, '.gitkeep'), '');
  writeFileSync(join(dir, 'support-20261013-010301.md'), rep('support', '2026-10-13', 'PASS (1회 시도)', '가장오래된본문'));
  writeFileSync(join(dir, 'support-20261014-010301.md'), rep('support', '2026-10-14', 'PASS (1회 시도)', '두번째본문'));
  writeFileSync(join(dir, 'support-20261015-080302.md'), rep('support', '2026-10-15', 'FAIL (4회 시도)', '최신본문 ' + '가'.repeat(500)));
  writeFileSync(join(dir, 'bug-20261015-070302.md'), rep('bug', '2026-10-15', 'PASS (1회 시도)', '버그본문'));
  return dir;
}

test('reportDigest: 검증·게이트 줄 + 산출물 본문(헤딩·굵게 기호 제거), 글자수 초과 시 …', () => {
  const d = reportDigest(rep('x', 't', 'FAIL (4회 시도)', '본문'.repeat(10)), 8);
  assert.equal(d.verdict, 'FAIL (4회 시도)');
  assert.equal(d.gate, 'none');
  assert.equal(d.excerpt, '제목 굵게 본문…'); // 8글자(공백 포함) 후 …
});

test('summarizeReports: 에이전트별 최신 2개만(최신순), 시각 KST·검증 결과 포함, 가장 오래된 건 제외', () => {
  const out = summarizeReports({ dir: makeReports() });
  assert.ok(out.indexOf('[bug]') < out.indexOf('[support]'));          // 에이전트 이름순
  assert.match(out, /\[support\]\n- 10\/15 17:03 KST · 검증 FAIL \(4회 시도\) · 게이트 none\n  제목 굵게 최신본문/);
  assert.match(out, /- 10\/14 10:03 KST · 검증 PASS/);
  assert.doesNotMatch(out, /가장오래된본문/);                          // 3번째(가장 오래된)는 제외
  const latest = out.split('\n').find((l) => l.includes('최신본문'));
  assert.ok(latest.endsWith('…') && Array.from(latest.trim()).length <= 401); // 최신 400자 제한
});

test('summarizeReports: 폴더 없거나 비면 (리포트 없음)', () => {
  assert.equal(summarizeReports({ dir: join(tmpdir(), 'no-such-reports') }), '(리포트 없음)');
  assert.equal(summarizeReports({ dir: mkdtempSync(join(tmpdir(), 'ops-rep-empty-')) }), '(리포트 없음)');
});

test('runCeo: 리포트 요약(파일명 아님)을 모델에 전달, 승인 대기 목록에 .gitkeep 제외', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ceo2-'));
  for (const a of ['bug', 'support']) mkdirSync(join(ops, 'agents', a), { recursive: true });
  mkdirSync(join(ops, 'ceo'), { recursive: true });
  writeFileSync(join(ops, 'ceo', 'config.json'), JSON.stringify({ model: { provider: 'dryrun', name: 'dryrun' } }));
  writeFileSync(join(ops, 'ceo', 'ceo.md'), 'CEO');
  const src = makeReports();
  mkdirSync(join(ops, 'reports'));
  for (const n of ['support-20261014-010301.md', 'support-20261015-080302.md', 'bug-20261015-070302.md']) {
    writeFileSync(join(ops, 'reports', n), readFileSync(join(src, n)));
  }
  mkdirSync(join(ops, 'approvals'));
  writeFileSync(join(ops, 'approvals', '.gitkeep'), '');
  writeFileSync(join(ops, 'approvals', 'social-20261014-090317.md'), 'x');
  let sent = '';
  const router = { complete: async ({ messages }) => { sent = messages[0].content; return { text: '{"priorities":[],"tasks":{}}' }; } };
  await runCeo({ opsRoot: ops, router, now: () => new Date('2026-10-15T09:00:00Z') });
  assert.match(sent, /최근 리포트\(에이전트별 최신 2개/);
  assert.match(sent, /검증 FAIL \(4회 시도\)/);
  assert.doesNotMatch(sent, /support-20261015-080302\.md/);            // 파일명 나열 아님
  assert.match(sent, /승인 대기: social-20261014-090317\.md\n/);         // .gitkeep 제외
});
