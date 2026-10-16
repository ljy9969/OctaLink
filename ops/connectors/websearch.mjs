// 웹 검색 커넥터 → 에이전트 컨텍스트(context/<agent>.md)에 실데이터+출처 + 결정적 인벤토리(research·social=앱, sales=체육관 리드) 주입.
// 기본: DuckDuckGo HTML(무료·키 불필요). BRAVE_API_KEY 있으면 Brave Search(JSON, 안정적).
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { writeContextSection } from './context-file.mjs';

function stripTags(s) { return s.replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').replace(/&#x27;/g, "'").replace(/&quot;/g, '"').replace(/&#39;/g, "'").trim(); }
function decodeUddg(href) { const m = /[?&]uddg=([^&]+)/.exec(href); const u = m ? decodeURIComponent(m[1]) : href; return u.replace(/&amp;/g, '&'); }

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

// 우선순위: 로컬 SearXNG(self-host·키 불필요·가장 안정) → Google CSE(키+cx) → Brave(키)
//           → DuckDuckGo(재시도) → 공개 SearXNG. 첫 결과 반환.
// 참고: Google Custom Search JSON API는 신규 고객 발급 중단(403 PERMISSION_DENIED)이라 사실상 막힘.
//       공개 SearXNG 인스턴스는 대부분 JSON 비활성/429 → self-host(SEARXNG_URL) 권장.
export async function search(query, { transport = globalThis.fetch, env = process.env } = {}) {
  if (env.SEARXNG_URL) { const r = await searxSearch(query, transport, [env.SEARXNG_URL.replace(/\/$/, '')]); if (r.length) return r; }
  const gkey = env.GOOGLE_API_KEY || env.YOUTUBE_API_KEY; // 같은 프로젝트 API 키 재사용 가능
  if (env.GOOGLE_CSE_ID && gkey) return googleCse(query, transport, gkey, env.GOOGLE_CSE_ID);
  if (env.BRAVE_API_KEY) return braveSearch(query, transport, env.BRAVE_API_KEY);
  let r = await ddgSearch(query, transport); if (r.length) return r;
  return searxSearch(query, transport);
}

// 결정적 앱 추출: 검색 결과에서 스토어 링크만 골라 중복제거·카테고리 분류. LLM 미사용 → 환각 불가.
export function extractApps(results) {
  const seen = new Set(); const out = [];
  for (const r of results) {
    const url = r.url || ''; let store, id, m;
    if ((m = /play\.google\.com\/store\/apps\/details\?[^\s)]*\bid=([A-Za-z0-9._]+)/.exec(url))) { store = 'Google Play'; id = m[1]; }
    else if ((m = /apps\.apple\.com\/[^\s)]*\/id(\d+)/.exec(url))) { store = 'App Store'; id = 'id' + m[1]; }
    else continue;
    const key = `${store}:${id}`; if (seen.has(key)) continue; seen.add(key);
    const text = `${r.title || ''} ${r.snippet || ''}`;
    const category = /clash|legacy|\bgame\b|게임|배틀/i.test(text) ? '게임'
      : /management|manage|관리|admin|academy|dojo|membership|회원|studio|\bclub\b|운영/i.test(text) ? '체육관 관리SW'
      : '훈련/콘텐츠';
    const name = (r.title || '').replace(/\s*[-–|]\s*(Google Play.*|Apps on Google.*|App Store.*|앱\s?스토어.*)$/i, '').trim() || id;
    out.push({ name, store, id, url: url.replace(/[?&]hl=[a-z_-]+/i, ''), category, snippet: (r.snippet || '').replace(/\s+/g, ' ').slice(0, 120) });
  }
  return out;
}

// 결정적 체육관 리드 추출(sales): "실제 체육관"임이 링크 형태로 분명한 결과만 — 인스타그램 계정,
// Tapology 체육관 페이지, 카카오맵·네이버 지도 장소 페이지. 이름에 격투/체육관 단어가 있어야 함.
// 뉴스·위키·블로그·디렉터리·일반 웹사이트는 전부 제외(정확도 우선). LLM 미사용 → 지어낼 수 없음.
const GYM_KW = /주짓수|MMA|종합격투|격투|복싱|킥복싱|무에타이|레슬링|유도|BJJ|jiu.?jitsu|boxing|kickbox|muay|fight|gym|짐|도장|체육관|top team/i;
const IG_RESERVED = new Set(['p', 'reel', 'reels', 'explore', 'stories', 'accounts', 'tv', 'tags', 'direct', 'about', 'developer', 'legal']);

