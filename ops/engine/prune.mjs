// approvals/ 보존 정리: 최근 keepDays(기본 7일) 안의 제안서만 남기고 나머지 .md 삭제.
// 디스패처가 하루 1회 호출(dispatch.mjs). approvals/*.md 는 gitignore 런타임 파일이라 삭제 = 영구 삭제.
import { readdirSync, statSync, unlinkSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DAY_MS = 24 * 3600 * 1000;

// 생성 시각: gate.mjs 의 파일명 스탬프 `-YYYYMMDD-HHMMSS.md`(UTC). 스탬프가 없으면 null(→ mtime 사용).
export function stampTime(name) {
  const m = /-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})\.md$/.exec(name);
  if (!m) return null;
  const [, y, mo, d, h, mi, s] = m.map(Number);
  return Date.UTC(y, mo - 1, d, h, mi, s);
}

export function pruneApprovals({ dir, now = new Date(), keepDays = 7, dryRun = false } = {}) {
  if (!dir || !existsSync(dir)) return { removed: [], kept: 0 };
  const cutoff = now.getTime() - keepDays * DAY_MS;
  const removed = [];
  let kept = 0;
  for (const name of readdirSync(dir)) {
    if (name.startsWith('.') || !name.endsWith('.md')) continue; // .gitkeep 등 보존
    const path = join(dir, name);
    const t = stampTime(name) ?? statSync(path).mtimeMs;
    if (t < cutoff) {
      if (!dryRun) unlinkSync(path);
      removed.push(name);
    } else kept++;
  }
  return { removed: removed.sort(), kept };
}

// CLI: node engine/prune.mjs [--dry-run] [--days N]
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const opsRoot = process.env.OPS_ROOT || join(dirname(fileURLToPath(import.meta.url)), '..');
  const dryRun = process.argv.includes('--dry-run');
  const di = process.argv.indexOf('--days');
  const keepDays = di >= 0 ? Number(process.argv[di + 1]) : 7;
  const r = pruneApprovals({ dir: join(opsRoot, 'approvals'), keepDays, dryRun });
  console.log(`${dryRun ? '[dry-run] 삭제 예정' : '삭제'} ${r.removed.length}건 · 보존 ${r.kept}건 (최근 ${keepDays}일)`);
}
