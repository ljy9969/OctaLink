// 카카오 계정 상태 변경 웹훅 — SET(JWT) 검증 테스트. 실제 RSA 키로 서명해 검증한다.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, createSign } from 'node:crypto';
import { verifySet, JwksCache, kakaoUserIdOf, scopesOf, KAKAO_ISSUER } from '../lib/kakaoSet.js';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const other = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'k1', alg: 'RS256', use: 'sig' };
const REST = 'rest-api-key-of-octalink';
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const sign = (header, payload, key = privateKey) => {
  const h = b64(header), p = b64(payload);
  return `${h}.${p}.${createSign('RSA-SHA256').update(`${h}.${p}`).sign(key).toString('base64url')}`;
};
const HEADER = { kid: 'k1', typ: 'secevent+jwt', alg: 'RS256' };
const PAYLOAD = {
  iss: KAKAO_ISSUER, aud: REST, sub: '4242', app_id: '1453976', jti: 'j1', iat: 1, toe: 1,
  events: { 'https://schemas.openid.net/secevent/oauth/event-type/user-unlinked': { subject: { subject_type: 'iss-sub', iss: KAKAO_ISSUER, sub: '4242' }, reason: 'UNLINK_FROM_APPS' } },
};
const jwks = () => new JwksCache(async () => ({ ok: true, status: 200, json: async () => ({ keys: [jwk] }) }));

test('verifySet: 카카오 공개키로 서명 확인 + iss·aud 일치 → 통과', async () => {
  const r = await verifySet(sign(HEADER, PAYLOAD), { restApiKey: REST, getKey: (k) => jwks().getKey(k) });
  assert.equal(r.ok, true);
  assert.equal(r.claims.sub, '4242');
});

test('verifySet: 위조(페이로드 바꿔치기·다른 키 서명·없는 kid) → invalid_key', async () => {
  const cache = jwks();
  const opts = { restApiKey: REST, getKey: (k) => cache.getKey(k) };
  const [h, , s] = sign(HEADER, PAYLOAD).split('.');
  const forged = `${h}.${b64({ ...PAYLOAD, sub: '9999' })}.${s}`;
  assert.equal((await verifySet(forged, opts)).error.err, 'invalid_key');
  assert.equal((await verifySet(sign(HEADER, PAYLOAD, other.privateKey), opts)).error.err, 'invalid_key');
  assert.equal((await verifySet(sign({ ...HEADER, kid: 'nope' }, PAYLOAD), opts)).error.err, 'invalid_key');
});

test('verifySet: 발급자·수신 앱이 다르면 invalid_issuer · invalid_audience', async () => {
  const opts = { restApiKey: REST, getKey: (k) => jwks().getKey(k) };
  assert.equal((await verifySet(sign(HEADER, { ...PAYLOAD, iss: 'https://evil.example' }), opts)).error.err, 'invalid_issuer');
  assert.equal((await verifySet(sign(HEADER, { ...PAYLOAD, aud: 'someone-else' }), opts)).error.err, 'invalid_audience');
  assert.equal((await verifySet(sign(HEADER, PAYLOAD), { ...opts, restApiKey: '' })).error.err, 'invalid_audience');
});

test('verifySet: JWT 형식 아님·alg none → invalid_request', async () => {
  const opts = { restApiKey: REST, getKey: (k) => jwks().getKey(k) };
  assert.equal((await verifySet('not-a-jwt', opts)).error.err, 'invalid_request');
  assert.equal((await verifySet('', opts)).error.err, 'invalid_request');
  const none = `${b64({ ...HEADER, alg: 'none' })}.${b64(PAYLOAD)}.x`;
  assert.equal((await verifySet(none, opts)).error.err, 'invalid_request');
});

test('JwksCache: 공개키 캐시 — 같은 kid 는 다시 받지 않고, 모르는 kid 는 최소 간격 뒤에만 재조회', async () => {
  let calls = 0, t = 0;
  const cache = new JwksCache(async () => { calls++; return { ok: true, status: 200, json: async () => ({ keys: [jwk] }) }; },
    { ttlMs: 3_600_000, minRefetchMs: 60_000, now: () => t });
  assert.ok(await cache.getKey('k1'));
  assert.ok(await cache.getKey('k1'));
  assert.equal(calls, 1);
  assert.equal(await cache.getKey('unknown'), null);   // 방금 받았으니 재조회 안 함
  assert.equal(calls, 1);
  t = 61_000;
  assert.equal(await cache.getKey('unknown'), null);   // 최소 간격 지나서 한 번 재조회
  assert.equal(calls, 2);
  t = 3_700_000;
  assert.ok(await cache.getKey('k1'));                  // TTL 지나면 갱신
  assert.equal(calls, 3);
});

test('kakaoUserIdOf · scopesOf: 이벤트 subject.sub 우선, 없으면 최상위 sub · scope 는 공백 구분 문자열', () => {
  assert.equal(kakaoUserIdOf({ sub: '1' }, { subject: { subject_type: 'iss-sub', sub: '2' } }), '2');
  assert.equal(kakaoUserIdOf({ sub: '1' }, {}), '1');
  assert.equal(kakaoUserIdOf({}, {}), null);
  assert.deepEqual(scopesOf({ scope: 'account_email  birthday age_range' }), ['account_email', 'birthday', 'age_range']);
  assert.deepEqual(scopesOf({ scopes: ['phone_number'] }), ['phone_number']);
  assert.deepEqual(scopesOf({}), []);
});
