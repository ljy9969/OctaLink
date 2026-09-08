// Firebase(Firestore) 읽기 커넥터 → 에이전트별 앱데이터 스냅샷(context/<agent>.md).
// 실제 읽기는 firebase-admin(자격증명 있을 때 lazy import). 테스트는 readerFn 주입.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

async function defaultReader({ projectId, credentialJson, collection, limit }) {
  if (!credentialJson) return null; // 자격증명 없으면 no-op
  const admin = (await import('firebase-admin')).default;
  if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.cert(JSON.parse(credentialJson)), projectId });
  const snap = await admin.firestore().collection(collection).limit(limit).get();
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export function summarize(collection, docs) {
  if (!docs) return `- ${collection}: (자격증명 없음 — 미조회)`;
  if (docs.length === 0) return `- ${collection}: (0건)`;
  const keys = [...new Set(docs.flatMap((d) => Object.keys(d)))].slice(0, 8);
  const rows = docs.slice(0, 10).map((d) => '  · ' + keys.map((k) => `${k}=${JSON.stringify(d[k])}`).join(' · '));
  return `- ${collection} (${docs.length}건, 상위 ${rows.length}):\n` + rows.join('\n');
}

export async function refreshContext({ opsRoot, projectId, credentialJson, contextMap, readerFn = defaultReader, limit = 10, now = () => new Date() }) {
  const ctxDir = join(opsRoot, 'context'); if (!existsSync(ctxDir)) mkdirSync(ctxDir, { recursive: true });
  const written = [];
  for (const [agent, collections] of Object.entries(contextMap || {})) {
    const parts = [];
    for (const c of collections) parts.push(summarize(c, await readerFn({ projectId, credentialJson, collection: c, limit })));
    writeFileSync(join(ctxDir, `${agent}.md`), `# ${agent} 앱데이터 스냅샷 (${now().toISOString()})\n\n${parts.join('\n')}\n`);
    written.push(agent);
  }
  return written;
}

// CLI: node ops/connectors/firebase.mjs  (자격증명 있으면 context 갱신)
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8')).firebase || {};
  let cred = process.env[conf.credEnv || 'FIREBASE_SERVICE_ACCOUNT'] || '';
  if (cred && existsSync(cred)) cred = readFileSync(cred, 'utf8'); // 파일 경로면 내용으로
  const written = await refreshContext({ opsRoot, projectId: conf.projectId, credentialJson: cred, contextMap: conf.context });
  console.log(`context refreshed: ${written.join(', ') || 'none'}${cred ? '' : ' (자격증명 없음 → placeholder만)'}`);
}
