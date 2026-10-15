import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, basename } from 'node:path';
import { readJobs, runDispatch } from '../dispatch.mjs';
import { loadState, saveState } from '../state.mjs';

function makeOps() {
  const ops = mkdtempSync(join(tmpdir(), 'ops-disp-'));
  const mk = (n, trigger) => { mkdirSync(join(ops, 'agents', n), { recursive: true });
    writeFileSync(join(ops, 'agents', n, 'config.json'), JSON.stringify({
      name: n, risk: 'safe', trigger, model: { provider: 'dryrun', name: 'dryrun' },
      budget: { maxRetries: 1, maxUsd: 0.1 }, stop: 'verifier_pass', isolate: false })); };
  mk('a', 'cron:0 9 * * *');
  mk('b', 'cron:0 23 * * *');   // KST 23:00 — 오늘 오전엔 안 걸림
  mkdirSync(join(ops, 'ceo'), { recursive: true });
  writeFileSync(join(ops, 'ceo', 'cadence.json'), JSON.stringify({ daily: { cron: '0 9 * * *' } }));
  return ops;
}

test('readJobs collects agent crons + ceo', () => {
  const ops = makeOps();
  const jobs = readJobs(ops).map((j) => `${j.name}:${j.kind}`);
  assert.deepEqual(jobs, ['a:agent', 'b:agent', 'ceo:ceo']);
});

test('fires only due jobs; records lastRun; no double-fire', async () => {
  const ops = makeOps();
  // a와 ceo의 lastRun을 09:00 직전으로 심어 캐치업 발화 유도
  saveState(join(ops, 'state'), 'dispatch', { lastRun: { a: '2026-09-07T23:50:00Z', ceo: '2026-09-07T23:50:00Z', b: '2026-09-07T23:50:00Z' } });
  const ran = [];
  const now = () => new Date('2026-09-08T00:05:00Z'); // KST 09:05
  const runAgentFn = async ({ agentDir }) => { ran.push('A:' + basename(agentDir)); return { status: 'done' }; };
  const runCeoFn = async () => { ran.push('CEO'); return { status: 'done' }; };
  const res = await runDispatch({ opsRoot: ops, router: {}, now, runAgentFn, runCeoFn });
  assert.deepEqual(ran.sort(), ['A:a', 'CEO']);           // a, ceo 발화 / b(23시)는 아님
  const fired = res.filter((r) => r.ran).map((r) => r.job).sort();
  assert.deepEqual(fired, ['a', 'ceo']);
  // 같은 시각 재실행 → lastRun이 갱신돼 재발화 없음
  ran.length = 0;
  await runDispatch({ opsRoot: ops, router: {}, now, runAgentFn, runCeoFn });
  assert.deepEqual(ran, []);
});

test('lastRun 없던 잡도 다음 디스패치에서 창이 전진해 발화(영구 미발화 방지)', async () => {
  const ops = makeOps();  // lastRun 시드 없음
  const ran = [];
  const runAgentFn = async ({ agentDir }) => { ran.push(basename(agentDir)); return { status: 'done' }; };
  const runCeoFn = async () => { ran.push('ceo'); return { status: 'done' }; };
  // 1회차: 08:50 KST — 소급 없음(발화 0), 단 lastRun 기록됨
  await runDispatch({ opsRoot: ops, router: {}, now: () => new Date('2026-09-07T23:50:00Z'), runAgentFn, runCeoFn });
  assert.deepEqual(ran, []);
  // 2회차: 09:05 KST — 창(08:50,09:05]에 09:00 포함 → a·ceo 발화
  await runDispatch({ opsRoot: ops, router: {}, now: () => new Date('2026-09-08T00:05:00Z'), runAgentFn, runCeoFn });
  assert.deepEqual(ran.sort(), ['a', 'ceo']);
});

test('approvals 정리는 KST 하루 1회만 실행(같은 날 재디스패치 시 생략, 다음 날 다시)', async () => {
  const ops = makeOps();
  const calls = [];
  const pruneFn = ({ dir, keepDays }) => { calls.push([basename(dir), keepDays]); return { removed: ['x.md'], kept: 0 }; };
  const noop = async () => ({ status: 'done' });
  const run = (iso) => runDispatch({ opsRoot: ops, router: {}, now: () => new Date(iso), runAgentFn: noop, runCeoFn: noop, pruneFn });
  const r1 = await run('2026-10-15T00:05:00Z');   // KST 10-15 09:05
  assert.deepEqual(calls, [['approvals', 7]]);
  assert.ok(r1.some((x) => x.job === 'prune-approvals' && x.ran));
  await run('2026-10-15T14:50:00Z');              // KST 10-15 23:50 — 같은 날
  assert.equal(calls.length, 1);
  await run('2026-10-15T15:05:00Z');              // KST 10-16 00:05 — 다음 날
  assert.equal(calls.length, 2);
});

test('approvals 정리 실패해도 디스패치는 진행, 다음 디스패치에서 재시도', async () => {
  const ops = makeOps();
  let n = 0;
  const pruneFn = () => { n++; if (n === 1) throw new Error('EBUSY'); return { removed: [], kept: 0 }; };
  const noop = async () => ({ status: 'done' });
  const run = (iso) => runDispatch({ opsRoot: ops, router: {}, now: () => new Date(iso), runAgentFn: noop, runCeoFn: noop, pruneFn });
  const r1 = await run('2026-10-15T00:05:00Z');
  assert.ok(r1.some((x) => x.job === 'prune-approvals' && x.status === 'error'));
  await run('2026-10-15T00:20:00Z');              // 같은 날이지만 실패했으니 재시도
  assert.equal(n, 2);
});

test('이벤트: 새 배정 이슈 → dev 발화 + tasks/dev.md, 같은 이슈는 중복 방지', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ev-'));
  mkdirSync(join(ops, 'agents', 'dev'), { recursive: true });
  writeFileSync(join(ops, 'agents', 'dev', 'config.json'), JSON.stringify({
    name: 'dev', risk: 'gated', trigger: 'cron:0 14 * * 2 | event:issue.assigned',
    model: { provider: 'dryrun', name: 'dryrun' }, budget: { maxRetries: 1, maxUsd: 0.1 }, stop: 'verifier_pass', isolate: false }));
  mkdirSync(join(ops, 'connectors'), { recursive: true });
  writeFileSync(join(ops, 'connectors', 'config.json'), JSON.stringify({ github: { repo: 'ljy9969/OctaLink' } }));
  const ran = [];
  const runAgentFn = async ({ agentDir }) => { ran.push(basename(agentDir)); return { status: 'queued' }; };
  const runCeoFn = async () => ({ status: 'done' });
  const now = () => new Date('2026-09-08T04:00:00Z'); // 크론(화 14시) 미발화 시각
  const gh = async () => ({ fired: true, issue: { number: 7, title: '교류전 버그', assignee: 'ljy9969', url: 'u', body: '재현' } });
  const opts = { opsRoot: ops, router: {}, now, runAgentFn, runCeoFn, githubCheckFn: gh, env: { GH_TOKEN: 't' } };
  const res1 = await runDispatch(opts);
  assert.ok(res1.some((r) => r.job === 'dev' && r.ran && r.event === 'issue.assigned'));
  assert.match(readFileSync(join(ops, 'tasks', 'dev.md'), 'utf8'), /#7/);
  assert.deepEqual(ran, ['dev']);
  // 같은 이슈(7) → 재발화 없음
  ran.length = 0;
  await runDispatch(opts);
  assert.deepEqual(ran, []);
});
