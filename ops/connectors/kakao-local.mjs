// 카카오 로컬(장소) 키워드 검색 → sales 체육관 리드(코드 추출 · LLM 미사용).
// 실제 등록 업체만 나오므로 지어낼 수 없음. 연락처(전화)는 수집하지 않고 카카오맵 링크만 남김.
// 키: KAKAO_REST_API_KEY (카카오 개발자 앱 REST API 키). 없으면 호출하지 않음.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { writeContextSection } from './context-file.mjs';

const API = 'https://dapi.kakao.com/v2/local/search/keyword.json';
const PAGE_CAP = 45; // API 한계: 검색 1건당 최대 3페이지 × 15 = 45곳

export async function kakaoKeywordPage({ query, rect, page, key, transport = globalThis.fetch }) {
  const url = `${API}?size=15&page=${page}&rect=${rect}&query=${encodeURIComponent(query)}`;
  const res = await transport(url, { headers: { Authorization: `KakaoAK ${key}` } });
  if (!res.ok) throw new Error(`Kakao Local ${res.status}: ${(await res.text().catch(() => '')).slice(0, 200)}`);
  return res.json();
}

function splitRect(rect) {
  const [x1, y1, x2, y2] = rect.split(',').map(Number);
  const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
  const f = (n) => n.toFixed(6);
  return [[x1, y1, mx, my], [mx, y1, x2, my], [x1, my, mx, y2], [mx, my, x2, y2]].map((r) => r.map(f).join(','));
}

// 사각형 안의 키워드 결과 전부 수집. 한 구역 결과가 45곳을 넘으면 4등분해 재귀(쿼드트리).
// maxDepth 까지 쪼개도 45곳 초과면 받을 수 있는 만큼만 받고 truncated 로 기록(누락을 숨기지 않음).
export async function sweepKeyword({ query, rect, fetchPage, depth = 0, maxDepth = 7 }) {
  const first = await fetchPage({ query, rect, page: 1 });
  const total = first.meta?.total_count ?? 0;
  if (total > PAGE_CAP && depth < maxDepth) {
    const out = { docs: [], calls: 1, truncated: [] };
    for (const sub of splitRect(rect)) {
      const r = await sweepKeyword({ query, rect: sub, fetchPage, depth: depth + 1, maxDepth });
      out.docs.push(...r.docs); out.calls += r.calls; out.truncated.push(...r.truncated);
    }
    return out;
  }
  const docs = [...(first.documents || [])]; let calls = 1;
  for (let page = 2, end = first.meta?.is_end; !end && page <= 3; page++) {
    const p = await fetchPage({ query, rect, page }); calls++;
    docs.push(...(p.documents || [])); end = p.meta?.is_end;
  }
  const truncated = total > PAGE_CAP ? [{ query, rect, total }] : [];
  return { docs, calls, truncated };
}

const COMBAT_CATEGORY = /격투기|복싱|권투|주짓수|무에타이|킥복싱|유도|레슬링|종합격투/;
const EXCLUDED_CATEGORY = /태권도|합기도|검도/;
const COMBAT_NAME = /주짓수|주지츠|MMA|엠엠에이|종합격투|격투|복싱|킥복싱|무에타이|레슬링|유도|BJJ|파이트|fight|탑팀|top ?team/i;

// 격투기 체육관 판정(순수): 업종이 격투 계열이면 통과, 일반 체육관·헬스·학원은 이름에 격투 단어가 있을 때만.
// 태권도·합기도·검도 업종은 이름에 MMA 가 있어도 제외(예: 'MMA남부태권도&줄넘기클럽').
export function isCombatGym(doc) {
  const cat = doc.category_name || ''; const name = doc.place_name || '';
  if (EXCLUDED_CATEGORY.test(cat)) return false;
  if (COMBAT_CATEGORY.test(cat)) return true;
  return /^(스포츠,레저|교육,학문)/.test(cat) && COMBAT_NAME.test(name);
}

