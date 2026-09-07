import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRouter, estimateCost } from '../router.mjs';

test('estimateCost uses per-model price table', () => {
  const c = estimateCost('openrouter/deepseek/deepseek-chat',
    { inputTokens: 1_000_000, outputTokens: 1_000_000 });
  assert.ok(Math.abs(c - (0.27 + 1.10)) < 1e-9);
});

test('estimateCost prices the Claude Haiku 4.5 agent option', () => {
  const c = estimateCost('anthropic/claude-haiku-4-5',
    { inputTokens: 1_000_000, outputTokens: 1_000_000 });
  assert.ok(Math.abs(c - (1 + 5)) < 1e-9);
});

test('dryrun provider echoes deterministically, zero cost, no transport call', async () => {
  let called = false;
  const router = createRouter({ transport: async () => { called = true; } });
  const r = await router.complete({
    provider: 'dryrun', model: 'dryrun',
    system: 'sys', messages: [{ role: 'user', content: 'hello' }],
  });
  assert.equal(called, false);
  assert.match(r.text, /DRYRUN/);
  assert.equal(r.usage.costUsd, 0);
});