export function extractLeads(results) {
  const seen = new Set(); const out = [];
  for (const r of results) {
    const url = (r.url || '').trim(); const title = (r.title || '').trim(); let m, lead = null;
    if ((m = /^https?:\/\/(?:www\.)?instagram\.com\/([A-Za-z0-9._]+)\/?(?:\?.*)?$/.exec(url)) && !IG_RESERVED.has(m[1].toLowerCase())) {
      const name = title.replace(/\s*\(@[^)]*\)[\s\S]*$/, '').replace(/\s*(?:[•|·-]\s*)?Instagram[\s\S]*$/i, '').trim();
      lead = { name, source: `인스타그램 @${m[1]}`, url: `https://www.instagram.com/${m[1]}/`, key: `ig:${m[1].toLowerCase()}` };
    } else if ((m = /^https?:\/\/(?:www\.)?tapology\.com\/gyms\/(\d+)-[\w-]+/.exec(url))) {
      lead = { name: title.replace(/\s*\|\s*Gym Page[\s\S]*$/i, '').trim(), source: 'Tapology', url: m[0], key: `tap:${m[1]}` };
    } else if ((m = /^https?:\/\/place\.map\.kakao\.com\/(\d+)/.exec(url))) {
      lead = { name: title.replace(/\s*[|-]\s*카카오맵[\s\S]*$/, '').trim(), source: '카카오맵', url: m[0], key: `kakao:${m[1]}` };
    } else if ((m = /^https?:\/\/(?:m\.|pcmap\.)?(?:place\.naver\.com\/[a-z]+|map\.naver\.com\/[\s\S]*?place)\/(\d+)/.exec(url))) {
      lead = { name: title.replace(/\s*[:|-]\s*네이버[\s\S]*$/, '').trim(), source: '네이버 지도', url: url.split('?')[0], key: `naver:${m[1]}` };
    }
    if (!lead || !lead.name || !GYM_KW.test(lead.name)) continue;
    if (seen.has(lead.key)) continue; seen.add(lead.key);
    out.push({ name: lead.name, source: lead.source, url: lead.url, query: r.query || '' });
  }
  return out;
}

const cell = (s) => String(s).replace(/\|/g, '/').replace(/\s+/g, ' ').trim(); // 표 깨짐 방지

// 에이전트별 결정적 인벤토리(코드 추출) — Opus 분석·CEO 판단이 참고하는 신뢰 가능한 목록.
const INVENTORIES = {
  sales: {
    section: '리드 인벤토리',
    render(results) {
      const leads = extractLeads(results);
      if (!leads.length) return '(확인된 체육관 리드 없음 — 이번 검색 결과에 인스타그램 계정·Tapology·지도 장소 링크가 없음)';
      const rows = leads.map((l) => `| ${cell(l.name)} | ${l.source} | [열기](${l.url}) | ${cell(l.query)} |`);
      return `실제 검색 결과에서 결정적 추출(중복제거) — 총 ${leads.length}곳. 출처는 인스타그램 계정·Tapology 체육관 페이지·지도 장소 페이지만(뉴스·위키·블로그·일반 웹 제외).\n\n| 체육관 | 출처 | 링크 | 찾은 검색어 |\n|---|---|---|---|\n${rows.join('\n')}`;
    },
  },
  default: {
    section: '앱 인벤토리',
    render(results) {
      const apps = extractApps(results);
      const rows = apps.map((a) => `| ${a.name} | ${a.category} | ${a.store} | [열기](${a.url}) | ${a.snippet} |`);
      return apps.length
        ? `실제 검색 결과에서 결정적 추출(중복제거) — 총 ${apps.length}개.\n\n| 앱 | 카테고리 | 스토어 | 링크 | 스니펫 |\n|---|---|---|---|---|\n${rows.join('\n')}`
        : '(스토어 앱 링크 없음)';
    },
  },
};

export async function buildResearchContext({ opsRoot, agent = 'research', queries, searchFn = search, env, delayMs = 1500, now = () => new Date() }) {
  const blocks = []; const allResults = [];
  for (let qi = 0; qi < queries.length; qi++) {
    const q = queries[qi];
    if (qi > 0 && delayMs) await new Promise((r) => setTimeout(r, delayMs)); // DDG 연속요청 스로틀링 완화
    let results = [];
    try { results = await searchFn(q, { env }); } catch (e) { blocks.push(`## ${q}\n(검색 실패: ${e.message})`); continue; }
    allResults.push(...results.map((r) => ({ ...r, query: q })));
    const lines = results.map((r) => `- [${r.title}](${r.url})${r.snippet ? ` — ${r.snippet.slice(0, 180)}` : ''}`);
    blocks.push(`### ${q}\n${lines.join('\n') || '(결과 없음)'}`);
  }
  writeContextSection({ opsRoot, agent, section: '웹 검색', body: blocks.join('\n\n'), now });
  const inv = INVENTORIES[agent] || INVENTORIES.default;
  writeContextSection({ opsRoot, agent, section: inv.section, body: inv.render(allResults), now });
  return blocks.length;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8')).websearch || {};
  for (const [agent, queries] of Object.entries(conf.queries || {})) {
    if (!Array.isArray(queries)) continue; // "_social_note" 같은 설명 키 건너뜀
    const n = await buildResearchContext({ opsRoot, agent, queries });
    const prov = process.env.SEARXNG_URL ? 'SearXNG(self-host)' : process.env.GOOGLE_CSE_ID ? 'Google CSE' : process.env.BRAVE_API_KEY ? 'Brave' : 'DuckDuckGo';
    console.log(`context/${agent}.md ← ${n} 쿼리 (${prov})`);
  }
}
