# Loop Engine (Phase 1a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the dependency-light Node loop engine that runs one OctaLink agent through the 7-part loop (trigger→worktree→skill→MCP→act→verify→state→stop) and orchestrates several agents, testable end-to-end with a `dryrun` model provider (no external API needed).

**Architecture:** A small ESM Node package under `ops/engine/`. A `router` abstracts model providers (dryrun/openrouter/anthropic/ollama) behind one `complete()` call with cost accounting. `run-agent` composes config + skill + state into a model call, loops until a `verifier` passes or budget/retries run out, then a `gate` either auto-applies (risk=safe) or queues a proposal (risk=gated). `run-chain` runs several agents in sequence from a CLI. Everything external (HTTP, git, clock) is injected so unit tests use mocks.

**Tech Stack:** Node 22 ESM (`.mjs`), built-in `node:test` + `node:assert/strict`, no runtime dependencies. Config as JSON (dependency-free parsing). `git worktree` for isolation.

## Global Constraints

- Node 22, ESM only (`"type": "module"`), no runtime npm dependencies — parsing uses `JSON.parse`, tests use `node --test`.
- Engine lives under `ops/engine/`; it must never write outside `ops/` except when creating a git worktree under `ops/.worktrees/`.
- All external effects are injected: HTTP via a `transport` (fetch-compatible), time via `now()`, env via an `env` object. Tests pass mocks; production defaults to `globalThis.fetch`, `() => new Date()`, `process.env`.
- Money/quality safety: every agent run is bounded by `budget.maxRetries` and `budget.maxUsd`; the engine also enforces a daily cap (`dailyCapUsd`, default 5). Cost is estimated from a per-model price table; `dryrun`/`ollama` cost 0.
- Agent config field names (used verbatim across tasks): `name`, `risk` (`"safe"|"gated"`), `trigger`, `model.provider`, `model.name`, `budget.maxRetries`, `budget.maxUsd`, `stop`, `isolate` (bool, default false).
- Router return shape (verbatim): `{ text: string, usage: { inputTokens: number, outputTokens: number, costUsd: number } }`.
- Commit after every task. Branch: `main` only (no feature branches).

---

## File Structure

- `ops/engine/package.json` — ESM marker + test script.
- `ops/engine/router.mjs` — `estimateCost(model, usage)`, `createRouter({transport,env}) → {complete}`.
- `ops/engine/config.mjs` — `loadAgentConfig(agentDir) → configObject`.
- `ops/engine/state.mjs` — `loadState`, `saveState`, `appendLedger`, `spendSince`.
- `ops/engine/verifier.mjs` — `runVerifier({router,model,verifierPrompt,artifact}) → {pass,checks}`.
- `ops/engine/gate.mjs` — `applyGate({risk,name,artifact,approvalsDir,now}) → {action,path?}`.
- `ops/engine/worktree.mjs` — `createWorktree(repoRoot,name)`, `removeWorktree(repoRoot,wtPath)`.
- `ops/engine/run-agent.mjs` — `runAgent({agentDir,opsRoot,router,now,dailyCapUsd}) → result`.
- `ops/engine/run-chain.mjs` — `runChain({opsRoot,agents,router}) → results[]` + CLI.
- `ops/engine/test/*.test.mjs` — one test file per module.
- `ops/engine/fixtures/` — a dummy agent used by integration tests.

---

### Task 1: Package scaffold + router with `dryrun` provider and cost table

**Files:**
- Create: `ops/engine/package.json`
- Create: `ops/engine/router.mjs`
- Test: `ops/engine/test/router.test.mjs`

**Interfaces:**
- Produces: `estimateCost(model: string, usage: {inputTokens,outputTokens}) → number`; `createRouter({transport?, env?}) → { complete({provider, model, system, messages, maxTokens?}) → Promise<{text, usage:{inputTokens,outputTokens,costUsd}}> }`. `provider === "dryrun"` returns a deterministic echo without calling transport.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/router.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRouter, estimateCost } from '../router.mjs';

