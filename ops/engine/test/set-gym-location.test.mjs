import { test } from 'node:test';
import assert from 'node:assert/strict';
import { placeToLocation, addressToLocation, parseArgs } from '../../connectors/set-gym-location.mjs';

test('placeToLocation: 카카오 장소 → 체육관 위치(도로명 우선, 좌표 숫자, 장소 id)', () => {
  assert.deepEqual(
    placeToLocation({ id: 123, place_name: '팀포세', road_address_name: '서울 강남구 강남대로 1', address_name: '서울 강남구 역삼동 1', x: '127.0276', y: '37.4979' }),
    { address: '서울 강남구 강남대로 1', lat: 37.4979, lng: 127.0276, kakaoPlaceId: '123' },
  );
  assert.throws(() => placeToLocation({ id: 1, x: '', y: '' }), /좌표/);
});

test('addressToLocation: 주소 검색 결과 → 위치(장소 id 없음)', () => {
  assert.deepEqual(
    addressToLocation({ address_name: '서울 마포구 서교동 1', road_address: { address_name: '서울 마포구 양화로 1' }, x: '126.9245', y: '37.5572' }),
    { address: '서울 마포구 양화로 1', lat: 37.5572, lng: 126.9245, kakaoPlaceId: null },
  );
});

test('parseArgs: --gym 필수, --query 또는 --address, --pick 기본 1, --write 없으면 미리보기', () => {
  assert.deepEqual(parseArgs(['--gym', 'teamposse', '--query', '팀포세 강남']), { gym: 'teamposse', query: '팀포세 강남', address: null, pick: 1, write: false });
  assert.deepEqual(parseArgs(['--gym', 'g', '--address', '서울 마포구 양화로 1', '--write']), { gym: 'g', query: null, address: '서울 마포구 양화로 1', pick: 1, write: true });
  assert.equal(parseArgs(['--gym', 'g', '--query', 'q', '--pick', '3']).pick, 3);
  assert.throws(() => parseArgs(['--query', 'q']), /--gym/);
  assert.throws(() => parseArgs(['--gym', 'g']), /--query 또는 --address/);
});
