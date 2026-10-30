// 교류전 지도 — 주변 체육관 검색 순수 로직 테스트. `npm test`(tsc 후 lib 대상 실행).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  isCombatGym, sportGroup, mergePlaces, searchNearbyGyms, TtlCache,
  interestSummary, toggleRequester, NEARBY_KEYWORDS,
} from '../lib/nearbyGyms.js';

const doc = (o) => ({ id: '1', place_name: '강남복싱', category_name: '스포츠,레저 > 복싱,권투 > 복싱,권투장',
  road_address_name: '서울 강남구 강남대로 1', address_name: '서울 강남구 역삼동 1', x: '127.0276', y: '37.4979', distance: '120', ...o });

test('isCombatGym: 격투 업종 통과, 태권도류 제외, 일반 체육관은 이름에 격투 단어 있을 때만', () => {
  assert.equal(isCombatGym(doc({})), true);
  assert.equal(isCombatGym(doc({ place_name: 'MMA남부태권도', category_name: '스포츠,레저 > 무예 > 태권도' })), false);
  assert.equal(isCombatGym(doc({ place_name: '바디 MMA', category_name: '스포츠,레저 > 스포츠시설 > 헬스클럽' })), true);
  assert.equal(isCombatGym(doc({ place_name: '바디짐', category_name: '스포츠,레저 > 스포츠시설 > 헬스클럽' })), false);
});

test('sportGroup: 이름 우선 대표 종목 (0 복싱, 1 주짓수·유도, 2 MMA·킥)', () => {
  assert.equal(sportGroup('대화 MMA 무에타이 주짓수', '체육관'), 2);
  assert.equal(sportGroup('런주짓수체육관', '체육관'), 1);
  assert.equal(sportGroup('한판', '무예 > 유도 > 유도장'), 1);
  assert.equal(sportGroup('바디킥짐', '격투기 > 무에타이'), 2);
  assert.equal(sportGroup('강남복싱', '복싱,권투 > 복싱,권투장'), 0);
  assert.equal(sportGroup('어떤짐', '무예 > 격투기'), 2);
});

test('mergePlaces: id 중복 제거, 비격투·제외 id·좌표 없음 제거, 거리순, 구·주소 정리', () => {
  const out = mergePlaces([
    doc({ id: '9', place_name: '먼복싱', distance: '900' }),
    doc({ id: '1', distance: '120' }),
    doc({ id: '1', distance: '120' }),
    doc({ id: '2', place_name: '헬스장', category_name: '스포츠,레저 > 스포츠시설 > 헬스클럽', distance: '50' }),
    doc({ id: '3', place_name: '우리체육관 복싱', distance: '10' }),
    doc({ id: '4', place_name: '좌표없는복싱', x: '', y: '', distance: '30' }),
  ], '3');
  assert.deepEqual(out.map((p) => p.placeId), ['1', '9']);
  assert.deepEqual(out[0], {
    placeId: '1', name: '강남복싱', category: '복싱,권투 > 복싱,권투장', sportGroup: 0, district: '강남구',
    address: '서울 강남구 강남대로 1', distanceM: 120, lat: 37.4979, lng: 127.0276,
  });
});

test('searchNearbyGyms: 키워드마다 반경·거리순 검색, is_end 까지 최대 3페이지, 복싱만 하는 곳 제외', async () => {
  const urls = [];
  const fetchFn = async (url, init) => {
    urls.push(url);
    assert.equal(init.headers.Authorization, 'KakaoAK K');
    const u = new URL(url);
    const q = u.searchParams.get('query'), page = Number(u.searchParams.get('page'));
    const pages = {
      1: [doc({ id: 'k1', place_name: '바디킥복싱', distance: '100' }), doc({ id: 'x1', place_name: '강남복싱', distance: '50' })],
      2: [doc({ id: 'm2', place_name: '강남 MMA', distance: '200' })],
    };
    const body = q === '복싱'
      ? { documents: pages[page], meta: { is_end: page >= 2 } }
      : { documents: [], meta: { is_end: true } };
    return { ok: true, status: 200, json: async () => body, text: async () => '' };
  };
  const out = await searchNearbyGyms({ lat: 37.5, lng: 127.0, radiusKm: 1, key: 'K', fetchFn });
  // MMA 체육관 앱 — 대표 종목이 복싱(복싱만 하는 곳)은 교류전 대상에서 뺀다. 킥복싱은 남김.
  assert.deepEqual(out.map((p) => p.placeId), ['k1', 'm2']);
  const first = new URL(urls[0]);
  assert.equal(first.origin + first.pathname, 'https://dapi.kakao.com/v2/local/search/keyword.json');
  assert.equal(first.searchParams.get('x'), '127');
  assert.equal(first.searchParams.get('y'), '37.5');
  assert.equal(first.searchParams.get('radius'), '1000');
  assert.equal(first.searchParams.get('sort'), 'distance');
  assert.equal(urls.filter((u) => new URL(u).searchParams.get('query') === '복싱').length, 2);
  assert.equal(urls.length, NEARBY_KEYWORDS.length + 1);
});

test('searchNearbyGyms: 반경은 1·2·3km 만, 카카오 오류는 그대로 실패', async () => {
  await assert.rejects(() => searchNearbyGyms({ lat: 37.5, lng: 127, radiusKm: 5, key: 'K', fetchFn: async () => ({}) }), /반경/);
  const bad = async () => ({ ok: false, status: 401, json: async () => ({}), text: async () => 'denied' });
  await assert.rejects(() => searchNearbyGyms({ lat: 37.5, lng: 127, radiusKm: 2, key: 'K', fetchFn: bad }), /Kakao Local 401/);
});

test('TtlCache: 유효 시간 안에서만 돌려줌', () => {
  let now = 0;
  const c = new TtlCache(1000, () => now);
  c.set('a', [1]);
  now = 999; assert.deepEqual(c.get('a'), [1]);
  now = 1000; assert.equal(c.get('a'), undefined);
});

test('interestSummary · toggleRequester: 회원별 1건, 다시 누르면 취소, 우리 체육관 수 따로', () => {
  let r = toggleRequester({}, 'u1', 'gA', 100);
  assert.equal(r.requested, true);
  r = toggleRequester(r.requesters, 'u2', 'gB', 200);
  assert.deepEqual(interestSummary(r.requesters, 'u1', 'gA'), { count: 2, myGymCount: 1, requestedByMe: true });
  r = toggleRequester(r.requesters, 'u1', 'gA', 300);
  assert.equal(r.requested, false);
  assert.deepEqual(interestSummary(r.requesters, 'u1', 'gA'), { count: 1, myGymCount: 0, requestedByMe: false });
  assert.deepEqual(interestSummary(undefined, 'u1', 'gA'), { count: 0, myGymCount: 0, requestedByMe: false });
});
