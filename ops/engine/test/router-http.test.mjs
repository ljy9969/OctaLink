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

test('anthropic-oauth uses Bearer token + oauth beta header, cost 0 (subscription)', async () => {
  let captured;
  const transport = async (url, init) => {
    captured = { url, init };
    return mockResponse({ content: [{ type: 'text', text: 'claude reply' }],
      usage: { input_tokens: 1_000_000, output_tokens: 1_000_000 } });
  };
  const router = createRouter({ transport, env: { ANTHROPIC_AUTH_TOKEN: 'oauth-tok' } });
  const r = await router.complete({
    provider: 'anthropic-oauth', model: 'anthropic/claude-sonnet-5',
    system: 's', messages: [{ role: 'user', content: 'q' }],
  });
  assert.equal(captured.url, 'https://api.anthropic.com/v1/messages');
  assert.equal(captured.init.headers.Authorization, 'Bearer oauth-tok');
  assert.equal(captured.init.headers['anthropic-beta'], 'oauth-2025-04-20');
  assert.equal(r.text, 'claude reply');
  assert.equal(r.usage.costUsd, 0);   // Max 구독 = 종량 과금 없음
});

test('anthropic-oauth without token falls back to dryrun', async () => {
  const router = createRouter({ transport: async () => { throw new Error('should not call'); }, env: {} });
  const r = await router.complete({ provider: 'anthropic-oauth', model: 'anthropic/claude-haiku-4-5', system: 's', messages: [{ role: 'user', content: 'q' }] });
  assert.match(r.text, /DRYRUN/);
});
