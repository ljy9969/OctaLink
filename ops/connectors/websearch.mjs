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

export async function search(query, { transport = globalThis.fetch, env = process.env } = {}) {
  if (env.BRAVE_API_KEY) {
    const res = await transport(`https://api.search.brave.com/res/v1/web/search?count=8&q=${encodeURIComponent(query)}`,
      { headers: { 'X-Subscription-Token': env.BRAVE_API_KEY, Accept: 'application/json' } });
    if (!res.ok) throw new Error(`Brave ${res.status}: ${(await res.text().catch(() => '')).slice(0, 150)}`);
    const j = await res.json();
    return (j.web?.results || []).slice(0, 8).map((r) => ({ title: r.title, url: r.url, snippet: r.description || '' }));
  }
  const res = await transport('https://html.duckduckgo.com/html/', {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'User-Agent': 'Mozilla/5.0' },
    body: `q=${encodeURIComponent(query)}` });
  if (!res.ok) throw new Error(`DDG ${res.status}`);
  return parseDdg(await res.text());
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
