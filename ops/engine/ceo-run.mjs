// CEO 러너: state·리포트·승인대기를 읽고, 모델로 이번 주기 목표를 정해 tasks/<agent>.md 발행.
import { readFileSync, writeFileSync, existsSync, readdirSync, mkdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { LANG_GUARD } from './lang.mjs';
import { stampTime, agentOf } from './prune.mjs';

function extractJson(text) {
  const s = text.indexOf('{'), e = text.lastIndexOf('}');
  if (s < 0 || e < s) return null;
  try { return JSON.parse(text.slice(s, e + 1)); } catch { return null; }
}

const kst = (ms) => {
  const d = new Date(ms + 9 * 3600 * 1000);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())} KST`;
};

// 리포트 1건 요약: run-agent.mjs 리포트 머리의 검증·게이트 줄 + `## 산출물` 이후 본문 앞부분.
export function reportDigest(text, maxChars) {
  const verdict = /-\s*검증:\s*([^\n]+)/.exec(text)?.[1]?.trim();
  const gate = /-\s*게이트:\s*([^\n]+)/.exec(text)?.[1]?.trim();
  const i = text.indexOf('## 산출물');
  const body = (i >= 0 ? text.slice(i + '## 산출물'.length) : text)
    .replace(/^#+\s*/gm, '').replace(/\*\*/g, '').replace(/\s+/g, ' ').trim();
  const chars = Array.from(body); // 코드포인트 단위 자르기(이모지 등 깨짐 방지)
  const excerpt = chars.length > maxChars ? chars.slice(0, maxChars).join('') + '…' : body;
  return { verdict, gate, excerpt };
}

// 에이전트별 최신 리포트 perAgent 개를 CEO 컨텍스트용 텍스트로. excerpts[i] = i번째(0=최신) 본문 글자 수.
// (이전엔 readdirSync().slice(-10) 이라 알파벳순 마지막 10개 — social·support 만 보이고 파일명뿐이었음.)
export function summarizeReports({ dir, perAgent = 2, excerpts = [400, 200] }) {
  if (!existsSync(dir)) return '(리포트 없음)';
  const groups = new Map();
  for (const name of readdirSync(dir)) {
    if (name.startsWith('.') || !name.endsWith('.md')) continue;
    const path = join(dir, name);
    const t = stampTime(name) ?? statSync(path).mtimeMs;
    const agent = agentOf(name);
    if (!groups.has(agent)) groups.set(agent, []);
    groups.get(agent).push({ path, t });
  }
  if (!groups.size) return '(리포트 없음)';
  const blocks = [];
  for (const agent of [...groups.keys()].sort()) {
    const latest = groups.get(agent).sort((a, b) => b.t - a.t).slice(0, perAgent);
    const lines = latest.map((r, idx) => {
      const d = reportDigest(readFileSync(r.path, 'utf8'), excerpts[idx] ?? excerpts[excerpts.length - 1]);
      const meta = [kst(r.t), d.verdict && `검증 ${d.verdict}`, d.gate && `게이트 ${d.gate}`].filter(Boolean).join(' · ');
      return `- ${meta}\n  ${d.excerpt}`;
    });
    blocks.push(`[${agent}]\n${lines.join('\n')}`);
  }
  return blocks.join('\n');
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
  const reports = summarizeReports({ dir: join(opsRoot, 'reports') });
  const approvals = existsSync(join(opsRoot, 'approvals'))
    ? readdirSync(join(opsRoot, 'approvals')).filter((n) => !n.startsWith('.')) : [];

  const ctx = `에이전트: ${agents.join(', ')}\n상태(state): ${JSON.stringify(states)}\n`
    + `최근 리포트(에이전트별 최신 2개 · 본문 앞부분):\n${reports}\n승인 대기: ${approvals.join(', ')}\n\n`
    + `각 에이전트에게 이번 주기 목표를 정하라(숫자·기한, 형용사 금지). 오직 JSON만 출력: {"priorities":["..."],"tasks":{"<agent>":"목표"}}`;

  const out = await router.complete({ provider: cfg.model.provider, model: cfg.model.name, system: LANG_GUARD + system, messages: [{ role: 'user', content: ctx }] });
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
