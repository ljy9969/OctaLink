import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseDdg, buildResearchContext, search, extractApps } from '../../connectors/websearch.mjs';

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
