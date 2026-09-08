import { test } from 'node:test';
import assert from 'node:assert/strict';
import { searchChannels } from '../../connectors/youtube.mjs';

const res = (body, ok = true, status = 200) => ({ ok, status, async json() { return body; }, async text() { return JSON.stringify(body); } });

test('키 없으면 no-op', async () => {
  const r = await searchChannels({ apiKey: '', query: 'x', transport: async () => { throw new Error('nope'); } });
  assert.equal(r.ok, false); assert.deepEqual(r.channels, []);
});
test('채널 파싱(snippet.channelId → url)', async () => {
  let url;
  const transport = async (u) => { url = u; return res({ items: [{ snippet: { channelId: 'UC123', channelTitle: '코리안좀비 MMA', description: '정찬성' } }] }); };
  const r = await searchChannels({ apiKey: 'k', query: '코리안좀비', transport });
  assert.match(url, /type=channel/); assert.match(url, /key=k/);
  assert.equal(r.channels[0].url, 'https://www.youtube.com/channel/UC123');
  assert.equal(r.channels[0].title, '코리안좀비 MMA');
});
test('non-2xx → throw', async () => {
  await assert.rejects(searchChannels({ apiKey: 'k', query: 'x', transport: async () => res({ error: {} }, false, 403) }), /YouTube 403/);
});
