// sales 리드 → 카카오맵 위 체육관 지도(로컬 페이지). exports/gym-map-<지역>.html 생성.
// 카카오 지도 JS SDK 는 등록된 도메인에서만 동작 → `node engine/serve-exports.mjs` 로 http://127.0.0.1:8790 에서 연다(카카오 Web 도메인에 등록된 주소. localhost 는 다른 출처라 거부됨).
// 키: KAKAO_JS_KEY(카카오 개발자 앱의 JavaScript 키, 공개용·도메인 제한). 페이지는 gitignore 폴더에만 쓴다.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const SPORT_GROUPS = ['복싱', '주짓수·유도', 'MMA·킥복싱·무에타이'];

// 대표 종목 하나(이름 우선): MMA·종합격투 → 주짓수·유도 → 킥복싱·무에타이 → 복싱 → 그 외 격투기.
export function sportGroup({ name = '', category = '' }) {
  if (/MMA|엠엠에이|종합격투|파이트|fight/i.test(name)) return 2;
  if (/주짓수|주지츠|BJJ|jiu|유도/i.test(name) || /주짓수|유도/.test(category)) return 1;
  if (/킥복싱|무에타이|muay|kick/i.test(name) || /무에타이/.test(category)) return 2;
  if (/복싱|권투/.test(category) || /복싱|boxing/i.test(name)) return 0;
  return 2;
}

// 페이지에 넣을 데이터: [이름, 구, 종목, 경도, 위도, 장소id, 업종, 주소] — 좌표 없는 건 제외.
export function mapRows(leads) {
  const r6 = (v) => Math.round(v * 1e6) / 1e6;
  return leads.filter((l) => Number.isFinite(l.lon) && Number.isFinite(l.lat))
    .map((l) => [l.name, l.district, sportGroup(l), r6(l.lon), r6(l.lat), l.id, l.category, l.address]);
}

