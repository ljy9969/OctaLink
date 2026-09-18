// 1:1 문의 답변 초안 흐름: pending 문의 → support 초안 → **CEO 검토(무조건)** → 통과분만
// Firestore inquiries.draftAnswer 에 저장(status=DRAFTED) → 어드민 답변창 placeholder 로 노출 →
// 운영자 검토/수정 후 인앱 게시(ANSWERED). CEO 미승인 초안은 저장 안 함(다음 주기 재시도).
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const CAT_LABEL = { PRAISE: '칭찬', IMPROVEMENT: '개선 제안', QUESTION: '문의', BUG: '버그 신고' };

// 앱 기능 그라운딩 — 초안이 없는 기능/일정을 지어내지 않도록.
export const APP_GROUNDING = `# OctaLink 사실(이것만 근거로, 없는 기능/일정 약속 금지)
- 가입은 관장 승인 후 이용. 출석은 출석 화면 체크인. 실력은 관장이 승급(알림), 프로필 6각형 그래프.
- 교류전은 대진표 추첨(알림). 수업 리마인더는 설정>알림 설정(30분 전, 권한/배터리 필요).
- 로그아웃은 데이터 보존. 탈퇴 기록은 운영 자료 보존, 완전삭제는 운영자 요청.
- AI 쉐도우 코치/맞춤 루틴 있음. 유료화(포인트/구독)는 아직 미출시 — 가격·출시일 약속 금지.`;

function extractJson(text) {
  const s = text.indexOf('{'), e = text.lastIndexOf('}');
  if (s < 0 || e < s) return null;
  try { return JSON.parse(text.slice(s, e + 1)); } catch { return null; }
}

// support 모델로 답변 초안 1건 생성(본문만).
export async function draftAnswer({ inquiry, router, opsRoot }) {
  const { LANG_GUARD } = await import('./lang.mjs');
  const support = readFileSync(join(opsRoot, 'agents', 'support', 'skill.md'), 'utf8');
  const cfg = JSON.parse(readFileSync(join(opsRoot, 'agents', 'support', 'config.json'), 'utf8'));
  const out = await router.complete({
    provider: cfg.model.provider, model: cfg.model.name,
    system: LANG_GUARD + support
      + '\n\n지금은 1:1 문의 답변 초안을 쓴다. 존댓말·간결·정확·겸손. 아래 사실만 근거로, 없는 기능/일정/가격 약속 금지.'
      + '\n\n[출력 규칙] 회원에게 그대로 보낼 답변 본문만 써라. "에스컬레이션","산출물","원문 요약" 같은 내부 라벨/머리말 금지. 인사말+본문 2~4문장.\n\n'
      + APP_GROUNDING,
    messages: [{ role: 'user', content: `[카테고리] ${CAT_LABEL[inquiry.category] || inquiry.category}\n[문의]\n${inquiry.text}\n\n답변 초안만 출력(머리말 없이).` }],
  });
  return out.text.trim();
}

// CEO 검토 — support 초안을 승인/수정. {approved, answer(최종), reason} JSON.
export async function ceoReview({ inquiry, draft, router, opsRoot }) {
  const { LANG_GUARD } = await import('./lang.mjs');
  const ceo = readFileSync(join(opsRoot, 'ceo', 'ceo.md'), 'utf8');
  const cfg = JSON.parse(readFileSync(join(opsRoot, 'ceo', 'config.json'), 'utf8'));
  const out = await router.complete({
    provider: cfg.model.provider, model: cfg.model.name,
    system: LANG_GUARD + ceo + '\n\n지금은 support가 쓴 1:1 문의 답변 초안을 CEO로서 검토·승인하는 일이다. 허위 약속·부정확·무례·정책 위반이 있으면 고쳐서 최종 답변을 만든다.',
    messages: [{ role: 'user', content:
      `[문의]\n${inquiry.text}\n\n[support 초안]\n${draft}\n\n`
      + `반드시 JSON만 출력: {"approved": true/false, "answer": "게시할 최종 답변(승인이면 초안 그대로, 문제 있으면 수정본. 존댓말·본문만)", "reason": "간단 사유"}` }],
  });
  return extractJson(out.text);
}

// pending 문의 → support 초안 → CEO 검토 → 통과분만 draftAnswer 저장(DRAFTED).
export async function draftPendingInquiries({ opsRoot, projectId, credentialJson, router, fetchFn, draftFn, reviewFn, saveDraftFn, now = () => new Date() }) {
  const conn = await import('../connectors/inquiries.mjs');
  const res = await (fetchFn || conn.fetchPending)({ projectId, credentialJson });
  if (!res.ok) return { drafted: 0, skipped: 0, reason: res.reason };
  let drafted = 0, skipped = 0;
  for (const inq of res.inquiries) {
    const draft = draftFn ? await draftFn(inq) : await draftAnswer({ inquiry: inq, router, opsRoot });
    const review = reviewFn ? await reviewFn(inq, draft) : await ceoReview({ inquiry: inq, draft, router, opsRoot });
    const finalAnswer = review && typeof review.answer === 'string' ? review.answer.trim() : '';
    if (review && review.approved === true && finalAnswer) {
      await (saveDraftFn || conn.saveDraft)({ projectId, credentialJson, inquiryId: inq.id, draftAnswer: finalAnswer });
      drafted++;
    } else {
      skipped++; // CEO 미승인/파싱 실패 → 저장 안 함(PENDING 유지, 다음 주기 재시도)
    }
  }
  return { drafted, skipped };
}

// CLI: node inquiry-flow.mjs  → pending 문의 초안(→CEO검토→Firestore draftAnswer)
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  const { loadEnv } = await import('./env.mjs');
  loadEnv(opsRoot);
  const { createRouter } = await import('./router.mjs');
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8'));
  const projectId = conf.firebase?.projectId;
  const credRaw = process.env[conf.firebase?.credEnv || 'FIREBASE_SERVICE_ACCOUNT'] || '';
  const cred = credRaw && existsSync(credRaw) ? readFileSync(credRaw, 'utf8') : credRaw;
  const r = await draftPendingInquiries({ opsRoot, projectId, credentialJson: cred, router: createRouter() });
  console.log(`문의 초안(CEO 검토 통과 저장): ${r.drafted}건 · 미승인 보류 ${r.skipped}건${r.reason ? ` (${r.reason})` : ''}`);
}
