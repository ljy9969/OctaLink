import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { isCombatGym, toLead, sweepKeyword, collectLeads, buildLeadsContext } from '../../connectors/kakao-local.mjs';
import { parseContextSection } from '../../connectors/context-file.mjs';

// 실제 응답(2026-10-16 probe)의 업종 분류 형태 그대로.
const doc = (id, name, category, address) => ({
  id, place_name: name, category_name: `스포츠,레저 > ${category}`, address_name: address,
  road_address_name: `${address} 도로명`, place_url: `http://place.map.kakao.com/${id}`, phone: '02-000-0000', x: '126.9', y: '37.5',
});

test('isCombatGym: 격투 업종 통과, 일반 체육관·헬스는 이름에 격투 단어 있을 때만, 태권도·합기도·검도 제외', () => {
  assert.equal(isCombatGym(doc(1, '바디킥짐', '무예 > 격투기 > 무에타이', '서울 강서구 x')), true);
  assert.equal(isCombatGym(doc(2, '어떤복싱', '복싱,권투 > 복싱,권투장', '서울 x')), true);
  assert.equal(isCombatGym(doc(3, '한판유도장', '무예 > 유도 > 유도장', '서울 x')), true);
  assert.equal(isCombatGym(doc(4, '런주짓수체육관', '스포츠시설 > 체육관', '서울 x')), true);   // 이름에 주짓수
  assert.equal(isCombatGym(doc(5, '코리안탑팀 다운짐', '스포츠시설 > 체육관', '서울 x')), true); // 탑팀
  assert.equal(isCombatGym(doc(6, '동네헬스', '스포츠시설 > 헬스클럽', '서울 x')), false);
  assert.equal(isCombatGym(doc(7, 'MMA남부태권도&줄넘기클럽', '무예 > 태권도 > 태권도장', '서울 x')), false);
  assert.equal(isCombatGym(doc(8, '합기도장', '무예 > 합기도 > 합기도장', '서울 x')), false);
  assert.equal(isCombatGym({ id: 9, place_name: 'MMA 의료기', category_name: '제조업 > 의료용품', address_name: '서울' }), false);
  assert.equal(isCombatGym({ id: 10, place_name: '강남복싱학원', category_name: '교육,학문 > 학원 > 예체능학원', address_name: '서울' }), true);
});

test('toLead: 전화번호 미포함, 구·업종(끝 2단계)·https 링크', () => {
  const l = toLead(doc(330735267, '바디킥짐', '무예 > 격투기 > 무에타이', '서울 강서구 등촌동 1'));
  assert.deepEqual(l, { id: '330735267', name: '바디킥짐', category: '격투기 > 무에타이', district: '강서구',
    address: '서울 강서구 등촌동 1 도로명', url: 'https://place.map.kakao.com/330735267', lon: 126.9, lat: 37.5 });
  assert.equal('phone' in l, false);
});

// 가짜 API: 루트 사각형에 100곳 → 45 초과라 4등분, 각 하위 구역은 25곳(2페이지).
function fakeApi() {
  const calls = [];
  const fetchPage = async ({ query, rect, page }) => {
    calls.push({ query, rect, page });
    const isRoot = rect === '0,0,4,4';
    const total = isRoot ? 100 : 25;
    const docs = isRoot ? [] : Array.from({ length: page === 1 ? 15 : 10 }, (_, i) =>
      doc(`${rect}-${page}-${i}`, `${query}짐${i}`, '무예 > 격투기', i % 5 === 0 ? '경기 성남시 x' : '서울 마포구 x'));
    return { meta: { total_count: total, is_end: isRoot ? false : page === 2 }, documents: docs };
  };
  return { fetchPage, calls };
}

test('sweepKeyword: 45곳 초과 구역은 4등분 재귀, 하위 구역은 페이지 끝까지', async () => {
  const { fetchPage, calls } = fakeApi();
  const r = await sweepKeyword({ query: '주짓수', rect: '0,0,4,4', fetchPage });
  assert.equal(r.docs.length, 4 * 25);
  assert.equal(r.calls, 1 + 4 * 2);
  assert.deepEqual(r.truncated, []);
  assert.ok(calls.some((c) => c.rect === '0.000000,0.000000,2.000000,2.000000'));
});

test('sweepKeyword: 최대 깊이에서도 45곳 초과면 받을 만큼 받고 누락 가능성을 기록', async () => {
  const fetchPage = async ({ page }) => ({ meta: { total_count: 500, is_end: page === 3 }, documents: [doc(`x${page}`, '격투짐', '무예 > 격투기', '서울 x')] });
  const r = await sweepKeyword({ query: '복싱', rect: '0,0,1,1', fetchPage, maxDepth: 0 });
  assert.equal(r.docs.length, 3);
  assert.deepEqual(r.truncated, [{ query: '복싱', rect: '0,0,1,1', total: 500 }]);
});

test('collectLeads: 주소 접두사로 지역 확정, 키워드 간 중복 id 제거', async () => {
  const { fetchPage } = fakeApi();
  const r = await collectLeads({ region: { rect: '0,0,4,4', addressPrefix: '서울' }, keywords: ['주짓수', '주짓수'], fetchPage });
  assert.equal(r.leads.length, 80);                 // 100 중 경기도(20) 제외, 두 번째 키워드는 같은 id라 중복
  assert.ok(r.leads.every((l) => l.address.startsWith('서울')));
});

