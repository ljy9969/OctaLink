// 트리거 디스패처: config/cadence의 cron을 읽어 '지금 실행할' 잡을 판정·실행.
// Windows 작업 스케줄러가 ~15분마다 1회 호출. last-run 상태로 중복 방지 + 미실행 캐치업(24h 캡).
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dueSince } from './cron.mjs';
import { loadState, saveState } from './state.mjs';
import { runAgent } from './run-agent.mjs';
import { runCeo } from './ceo-run.mjs';
import { createRouter } from './router.mjs';

function cronOf(trigger) { const m = /cron:\s*([^|]+)/.exec(trigger || ''); return m ? m[1].trim() : null; }

export function readJobs(opsRoot) {
  const jobs = [];
  const agentsDir = join(opsRoot, 'agents');
  for (const n of (existsSync(agentsDir) ? readdirSync(agentsDir).sort() : [])) {
    const c = JSON.parse(readFileSync(join(agentsDir, n, 'config.json'), 'utf8'));
    const cron = cronOf(c.trigger);
    if (cron) jobs.push({ name: n, kind: 'agent', cron });
  }
  const cadPath = join(opsRoot, 'ceo', 'cadence.json');
  if (existsSync(cadPath)) { const cad = JSON.parse(readFileSync(cadPath, 'utf8')); if (cad.daily?.cron) jobs.push({ name: 'ceo', kind: 'ceo', cron: cad.daily.cron }); }
  return jobs;
}

export async function runDispatch({ opsRoot, router, now = () => new Date(), runAgentFn = runAgent, runCeoFn = runCeo }) {
  const at = now(); const nowMs = at.getTime();
  const stateDir = join(opsRoot, 'state');
  const st = loadState(stateDir, 'dispatch'); st.lastRun = st.lastRun || {};
  const results = [];
  for (const j of readJobs(opsRoot)) {
    const fromMs = st.lastRun[j.name] != null ? Date.parse(st.lastRun[j.name]) : nowMs; // 최초 목격 땐 소급 안 함
    const due = dueSince(j.cron, fromMs, nowMs);
    let status;
    if (due) {
      const r = j.kind === 'ceo'
        ? await runCeoFn({ opsRoot, router, now })
        : await runAgentFn({ agentDir: join(opsRoot, 'agents', j.name), opsRoot, router, now });
      status = r.status;
    }
    st.lastRun[j.name] = at.toISOString(); // 발화 여부와 무관하게 항상 갱신 → 다음 창이 전진
    results.push({ job: j.name, ran: due, status });
  }
  saveState(stateDir, 'dispatch', st);
  return results;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  if (process.argv.includes('--list')) {
    for (const j of readJobs(opsRoot)) console.log(`${j.name.padEnd(9)} ${j.kind.padEnd(6)} ${j.cron}`);
  } else {
    const res = await runDispatch({ opsRoot, router: createRouter() });
    const fired = res.filter((r) => r.ran).map((r) => `${r.job}(${r.status})`);
    console.log(`[dispatch ${new Date().toISOString()}] fired: ${fired.join(', ') || 'none'}`);
  }
}
