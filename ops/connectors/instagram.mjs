// 인스타그램 게시 커넥터 — Meta 그래프 API(액세스 토큰 기반, 비밀번호 아님).
// 승인된(approvals/) 게시물만, 사람이 최종 실행. 토큰/유저ID 없으면 no-op.
// mediaType: 'IMAGE'(피드 기본) | 'STORIES'(스토리, 캡션 미지원).
export async function publish({ token, igUserId, imageUrl, caption, mediaType = 'IMAGE',
    apiBase = 'https://graph.facebook.com/v21.0', transport = globalThis.fetch }) {
  if (!token || !igUserId) return { ok: false, reason: 'INSTAGRAM_TOKEN/IG_USER_ID 없음 (게시 skip)' };
  // 1) 미디어 컨테이너 생성(이미지 URL은 공개 접근 가능해야 함)
  const body = { image_url: imageUrl, access_token: token };
  if (mediaType === 'STORIES') body.media_type = 'STORIES'; // 스토리는 캡션 미지원
  else if (caption) body.caption = caption;                 // 피드만 캡션
  const c = await transport(`${apiBase}/${igUserId}/media`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const cj = await c.json();
  if (!c.ok || !cj.id) throw new Error(`IG container 실패 ${c.status}: ${JSON.stringify(cj).slice(0, 200)}`);
  // 2) 게시
  const p = await transport(`${apiBase}/${igUserId}/media_publish`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ creation_id: cj.id, access_token: token }) });
  const pj = await p.json();
  if (!p.ok || !pj.id) throw new Error(`IG publish 실패 ${p.status}: ${JSON.stringify(pj).slice(0, 200)}`);
  return { ok: true, mediaId: pj.id, mediaType };
}

// 로컬 이미지 → Firebase Storage 공개 호스팅 → 게시. (social 에이전트 자동 흐름)
// credentialJson: Firebase 서비스계정(문자열/객체). uploadFn 주입 시 그걸 사용(테스트).
export async function publishLocal({ token, igUserId, imagePath, caption, mediaType = 'IMAGE',
    credentialJson, uploadFn, apiBase, transport = globalThis.fetch }) {
  if (!token || !igUserId) return { ok: false, reason: 'INSTAGRAM_TOKEN/IG_USER_ID 없음 (게시 skip)' };
  const up = uploadFn || (await import('./storage.mjs')).uploadPublic;
  const { url } = await up({ localPath: imagePath, credentialJson });
  return publish({ token, igUserId, imageUrl: url, caption, mediaType, apiBase, transport });
}