export function renderMapHtml({ rows, jsKey, regionName = '서울', collectedAt = '' }) {
  const data = JSON.stringify({ rows, groups: SPORT_GROUPS, collectedAt }).replace(/</g, '\\u003c');
  const key = encodeURIComponent(jsKey);
  return `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${regionName} 격투기 체육관 지도</title>
<style>
  /* 레이아웃: 왼쪽 패널(제목·필터·목록) + 오른쪽 카카오맵. 좁으면 지도 위, 목록 아래. 지도 배경이 밝아 밝은 테마로 고정. */
  :root {
    color-scheme: light;
    --page: #f9f9f7; --surface: #fcfcfb; --ink: #0b0b0b; --ink-2: #52514e; --muted: #898781;
    --line: #e1e0d9; --ring: rgba(11,11,11,0.10); --chip-off: #f0efec;
    --g0: #2a78d6; --g1: #eb6834; --g2: #1baf7a; /* 검증된 범주색 1~3 */
    --sans: system-ui, -apple-system, "Segoe UI", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif;
  }
  * { box-sizing: border-box; }
  [hidden] { display: none !important; }
  html, body { height: 100%; margin: 0; }
  body { font-family: var(--sans); font-size: 14px; color: var(--ink); background: var(--page); }
  .app { display: grid; grid-template-columns: 360px 1fr; height: 100%; }
  @media (max-width: 820px) { .app { grid-template-columns: 1fr; grid-template-rows: 62vh auto; } .panel { order: 2; } }
  .panel { display: flex; flex-direction: column; min-height: 0; border-right: 1px solid var(--line); background: var(--surface); }
  .head { padding: 18px 18px 10px; border-bottom: 1px solid var(--line); }
  h1 { font-size: 20px; margin: 0 0 2px; letter-spacing: -0.01em; }
  .sub { color: var(--ink-2); font-size: 12.5px; }
  .filters { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
  .chip { display: inline-flex; align-items: center; gap: 7px; border: 1px solid var(--ring); background: var(--surface);
    color: var(--ink); border-radius: 999px; padding: 5px 11px 5px 9px; font: inherit; font-size: 13px; cursor: pointer; }
  .chip[aria-pressed="false"] { background: var(--chip-off); color: var(--muted); }
  .chip[aria-pressed="false"] .sw { opacity: 0.25; }
  .sw { width: 10px; height: 10px; border-radius: 50%; border: 2px solid #fff; box-shadow: 0 0 0 1px var(--ring); flex: none; }
  .n { color: var(--ink-2); font-variant-numeric: tabular-nums; }
  .row2 { display: flex; gap: 6px; margin-top: 8px; }
  select, input[type=search] { font: inherit; font-size: 13px; border: 1px solid var(--ring); border-radius: 8px; padding: 6px 9px; background: var(--surface); color: var(--ink); min-width: 0; }
  select { flex: 0 0 120px; } input[type=search] { flex: 1; }
  .count { color: var(--ink-2); font-size: 12.5px; margin-top: 8px; }
  .list { overflow-y: auto; flex: 1; min-height: 0; }
  .item { display: flex; gap: 10px; align-items: flex-start; width: 100%; text-align: left; background: none; border: 0; border-bottom: 1px solid var(--line);
    padding: 9px 18px; font: inherit; color: inherit; cursor: pointer; }
  .item:hover, .item:focus-visible { background: var(--chip-off); outline: none; }
  .item .sw { margin-top: 4px; }
  .item b { display: block; font-weight: 600; }
  .item span { color: var(--ink-2); font-size: 12.5px; }
  .chip:focus-visible, select:focus-visible, input:focus-visible { outline: 2px solid var(--ink); outline-offset: 1px; }
  #map { width: 100%; height: 100%; min-height: 320px; }
  .card { background: var(--surface); border: 1px solid var(--ring); border-radius: 10px; padding: 10px 12px 10px; box-shadow: 0 6px 18px rgba(0,0,0,0.18);
    font-size: 12.5px; min-width: 220px; max-width: 280px; position: relative; bottom: 16px; }
  .card b { font-size: 14px; display: block; padding-right: 18px; }
  .card .meta { color: var(--ink-2); margin-top: 2px; }
  .card .addr { color: var(--muted); margin-top: 4px; }
  .card a { display: inline-block; margin-top: 6px; color: var(--g0); font-weight: 600; }
  .card .x { position: absolute; top: 6px; right: 8px; border: 0; background: none; font-size: 16px; color: var(--muted); cursor: pointer; }
  .err { padding: 24px; color: #b42318; line-height: 1.6; }
</style>
</head>
<body>
<div class="app">
  <aside class="panel">
    <div class="head">
      <h1>${regionName} 격투기 체육관 지도</h1>
      <div class="sub" id="sub"></div>
      <div class="filters" id="chips" role="group" aria-label="종목 필터"></div>
      <div class="row2">
        <select id="dist" aria-label="구 선택"><option value="">구 전체</option></select>
        <input type="search" id="q" placeholder="체육관 이름 검색" aria-label="체육관 이름 검색">
      </div>
      <div class="count" id="count"></div>
    </div>
    <div class="list" id="list" role="list"></div>
  </aside>
  <main id="map" aria-label="카카오맵"></main>
</div>
<script type="application/json" id="data">${data}</script>
<script>
  // SDK 로드 실패(도메인 미등록·키 오류) 시 원인을 화면에 표시.
  window.addEventListener('error', function (e) {
    if (e.target && e.target.tagName === 'SCRIPT') {
      const box = document.createElement('div'); box.className = 'err';
      box.textContent = '카카오 지도 SDK를 불러오지 못했습니다. 카카오 개발자 콘솔 → 플랫폼 → Web 사이트 도메인에 '
        + location.origin + ' 이 등록됐는지, JavaScript 키가 맞는지 확인해 주세요.';
      document.getElementById('map').replaceChildren(box);
    }
  }, true);
</script>
<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&libraries=clusterer&autoload=false"></script>
<script>
// 패널(칩·구·검색·목록)은 SDK 와 무관하게 항상 동작 — 지도 SDK 가 실패해도 목록으로 볼 수 있게(표 보기 역할).
(function () {
  const D = JSON.parse(document.getElementById('data').textContent);
  const COLORS = ['#2a78d6', '#eb6834', '#1baf7a'];
  const gyms = D.rows.map(function (r) { return { name: r[0], district: r[1], group: r[2], lon: r[3], lat: r[4], id: r[5], category: r[6], address: r[7] }; });
  const fmt = function (n) { return n.toLocaleString('ko-KR'); };
  const h = function (tag, props, parent) { const n = document.createElement(tag); Object.assign(n, props || {}); if (parent) parent.appendChild(n); return n; };
  document.getElementById('sub').textContent = '카카오 로컬로 모은 ' + fmt(gyms.length) + '곳 · 수집 ' + D.collectedAt + ' · 점=체육관, 숫자 원=근처 체육관 묶음';
  let M = null; // 지도(SDK 로드 후 채움): { show(vis, fit), focus(g) }

  // 필터: 종목 칩 + 구 + 이름 검색 → 지도·목록·개수 함께.
  const on = new Set([0, 1, 2]);
  const chips = document.getElementById('chips');
  D.groups.forEach(function (label, k) {
    const b = h('button', { className: 'chip', type: 'button' }, chips); b.setAttribute('aria-pressed', 'true');
    const sw = h('span', { className: 'sw' }, b); sw.style.background = COLORS[k];
    h('span', { textContent: label }, b);
    h('span', { className: 'n', textContent: fmt(gyms.filter(function (g) { return g.group === k; }).length) }, b);
    b.onclick = function () { if (on.has(k) && on.size === 1) return; on.has(k) ? on.delete(k) : on.add(k); b.setAttribute('aria-pressed', String(on.has(k))); apply(false); };
  });
  const dist = document.getElementById('dist');
  const byDist = {}; gyms.forEach(function (g) { byDist[g.district] = (byDist[g.district] || 0) + 1; });
  Object.keys(byDist).sort(function (a, b) { return byDist[b] - byDist[a]; }).forEach(function (d) { h('option', { value: d, textContent: d + ' (' + byDist[d] + ')' }, dist); });
  dist.onchange = function () { apply(true); };
  const q = document.getElementById('q'); let t; q.oninput = function () { clearTimeout(t); t = setTimeout(function () { apply(false); }, 150); };

  const list = document.getElementById('list');
  function apply(fit) {
    const d = dist.value, s = q.value.trim().toLowerCase();
    const vis = gyms.filter(function (g) { return on.has(g.group) && (!d || g.district === d) && (!s || g.name.toLowerCase().indexOf(s) >= 0); });
    document.getElementById('count').textContent = fmt(vis.length) + '곳 표시 중';
    list.replaceChildren();
    vis.slice().sort(function (a, b) { return a.district.localeCompare(b.district, 'ko') || a.name.localeCompare(b.name, 'ko'); }).forEach(function (g) {
      const it = h('button', { className: 'item', type: 'button' }, list); it.setAttribute('role', 'listitem');
      const sw = h('span', { className: 'sw' }, it); sw.style.background = COLORS[g.group];
      const tx = h('div', {}, it); h('b', { textContent: g.name }, tx); h('span', { textContent: g.district + ' · ' + D.groups[g.group] }, tx);
      // 지도 없으면 카카오맵 장소 페이지로.
      it.onclick = function () { if (M) M.focus(g); else window.open('https://place.map.kakao.com/' + g.id, '_blank', 'noopener'); };
    });
    if (M) M.show(vis, fit);
  }
  apply(false);

  if (window.kakao && kakao.maps) kakao.maps.load(function () { M = initMap(); apply(false); });

  function initMap() {
    const map = new kakao.maps.Map(document.getElementById('map'), { center: new kakao.maps.LatLng(37.5565, 126.9780), level: 8 });
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);

    const img = function (c) {
      const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18"><circle cx="9" cy="9" r="6.5" fill="' + c + '" stroke="#ffffff" stroke-width="2"/></svg>';
      return new kakao.maps.MarkerImage('data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg), new kakao.maps.Size(18, 18), { offset: new kakao.maps.Point(9, 9) });
    };
    const IMGS = COLORS.map(img);
    gyms.forEach(function (g) {
      g.pos = new kakao.maps.LatLng(g.lat, g.lon);
      g.marker = new kakao.maps.Marker({ position: g.pos, image: IMGS[g.group], title: g.name });
      kakao.maps.event.addListener(g.marker, 'click', function () { openCard(g); });
    });

    const clusterer = new kakao.maps.MarkerClusterer({
      map: map, averageCenter: true, minLevel: 6, disableClickZoom: false, gridSize: 60,
      calculator: [10, 30, 80],
      styles: [36, 44, 52, 60].map(function (s) { return {
        width: s + 'px', height: s + 'px', lineHeight: s + 'px', borderRadius: '50%', textAlign: 'center',
        background: 'rgba(11,11,11,0.72)', color: '#fcfcfb', fontWeight: '650', fontSize: '13px', border: '2px solid #fcfcfb',
      }; }),
    });

    // 상세 카드(오버레이) — 이름 등은 textContent 로(외부 데이터).
    const card = new kakao.maps.CustomOverlay({ yAnchor: 1, zIndex: 5 });
    function openCard(g) {
      const box = h('div', { className: 'card' });
      const x = h('button', { className: 'x', type: 'button', textContent: '×' }, box); x.setAttribute('aria-label', '닫기');
      x.onclick = function () { card.setMap(null); };
      h('b', { textContent: g.name }, box);
      h('div', { className: 'meta', textContent: g.district + ' · ' + D.groups[g.group] + ' · ' + g.category }, box);
      h('div', { className: 'addr', textContent: g.address }, box);
      h('a', { href: 'https://place.map.kakao.com/' + g.id, target: '_blank', rel: 'noopener', textContent: '카카오맵에서 보기 →' }, box);
      card.setContent(box); card.setPosition(g.pos); card.setMap(map);
    }

    return {
      show: function (vis, fit) {
        clusterer.clear(); clusterer.addMarkers(vis.map(function (g) { return g.marker; }));
        card.setMap(null);
        if (fit && vis.length) {
          const b = new kakao.maps.LatLngBounds(); vis.forEach(function (g) { b.extend(g.pos); }); map.setBounds(b, 40, 40, 40, 40);
        }
      },
      focus: function (g) { map.setLevel(4); map.panTo(g.pos); openCard(g); },
    };
  }
})();
</script>
</body>
</html>
`;
}