export function toLead(doc) {
  const address = doc.road_address_name || doc.address_name || '';
  const num = (v) => (v == null || v === '' || Number.isNaN(Number(v)) ? null : Number(v));
  return {
    id: String(doc.id),
    name: doc.place_name,
    category: (doc.category_name || '').split(' > ').slice(-2).join(' > '),
    district: (doc.address_name || address).split(' ')[1] || '',
    address,
    url: (doc.place_url || `https://place.map.kakao.com/${doc.id}`).replace(/^http:/, 'https:'),
    lon: num(doc.x), // 카카오가 주는 장소 좌표(WGS84) — 지도 시각화용(업체 위치 = 공개 정보)
    lat: num(doc.y),
  };
}

// 지역 전체를 키워드별로 쓸어 담아 격투기 체육관만, 주소 접두사(예: '서울')로 지역 확정, 장소 id 로 중복 제거.
export async function collectLeads({ key, region, keywords, transport, fetchPage }) {
  const fp = fetchPage || ((a) => kakaoKeywordPage({ ...a, key, transport }));
  const byId = new Map(); let calls = 0; const truncated = [];
  for (const query of keywords) {
    const r = await sweepKeyword({ query, rect: region.rect, fetchPage: fp });
    calls += r.calls; truncated.push(...r.truncated);
    for (const d of r.docs) {
      if (!(d.address_name || '').startsWith(region.addressPrefix)) continue;
      if (!isCombatGym(d)) continue;
      if (!byId.has(String(d.id))) byId.set(String(d.id), toLead(d));
    }
  }
  return { leads: [...byId.values()], calls, truncated };
}

const csvCell = (s) => `"${String(s ?? '').replace(/"/g, '""')}"`;
const cell = (s) => String(s ?? '').replace(/\|/g, '/').replace(/\s+/g, ' ').trim();

