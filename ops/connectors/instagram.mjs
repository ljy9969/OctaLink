// 인스타그램 게시 커넥터 — Meta 그래프 API(액세스 토큰 기반, 비밀번호 아님).
// 승인된(approvals/) 게시물만, 사람이 최종 실행. 토큰/유저ID 없으면 no-op.
export async function publish({ token, igUserId, imageUrl, caption,
    apiBase = 'https://graph.facebook.com/v21.0', transport = globalThis.fetch }) {
  if (!token || !igUserId) return { ok: false, reason: 'INSTAGRAM_TOKEN/IG_USER_ID 없음 (게시 skip)' };
  // 1) 미디어 컨테이너 생성(이미지 URL은 공개 접근 가능해야 함)
  const c = await transport(`${apiBase}/${igUserId}/media`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ image_url: imageUrl, caption, access_token: token }) });
  const cj = await c.json();
  if (!c.ok || !cj.id) throw new Error(`IG container 실패 ${c.status}: ${JSON.stringify(cj).slice(0, 200)}`);
  // 2) 게시
  const p = await transport(`${apiBase}/${igUserId}/media_publish`, { method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ creation_id: cj.id, access_token: token }) });
  const pj = await p.json();
  if (!p.ok || !pj.id) throw new Error(`IG publish 실패 ${p.status}: ${JSON.stringify(pj).slice(0, 200)}`);
  return { ok: true, mediaId: pj.id };
}
