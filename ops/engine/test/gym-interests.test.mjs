import { test } from 'node:test';
import assert from 'node:assert/strict';
import { summarizeInterests, loadGymInterests } from '../../connectors/gym-interests.mjs';

const docs = [
  { id: '864257425', name: '케이레슬링 강남선릉점', requesters: { u1: { gymId: 'teamposse', at: 1 }, u2: { gymId: 'teamposse', at: 2 }, u3: { gymId: 'octagon', at: 3 } } },
  { id: '777', name: '울프킥복싱', requesters: { u4: { gymId: 'teamposse', at: 4 } } },
  { id: '999', name: '빈 요청', requesters: {} },
];

test('summarizeInterests: 장소별 요청 수·요청 체육관 수, 회원 uid 는 내보내지 않음', () => {
  const m = summarizeInterests(docs);
  assert.deepEqual(m.get('864257425'), { placeId: '864257425', name: '케이레슬링 강남선릉점', count: 3, gyms: 2 });
  assert.deepEqual(m.get('777'), { placeId: '777', name: '울프킥복싱', count: 1, gyms: 1 });
  assert.equal(m.has('999'), false);                       // 요청 0건은 제외
  assert.doesNotMatch(JSON.stringify([...m.values()]), /u1|u2|u3|u4/);
});

test('loadGymInterests: 읽기 함수 주입(자격증명 없이 테스트)', async () => {
  const m = await loadGymInterests({ readerFn: async () => docs });
  assert.equal(m.size, 2);
});
