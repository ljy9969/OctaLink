// ops/mcp/.env 를 process.env 로 로드(이미 설정된 값은 유지). 제로 의존성.
// 스케줄러/CLI 시작 시 호출해 커넥터 자격증명(YOUTUBE_API_KEY 등)을 사용 가능하게 한다.
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

export function loadEnv(opsRoot) {
  const p = join(opsRoot, 'mcp', '.env');
  if (!existsSync(p)) return;
  for (const raw of readFileSync(p, 'utf8').split(/\r?\n/)) {
    if (!raw || raw.trim().startsWith('#')) continue;
    const m = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/.exec(raw);
    if (!m) continue;
    const v = m[2].trim().replace(/^["']|["']$/g, '');
    if (v && (process.env[m[1]] === undefined || process.env[m[1]] === '')) process.env[m[1]] = v;
  }
}
