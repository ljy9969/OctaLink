import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runChain } from '../run-chain.mjs';

const here = dirname(fileURLToPath(import.meta.url));

test('runs multiple agents sequentially and returns one result each', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-chain-'));
  for (const n of ['a', 'b']) {
    const d = join(ops, 'agents', n);
    mkdirSync(d, { recursive: true });
    cpSync(join(here, '..', 'fixtures', 'dummy'), d, { recursive: true });
  }
  const router = { complete: async ({ messages }) => ({
    text: /Respond with ONLY the JSON verdict/.test(messages[0].content) ? '{"pass":true,"checks":[]}' : 'OK',
    usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } }) };
  const results = await runChain({ opsRoot: ops, agents: ['a', 'b'], router, now: () => new Date('2026-09-07T00:00:00Z') });
  assert.equal(results.length, 2);
  assert.equal(results[0].status, 'done');
});
