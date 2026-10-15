import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, existsSync, utimesSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pruneByAgent, stampTime, agentOf, RETENTION } from '../prune.mjs';

function makeDir() {
  const dir = mkdtempSync(join(tmpdir(), 'ops-prune-'));
  const w = (n) => writeFileSync(join(dir, n), 'x');
  w('.gitkeep');
  w('notes.txt'); // .md 아님 → 무시
  // support 5개 → 최신 3개만 보존
  for (const s of ['20261010-010301', '20261012-040302', '20261013-080301', '20261014-010301', '20261014-040302']) w(`support-${s}.md`);
  // social 3개 → 전부 보존
  for (const s of ['20260920-090323', '20261001-230339', '20261014-090323']) w(`social-${s}.md`);
  // ads 1개(오래돼도) → 보존
  w('ads-20260908-053952.md');
  // 스탬프 없는 inquiry 4개: mtime 순으로 최신 3개 보존
  [['inquiry-a.md', '2026-09-01'], ['inquiry-b.md', '2026-09-10'], ['inquiry-c.md', '2026-09-20'], ['inquiry-d.md', '2026-10-01']]
    .forEach(([n, d]) => { w(n); const t = new Date(`${d}T00:00:00Z`); utimesSync(join(dir, n), t, t); });
  return dir;
}

test('stampTime / agentOf: gate.mjs 파일명에서 시각·에이전트 추출', () => {
  assert.equal(stampTime('social-20261001-230339.md'), Date.UTC(2026, 9, 1, 23, 3, 39));
  assert.equal(stampTime('inquiry-j7HHQWKDcnXr20LwZ7YJ.md'), null);
  assert.equal(agentOf('support-20261014-010301.md'), 'support');
  assert.equal(agentOf('my-agent-20261014-010301.md'), 'my-agent'); // 이름에 '-' 있어도 스탬프 기준
  assert.equal(agentOf('inquiry-j7HHQWKDcnXr20LwZ7YJ.md'), 'inquiry');
});

test('pruneByAgent: 에이전트별 최신 3개만 보존, 3개 이하 에이전트는 그대로', () => {
  const dir = makeDir();
  const r = pruneByAgent({ dir, keepPerAgent: 3 });
  assert.deepEqual(r.removed, ['inquiry-a.md', 'support-20261010-010301.md', 'support-20261012-040302.md']);
  assert.equal(r.kept, 10); // support 3 + social 3 + ads 1 + inquiry 3
  for (const n of r.removed) assert.equal(existsSync(join(dir, n)), false, n);
  for (const n of ['.gitkeep', 'notes.txt', 'ads-20260908-053952.md', 'social-20260920-090323.md',
    'support-20261013-080301.md', 'support-20261014-040302.md', 'inquiry-b.md', 'inquiry-d.md']) {
    assert.equal(existsSync(join(dir, n)), true, n);
  }
});

test('pruneByAgent: dryRun 은 목록만, 파일은 그대로', () => {
  const dir = makeDir();
  const r = pruneByAgent({ dir, keepPerAgent: 3, dryRun: true });
  assert.equal(r.removed.length, 3);
  assert.equal(existsSync(join(dir, 'support-20261010-010301.md')), true);
});

test('pruneByAgent: keepPerAgent 가 0·음수·NaN 이면 아무것도 지우지 않고 거부', () => {
  const dir = makeDir();
  for (const bad of [0, -1, NaN, 2.5]) {
    assert.throws(() => pruneByAgent({ dir, keepPerAgent: bad }), /keepPerAgent/);
  }
  assert.equal(existsSync(join(dir, 'support-20261010-010301.md')), true);
});

test('pruneByAgent: 폴더 없으면 no-op', () => {
  assert.deepEqual(pruneByAgent({ dir: join(tmpdir(), 'no-such-approvals-dir'), keepPerAgent: 3 }), { removed: [], kept: 0 });
});

test('RETENTION: approvals 3개 · reports 5개, keepPerAgent 누락 시 거부(기본값 없음)', () => {
  assert.deepEqual(RETENTION, { approvals: 3, reports: 5 });
  assert.throws(() => pruneByAgent({ dir: tmpdir() }), /keepPerAgent/);
});

test('pruneByAgent: reports 처럼 keep 5 면 5개 보존(support 5개 전부 유지)', () => {
  const dir = makeDir();
  const r = pruneByAgent({ dir, keepPerAgent: 5 });
  assert.deepEqual(r.removed, []); // support 5 · social 3 · ads 1 · inquiry 4 모두 5 이하
});
