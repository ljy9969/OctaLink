// 런타임 산출물 보존 정리: 폴더마다 에이전트별로 가장 최근 N개 .md 만 남기고 나머지 삭제.
//   approvals/ (gate.mjs 제안서) → 3개, reports/ (run-agent.mjs 보고) → 5개.
// 디스패처가 매 실행 끝에 호출(dispatch.mjs). 두 폴더의 *.md 는 gitignore 런타임 파일이라 삭제 = 영구 삭제.
import { readdirSync, statSync, unlinkSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

/** 폴더(opsRoot 기준) → 에이전트별 보존 개수. 디스패처·CLI 가 같은 값을 쓴다. */
export const RETENTION = { approvals: 3, reports: 5 };

const STAMP = /-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})\.md$/;

// 생성 시각: gate.mjs / run-agent.mjs 의 파일명 스탬프 `-YYYYMMDD-HHMMSS.md`(UTC). 없으면 null(→ mtime).
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

export function pruneByAgent({ dir, keepPerAgent, dryRun = false } = {}) {
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

// CLI: node engine/prune.mjs [--dry-run] [--only approvals|reports] [--keep N]
//   인자 없으면 RETENTION 의 모든 폴더를 기본 개수로 정리. --keep 은 --only 와 함께만.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const opsRoot = process.env.OPS_ROOT || join(dirname(fileURLToPath(import.meta.url)), '..');
  const argv = process.argv;
  const dryRun = argv.includes('--dry-run');
  const oi = argv.indexOf('--only');
  const ki = argv.indexOf('--keep');
  const only = oi >= 0 ? argv[oi + 1] : null;
  if (only && !(only in RETENTION)) throw new Error(`--only 는 ${Object.keys(RETENTION).join('|')} 중 하나`);
  if (ki >= 0 && !only) throw new Error('--keep 은 --only 와 함께 써야 함');
  for (const [sub, def] of Object.entries(RETENTION)) {
    if (only && sub !== only) continue;
    const keepPerAgent = ki >= 0 ? Number(argv[ki + 1]) : def;
    const r = pruneByAgent({ dir: join(opsRoot, sub), keepPerAgent, dryRun });
    console.log(`${sub}: ${dryRun ? '[dry-run] 삭제 예정' : '삭제'} ${r.removed.length}건 · 보존 ${r.kept}건 (에이전트별 최신 ${keepPerAgent}개)`);
  }
}
