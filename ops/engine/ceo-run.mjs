// CEO 러너: state·리포트·승인대기를 읽고, 모델로 이번 주기 목표를 정해 tasks/<agent>.md 발행.
import { readFileSync, writeFileSync, existsSync, readdirSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

function extractJson(text) {
  const s = text.indexOf('{'), e = text.lastIndexOf('}');
  if (s < 0 || e < s) return null;
  try { return JSON.parse(text.slice(s, e + 1)); } catch { return null; }
}

export async function runCeo({ opsRoot, router, now = () => new Date() }) {
  const at = now();
  const cfg = JSON.parse(readFileSync(join(opsRoot, 'ceo', 'config.json'), 'utf8'));
  const system = readFileSync(join(opsRoot, 'ceo', 'ceo.md'), 'utf8');
  const stateDir = join(opsRoot, 'state');
  const agentsDir = join(opsRoot, 'agents');
  const agents = existsSync(agentsDir) ? readdirSync(agentsDir) : [];

  const states = {};
  for (const a of agents) { const p = join(stateDir, `${a}.json`); if (existsSync(p)) states[a] = JSON.parse(readFileSync(p, 'utf8')); }
  const reports = existsSync(join(opsRoot, 'reports')) ? readdirSync(join(opsRoot, 'reports')).slice(-10) : [];
  const approvals = existsSync(join(opsRoot, 'approvals')) ? readdirSync(join(opsRoot, 'approvals')) : [];

  const ctx = `에이전트: ${agents.join(', ')}\n상태(state): ${JSON.stringify(states)}\n최근 리포트: ${reports.join(', ')}\n승인 대기: ${approvals.join(', ')}\n\n`
    + `각 에이전트에게 이번 주기 목표를 정하라(숫자·기한, 형용사 금지). 오직 JSON만 출력: {"priorities":["..."],"tasks":{"<agent>":"목표"}}`;

  const out = await router.complete({ provider: cfg.model.provider, model: cfg.model.name, system, messages: [{ role: 'user', content: ctx }] });
  const parsed = extractJson(out.text) || { priorities: [], tasks: {} };

  const tasksDir = join(opsRoot, 'tasks'); if (!existsSync(tasksDir)) mkdirSync(tasksDir, { recursive: true });
  const written = [];
  for (const [a, goal] of Object.entries(parsed.tasks || {})) {
    if (!agents.includes(a)) continue;
    writeFileSync(join(tasksDir, `${a}.md`), `# ${a} — 이번 주기 목표 (CEO ${at.toISOString()})\n\n${goal}\n`);
    written.push(a);
  }
  if (!existsSync(stateDir)) mkdirSync(stateDir, { recursive: true });
  writeFileSync(join(stateDir, 'ceo.json'), JSON.stringify({ lastRun: at.toISOString(), priorities: parsed.priorities || [], tasksWritten: written }, null, 2));
  return { status: 'done', priorities: parsed.priorities || [], tasksWritten: written };
}
