// 웹 검색 커넥터 → research 컨텍스트(context/research.md)에 실데이터+출처 주입.
// 기본: DuckDuckGo HTML(무료·키 불필요). BRAVE_API_KEY 있으면 Brave Search(JSON, 안정적).
import { writeFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

function stripTags(s) { return s.replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&#x27;/g, "'").replace(/&quot;/g, '"').replace(/&#39;/g, "'").trim(); }
function decodeUddg(href) { const m = /[?&]uddg=([^&]+)/.exec(href); return m ? decodeURIComponent(m[1]) : href; }

export function parseDdg(html) {
  const out = [];
  const linkRe = /class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g;
  const snips = [...html.matchAll(/class="result__snippet"[^>]*>([\s\S]*?)<\/(?:a|div)>/g)].map((x) => stripTags(x[1]));
  let m, i = 0;
  while ((m = linkRe.exec(html)) && out.length < 8) {
    out.push({ title: stripTags(m[2]), url: decodeUddg(m[1]), snippet: snips[i] || '' }); i++;
  }
  return out;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function braveSearch(query, transport, key) {
  const res = await transport(`https://api.search.brave.com/res/v1/web/search?count=8&q=${encodeURIComponent(query)}`,
    { headers: { 'X-Subscription-Token': key, Accept: 'application/json' } });
  if (!res.ok) throw new Error(`Brave ${res.status}: ${(await res.text().catch(() => '')).slice(0, 150)}`);
  const j = await res.json();
  return (j.web?.results || []).slice(0, 8).map((r) => ({ title: r.title, url: r.url, snippet: r.description || '' }));
}
async function ddgSearch(query, transport, tries = 3) {
  for (let i = 0; i < tries; i++) {
    const res = await transport('https://html.duckduckgo.com/html/', {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': 'Mozilla/5.0' },
      body: `q=${encodeURIComponent(query)}` });
    if (res.ok && res.status === 200) { const r = parseDdg(await res.text()); if (r.length) return r; }
    if (i < tries - 1) await sleep(1200 * (i + 1)); // 백오프(202 anomaly 완화)
  }
  return [];
}
async function searxSearch(query, transport, instances = ['https://searx.be', 'https://search.rhscz.eu', 'https://priv.au']) {
  for (const base of instances) {
    try {
      const res = await transport(`${base}/search?format=json&q=${encodeURIComponent(query)}`,
        { headers: { 'User-Agent': 'Mozilla/5.0', Accept: 'application/json' } });
      if (!res.ok) continue;
      const j = await res.json();
      const r = (j.results || []).slice(0, 8).map((x) => ({ title: x.title, url: x.url, snippet: x.content || '' }));
      if (r.length) return r;
    } catch { /* 다음 인스턴스 */ }
  }
  return [];
}

async function googleCse(query, transport, key, cx) {
  const res = await transport(`https://www.googleapis.com/customsearch/v1?num=8&key=${key}&cx=${cx}&q=${encodeURIComponent(query)}`);
  if (!res.ok) throw new Error(`Google CSE ${res.status}: ${(await res.text().catch(() => '')).slice(0, 150)}`);
  const j = await res.json();
  return (j.items || []).slice(0, 8).map((i) => ({ title: i.title, url: i.link, snippet: i.snippet || '' }));
}

// 우선순위: Google CSE(키+cx) → Brave(키) → DuckDuckGo(재시도) → SearXNG. 첫 결과 반환.
export async function search(query, { transport = globalThis.fetch, env = process.env } = {}) {
  const gkey = env.GOOGLE_API_KEY || env.YOUTUBE_API_KEY; // 같은 프로젝트 API 키 재사용 가능
  if (env.GOOGLE_CSE_ID && gkey) return googleCse(query, transport, gkey, env.GOOGLE_CSE_ID);
  if (env.BRAVE_API_KEY) return braveSearch(query, transport, env.BRAVE_API_KEY);
  let r = await ddgSearch(query, transport); if (r.length) return r;
  return searxSearch(query, transport);
}

export async function buildResearchContext({ opsRoot, agent = 'research', queries, searchFn = search, env, delayMs = 1500, now = () => new Date() }) {
  const blocks = [];
  for (let qi = 0; qi < queries.length; qi++) {
    const q = queries[qi];
    if (qi > 0 && delayMs) await new Promise((r) => setTimeout(r, delayMs)); // DDG 연속요청 스로틀링 완화
    let results = [];
    try { results = await searchFn(q, { env }); } catch (e) { blocks.push(`## ${q}\n(검색 실패: ${e.message})`); continue; }
    const lines = results.map((r) => `- [${r.title}](${r.url})${r.snippet ? ` — ${r.snippet.slice(0, 180)}` : ''}`);
    blocks.push(`## ${q}\n${lines.join('\n') || '(결과 없음)'}`);
  }
  const ctxDir = join(opsRoot, 'context'); if (!existsSync(ctxDir)) mkdirSync(ctxDir, { recursive: true });
  writeFileSync(join(ctxDir, `${agent}.md`), `# ${agent} 웹 검색 스냅샷 (${now().toISOString()})\n\n${blocks.join('\n\n')}\n`);
  return results_count(blocks);
}
function results_count(blocks) { return blocks.length; }

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8')).websearch || {};
  for (const [agent, queries] of Object.entries(conf.queries || {})) {
    const n = await buildResearchContext({ opsRoot, agent, queries });
    console.log(`context/${agent}.md ← ${n} 쿼리 (${process.env.BRAVE_API_KEY ? 'Brave' : 'DuckDuckGo'})`);
  }
}
