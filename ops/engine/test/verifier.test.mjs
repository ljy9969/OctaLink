import { test } from 'node:test';
import assert from 'node:assert/strict';
import { runVerifier } from '../verifier.mjs';

const routerReturning = (text) => ({ complete: async () => ({ text, usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } }) });

test('parses pass:true JSON embedded in prose', async () => {
  const r = await runVerifier({
    router: routerReturning('Sure: {"pass": true, "checks": [{"name":"x","pass":true,"reason":"ok"}]} done'),
    model: 'dryrun', verifierPrompt: 'p', artifact: 'a',
  });
  assert.equal(r.pass, true);
  assert.equal(r.checks[0].name, 'x');
});

test('unparseable output => pass:false', async () => {
  const r = await runVerifier({ router: routerReturning('no json here'), model: 'dryrun', verifierPrompt: 'p', artifact: 'a' });
  assert.equal(r.pass, false);
  assert.equal(r.checks[0].name, 'parse');
});

test('uses caller-provided provider (not name heuristic) and returns usage', async () => {
  let seenProvider;
  const router = { complete: async ({ provider }) => {
    seenProvider = provider;
    return { text: '{"pass":true,"checks":[]}', usage: { inputTokens: 10, outputTokens: 5, costUsd: 0.001 } };
  } };
  const r = await runVerifier({ router, provider: 'openrouter', model: 'anthropic/looks-anthropic', verifierPrompt: 'p', artifact: 'a' });
  assert.equal(seenProvider, 'openrouter');
  assert.equal(r.usage.costUsd, 0.001);
  assert.equal(r.pass, true);
});
