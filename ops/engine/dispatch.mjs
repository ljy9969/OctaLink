// 트리거 디스패처: config/cadence의 cron을 읽어 '지금 실행할' 잡을 판정·실행.
// Windows 작업 스케줄러가 ~15분마다 1회 호출. last-run 상태로 중복 방지 + 미실행 캐치업(24h 캡).
import { readFileSync, existsSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dueSince } from './cron.mjs';
import { loadState, saveState } from './state.mjs';
import { runAgent } from './run-agent.mjs';
import { runCeo } from './ceo-run.mjs';
import { createRouter } from './router.mjs';
import { checkAssignedIssue } from '../connectors/github-issues.mjs';
import { buildResearchContext } from '../connectors/websearch.mjs';
import { buildYoutubeContext } from '../connectors/youtube.mjs';
import { refreshContext as refreshFirebase } from '../connectors/firebase.mjs';
import { loadEnv } from './env.mjs';

function cronOf(trigger) { const m = /cron:\s*([^|]+)/.exec(trigger || ''); return m ? m[1].trim() : null; }
function loadConnectors(opsRoot) { const p = join(opsRoot, 'connectors', 'config.json'); return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : {}; }

// 에이전트 실행 전 웹 검색 컨텍스트 갱신(websearch.queries[agent] 있으면). 실패해도 진행.
async function defaultRefresh({ opsRoot, agent, env }) {
  const conn = loadConnectors(opsRoot);
  const queries = conn.websearch?.queries?.[agent];
  if (queries?.length) { try { await buildResearchContext({ opsRoot, agent, queries, env }); } catch { /* stale로 진행 */ } }
  const yt = conn.youtube; // research: 유튜브 채널 조사 결과를 컨텍스트에 추가
  if (agent === 'research' && yt?.channelQueries?.length && env.YOUTUBE_API_KEY) {
    try { await buildYoutubeContext({ opsRoot, agent, queries: yt.channelQueries, apiKey: env.YOUTUBE_API_KEY }); } catch { /* skip */ }
  }
  const fb = conn.firebase; // 앱데이터(Firestore) 구획 갱신 (context 맵에 이 에이전트가 있으면)
  const collections = fb?.context?.[agent];
  if (collections?.length) {
    const raw = env[fb.credEnv || 'FIREBASE_SERVICE_ACCOUNT'];
    if (raw) {
      try {
        const cred = existsSync(raw) ? readFileSync(raw, 'utf8') : raw; // 경로면 내용으로
        await refreshFirebase({ opsRoot, projectId: fb.projectId, credentialJson: cred, contextMap: { [agent]: collections } });
      } catch { /* stale로 진행 */ }
    }
  }
}

export function readEventAgents(opsRoot) {
  const out = []; const dir = join(opsRoot, 'agents');
  for (const n of (existsSync(dir) ? readdirSync(dir).sort() : [])) {
    const c = JSON.parse(readFileSync(join(dir, n, 'config.json'), 'utf8'));
    if (/event:\s*issue\.assigned/.test(c.trigger || '')) out.push(n);
  }
  return out;
}

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

export async function runDispatch({ opsRoot, router, now = () => new Date(), runAgentFn = runAgent, runCeoFn = runCeo, githubCheckFn = checkAssignedIssue, refreshContextFn = defaultRefresh, env = process.env }) {
  const at = now(); const nowMs = at.getTime();
  const stateDir = join(opsRoot, 'state');
  const st = loadState(stateDir, 'dispatch'); st.lastRun = st.lastRun || {};
  const results = [];
  for (const j of readJobs(opsRoot)) {
    const fromMs = st.lastRun[j.name] != null ? Date.parse(st.lastRun[j.name]) : nowMs; // 최초 목격 땐 소급 안 함
    const due = dueSince(j.cron, fromMs, nowMs);
    let status;
    if (due) {
      if (j.kind !== 'ceo') await refreshContextFn({ opsRoot, agent: j.name, env }); // 웹 컨텍스트 갱신
      const r = j.kind === 'ceo'
        ? await runCeoFn({ opsRoot, router, now })
        : await runAgentFn({ agentDir: join(opsRoot, 'agents', j.name), opsRoot, router, now });
      status = r.status;
    }
    st.lastRun[j.name] = at.toISOString(); // 발화 여부와 무관하게 항상 갱신 → 다음 창이 전진
    results.push({ job: j.name, ran: due, status });
  }

  // --- 이벤트 트리거 (dev issue.assigned): 새 배정 이슈면 태스크 발행 + 실행(이슈번호로 중복 방지) ---
  const gh = loadConnectors(opsRoot).github;
  if (gh?.repo) {
    st.lastEventIssue = st.lastEventIssue || {};
    for (const name of readEventAgents(opsRoot)) {
      let r;
      try { r = await githubCheckFn({ repo: gh.repo, token: env.GH_TOKEN, sinceMs: nowMs - 24 * 3600 * 1000 }); }
      catch (e) { results.push({ job: name, ran: false, event: 'error', error: e.message }); continue; }
      if (!r.fired || r.issue.number === st.lastEventIssue[name]) continue;
      const tasksDir = join(opsRoot, 'tasks'); if (!existsSync(tasksDir)) mkdirSync(tasksDir, { recursive: true });
      writeFileSync(join(tasksDir, `${name}.md`),
        `# ${name} — GitHub 이슈 #${r.issue.number} 구현\n\n제목: ${r.issue.title}\n배정: ${r.issue.assignee}\n${r.issue.url}\n\n${r.issue.body}\n`);
      if (!results.some((x) => x.job === name && x.ran)) { // 이번 디스패치에서 이미 cron으로 돌았으면 재실행 안 함
        await refreshContextFn({ opsRoot, agent: name, env });
        const rr = await runAgentFn({ agentDir: join(opsRoot, 'agents', name), opsRoot, router, now });
        results.push({ job: name, ran: true, status: rr.status, event: 'issue.assigned' });
      }
      st.lastEventIssue[name] = r.issue.number;
    }
  }

  saveState(stateDir, 'dispatch', st);
  return results;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  loadEnv(opsRoot); // ops/mcp/.env 의 커넥터 자격증명 로드
  if (process.argv.includes('--list')) {
    for (const j of readJobs(opsRoot)) console.log(`${j.name.padEnd(9)} ${j.kind.padEnd(6)} ${j.cron}`);
  } else {
    const res = await runDispatch({ opsRoot, router: createRouter() });
    const fired = res.filter((r) => r.ran).map((r) => `${r.job}(${r.status})`);
    console.log(`[dispatch ${new Date().toISOString()}] fired: ${fired.join(', ') || 'none'}`);
  }
}
