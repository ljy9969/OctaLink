import { writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

function stamp(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}-${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}`;
}

export function applyGate({ risk, name, artifact, approvalsDir, now }) {
  if (risk === 'safe') return { action: 'auto' };
  if (!existsSync(approvalsDir)) mkdirSync(approvalsDir, { recursive: true });
  const path = join(approvalsDir, `${name}-${stamp(now)}.md`);
  writeFileSync(path, `# 승인 대기: ${name}\n\n- 생성: ${now.toISOString()}\n- 승인 시 실행, 반려 시 폐기\n\n---\n\n${artifact}\n`);
  return { action: 'queued', path };
}
