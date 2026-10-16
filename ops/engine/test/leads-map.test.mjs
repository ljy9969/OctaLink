import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { get, createServer } from 'node:http';
import { sportGroup, mapRows, renderMapHtml, writeLeadsMap } from '../leads-map.mjs';
import { safePath, startServer } from '../serve-exports.mjs';

test('sportGroup: 이름 우선 대표 종목 하나', () => {
  assert.equal(sportGroup({ name: '대화 MMA 무에타이 주짓수 방배점', category: '스포츠시설 > 체육관' }), 2); // MMA 우선
  assert.equal(sportGroup({ name: '런주짓수체육관', category: '스포츠시설 > 체육관' }), 1);
  assert.equal(sportGroup({ name: '한판', category: '무예 > 유도 > 유도장' }), 1);
  assert.equal(sportGroup({ name: '바디킥짐', category: '격투기 > 무에타이' }), 2);
  assert.equal(sportGroup({ name: '강남복싱', category: '복싱,권투 > 복싱,권투장' }), 0);
  assert.equal(sportGroup({ name: '어떤짐', category: '무예 > 격투기' }), 2);
});

test('mapRows: 좌표 없는 리드 제외, 행 형식', () => {
  const rows = mapRows([
    { id: '1', name: 'A복싱', district: '마포구', category: '복싱,권투 > 복싱,권투장', address: '서울 마포구', lon: 126.9123456789, lat: 37.5 },
    { id: '2', name: 'B', district: '강남구', category: '무예 > 격투기', address: '서울', lon: null, lat: null },
  ]);
  assert.deepEqual(rows, [['A복싱', '마포구', 0, 126.912346, 37.5, '1', '복싱,권투 > 복싱,권투장', '서울 마포구']]);
});

test('renderMapHtml: 키·SDK(클러스터) 포함, 데이터의 </script> 무력화', () => {
  const html = renderMapHtml({ rows: [['</script><b>x', '구', 0, 127, 37.5, '9', 'c', 'a']], jsKey: 'KEY123', collectedAt: '2026-10-16' });
  assert.match(html, /dapi\.kakao\.com\/v2\/maps\/sdk\.js\?appkey=KEY123&libraries=clusterer&autoload=false/);
  assert.doesNotMatch(html, /"<\/script><b>x"/);
  assert.match(html, /\\u003c\/script>\\u003cb>x/);
});

test('writeLeadsMap: 저장소 → exports/gym-map-<slug>.html, 키 없으면 생성 안 함', () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-map-'));
  mkdirSync(join(ops, 'state'));
  writeFileSync(join(ops, 'state', 'sales-leads.json'), JSON.stringify({ updatedAt: '2026-10-16T00:00:00Z', leads: {
    1: { id: '1', name: 'A복싱', district: '마포구', category: '복싱,권투 > 복싱,권투장', address: '서울', lon: 126.9, lat: 37.5 } } }));
  assert.equal(writeLeadsMap({ opsRoot: ops, jsKey: '' }), null);
  const r = writeLeadsMap({ opsRoot: ops, jsKey: 'K', region: { name: '서울', slug: 'seoul' } });
  assert.equal(r.count, 1);
  assert.match(readFileSync(join(ops, 'exports', 'gym-map-seoul.html'), 'utf8'), /서울 격투기 체육관 지도/);
});

test('safePath: exports 밖(.. · 인코딩된 ..) 차단, 폴더는 index.html', () => {
  const root = join(tmpdir(), 'exp-root');
  assert.equal(safePath(root, '/../secret.txt'), null);
  assert.equal(safePath(root, '/%2e%2e/%2e%2e/mcp/.env'), null);
  assert.equal(safePath(root, '/a/%E0%A4%A'), null); // 잘못된 인코딩
  assert.equal(safePath(root, '/gym-map-seoul.html?x=1'), join(root, 'gym-map-seoul.html'));
  assert.equal(safePath(root, '/'), join(root, 'index.html'));
});

test('startServer: 포트를 다른 프로그램이 쓰면 조용히 넘어가지 않고 실패', async () => {
  const other = createServer(() => {});
  await new Promise((ok) => other.listen(0, '127.0.0.1', ok));
  try {
    await assert.rejects(() => startServer({ root: tmpdir(), port: other.address().port }), /다른 프로그램이 쓰는 중/);
  } finally { other.close(); }
});

test('startServer: exports 파일 서빙, 없는 파일 404, 밖 경로 403', async () => {
  const root = mkdtempSync(join(tmpdir(), 'exp-'));
  writeFileSync(join(root, 'm.html'), '<p>ok</p>');
  const server = await startServer({ root, port: 0 });
  const { port } = server;
  try {
    const ok = await fetch(`http://127.0.0.1:${port}/m.html`);
    assert.equal(ok.status, 200);
    assert.match(ok.headers.get('content-type'), /text\/html/);
    assert.equal(await ok.text(), '<p>ok</p>');
    assert.equal((await fetch(`http://127.0.0.1:${port}/none.html`)).status, 404);
    // fetch 는 URL 의 %2e%2e 를 정규화해 버리므로, 경로를 그대로 보내는 node:http 로 우회 시도 검증.
    const raw = await new Promise((ok, no) => get({ host: '127.0.0.1', port, path: '/%2e%2e/x' }, (r) => { r.resume(); ok(r.statusCode); }).on('error', no));
    assert.equal(raw, 403);
  } finally { server.close(); }
});
