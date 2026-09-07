// ops/engine/test/run-agent.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync, existsSync } from 'node:fs';
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
