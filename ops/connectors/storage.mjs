// Firebase Storage 업로드 → 서명 공개 URL(기본 1시간). IG image_url 등 외부 fetch용.
// firebase-admin 필요(ops/connectors에 설치). 버킷은 프로젝트 기본(.firebasestorage.app 우선).
import { basename } from 'node:path';

export async function uploadPublic({ localPath, credentialJson, destPrefix = 'ops/instagram',
    ttlMs = 3600 * 1000, contentType = 'image/jpeg' }) {
  const admin = (await import('firebase-admin')).default;
  const cred = typeof credentialJson === 'string' ? JSON.parse(credentialJson) : credentialJson;
  if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.cert(cred), projectId: cred.project_id });
  let bucket = null;
  for (const name of [`${cred.project_id}.firebasestorage.app`, `${cred.project_id}.appspot.com`]) {
    const b = admin.storage().bucket(name);
    const [ok] = await b.exists();
    if (ok) { bucket = b; break; }
  }
  if (!bucket) throw new Error('Firebase Storage 버킷 없음 — 콘솔에서 Storage 활성화 필요');
  const dest = `${destPrefix}/${Date.now()}_${basename(localPath)}`;
  await bucket.upload(localPath, { destination: dest, metadata: { contentType } });
  const [url] = await bucket.file(dest).getSignedUrl({ action: 'read', version: 'v4', expires: Date.now() + ttlMs });
  return { url, dest };
}