// 수집 → 누적 저장(state/sales-leads.json: 처음 발견·마지막 확인) → CSV 내보내기 → context '리드 인벤토리'.
// interests: 앱 회원의 교류 요청(connectors/gym-interests.mjs, placeId → {name, count, gyms}) —
//   요청 받은 체육관을 '교류 요청 우선 리드' 표로 맨 위에, 인벤토리에서도 먼저 정렬(영업 연락 1순위).
export async function buildLeadsContext({ opsRoot, agent = 'sales', key, region, keywords, transport, fetchPage, interests = new Map(), now = () => new Date() }) {
  const at = now().toISOString();
  const { leads, calls, truncated } = await collectLeads({ key, region, keywords, transport, fetchPage });

  const storePath = join(opsRoot, 'state', `${agent}-leads.json`);
  const store = existsSync(storePath) ? JSON.parse(readFileSync(storePath, 'utf8')) : { leads: {} };
  const seenNow = new Set();
  const fresh = [];
  for (const l of leads) {
    seenNow.add(l.id);
    const prev = store.leads[l.id];
    if (!prev) fresh.push(l.id);
    store.leads[l.id] = { ...l, firstSeen: prev?.firstSeen || at, lastSeen: at };
  }
  const missing = Object.keys(store.leads).filter((id) => !seenNow.has(id));
  store.updatedAt = at; store.region = region.name;
  if (!existsSync(join(opsRoot, 'state'))) mkdirSync(join(opsRoot, 'state'), { recursive: true });
  writeFileSync(storePath, JSON.stringify(store, null, 2));

  // 이번에 확인된 리드: 교류 요청 많은 순 → 신규 → 구·이름순.
  const isNew = new Set(fresh);
  const reqs = (id) => interests.get(id)?.count || 0;
  const current = leads.map((l) => store.leads[l.id])
    .sort((a, b) => (reqs(b.id) - reqs(a.id)) || (isNew.has(b.id) - isNew.has(a.id)) ||
      a.district.localeCompare(b.district, 'ko') || a.name.localeCompare(b.name, 'ko'));

  const exportDir = join(opsRoot, 'exports');
  if (!existsSync(exportDir)) mkdirSync(exportDir, { recursive: true });
  const csvName = `${agent}-leads-${region.slug || 'region'}.csv`;
  const csv = ['구,체육관,업종,주소,카카오맵,경도,위도,신규,처음 발견,마지막 확인,교류요청',
    ...current.map((l) => [l.district, l.name, l.category, l.address, l.url, l.lon ?? '', l.lat ?? '', isNew.has(l.id) ? 'Y' : '', l.firstSeen.slice(0, 10), l.lastSeen.slice(0, 10), reqs(l.id) || ''].map(csvCell).join(','))];
  writeFileSync(join(exportDir, csvName), '﻿' + csv.join('\r\n') + '\r\n'); // BOM: 엑셀 한글 깨짐 방지

  const byDistrict = new Map();
  for (const l of current) byDistrict.set(l.district, (byDistrict.get(l.district) || 0) + 1);
  const districts = [...byDistrict].sort((a, b) => b[1] - a[1]).map(([d, n]) => `${d} ${n}`).join(' · ');
  const notes = [
    `카카오 로컬 장소 검색에서 결정적 추출 — **${region.name} 격투기 체육관 ${current.length}곳 · 이번 신규 ${fresh.length}곳**${missing.length ? ` · 이번에 안 잡힌 기존 ${missing.length}곳(폐업·명칭 변경 가능)` : ''}.`,
    `키워드: ${keywords.join(', ')} · API 호출 ${calls}회 · 연락처 미수집(카카오맵 링크만) · 전체 목록 CSV: exports/${csvName}`,
    `구별: ${districts || '-'}`,
  ];
  if (truncated.length) notes.push(`⚠ 구역을 끝까지 나눠도 45곳 상한에 걸린 검색 ${truncated.length}건 — 일부 누락 가능(${truncated.map((t) => `${t.query} ${t.total}곳`).slice(0, 5).join(', ')})`);
  const rows = current.map((l) => `| ${isNew.has(l.id) ? '🆕' : ''} | ${reqs(l.id) ? `🤝 ${reqs(l.id)}` : ''} | ${cell(l.district)} | ${cell(l.name)} | ${cell(l.category)} | [카카오맵](${l.url}) |`);
  const inventory = current.length
    ? `${notes.join('\n')}\n\n| 신규 | 교류 요청 | 구 | 체육관 | 업종 | 링크 |\n|---|---|---|---|---|---|\n${rows.join('\n')}`
    : `(확인된 체육관 리드 없음 — 카카오 로컬 결과에 ${region.name} 격투기 업종이 없음)`;
  // 교류 요청 우선 리드 — 서울 리드 밖(경계 너머) 체육관도 요청이 있으면 포함.
  const wanted = [...interests.values()].sort((a, b) => (b.count - a.count) || (b.gyms - a.gyms));
  const priority = wanted.length
    ? `**교류 요청 우선 리드 ${wanted.length}곳 · 요청 ${wanted.reduce((n, w) => n + w.count, 0)}건** — 앱 회원이 교류전을 원한다고 남긴 체육관(요청 많은 순). 영업 연락 1순위.\n\n` +
      `| 순위 | 체육관 | 요청 | 요청 체육관 | 링크 |\n|---|---|---|---|---|\n` +
      wanted.map((w, i) => `| ${i + 1} | ${cell(store.leads[w.placeId]?.name || w.name)} | ${w.count} | ${w.gyms} | [카카오맵](https://place.map.kakao.com/${w.placeId}) |`).join('\n') + '\n\n'
    : '';
  writeContextSection({ opsRoot, agent, section: '리드 인벤토리', body: priority + inventory, now });
  return { total: current.length, fresh: fresh.length, missing: missing.length, calls, truncated: truncated.length, csv: join(exportDir, csvName) };
}
