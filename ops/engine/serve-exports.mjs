// exports/ 를 http://127.0.0.1:8790 으로 서빙(카카오 지도 JS SDK 는 등록 도메인에서만 동작 → file:// 불가).
// 사용: node engine/serve-exports.mjs [--port 8790]  → 브라우저에서 http://127.0.0.1:8790/gym-map-seoul.html (카카오에 등록된 도메인 — localhost 는 미등록이라 지도 SDK 거부)
// 로컬 전용: IPv4·IPv6 루프백(127.0.0.1, ::1) 둘 다 바인딩 — 'localhost' 가 어느 쪽으로 풀려도 이 서버로.
// (5173 은 Vite 기본 포트라 다른 개발 서버와 겹쳐 'localhost' 가 그 앱으로 열리는 사고가 있었음 → 8790.)
// 포트가 이미 쓰이면 조용히 넘어가지 않고 실패. 읽기 전용, exports/ 밖 경로 차단.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { join, resolve, extname, sep, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const DEFAULT_PORT = 8790;
const TYPES = { '.html': 'text/html; charset=utf-8', '.csv': 'text/csv; charset=utf-8', '.json': 'application/json; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };

// 요청 경로 → exports/ 안의 실제 파일 경로. 밖으로 나가면(.. 등) null.
export function safePath(root, urlPath) {
  let p;
  try { p = decodeURIComponent((urlPath || '/').split('?')[0]); } catch { return null; }
  if (p.endsWith('/')) p += 'index.html';
  const full = resolve(root, '.' + p);
  const base = resolve(root);
  return full === base || full.startsWith(base + sep) ? full : null;
}

export async function startServer({ root, port = DEFAULT_PORT, hosts = ['127.0.0.1', '::1'] }) {
  const handler = async (req, res) => {
    const file = safePath(root, req.url);
    if (!file) { res.writeHead(403).end('forbidden'); return; }
    try {
      if (!(await stat(file)).isFile()) throw new Error('not file');
      res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
      res.end(await readFile(file));
    } catch { res.writeHead(404).end('not found'); }
  };
  const servers = [];
  const close = () => servers.forEach((s) => s.close());
  for (const host of hosts) {
    const s = createServer(handler);
    try {
      await new Promise((ok, no) => { s.once('error', no); s.listen(port, host, ok); });
    } catch (e) {
      if (e.code === 'EADDRNOTAVAIL' && host === '::1') continue; // IPv6 꺼진 PC — IPv4 만으로 진행
      close();
      throw e.code === 'EADDRINUSE' ? new Error(`포트 ${port} 를 다른 프로그램이 쓰는 중(${host}) — --port 로 다른 포트 지정`) : e;
    }
    servers.push(s);
    if (port === 0) port = s.address().port; // 두 번째 루프백도 같은 포트로
  }
  return { port, close };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const opsRoot = process.env.OPS_ROOT || join(dirname(fileURLToPath(import.meta.url)), '..');
  const pi = process.argv.indexOf('--port');
  const port = pi >= 0 ? Number(process.argv[pi + 1]) : DEFAULT_PORT;
  try {
    await startServer({ root: join(opsRoot, 'exports'), port });
    console.log(`서빙 중: http://127.0.0.1:${port}/  (exports/) — 종료: Ctrl+C`);
  } catch (e) { console.error(e.message); process.exit(1); }
}