test('estimateCost uses per-model price table', () => {
  const c = estimateCost('openrouter/deepseek/deepseek-chat',
    { inputTokens: 1_000_000, outputTokens: 1_000_000 });
  assert.ok(Math.abs(c - (0.27 + 1.10)) < 1e-9);
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/router.test.mjs`
Expected: FAIL — `Cannot find module '../router.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```json
// ops/engine/package.json
{
  "name": "octalink-loop-engine",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": { "test": "node --test" }
}
```

```javascript
// ops/engine/router.mjs
// Price table: USD per 1,000,000 tokens [input, output].
const PRICES = {
  'anthropic/claude-opus-4': [15, 75],
  'anthropic/claude-sonnet-4': [3, 15],
  'openrouter/deepseek/deepseek-chat': [0.27, 1.10],
  'openrouter/meta-llama/llama-3.3-70b-instruct': [0.13, 0.40],
};

export function estimateCost(model, usage) {
  const [pin, pout] = PRICES[model] ?? [0, 0];
  return (usage.inputTokens / 1e6) * pin + (usage.outputTokens / 1e6) * pout;
}

export function createRouter({ transport = globalThis.fetch, env = process.env } = {}) {
  async function complete({ provider, model, system, messages, maxTokens = 1024 }) {
    if (provider === 'dryrun') {
      const last = messages[messages.length - 1]?.content ?? '';
      return {
        text: `DRYRUN[${model}] ${String(last).slice(0, 120)}`,
        usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 },
      };
    }
    throw new Error(`provider not implemented in Task 1: ${provider}`);
  }
  return { complete, _transport: transport, _env: env };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/router.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/package.json ops/engine/router.mjs ops/engine/test/router.test.mjs
git commit -m "feat(ops): loop-engine router scaffold with dryrun provider + cost table"
```

---

### Task 2: Router `openrouter` and `anthropic` providers (mock transport)

**Files:**
- Modify: `ops/engine/router.mjs`
- Test: `ops/engine/test/router-http.test.mjs`

**Interfaces:**
- Consumes: `createRouter`, `estimateCost` from Task 1.
- Produces: `complete` now supports `provider` `"openrouter"` (POST `https://openrouter.ai/api/v1/chat/completions`, `Authorization: Bearer <OPENROUTER_API_KEY>`, OpenAI schema) and `"anthropic"` (POST `https://api.anthropic.com/v1/messages`, `x-api-key`, `anthropic-version: 2023-06-01`). Both parse text + token usage and set `costUsd = estimateCost(model, usage)`. Missing API key → falls back to `dryrun` behavior.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/router-http.test.mjs
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/router-http.test.mjs`
Expected: FAIL — `provider not implemented in Task 1: openrouter`.

- [ ] **Step 3: Write minimal implementation**

Replace the body of `complete` in `ops/engine/router.mjs` (keep `PRICES`/`estimateCost`):

```javascript
export function createRouter({ transport = globalThis.fetch, env = process.env } = {}) {
  function dryrun(model, messages) {
    const last = messages[messages.length - 1]?.content ?? '';
    return { text: `DRYRUN[${model}] ${String(last).slice(0, 120)}`,
      usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } };
  }
  async function complete({ provider, model, system, messages, maxTokens = 1024 }) {
    if (provider === 'dryrun') return dryrun(model, messages);

    if (provider === 'openrouter') {
      const key = env.OPENROUTER_API_KEY;
      if (!key) return dryrun(model, messages);
      const res = await transport('https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
        body: JSON.stringify({ model: model.replace(/^openrouter\//, ''),
          max_tokens: maxTokens,
          messages: [{ role: 'system', content: system }, ...messages] }),
      });
      const j = await res.json();
      const usage = { inputTokens: j.usage?.prompt_tokens ?? 0, outputTokens: j.usage?.completion_tokens ?? 0 };
      return { text: j.choices?.[0]?.message?.content ?? '', usage: { ...usage, costUsd: estimateCost(model, usage) } };
    }

    if (provider === 'anthropic') {
      const key = env.ANTHROPIC_API_KEY;
      if (!key) return dryrun(model, messages);
      const res = await transport('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-api-key': key, 'anthropic-version': '2023-06-01' },
        body: JSON.stringify({ model: model.replace(/^anthropic\//, ''),
          max_tokens: maxTokens, system, messages }),
      });
      const j = await res.json();
      const usage = { inputTokens: j.usage?.input_tokens ?? 0, outputTokens: j.usage?.output_tokens ?? 0 };
      const text = (j.content ?? []).map((b) => b.text ?? '').join('');
      return { text, usage: { ...usage, costUsd: estimateCost(model, usage) } };
    }

    if (provider === 'ollama') {
      const res = await transport(`${env.OLLAMA_URL ?? 'http://localhost:11434'}/api/chat`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: model.replace(/^ollama\//, ''), stream: false,
          messages: [{ role: 'system', content: system }, ...messages] }),
      });
      const j = await res.json();
      return { text: j.message?.content ?? '', usage: { inputTokens: 0, outputTokens: 0, costUsd: 0 } };
    }

    throw new Error(`unknown provider: ${provider}`);
  }
  return { complete, _transport: transport, _env: env };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test`
Expected: PASS (all router tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/router.mjs ops/engine/test/router-http.test.mjs
git commit -m "feat(ops): router openrouter/anthropic/ollama providers with key fallback"
```

