import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseDdg, buildResearchContext, search, extractApps, extractLeads } from '../../connectors/websearch.mjs';

test('extractApps: 스토어 링크만 추출·중복제거·카테고리 분류 (LLM 없음)', () => {
  const results = [
    { title: 'Gym Admin - Easy management - Apps on Google Play', url: 'https://play.google.com/store/apps/details?id=com.HF.GymAdmin', snippet: 'all-in-one management for gym, martial-arts' },
    { title: 'Gym Admin 중복', url: 'https://play.google.com/store/apps/details?id=com.HF.GymAdmin&hl=ko', snippet: '중복이라 무시돼야 함' },
    { title: 'MMA Legacy - Google Play 앱', url: 'https://play.google.com/store/apps/details?id=com.mmalegacy.game&hl=ko', snippet: '선수 육성 게임' },
    { title: '나무위키 종합격투기', url: 'https://namu.wiki/w/x', snippet: '앱 아님 → 제외' },
    { title: 'Lion Fighters - MMA', url: 'https://play.google.com/store/apps/details?id=digifit.android.virtuagym.pro.lionfighters&hl=ko', snippet: '격투 훈련 예약' },
  ];
  const apps = extractApps(results);
  assert.equal(apps.length, 3);                 // 중복 1개 제거, 나무위키 제외
  assert.equal(apps.find((a) => a.id === 'com.mmalegacy.game').category, '게임');
  assert.equal(apps.find((a) => a.id === 'com.HF.GymAdmin').category, '체육관 관리SW');
  assert.equal(apps.find((a) => a.id.includes('lionfighters')).category, '훈련/콘텐츠');
  assert.equal(apps[0].name, 'Gym Admin - Easy management'); // 스토어 접미사만 제거, 앱 이름은 보존
  assert.ok(!apps.some((a) => /hl=/.test(a.url))); // hl 파라미터 제거
});


test('search: Google CSE(cx+key) 우선 사용', async () => {
  let url;
  const transport = async (u) => { url = u; return { ok: true, status: 200, async json() { return { items: [{ title: '정찬성 체육관', link: 'https://youtube.com/@kz', snippet: 'MMA' }] }; }, async text() { return ''; } }; };
  const r = await search('q', { transport, env: { GOOGLE_CSE_ID: 'cx1', GOOGLE_API_KEY: 'k1' } });
  assert.match(url, /customsearch\/v1/);
  assert.match(url, /cx=cx1/);
  assert.equal(r[0].url, 'https://youtube.com/@kz');
});

test('search: SEARXNG_URL 있으면 self-host 최우선(트레일링 슬래시 제거)', async () => {
  let url;
  const transport = async (u) => { url = u; return { ok: true, status: 200, async json() { return { results: [{ title: '로컬', url: 'https://x', content: 'y' }] }; }, async text() { return ''; } }; };
  const r = await search('q', { transport, env: { SEARXNG_URL: 'http://localhost:8888/' } });
  assert.match(url, /localhost:8888\/search\?format=json/);
  assert.equal(r[0].url, 'https://x');
});

test('search: DDG 차단(202) 시 SearXNG 폴백', async () => {
  const transport = async (url) => {
    if (url.includes('duckduckgo')) return { ok: true, status: 202, async text() { return '<html>anomaly</html>'; } };
    if (url.includes('format=json')) return { ok: true, status: 200, async json() { return { results: [{ title: '선수 체육관', url: 'https://youtube.com/@x', content: '채널' }] }; } };
    return { ok: false, status: 404, async text() { return ''; } };
  };
  const r = await search('q', { transport, env: {} });
  assert.equal(r.length, 1);
  assert.equal(r[0].url, 'https://youtube.com/@x');
});

test('parseDdg: result__a uddg 디코드 + 스니펫', () => {
  const html = `
    <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dbr.com.fitblox&rut=x">Fitblox &amp; Gym</a>
    <a class="result__snippet" href="#">격투 체육관 관리 앱</a>`;
  const r = parseDdg(html);
  assert.equal(r.length, 1);
  assert.equal(r[0].url, 'https://play.google.com/store/apps/details?id=br.com.fitblox');
  assert.equal(r[0].title, 'Fitblox & Gym');
  assert.match(r[0].snippet, /체육관 관리/);
});

test('buildResearchContext: 주입 searchFn → context/research.md에 출처 링크', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ws-'));
  const searchFn = async (q) => [{ title: 'Membo', url: 'https://play.google.com/store/apps/details?id=com.membo.app', snippet: 'MMA 아카데미' }];
  const n = await buildResearchContext({ opsRoot: ops, queries: ['MMA gym app'], searchFn, now: () => new Date('2026-09-08T00:00:00Z') });
  assert.equal(n, 1);
  const md = readFileSync(join(ops, 'context', 'research.md'), 'utf8');
  assert.match(md, /\[Membo\]\(https:\/\/play\.google\.com/);
  assert.match(md, /MMA gym app/);
});

