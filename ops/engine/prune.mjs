// approvals/ 보존 정리: 에이전트별로 가장 최근 keepPerAgent(기본 3)개 제안서만 남기고 나머지 .md 삭제.
// 디스패처가 매 실행마다 호출(dispatch.mjs). approvals/*.md 는 gitignore 런타임 파일이라 삭제 = 영구 삭제.
import { readdirSync, statSync, unlinkSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const STAMP = /-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})\.md$/;

// 생성 시각: gate.mjs 의 파일명 스탬프 `-YYYYMMDD-HHMMSS.md`(UTC). 스탬프가 없으면 null(→ mtime 사용).
export function stampTime(name) {
  const m = STAMP.exec(name);
  if (!m) return null;
  const [, y, mo, d, h, mi, s] = m.map(Number);
  return Date.UTC(y, mo - 1, d, h, mi, s);
}

// 에이전트 이름: 스탬프 앞부분(`support-20261014-010301.md` → support).
// 스탬프가 없으면 첫 '-' 앞(`inquiry-j7HH….md` → inquiry), '-' 도 없으면 확장자 뺀 이름.
export function agentOf(name) {
  if (STAMP.test(name)) return name.replace(STAMP, '');
  const base = name.replace(/\.md$/, '');
  const i = base.indexOf('-');
  return i > 0 ? base.slice(0, i) : base;
}

export function pruneApprovals({ dir, keepPerAgent = 3, dryRun = false } = {}) {
  // 안전장치: 0·음수·NaN 이면 `i < keep` 이 항상 거짓 → 전부 삭제되므로 실행 거부.
  if (!Number.isInteger(keepPerAgent) || keepPerAgent < 1) {
    throw new Error(`keepPerAgent 는 1 이상의 정수여야 함 (받은 값: ${keepPerAgent})`);
  }
  if (!dir || !existsSync(dir)) return { removed: [], kept: 0 };
  const groups = new Map();
  for (const name of readdirSync(dir)) {
    if (name.startsWith('.') || !name.endsWith('.md')) continue; // .gitkeep 등 보존
    const path = join(dir, name);
    const t = stampTime(name) ?? statSync(path).mtimeMs;
    const agent = agentOf(name);
    if (!groups.has(agent)) groups.set(agent, []);
    groups.get(agent).push({ name, path, t });
  }
  const removed = [];
  let kept = 0;
  for (const files of groups.values()) {
    files.sort((a, b) => b.t - a.t || (a.name < b.name ? 1 : -1)); // 최신순(동시각은 이름 역순)
    files.forEach((f, i) => {
      if (i < keepPerAgent) { kept++; return; }
      if (!dryRun) unlinkSync(f.path);
      removed.push(f.name);
    });
  }
  return { removed: removed.sort(), kept };
}

// CLI: node engine/prune.mjs [--dry-run] [--keep N]
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const opsRoot = process.env.OPS_ROOT || join(dirname(fileURLToPath(import.meta.url)), '..');
  const dryRun = process.argv.includes('--dry-run');
  const ki = process.argv.indexOf('--keep');
  const keepPerAgent = ki >= 0 ? Number(process.argv[ki + 1]) : 3;
  const r = pruneApprovals({ dir: join(opsRoot, 'approvals'), keepPerAgent, dryRun });
  console.log(`${dryRun ? '[dry-run] 삭제 예정' : '삭제'} ${r.removed.length}건 · 보존 ${r.kept}건 (에이전트별 최신 ${keepPerAgent}개)`);
}
