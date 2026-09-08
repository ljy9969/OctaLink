import { test } from 'node:test';
import assert from 'node:assert/strict';
import { publish } from '../../connectors/instagram.mjs';

const res = (body, ok = true, status = 200) => ({ ok, status, async json() { return body; }, async text() { return JSON.stringify(body); } });

test('토큰/유저ID 없으면 no-op', async () => {
  const r = await publish({ token: '', igUserId: '', imageUrl: 'u', caption: 'c', transport: async () => { throw new Error('nope'); } });
  assert.equal(r.ok, false);
});

test('2단계(container→publish) 그래프 API 호출', async () => {
  const calls = [];
  const transport = async (url, init) => {
    calls.push(url);
    if (url.endsWith('/media')) return res({ id: 'CONTAINER123' });
    if (url.endsWith('/media_publish')) return res({ id: 'MEDIA999' });
    return res({}, false, 404);
  };
  const r = await publish({ token: 't', igUserId: '178', imageUrl: 'https://x/y.jpg', caption: '안녕', transport });
  assert.ok(calls[0].includes('/178/media'));
  assert.ok(calls[1].includes('/178/media_publish'));
  assert.equal(r.ok, true);
  assert.equal(r.mediaId, 'MEDIA999');
});

test('container 오류 → throw', async () => {
  await assert.rejects(
    publish({ token: 't', igUserId: '1', imageUrl: 'u', caption: 'c', transport: async () => res({ error: { message: 'bad' } }, false, 400) }),
    /IG container 실패 400/);
});
