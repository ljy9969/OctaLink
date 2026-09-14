import { test } from 'node:test';
import assert from 'node:assert/strict';
import { publish, publishLocal } from '../../connectors/instagram.mjs';

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

test('스토리(mediaType=STORIES): media_type 포함, 캡션 제외', async () => {
  let body;
  const transport = async (url, init) => {
    if (url.endsWith('/media')) { body = JSON.parse(init.body); return res({ id: 'C1' }); }
    if (url.endsWith('/media_publish')) return res({ id: 'M1' });
    return res({}, false, 404);
  };
  const r = await publish({ token: 't', igUserId: '178', imageUrl: 'https://x/y.jpg', caption: '무시됨', mediaType: 'STORIES', transport });
  assert.equal(body.media_type, 'STORIES');
  assert.equal(body.caption, undefined);
  assert.equal(r.mediaId, 'M1');
  assert.equal(r.mediaType, 'STORIES');
});

test('publishLocal: 주입 uploadFn으로 호스팅 후 그 URL로 게시', async () => {
  let usedUrl;
  const uploadFn = async ({ localPath }) => ({ url: 'https://host/' + localPath.split('/').pop(), dest: 'd' });
  const transport = async (url, init) => {
    if (url.endsWith('/media')) { usedUrl = JSON.parse(init.body).image_url; return res({ id: 'C2' }); }
    if (url.endsWith('/media_publish')) return res({ id: 'M2' });
    return res({}, false, 404);
  };
  const r = await publishLocal({ token: 't', igUserId: '178', imagePath: '/tmp/story.jpg', mediaType: 'STORIES', uploadFn, transport });
  assert.equal(usedUrl, 'https://host/story.jpg');
  assert.equal(r.mediaId, 'M2');
});
