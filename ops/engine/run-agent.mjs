import { readFileSync, existsSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { loadAgentConfig } from './config.mjs';
import { runVerifier } from './verifier.mjs';
import { applyGate } from './gate.mjs';
import { loadState, saveState, appendLedger, spendSince } from './state.mjs';
import { LANG_GUARD } from './lang.mjs';
import { parseContextSection } from '../connectors/context-file.mjs';

// 마크다운 표의 데이터 행 수(머리·구분선 제외).
function tableRows(body) {
  const lines = body.split('\n').filter((l) => l.trim().startsWith('|'));
  return Math.max(0, lines.length - 2);
}

// 결정적(extract) 모드: LLM 없이 커넥터가 코드로 추출한 context 구획을 그대로 산출물로 쓴다 → 지어낼 단계가 없음.
// 검증도 코드: 구획이 있고, 이번 주기에 갱신됐고(maxAgeHours 이내), 표에 1건 이상.
function extractArtifact({ context, extract, at }) {
  const { section, maxAgeHours = 6 } = extract;
  const sec = parseContextSection(context, section);
  const n = sec ? tableRows(sec.body) : 0;
  let reason = null;
  if (!sec) reason = `context 에 '${section}' 구획 없음(검색 갱신 전)`;
  else if (sec.updatedAt == null || at.getTime() - sec.updatedAt > maxAgeHours * 3600 * 1000) {
    reason = `'${section}' 이 ${maxAgeHours}시간보다 오래됨 — 이번 주기 검색 갱신 실패로 보고 재사용 안 함`;
  } else if (n < 1) reason = '확인된 항목 0건';
  const pass = !reason;
  const fresh = sec && !(reason && reason.includes('오래됨'));
  const artifact = `### ${section} (코드 추출 · LLM 미사용)\n\n${fresh ? sec.body : `(${reason})`}`;
  return {
    artifact,
    verifier: { pass, checks: [{ name: 'extract', pass, reason: pass ? `${n}건 · 갱신 ${new Date(sec.updatedAt).toISOString()}` : reason }] },
  };
}

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
  const ctxFile = join(opsRoot, 'context', `${cfg.name}.md`); // 커넥터가 채우는 앱 데이터 스냅샷(있으면 참고)
  const context = existsSync(ctxFile) ? readFileSync(ctxFile, 'utf8') : '';
  const prev = loadState(stateDir, cfg.name);

  let artifact = '', verifier = { pass: false, checks: [] }, cost = 0, iterations = 0, feedback = '';
  if (cfg.extract) {
    ({ artifact, verifier } = extractArtifact({ context, extract: cfg.extract, at }));
    iterations = 1;
  } else {
    for (let i = 0; i < cfg.budget.maxRetries + 1; i++) {
      iterations = i + 1;
      const out = await router.complete({
        provider: cfg.model.provider, model: cfg.model.name,
        system: LANG_GUARD + cfg.skill,
        messages: [{ role: 'user', content: `TASK:\n${task}\n${context ? `\nAPP DATA(참고):\n${context}\n` : ''}\nPREVIOUS STATE:\n${JSON.stringify(prev)}\n${feedback ? `\nFIX THIS:\n${feedback}` : ''}` }],
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
