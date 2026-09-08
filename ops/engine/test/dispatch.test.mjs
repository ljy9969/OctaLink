import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
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