// 저장소(state/<agent>-leads.json) → exports/gym-map-<slug>.html
export function writeLeadsMap({ opsRoot, agent = 'sales', jsKey, region = { name: '서울', slug: 'seoul' } }) {
  if (!jsKey) return null;
  const storePath = join(opsRoot, 'state', `${agent}-leads.json`);
  if (!existsSync(storePath)) return null;
  const store = JSON.parse(readFileSync(storePath, 'utf8'));
  const rows = mapRows(Object.values(store.leads || {}));
  const dir = join(opsRoot, 'exports');
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  const file = join(dir, `gym-map-${region.slug}.html`);
  writeFileSync(file, renderMapHtml({ rows, jsKey, regionName: region.name, collectedAt: (store.updatedAt || '').slice(0, 10) }));
  return { file, count: rows.length };
}

// CLI: node engine/leads-map.mjs  → 지도 생성 후 `node engine/serve-exports.mjs` 로 열기
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const opsRoot = process.env.OPS_ROOT || join(dirname(fileURLToPath(import.meta.url)), '..');
  const { loadEnv } = await import('./env.mjs'); loadEnv(opsRoot);
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8')).leads?.sales;
  const r = writeLeadsMap({ opsRoot, jsKey: process.env.KAKAO_JS_KEY, region: conf?.region });
  console.log(r ? `지도 생성: ${r.file} (${r.count}곳) → node engine/serve-exports.mjs 후 http://127.0.0.1:8790/${r.file.split(/[\\/]/).pop()}` : 'KAKAO_JS_KEY 또는 리드 저장소 없음');
}
