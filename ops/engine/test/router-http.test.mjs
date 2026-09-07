import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRouter } from '../router.mjs';

function mockResponse(body) {
  return { ok: true, status: 200, async json() { return body; }, async text() { return JSON.stringify(body); } };
}

test('openrouter posts OpenAI-schema request and parses reply + cost', async () => {
  let captured;
  const transport = async (url, init) => {
    captured = { url, init };
    return mockResponse({
      choices: [{ message: { content: 'hi there' } }],
      usage: { prompt_tokens: 1_000_000, completion_tokens: 0 },
    });
  };
  const router = createRouter({ transport, env: { OPENROUTER_API_KEY: 'k' } });
  const r = await router.complete({
    provider: 'openrouter', model: 'openrouter/deepseek/deepseek-chat',
    system: 's', messages: [{ role: 'user', content: 'q' }],
  });
  assert.equal(captured.url, 'https://openrouter.ai/api/v1/chat/completions');
  assert.equal(captured.init.headers.Authorization, 'Bearer k');
  assert.equal(r.text, 'hi there');
  assert.ok(Math.abs(r.usage.costUsd - 0.27) < 1e-9);
});

test('missing api key falls back to dryrun', async () => {
  const router = createRouter({ transport: async () => { throw new Error('should not call'); }, env: {} });
  const r = await router.complete({ provider: 'openrouter', model: 'm', system: 's', messages: [{ role: 'user', content: 'q' }] });
  assert.match(r.text, /DRYRUN/);
});