// 실제 SearXNG 결과(2026-10-16 probe)에서 가져온 형태 그대로.
const LEAD_RESULTS = [
  { title: '팀유짐 영등포점 (@ydp_ugym) - Instagram', url: 'https://www.instagram.com/ydp_ugym/', query: '복싱 킥복싱 체육관 site:instagram.com' },
  { title: '정진체육관 (@jungjingym) • Instagram photos and videos', url: 'https://www.instagram.com/jungjingym/', query: 'q' },
  { title: '랑 무에타이 체육관 (서울대입구역점) (@rang_muaythai) - Instagram', url: 'https://www.instagram.com/rang_muaythai/', query: 'q' },
  { title: '정진체육관 중복', url: 'https://instagram.com/JungJinGym', query: 'q' },                 // 대소문자·www 다른 중복
  { title: 'ALLSTADIUM 올스타디움 본점 (@allstadium_korea) - Instagram', url: 'https://www.instagram.com/allstadium_korea/' }, // 격투 단어 없음
  { title: 'MMA 하이라이트 게시물', url: 'https://www.instagram.com/p/Cxyz123/' },               // 게시물(계정 아님)
  { title: 'Korean Top Team | Gym Page - Tapology', url: 'https://www.tapology.com/gyms/4-korean-top-team' },
  { title: 'South Korea MMA Gyms - Tapology', url: 'https://www.tapology.com/gyms/country/kr' },   // 디렉터리
  { title: '홍대 주짓수 아카데미 | 카카오맵', url: 'https://place.map.kakao.com/123456789' },
  { title: '강남 MMA 짐 : 네이버 플레이스', url: 'https://m.place.naver.com/place/987654321/home?entry=pll' },
  { title: '네이버 지도', url: 'https://map.naver.com/p/' },                                       // 장소 아님
  { title: '결의 다지는 종합격투기 부산 팀매드 - 연합뉴스', url: 'https://www.yna.co.kr/view/PYH20161025171200051' }, // 뉴스
  { title: 'MMA Fighting', url: 'https://www.mmafighting.com/' },                                   // 일반 웹
  { title: '종합격투기 - 나무위키', url: 'https://namu.wiki/w/x' },
];

test('extractLeads: 인스타 계정·Tapology 체육관·지도 장소만, 이름에 격투/체육관 단어, 중복 제거', () => {
  const leads = extractLeads(LEAD_RESULTS);
  assert.deepEqual(leads.map((l) => l.name), [
    '팀유짐 영등포점', '정진체육관', '랑 무에타이 체육관 (서울대입구역점)', 'Korean Top Team', '홍대 주짓수 아카데미', '강남 MMA 짐',
  ]);
  assert.deepEqual(leads.map((l) => l.source), [
    '인스타그램 @ydp_ugym', '인스타그램 @jungjingym', '인스타그램 @rang_muaythai', 'Tapology', '카카오맵', '네이버 지도',
  ]);
  assert.equal(leads[0].url, 'https://www.instagram.com/ydp_ugym/');
  assert.equal(leads[0].query, '복싱 킥복싱 체육관 site:instagram.com');            // 찾은 검색어 보존
  assert.equal(leads[5].url, 'https://m.place.naver.com/place/987654321/home');      // 쿼리스트링 제거
});

test('buildResearchContext: sales 는 리드 인벤토리, research 는 기존 앱 인벤토리 유지', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ws-sales-'));
  const searchFn = async () => LEAD_RESULTS;
  const now = () => new Date('2026-10-16T02:00:00Z');
  await buildResearchContext({ opsRoot: ops, agent: 'sales', queries: ['q1'], searchFn, delayMs: 0, now });
  const sales = readFileSync(join(ops, 'context', 'sales.md'), 'utf8');
  assert.match(sales, /<!-- sec:리드 인벤토리 -->/);
  assert.match(sales, /총 6곳/);
  assert.match(sales, /\| 팀유짐 영등포점 \| 인스타그램 @ydp_ugym \| \[열기\]\(https:\/\/www\.instagram\.com\/ydp_ugym\/\) \| q1 \|/);
  assert.doesNotMatch(sales, /sec:앱 인벤토리/);
  await buildResearchContext({ opsRoot: ops, agent: 'research', queries: ['q1'], searchFn, delayMs: 0, now });
  assert.match(readFileSync(join(ops, 'context', 'research.md'), 'utf8'), /<!-- sec:앱 인벤토리 -->/);
});

test('buildResearchContext: sales 리드 0건이면 이유를 적은 빈 인벤토리', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-ws-sales0-'));
  await buildResearchContext({ opsRoot: ops, agent: 'sales', queries: ['q'], searchFn: async () => [LEAD_RESULTS[11]], delayMs: 0 });
  assert.match(readFileSync(join(ops, 'context', 'sales.md'), 'utf8'), /확인된 체육관 리드 없음/);
});
