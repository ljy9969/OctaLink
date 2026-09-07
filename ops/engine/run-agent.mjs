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
      provider: cfg.model.provider, model: cfg.model.name,
      system: cfg.skill,
      messages: [{ role: 'user', content: `TASK:\n${task}\n\nPREVIOUS STATE:\n${JSON.stringify(prev)}\n${feedback ? `\nFIX THIS:\n${feedback}` : ''}` }],
    });
    artifact = out.text; cost += out.usage.costUsd;
    verifier = await runVerifier({ router, provider: cfg.model.provider, model: cfg.model.name, verifierPrompt: cfg.verifier, artifact });
    cost += verifier.usage?.costUsd || 0;
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
