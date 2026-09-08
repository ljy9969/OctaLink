// ops/engine/test/run-agent.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync, existsSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runAgent } from '../run-agent.mjs';

const here = dirname(fileURLToPath(import.meta.url));
function opsWithDummy() {
  const ops = mkdtempSync(join(tmpdir(), 'ops-run-'));
  const agentDir = join(ops, 'agents', 'dummy');
  mkdirSync(agentDir, { recursive: true });
  cpSync(join(here, '..', 'fixtures', 'dummy'), agentDir, { recursive: true });
  return { ops, agentDir };
}
// router whose verifier reply always passes; agent reply echoes
const okRouter = { complete: async ({ system }) => ({
  text: /ONLY the JSON/.test('') ? '' : (system.includes('pass') ? '{"pass":true,"checks":[]}' : 'ARTIFACT OK'),
  usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } }) };

test('safe agent runs, verifier passes, auto status done, report written', async () => {
  const { ops, agentDir } = opsWithDummy();
  // route by role: verifier prompt contains "pass"; skill does not
  const router = { complete: async ({ system, messages }) => {
    const isVerifier = /Respond with ONLY the JSON verdict/.test(messages[0].content);
    return { text: isVerifier ? '{"pass":true,"checks":[{"name":"nonempty","pass":true,"reason":"ok"}]}' : 'ARTIFACT OK',
      usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } };
  }};
  const r = await runAgent({ agentDir, opsRoot: ops, router, now: () => new Date('2026-09-07T00:00:00Z') });
  assert.equal(r.status, 'done');
  assert.equal(r.verifier.pass, true);
  assert.ok(existsSync(r.reportPath));
});

test('daily cap blocks the run', async () => {
  const { ops, agentDir } = opsWithDummy();
  const r = await runAgent({ agentDir, opsRoot: ops, router: okRouter, now: () => new Date('2026-09-07T00:00:00Z'), dailyCapUsd: 0 });
  assert.equal(r.status, 'daily_cap');
});

test('config model.provider is authoritative and verifier cost is billed', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-prov-'));
  const agentDir = join(ops, 'agents', 'p');
  mkdirSync(agentDir, { recursive: true });
  // provider is openrouter but the model NAME starts with "anthropic" — a name-prefix
  // heuristic would mis-derive "anthropic"; the config's provider must win.
  writeFileSync(join(agentDir, 'config.json'), JSON.stringify({
    name: 'p', risk: 'safe', trigger: 'manual',
    model: { provider: 'openrouter', name: 'anthropic/looks-like-anthropic' },
    budget: { maxRetries: 0, maxUsd: 1 }, stop: 'verifier_pass', isolate: false }));
  writeFileSync(join(agentDir, 'skill.md'), 'skill');
  writeFileSync(join(agentDir, 'verifier.md'), 'verify');
  const providers = [];
  const router = { complete: async ({ provider, messages }) => {
    providers.push(provider);
    const isVerifier = /Respond with ONLY the JSON verdict/.test(messages[0].content);
    return { text: isVerifier ? '{"pass":true,"checks":[]}' : 'artifact',
      usage: { inputTokens: 0, outputTokens: 0, costUsd: isVerifier ? 0.02 : 0.05 } };
  }};
  const r = await runAgent({ agentDir, opsRoot: ops, router, now: () => new Date('2026-09-07T00:00:00Z') });
  assert.deepEqual(providers, ['openrouter', 'openrouter']); // both calls used config provider
  assert.ok(Math.abs(r.costUsd - 0.07) < 1e-9);              // agent 0.05 + verifier 0.02
  assert.equal(r.status, 'done');
});

test('agent system prompt carries the Korean language guard', async () => {
  const { ops, agentDir } = opsWithDummy();
  let agentSystem = '';
  const router = { complete: async ({ system, messages }) => {
    const isVerifier = /Respond with ONLY the JSON verdict/.test(messages[0].content);
    if (!isVerifier) agentSystem = system;
    return { text: isVerifier ? '{"pass":true,"checks":[]}' : 'ok', usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } };
  }};
  await runAgent({ agentDir, opsRoot: ops, router, now: () => new Date('2026-09-08T00:00:00Z') });
  assert.match(agentSystem, /반드시 한국어로만/);   // 언어 가드가 스킬 앞에 붙는다
  assert.match(agentSystem, /DUMMY SKILL/);        // 원래 skill 도 유지
});
