// context/<agent>.md 를 여러 커넥터가 공유. 각 커넥터는 자기 '구획'만 교체(다른 구획 보존).
// 구획은 <!-- sec:NAME --> ... <!-- /sec:NAME --> 마커로 구분 → run-agent는 파일 전체를 읽어 참고.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

function escapeRe(s) { return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

// context 문서 텍스트에서 구획 하나를 읽는다(순수). → { body, updatedAt(ms|null) } | null.
// updatedAt 은 writeContextSection 이 남긴 `## <구획> (<ISO>)` 머리의 시각.
export function parseContextSection(doc, section) {
  const re = new RegExp(`${escapeRe(`<!-- sec:${section} -->`)}\\n## ${escapeRe(section)} \\(([^)]*)\\)\\n\\n([\\s\\S]*?)\\n${escapeRe(`<!-- /sec:${section} -->`)}`);
  const m = re.exec(doc || '');
  if (!m) return null;
  const t = Date.parse(m[1]);
  return { body: m[2], updatedAt: Number.isNaN(t) ? null : t };
}

export function writeContextSection({ opsRoot, agent, section, body, now = () => new Date() }) {
  const ctxDir = join(opsRoot, 'context');
  if (!existsSync(ctxDir)) mkdirSync(ctxDir, { recursive: true });
  const file = join(ctxDir, `${agent}.md`);
  let doc = existsSync(file) ? readFileSync(file, 'utf8') : `# ${agent} 컨텍스트\n`;
  const start = `<!-- sec:${section} -->`;
  const end = `<!-- /sec:${section} -->`;
  const block = `${start}\n## ${section} (${now().toISOString()})\n\n${body}\n${end}`;
  const re = new RegExp(`${escapeRe(start)}[\\s\\S]*?${escapeRe(end)}`);
  doc = re.test(doc) ? doc.replace(re, block) : `${doc.replace(/\s+$/, '')}\n\n${block}\n`;
  writeFileSync(file, doc);
  return file;
}