test('buildLeadsContext: 누적 저장 → 2회차에 신규·미확인 표시, CSV(BOM)·context 표', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-leads-'));
  const region = { name: '서울', slug: 'seoul', addressPrefix: '서울', rect: '0,0,1,1' };
  const pageOf = (docs) => async () => ({ meta: { total_count: docs.length, is_end: true }, documents: docs });
  const a = doc(1, '런주짓수', '무예 > 격투기', '서울 강서구 x');
  const b = doc(2, '바디킥짐', '무예 > 격투기 > 무에타이', '서울 강서구 y');
  const c = doc(3, '새복싱', '복싱,권투 > 복싱,권투장', '서울 마포구 z');

  const r1 = await buildLeadsContext({ opsRoot: ops, region, keywords: ['격투'], fetchPage: pageOf([a, b]), now: () => new Date('2026-10-16T02:00:00Z') });
  assert.deepEqual([r1.total, r1.fresh, r1.missing], [2, 2, 0]);

  const r2 = await buildLeadsContext({ opsRoot: ops, region, keywords: ['격투'], fetchPage: pageOf([b, c]), now: () => new Date('2026-10-23T02:00:00Z') });
  assert.deepEqual([r2.total, r2.fresh, r2.missing], [2, 1, 1]);   // c 신규, a 이번에 안 잡힘

  const store = JSON.parse(readFileSync(join(ops, 'state', 'sales-leads.json'), 'utf8'));
  assert.equal(store.leads['2'].firstSeen, '2026-10-16T02:00:00.000Z');  // 처음 발견일 유지
  assert.equal(store.leads['2'].lastSeen, '2026-10-23T02:00:00.000Z');
  assert.equal(store.leads['1'].lastSeen, '2026-10-16T02:00:00.000Z');   // 미확인은 그대로 보존

  const csv = readFileSync(join(ops, 'exports', 'sales-leads-seoul.csv'), 'utf8');
  assert.equal(csv.charCodeAt(0), 0xFEFF);
  assert.match(csv, /"마포구","새복싱","복싱,권투 > 복싱,권투장",.*"126.9","37.5","Y"/);
  assert.doesNotMatch(csv, /02-000-0000/);                               // 전화번호 미수집

  const sec = parseContextSection(readFileSync(join(ops, 'context', 'sales.md'), 'utf8'), '리드 인벤토리');
  assert.match(sec.body, /서울 격투기 체육관 2곳 · 이번 신규 1곳\*\* · 이번에 안 잡힌 기존 1곳/);
  const rows = sec.body.split('\n').filter((l) => l.startsWith('| '));     // rows[0]=머리글(구분선은 '|-'라 제외)
  assert.match(rows[1], /^\| 🆕 \| +\| 마포구 \| 새복싱 \|/);              // 신규가 첫 데이터 행(교류 요청 칸은 비어 있음)
  assert.equal(existsSync(r2.csv), true);
});

test('buildLeadsContext: 교류 요청(앱 회원) 받은 체육관을 맨 위로 — 우선 리드 표·요청 수 칸·CSV 열', async () => {
  const ops = mkdtempSync(join(tmpdir(), 'ops-leads-int-'));
  const region = { name: '서울', slug: 'seoul', addressPrefix: '서울', rect: '0,0,1,1' };
  const pageOf = (docs) => async () => ({ meta: { total_count: docs.length, is_end: true }, documents: docs });
  const a = doc(1, '런주짓수', '무예 > 격투기', '서울 강서구 x');
  const b = doc(2, '바디킥짐', '무예 > 격투기 > 무에타이', '서울 강서구 y');
  const interests = new Map([
    ['2', { placeId: '2', name: '바디킥짐', count: 3, gyms: 2 }],
    ['77', { placeId: '77', name: '경기 MMA', count: 1, gyms: 1 }],   // 서울 리드 밖(경기) 요청도 우선 리드에 표시
  ]);
  await buildLeadsContext({ opsRoot: ops, region, keywords: ['격투'], fetchPage: pageOf([a, b]), interests, now: () => new Date('2026-10-30T02:00:00Z') });

  const sec = parseContextSection(readFileSync(join(ops, 'context', 'sales.md'), 'utf8'), '리드 인벤토리');
  assert.match(sec.body, /교류 요청 우선 리드 2곳 · 요청 4건/);
  assert.match(sec.body, /\| 1 \| 바디킥짐 \| 3 \| 2 \| \[카카오맵\]\(https:\/\/place\.map\.kakao\.com\/2\) \|/);
  assert.match(sec.body, /\| 2 \| 경기 MMA \| 1 \| 1 \|/);
  const inv = sec.body.slice(sec.body.indexOf('| 신규 |')).split('\n').filter((l) => l.startsWith('| '));
  assert.match(inv[1], /^\| 🆕 \| 🤝 3 \| 강서구 \| 바디킥짐 \|/);         // 요청 받은 리드가 신규·구 순서보다 먼저
  assert.match(inv[2], /^\| 🆕 \| +\| 강서구 \| 런주짓수 \|/);
  const csv = readFileSync(join(ops, 'exports', 'sales-leads-seoul.csv'), 'utf8');
  assert.match(csv.split('\r\n')[0], /,교류요청$/);
  assert.match(csv, /"바디킥짐".*,"3"\r\n/);
});
