import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseDdg, buildResearchContext, search } from '../../connectors/websearch.mjs';

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
