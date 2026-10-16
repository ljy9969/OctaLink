// ops/engine/test/run-agent.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync, existsSync, writeFileSync, readFileSync } from 'node:fs';
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

// extract(결정적) 모드: LLM 호출 없이 context 구획을 그대로 산출물로.
function opsWithExtractAgent({ body, updatedIso }) {
  const ops = mkdtempSync(join(tmpdir(), 'ops-extract-'));
  const agentDir = join(ops, 'agents', 'sales');
  mkdirSync(agentDir, { recursive: true });
  writeFileSync(join(agentDir, 'config.json'), JSON.stringify({
    name: 'sales', risk: 'gated', trigger: 'cron:0 11 * * 1', model: { provider: 'ollama', name: 'x' },
    budget: { maxRetries: 3, maxUsd: 0.3 }, stop: 'verifier_pass', extract: { section: '리드 인벤토리', maxAgeHours: 6 } }));
  writeFileSync(join(agentDir, 'skill.md'), 'doc'); writeFileSync(join(agentDir, 'verifier.md'), 'unused');
  if (body != null) {
    mkdirSync(join(ops, 'context'));
    writeFileSync(join(ops, 'context', 'sales.md'),
      `# sales 컨텍스트\n\n<!-- sec:리드 인벤토리 -->\n## 리드 인벤토리 (${updatedIso})\n\n${body}\n<!-- /sec:리드 인벤토리 -->\n`);
  }
  return { ops, agentDir };
}
const noLlm = { complete: async () => { throw new Error('extract 모드에서 LLM 호출 금지'); } };
const TABLE = '총 2곳.\n\n| 체육관 | 출처 | 링크 | 찾은 검색어 |\n|---|---|---|---|\n| 팀유짐 영등포점 | 인스타그램 @ydp_ugym | [열기](https://www.instagram.com/ydp_ugym/) | q |\n| Korean Top Team | Tapology | [열기](https://www.tapology.com/gyms/4-korean-top-team) | q |';

test('extract 모드: LLM 없이 구획을 산출물로, 검증 통과 → gated 승인대기 + 리포트', async () => {
  const { ops, agentDir } = opsWithExtractAgent({ body: TABLE, updatedIso: '2026-10-16T02:00:00.000Z' });
  const r = await runAgent({ agentDir, opsRoot: ops, router: noLlm, now: () => new Date('2026-10-16T02:00:30Z') });
  assert.equal(r.status, 'queued');
  assert.equal(r.iterations, 1);
  assert.equal(r.costUsd, 0);
  assert.match(r.artifact, /코드 추출 · LLM 미사용/);
  assert.match(r.artifact, /팀유짐 영등포점/);
  assert.match(r.verifier.checks[0].reason, /^2건/);
  assert.ok(existsSync(r.gate.path));
  assert.match(readFileSync(r.gate.path, 'utf8'), /Korean Top Team/);
});

test('extract 모드: 구획이 6시간보다 오래되면(검색 갱신 실패) 실패, 옛 표 재사용 안 함', async () => {
  const { ops, agentDir } = opsWithExtractAgent({ body: TABLE, updatedIso: '2026-10-15T18:00:00.000Z' });
  const r = await runAgent({ agentDir, opsRoot: ops, router: noLlm, now: () => new Date('2026-10-16T02:00:00Z') });
  assert.equal(r.status, 'verify_failed');
  assert.match(r.verifier.checks[0].reason, /오래됨/);
  assert.doesNotMatch(r.artifact, /팀유짐/);
  assert.equal(r.gate.action, 'none');
});

test('extract 모드: 리드 0건·구획 없음이면 실패(승인대기 안 만듦)', async () => {
  const empty = opsWithExtractAgent({ body: '(확인된 체육관 리드 없음 — 이번 검색 결과에 …)', updatedIso: '2026-10-16T02:00:00.000Z' });
  const r0 = await runAgent({ agentDir: empty.agentDir, opsRoot: empty.ops, router: noLlm, now: () => new Date('2026-10-16T02:00:10Z') });
  assert.equal(r0.status, 'verify_failed');
  assert.match(r0.verifier.checks[0].reason, /0건/);
  const none = opsWithExtractAgent({ body: null });
  const r1 = await runAgent({ agentDir: none.agentDir, opsRoot: none.ops, router: noLlm, now: () => new Date('2026-10-16T02:00:10Z') });
  assert.equal(r1.status, 'verify_failed');
  assert.match(r1.verifier.checks[0].reason, /구획 없음/);
});