---

### Task 3: Agent config loader

**Files:**
- Create: `ops/engine/config.mjs`
- Create: `ops/engine/fixtures/dummy/config.json`
- Create: `ops/engine/fixtures/dummy/skill.md`
- Create: `ops/engine/fixtures/dummy/verifier.md`
- Test: `ops/engine/test/config.test.mjs`

**Interfaces:**
- Produces: `loadAgentConfig(agentDir) → { name, risk, trigger, model:{provider,name}, budget:{maxRetries,maxUsd}, stop, isolate, skill, verifier }` where `skill`/`verifier` are the file contents of `skill.md`/`verifier.md`. Throws `Error` if a required field is missing.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/config.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadAgentConfig } from '../config.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const dummy = join(here, '..', 'fixtures', 'dummy');

test('loads config + skill + verifier content', () => {
  const c = loadAgentConfig(dummy);
  assert.equal(c.name, 'dummy');
  assert.equal(c.risk, 'safe');
  assert.equal(c.model.provider, 'dryrun');
  assert.equal(c.budget.maxRetries, 2);
  assert.match(c.skill, /DUMMY SKILL/);
  assert.match(c.verifier, /pass/);
});

test('throws on missing required field', () => {
  assert.throws(() => loadAgentConfig(join(here, 'nope')), /config/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/config.test.mjs`
Expected: FAIL — `Cannot find module '../config.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```json
// ops/engine/fixtures/dummy/config.json
{
  "name": "dummy",
  "risk": "safe",
  "trigger": "manual",
  "model": { "provider": "dryrun", "name": "dryrun" },
  "budget": { "maxRetries": 2, "maxUsd": 0.1 },
  "stop": "verifier_pass",
  "isolate": false
}
```

```markdown
<!-- ops/engine/fixtures/dummy/skill.md -->
DUMMY SKILL: echo the task back in one sentence.
```

```markdown
<!-- ops/engine/fixtures/dummy/verifier.md -->
Return JSON {"pass": true, "checks": [{"name":"nonempty","pass":true,"reason":"ok"}]} if the artifact is non-empty.
```

```javascript
// ops/engine/config.mjs
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

export function loadAgentConfig(agentDir) {
  let raw;
  try {
    raw = JSON.parse(readFileSync(join(agentDir, 'config.json'), 'utf8'));
  } catch (e) {
    throw new Error(`cannot read config at ${agentDir}: ${e.message}`);
  }
  for (const f of ['name', 'risk', 'trigger', 'model', 'budget', 'stop']) {
    if (raw[f] === undefined) throw new Error(`config missing field: ${f}`);
  }
  if (!raw.model.provider || !raw.model.name) throw new Error('config missing model.provider/name');
  if (raw.budget.maxRetries === undefined || raw.budget.maxUsd === undefined) {
    throw new Error('config missing budget.maxRetries/maxUsd');
  }
  return {
    ...raw,
    isolate: Boolean(raw.isolate),
    skill: readFileSync(join(agentDir, 'skill.md'), 'utf8'),
    verifier: readFileSync(join(agentDir, 'verifier.md'), 'utf8'),
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/config.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/config.mjs ops/engine/fixtures/dummy ops/engine/test/config.test.mjs
git commit -m "feat(ops): agent config loader + dummy fixture"
```

---

### Task 4: State store, cost ledger, and spend window

**Files:**
- Create: `ops/engine/state.mjs`
- Test: `ops/engine/test/state.test.mjs`

**Interfaces:**
- Produces: `loadState(stateDir, name) → object` (`{}` if absent); `saveState(stateDir, name, obj)`; `appendLedger(stateDir, entry)` where `entry = {ts, agent, costUsd}` appended as JSONL to `${stateDir}/ledger.jsonl`; `spendSince(stateDir, sinceIso) → number` summing `costUsd` of ledger entries with `ts >= sinceIso`.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/state.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadState, saveState, appendLedger, spendSince } from '../state.mjs';

test('save/load round-trips and defaults to {}', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-state-'));
  assert.deepEqual(loadState(dir, 'a'), {});
  saveState(dir, 'a', { runs: 3 });
  assert.deepEqual(loadState(dir, 'a'), { runs: 3 });
});

test('ledger sums spend since a timestamp', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-led-'));
  appendLedger(dir, { ts: '2026-09-07T00:00:00Z', agent: 'a', costUsd: 0.10 });
  appendLedger(dir, { ts: '2026-09-07T10:00:00Z', agent: 'b', costUsd: 0.25 });
  assert.ok(Math.abs(spendSince(dir, '2026-09-07T05:00:00Z') - 0.25) < 1e-9);
  assert.ok(Math.abs(spendSince(dir, '2026-09-07T00:00:00Z') - 0.35) < 1e-9);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/state.test.mjs`
Expected: FAIL — `Cannot find module '../state.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```javascript
// ops/engine/state.mjs
import { readFileSync, writeFileSync, appendFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

function ensure(dir) { if (!existsSync(dir)) mkdirSync(dir, { recursive: true }); }

export function loadState(stateDir, name) {
  const f = join(stateDir, `${name}.json`);
  if (!existsSync(f)) return {};
  return JSON.parse(readFileSync(f, 'utf8'));
}

export function saveState(stateDir, name, obj) {
  ensure(stateDir);
  writeFileSync(join(stateDir, `${name}.json`), JSON.stringify(obj, null, 2));
}

export function appendLedger(stateDir, entry) {
  ensure(stateDir);
  appendFileSync(join(stateDir, 'ledger.jsonl'), JSON.stringify(entry) + '\n');
}

export function spendSince(stateDir, sinceIso) {
  const f = join(stateDir, 'ledger.jsonl');
  if (!existsSync(f)) return 0;
  return readFileSync(f, 'utf8').split('\n').filter(Boolean)
    .map((l) => JSON.parse(l))
    .filter((e) => e.ts >= sinceIso)
    .reduce((s, e) => s + (e.costUsd || 0), 0);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/state.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/state.mjs ops/engine/test/state.test.mjs
git commit -m "feat(ops): state store + cost ledger + spend window"
```

---

### Task 5: Verifier (true/false gate via model)

**Files:**
- Create: `ops/engine/verifier.mjs`
- Test: `ops/engine/test/verifier.test.mjs`

**Interfaces:**
- Consumes: a `router` (Task 1/2) — only `router.complete` is used.
- Produces: `runVerifier({ router, model, verifierPrompt, artifact }) → Promise<{ pass: boolean, checks: Array<{name,pass,reason}> }>`. Extracts the first `{...}` JSON block from the model text; on parse/shape failure returns `{ pass:false, checks:[{name:'parse',pass:false,reason:<msg>}] }`.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/verifier.test.mjs
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/verifier.test.mjs`
Expected: FAIL — `Cannot find module '../verifier.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```javascript
// ops/engine/verifier.mjs
function extractJson(text) {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start === -1 || end === -1 || end < start) return null;
  try { return JSON.parse(text.slice(start, end + 1)); } catch { return null; }
}

export async function runVerifier({ router, model, verifierPrompt, artifact }) {
  const { text } = await router.complete({
    provider: model.startsWith('anthropic') ? 'anthropic'
      : model.startsWith('ollama') ? 'ollama'
      : model === 'dryrun' ? 'dryrun' : 'openrouter',
    model,
    system: verifierPrompt,
    messages: [{ role: 'user', content: `ARTIFACT:\n${artifact}\n\nRespond with ONLY the JSON verdict.` }],
  });
  const parsed = extractJson(text);
  if (!parsed || typeof parsed.pass !== 'boolean') {
    return { pass: false, checks: [{ name: 'parse', pass: false, reason: 'verifier did not return valid verdict JSON' }] };
  }
  return { pass: parsed.pass, checks: Array.isArray(parsed.checks) ? parsed.checks : [] };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/verifier.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/verifier.mjs ops/engine/test/verifier.test.mjs
git commit -m "feat(ops): verifier parses true/false verdict from model"
```

---

### Task 6: Risk gate (auto vs queued proposal)

**Files:**
- Create: `ops/engine/gate.mjs`
- Test: `ops/engine/test/gate.test.mjs`

**Interfaces:**
- Produces: `applyGate({ risk, name, artifact, approvalsDir, now }) → { action:'auto' } | { action:'queued', path }`. `risk==='safe'` → `{action:'auto'}` (no file). Otherwise writes `${approvalsDir}/${name}-<yyyymmdd-hhmmss>.md` containing the artifact and returns `{action:'queued', path}`. `now` is a `Date`.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/gate.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, existsSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { applyGate } from '../gate.mjs';

test('safe risk auto-applies without file', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-gate-'));
  const r = applyGate({ risk: 'safe', name: 'research', artifact: 'x', approvalsDir: dir, now: new Date('2026-09-07T01:02:03Z') });
  assert.deepEqual(r, { action: 'auto' });
});

test('gated risk writes a proposal file', () => {
  const dir = mkdtempSync(join(tmpdir(), 'ops-gate2-'));
  const r = applyGate({ risk: 'gated', name: 'social', artifact: 'POST BODY', approvalsDir: dir, now: new Date('2026-09-07T01:02:03Z') });
  assert.equal(r.action, 'queued');
  assert.ok(existsSync(r.path));
  assert.match(readFileSync(r.path, 'utf8'), /POST BODY/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/gate.test.mjs`
Expected: FAIL — `Cannot find module '../gate.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```javascript
// ops/engine/gate.mjs
import { writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

function stamp(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}-${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}`;
}

export function applyGate({ risk, name, artifact, approvalsDir, now }) {
  if (risk === 'safe') return { action: 'auto' };
  if (!existsSync(approvalsDir)) mkdirSync(approvalsDir, { recursive: true });
  const path = join(approvalsDir, `${name}-${stamp(now)}.md`);
  writeFileSync(path, `# 승인 대기: ${name}\n\n- 생성: ${now.toISOString()}\n- 승인 시 실행, 반려 시 폐기\n\n---\n\n${artifact}\n`);
  return { action: 'queued', path };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/gate.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/gate.mjs ops/engine/test/gate.test.mjs
git commit -m "feat(ops): risk gate routes gated output to approvals/"
```

---

### Task 7: `run-agent` — the 7-part loop

**Files:**
- Create: `ops/engine/run-agent.mjs`
- Test: `ops/engine/test/run-agent.test.mjs`

**Interfaces:**
- Consumes: `loadAgentConfig` (T3), `runVerifier` (T5), `applyGate` (T6), `loadState/saveState/appendLedger/spendSince` (T4), a `router` (T1/2).
- Produces: `runAgent({ agentDir, opsRoot, router, now = () => new Date(), dailyCapUsd = 5 }) → Promise<{ status, iterations, costUsd, artifact, verifier, gate, reportPath }>`. `status ∈ {'done','queued','verify_failed','budget_exceeded','daily_cap'}`. Reads task text from `${opsRoot}/tasks/${name}.md` if present. Retries up to `budget.maxRetries` until verifier passes; stops early if accumulated cost exceeds `budget.maxUsd`. Writes report to `${opsRoot}/reports/${name}-<stamp>.md`, updates `${opsRoot}/state/${name}.json`, appends ledger.

- [ ] **Step 1: Write the failing test**

```javascript
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/run-agent.test.mjs`
Expected: FAIL — `Cannot find module '../run-agent.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```javascript
// ops/engine/run-agent.mjs
import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { loadAgentConfig } from './config.mjs';
import { runVerifier } from './verifier.mjs';
import { applyGate } from './gate.mjs';
import { loadState, saveState, appendLedger, spendSince } from './state.mjs';

function stamp(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}-${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}`;
}
function providerOf(model) {
  if (model.startsWith('anthropic')) return 'anthropic';
  if (model.startsWith('ollama')) return 'ollama';
  if (model === 'dryrun') return 'dryrun';
  return 'openrouter';
}

export async function runAgent({ agentDir, opsRoot, router, now = () => new Date(), dailyCapUsd = 5 }) {
  const cfg = loadAgentConfig(agentDir);
  const stateDir = join(opsRoot, 'state');
  const at = now();
  const dayStart = at.toISOString().slice(0, 10) + 'T00:00:00Z';

  if (spendSince(stateDir, dayStart) >= dailyCapUsd) {
    return { status: 'daily_cap', iterations: 0, costUsd: 0, artifact: null, verifier: null, gate: null, reportPath: null };
  }

  const taskFile = join(opsRoot, 'tasks', `${cfg.name}.md`);
  const task = existsSync(taskFile) ? readFileSync(taskFile, 'utf8') : '(이번 주기 지시 없음 — 기본 임무 수행)';
  const prev = loadState(stateDir, cfg.name);

  let artifact = '', verifier = { pass: false, checks: [] }, cost = 0, iterations = 0, feedback = '';
  for (let i = 0; i < cfg.budget.maxRetries + 1; i++) {
    iterations = i + 1;
    const out = await router.complete({
      provider: providerOf(cfg.model.name), model: cfg.model.name,
      system: cfg.skill,
      messages: [{ role: 'user', content: `TASK:\n${task}\n\nPREVIOUS STATE:\n${JSON.stringify(prev)}\n${feedback ? `\nFIX THIS:\n${feedback}` : ''}` }],
    });
    artifact = out.text; cost += out.usage.costUsd;
    verifier = await runVerifier({ router, model: cfg.model.name, verifierPrompt: cfg.verifier, artifact });
    if (verifier.pass) break;
    feedback = verifier.checks.map((c) => c.reason).join('; ');
    if (cost > cfg.budget.maxUsd) {
      appendLedger(stateDir, { ts: at.toISOString(), agent: cfg.name, costUsd: cost });
      return { status: 'budget_exceeded', iterations, costUsd: cost, artifact, verifier, gate: null, reportPath: null };
    }
  }

  const gate = verifier.pass
    ? applyGate({ risk: cfg.risk, name: cfg.name, artifact, approvalsDir: join(opsRoot, 'approvals'), now: at })
    : { action: 'none' };

  const reportsDir = join(opsRoot, 'reports');
  if (!existsSync(reportsDir)) mkdirSync(reportsDir, { recursive: true });
  const reportPath = join(reportsDir, `${cfg.name}-${stamp(at)}.md`);
  writeFileSync(reportPath, `# ${cfg.name} 리포트 (${at.toISOString()})\n\n- 검증: ${verifier.pass ? 'PASS' : 'FAIL'} (${iterations}회 시도)\n- 게이트: ${gate.action}\n- 비용: $${cost.toFixed(4)}\n\n## 산출물\n\n${artifact}\n`);

  saveState(stateDir, cfg.name, { lastRun: at.toISOString(), lastStatus: verifier.pass ? 'done' : 'verify_failed', runs: (prev.runs || 0) + 1 });
  appendLedger(stateDir, { ts: at.toISOString(), agent: cfg.name, costUsd: cost });

  const status = !verifier.pass ? 'verify_failed' : gate.action === 'queued' ? 'queued' : 'done';
  return { status, iterations, costUsd: cost, artifact, verifier, gate, reportPath };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/run-agent.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/run-agent.mjs ops/engine/test/run-agent.test.mjs
git commit -m "feat(ops): run-agent executes the 7-part loop with verify+gate+state"
```

---

### Task 8: `run-chain` orchestrator + CLI

**Files:**
- Create: `ops/engine/run-chain.mjs`
- Test: `ops/engine/test/run-chain.test.mjs`

**Interfaces:**
- Consumes: `runAgent` (T7).
- Produces: `runChain({ opsRoot, agents, router, now, dailyCapUsd }) → Promise<Array<result>>` running each agent name in `agents` sequentially against `${opsRoot}/agents/<name>`. When run as a CLI (`node run-chain.mjs <name...>`), it uses `OPS_ROOT` env (default `..` from engine → repo `ops/`) and a real router.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/run-chain.test.mjs
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/run-chain.test.mjs`
Expected: FAIL — `Cannot find module '../run-chain.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```javascript
// ops/engine/run-chain.mjs
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runAgent } from './run-agent.mjs';
import { createRouter } from './router.mjs';

export async function runChain({ opsRoot, agents, router, now = () => new Date(), dailyCapUsd = 5 }) {
  const results = [];
  for (const name of agents) {
    results.push(await runAgent({ agentDir: join(opsRoot, 'agents', name), opsRoot, router, now, dailyCapUsd }));
  }
  return results;
}

// CLI: node run-chain.mjs research bug
if (import.meta.url === `file://${process.argv[1]}`) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const agents = process.argv.slice(2);
  if (agents.length === 0) { console.error('usage: run-chain.mjs <agent...>'); process.exit(1); }
  const out = await runChain({ opsRoot, agents, router: createRouter() });
  for (const r of out) console.log(`${r.status}\t$${r.costUsd.toFixed(4)}\t${r.reportPath ?? ''}`);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/run-chain.test.mjs`
Expected: PASS (1 test). Then full suite: `cd ops/engine && node --test` → all pass.

- [ ] **Step 5: Commit**

```bash
git add ops/engine/run-chain.mjs ops/engine/test/run-chain.test.mjs
git commit -m "feat(ops): run-chain sequential orchestrator + CLI entry"
```

---

### Task 9: Git worktree isolation helper

**Files:**
- Create: `ops/engine/worktree.mjs`
- Test: `ops/engine/test/worktree.test.mjs`

**Interfaces:**
- Produces: `createWorktree(repoRoot, name) → wtPath` (runs `git worktree add --detach ops/.worktrees/<name>-<ts>` under `repoRoot`, returns absolute path); `removeWorktree(repoRoot, wtPath)` (runs `git worktree remove --force <wtPath>`). Synchronous via `node:child_process execFileSync`.

- [ ] **Step 1: Write the failing test**

```javascript
// ops/engine/test/worktree.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, existsSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createWorktree, removeWorktree } from '../worktree.mjs';

test('creates and removes a worktree in a temp git repo', () => {
  const repo = mkdtempSync(join(tmpdir(), 'ops-wt-'));
  const git = (...a) => execFileSync('git', a, { cwd: repo });
  git('init', '-q');
  git('config', 'user.email', 't@t');
  git('config', 'user.name', 't');
  writeFileSync(join(repo, 'f.txt'), 'x');
  git('add', '.'); git('commit', '-qm', 'init');
  const wt = createWorktree(repo, 'dummy');
  assert.ok(existsSync(wt));
  removeWorktree(repo, wt);
  assert.ok(!existsSync(wt));
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ops/engine && node --test test/worktree.test.mjs`
Expected: FAIL — `Cannot find module '../worktree.mjs'`.

- [ ] **Step 3: Write minimal implementation**

```javascript
// ops/engine/worktree.mjs
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';

export function createWorktree(repoRoot, name) {
  const rel = join('ops', '.worktrees', `${name}-${Date.now()}`);
  execFileSync('git', ['worktree', 'add', '--detach', rel], { cwd: repoRoot });
  return join(repoRoot, rel);
}

export function removeWorktree(repoRoot, wtPath) {
  execFileSync('git', ['worktree', 'remove', '--force', wtPath], { cwd: repoRoot });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd ops/engine && node --test test/worktree.test.mjs`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add ops/engine/worktree.mjs ops/engine/test/worktree.test.mjs
echo 'ops/.worktrees/' >> ../../.gitignore
git add ../../.gitignore
git commit -m "feat(ops): git worktree isolation helper + ignore .worktrees"
```

---

## Self-Review

**Spec coverage (Phase 1a subset):** router/model-tiering (T1,T2 — providers incl. openrouter+anthropic, cost table) ✔; 종료조건/예산 (T4 ledger, T7 maxRetries/maxUsd + daily cap) ✔; 검증 서브에이전트 (T5) ✔; 게이트형 자율운영 (T6 safe/gated) ✔; 상태파일 (T4) ✔; 7-part loop assembly (T7) ✔; 오케스트레이션 (T8) ✔; 워크트리 (T9) ✔. **Deferred to Phase 1b (separate plan):** `ops/agents/<8 agents>` real config/skill/verifier content, `ceo/` loop + cadence, `triggers/` schedules, `mcp/` connectors + `.env.example`, `runbooks/`, wiring research+bug to real OpenRouter. Trigger evaluation is represented as config only in 1a (run-chain invokes agents directly); the cron/event dispatcher is Phase 1b.

**Placeholder scan:** none — every step has real code and exact commands.

**Type consistency:** router return `{text, usage:{inputTokens,outputTokens,costUsd}}` used identically in T1/T2/T5/T7; `runAgent` result `{status,iterations,costUsd,artifact,verifier,gate,reportPath}` consumed by T8; `applyGate` returns `{action, path?}` used in T7; config field names match Global Constraints. Consistent.

---

## Notes / deviations from spec

- Config files are **`config.json`** (not `config.yaml`) to keep the engine zero-dependency and fully testable with `node --test`. The spec's `ops/agents/<name>/config.yaml` is realized as `config.json`. Update the spec's wording in Phase 1b if desired.
- Phase 1a intentionally excludes real agent content and the trigger dispatcher; it delivers a tested engine that runs any well-formed agent folder via `run-chain`.
