// 1:1 문의 답변 흐름(게이트): pending 조회 → support 초안 → approvals/inquiry-<id>.md
// → 운영자가 파일의 "상태: 대기"를 "승인"(또는 "반려")으로 변경 → apply 시 Firestore 게시(ANSWERED).
import { readFileSync, writeFileSync, existsSync, mkdirSync, readdirSync, renameSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const CAT_LABEL = { PRAISE: '칭찬', IMPROVEMENT: '개선 제안', QUESTION: '문의', BUG: '버그 신고' };

// 앱 기능 그라운딩 — 초안이 없는 기능/일정을 지어내지 않도록.
export const APP_GROUNDING = `# OctaLink 사실(이것만 근거로, 없는 기능/일정 약속 금지)
- 가입은 관장 승인 후 이용. 출석은 출석 화면 체크인. 실력은 관장이 승급(알림), 프로필 6각형 그래프.
- 교류전은 대진표 추첨(알림). 수업 리마인더는 설정>알림 설정(30분 전, 권한/배터리 필요).
- 로그아웃은 데이터 보존. 탈퇴 기록은 운영 자료 보존, 완전삭제는 운영자 요청.
- AI 쉐도우 코치/맞춤 루틴 있음. 유료화(포인트/구독)는 아직 미출시 — 가격·출시일 약속 금지.`;

export function renderProposal({ inquiry, draft, now = new Date() }) {
  const cat = CAT_LABEL[inquiry.category] || inquiry.category;
  return `# 문의 답변 승인 대기: ${inquiry.id}\n\n`
    + `- 문의ID: ${inquiry.id}\n`
    + `- 카테고리: ${cat}\n`
    + `- 작성자: ${inquiry.authorName || '?'} (${inquiry.authorId})\n`
    + `- 생성: ${now.toISOString()}\n`
    + `- 상태: 대기   (승인하려면 "승인", 반려는 "반려"로 바꾸세요)\n\n`
    + `## 문의 내용\n${inquiry.text}\n\n`
    + `## 답변 초안 (승인 전 자유롭게 수정하세요)\n${draft}\n`;
}

export function parseProposal(md) {
  const idM = /^- 문의ID:\s*(\S+)/m.exec(md);
  const stM = /^- 상태:\s*(\S+)/m.exec(md);
  const ansM = /## 답변 초안[^\n]*\n([\s\S]*?)\s*$/.exec(md);
  return {
    inquiryId: idM ? idM[1] : null,
    status: stM ? stM[1] : null, // 대기 / 승인 / 반려
    answer: ansM ? ansM[1].trim() : '',
  };
}

// pending 문의마다 초안을 만들어 approvals/ 에 기록하고 상태를 DRAFTED 로 표시.
export async function answerPending({ opsRoot, projectId, credentialJson, draftFn, fetchFn, setStatusFn, now = () => new Date() }) {
  const conn = await import('../connectors/inquiries.mjs');
  const res = await (fetchFn || conn.fetchPending)({ projectId, credentialJson });
  if (!res.ok) return { drafted: 0, reason: res.reason };
  const approvalsDir = join(opsRoot, 'approvals');
  if (!existsSync(approvalsDir)) mkdirSync(approvalsDir, { recursive: true });
  let drafted = 0;
  for (const inq of res.inquiries) {
    const draft = await draftFn(inq);
    writeFileSync(join(approvalsDir, `inquiry-${inq.id}.md`), renderProposal({ inquiry: inq, draft, now: now() }));
    await (setStatusFn || conn.setStatus)({ projectId, credentialJson, inquiryId: inq.id, status: 'DRAFTED' });
    drafted++;
  }
  return { drafted };
}

// approvals/inquiry-*.md 중 "승인" 표기된 것 → Firestore 게시(ANSWERED). "반려"는 폐기. 처리분은 .done.md 로.
export async function applyApproved({ opsRoot, projectId, credentialJson, postFn }) {
  const conn = await import('../connectors/inquiries.mjs');
  const approvalsDir = join(opsRoot, 'approvals');
  if (!existsSync(approvalsDir)) return { applied: 0, rejected: 0 };
  let applied = 0, rejected = 0;
  for (const f of readdirSync(approvalsDir)) {
    if (!/^inquiry-.+\.md$/.test(f) || f.endsWith('.done.md')) continue;
    const p = join(approvalsDir, f);
    const parsed = parseProposal(readFileSync(p, 'utf8'));
    if (!parsed.inquiryId) continue;
    if (parsed.status === '승인' && parsed.answer) {
      await (postFn || conn.postAnswer)({ projectId, credentialJson, inquiryId: parsed.inquiryId, answer: parsed.answer, answeredBy: 'ops' });
      renameSync(p, p.replace(/\.md$/, '.done.md'));
      applied++;
    } else if (parsed.status === '반려') {
      renameSync(p, p.replace(/\.md$/, '.done.md'));
      rejected++;
    }
  }
  return { applied, rejected };
}

// CLI: node inquiry-flow.mjs         → pending 초안 작성(approvals/)
//      node inquiry-flow.mjs apply   → "승인" 표기분 Firestore 게시
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const { loadEnv } = await import('./env.mjs');
  loadEnv(opsRoot);
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8'));
  const projectId = conf.firebase?.projectId;
  const credRaw = process.env[conf.firebase?.credEnv || 'FIREBASE_SERVICE_ACCOUNT'] || '';
  const cred = credRaw && existsSync(credRaw) ? readFileSync(credRaw, 'utf8') : credRaw;

  if (process.argv.includes('apply')) {
    const r = await applyApproved({ opsRoot, projectId, credentialJson: cred });
    console.log(`문의 답변 적용: 승인 게시 ${r.applied} · 반려 ${r.rejected}`);
  } else {
    const { createRouter } = await import('./router.mjs');
    const { LANG_GUARD } = await import('./lang.mjs');
    const router = createRouter();
    const support = readFileSync(join(opsRoot, 'agents', 'support', 'skill.md'), 'utf8');
    const supCfg = JSON.parse(readFileSync(join(opsRoot, 'agents', 'support', 'config.json'), 'utf8'));
    const draftFn = async (inq) => {
      const out = await router.complete({
        provider: supCfg.model.provider, model: supCfg.model.name,
        system: LANG_GUARD + support + '\n\n지금은 1:1 문의 답변 초안을 쓴다. 존댓말·간결·정확·겸손. 아래 사실만 근거로, 없는 기능/일정/가격 약속 금지.\n\n' + APP_GROUNDING,
        messages: [{ role: 'user', content: `[카테고리] ${inq.category}\n[문의]\n${inq.text}\n\n답변 초안만 출력(머리말 없이).` }],
      });
      return out.text.trim();
    };
    const r = await answerPending({ opsRoot, projectId, credentialJson: cred, draftFn });
    console.log(`문의 답변 초안: ${r.drafted}건${r.reason ? ` (${r.reason})` : ''} → approvals/inquiry-*.md`);
  }
}
