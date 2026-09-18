// 1:1 문의 답변 초안 흐름: pending 문의 → support 초안 → **CEO 검토(무조건)** → 통과분만
// Firestore inquiries.draftAnswer 에 저장(status=DRAFTED) → 어드민 답변창 placeholder 로 노출 →
// 운영자 검토/수정 후 인앱 게시(ANSWERED). CEO 미승인 초안은 저장 안 함(다음 주기 재시도).
import { readFileSync, writeFileSync, existsSync, mkdirSync, appendFileSync } from 'node:fs';
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

// 결정적 허위약속 가드 — 실제로 하지 않은 조치/확정 일정을 주장하는 초안을 차단(로컬 LLM 자가검열 불신).
// "감사합니다, 검토해 개선을 고려하겠습니다" 수준은 통과. "지시했다/보고했다/개선 예정/곧 업데이트"는 차단.
const OVER_PROMISE = [
  /지시(했|하였|해\s*두었|해\s*놓)/,               // "개선하도록 지시했습니다"
  /보고(하여|했|하였|해\s*두었)/,                   // "팀에 보고하여/보고했습니다"
  /작업을?\s*(진행하도록|착수|진행하고 있)/,        // "작업을 진행하도록/착수했"
  /(반영|수정|개선|업데이트|해결)\s*(될|할|하기로)\s*예정/, // "개선될 예정"
  /(곧|조만간|빠른\s*시일|머지않아).*(반영|수정|개선|업데이트|해결)/, // "곧 업데이트"
  /(조치|반영|수정|개선|해결)\s*(했|하였|완료)/,    // "조치했/개선 완료"
  /예정입니다/,
];
export function overPromises(text) { return OVER_PROMISE.some((re) => re.test(text || '')); }

// support 모델로 답변 초안 1건 생성(본문만).
export async function draftAnswer({ inquiry, router, opsRoot }) {
  const { LANG_GUARD } = await import('./lang.mjs');
  const support = readFileSync(join(opsRoot, 'agents', 'support', 'skill.md'), 'utf8');
  const cfg = JSON.parse(readFileSync(join(opsRoot, 'agents', 'support', 'config.json'), 'utf8'));
  const out = await router.complete({
    provider: cfg.model.provider, model: cfg.model.name,
    system: LANG_GUARD + support
      + '\n\n지금은 1:1 문의 답변 초안을 쓴다. 존댓말·간결·정확·겸손. 아래 사실만 근거로, 없는 기능/일정/가격 약속 금지.'
      + '\n\n[출력 규칙] 회원에게 그대로 보낼 답변 본문만 써라. "에스컬레이션","산출물","원문 요약" 같은 내부 라벨/머리말 금지. 인사말+본문 2~4문장.'
      + '\n[금지] 실제로 하지 않은 조치·확정 일정을 말하지 마라. "팀에 지시/보고했다", "개선 작업을 진행 중", "곧 업데이트/반영 예정", "수정 완료" 같은 표현 절대 금지. '
      + '버그/개선 제안에는 최대치로 "소중한 의견 감사합니다. 검토해 개선을 고려하겠습니다." 수준으로만 답하라(없는 착수/일정 약속 금지).\n\n'
      + APP_GROUNDING,
    messages: [{ role: 'user', content: `[카테고리] ${CAT_LABEL[inquiry.category] || inquiry.category}\n[문의]\n${inquiry.text}\n\n답변 초안만 출력(머리말 없이).` }],
  });
  return out.text.trim();
}

// CEO 검토 — support 초안 승인/수정 + (개선/버그면) dev 태스크 발행.
// {approved, answer(최종), devTask(개선·버그일 때 dev 지시 1줄), reason} JSON.
export async function ceoReview({ inquiry, draft, router, opsRoot }) {
  const { LANG_GUARD } = await import('./lang.mjs');
  const ceo = readFileSync(join(opsRoot, 'ceo', 'ceo.md'), 'utf8');
  const cfg = JSON.parse(readFileSync(join(opsRoot, 'ceo', 'config.json'), 'utf8'));
  const cat = CAT_LABEL[inquiry.category] || inquiry.category;
  const isDev = inquiry.category === 'IMPROVEMENT' || inquiry.category === 'BUG';
  const devNote = isDev
    ? `\n이 문의는 "${cat}"이다. 답변과 별개로, **dev 에이전트에게 내릴 구체적 태스크**를 devTask에 써라(무엇을 고칠지 명확히, 없는 기능 가정 금지). `
      + `**화면/UI/레이아웃/디자인/테마 변경이 포함되면, devTask에 반드시 "각 관련 화면의 as-is/to-be(개선 전·후) 스크린샷을 비교 가능하게 첨부하여 CEO에게 보고"라는 보고 요건을 함께 넣어라.** `
      + `답변(회원용)에는 "지시했다/곧 반영" 같은 확정 약속을 넣지 말고 "검토하겠습니다" 수준으로만.`
    : `\ndevTask는 빈 문자열("")로 둔다(개선/버그 아님).`;
  const out = await router.complete({
    provider: cfg.model.provider, model: cfg.model.name,
    system: LANG_GUARD + ceo + '\n\n지금은 support가 쓴 1:1 문의 답변 초안을 CEO로서 검토·승인하고, 개선/버그면 dev 태스크를 발행하는 일이다. 답변의 허위 약속·부정확·무례는 고친다.',
    messages: [{ role: 'user', content:
      `[문의 카테고리] ${cat}\n[문의]\n${inquiry.text}\n\n[support 초안]\n${draft}\n${devNote}\n\n`
      + `반드시 JSON만 출력: {"approved": true/false, "answer": "게시할 최종 답변(존댓말·본문만, 확정 약속 금지)", "devTask": "dev 지시 1줄(개선/버그만, 아니면 빈칸)", "reason": "간단 사유"}` }],
  });
  return extractJson(out.text);
}

const BACKLOG_HEADER = '# dev 백로그 — 1:1 문의(개선/버그)에서 CEO가 발행한 태스크';

// 개선/버그 문의 → dev 백로그(tasks/dev-backlog.md)에 CEO 발행 태스크 추가(문의 id로 중복 방지).
// 기존 항목/헤더를 파싱해 재작성 → "(대기 중인 태스크 없음)" 같은 플레이스홀더 잔여 없이 깔끔.
export function appendDevTask({ opsRoot, inquiry, devTask, now = new Date() }) {
  const dir = join(opsRoot, 'tasks');
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  const p = join(dir, 'dev-backlog.md');
  const raw = existsSync(p) ? readFileSync(p, 'utf8') : '';
  if (raw.includes(`문의 ${inquiry.id}`)) return false; // 이미 발행됨
  const { entries } = parseBacklog(raw);
  const cat = CAT_LABEL[inquiry.category] || inquiry.category;
  const entry = `## [${cat}] 문의 ${inquiry.id} (${now.toISOString()})\n- 상태: 대기\n- 원문: ${inquiry.text}\n- dev 지시: ${devTask}`;
  const all = [...entries.map((e) => e.text), entry];
  writeFileSync(p, `${BACKLOG_HEADER}\n\n${all.join('\n\n')}\n`);
  return true;
}

// 백로그 파싱 → {header, entries:[{id, status, text}]}. (순수)
export function parseBacklog(md) {
  const parts = (md || '').split(/\n(?=## )/);
  let header = '';
  const entries = [];
  for (const p of parts) {
    if (p.startsWith('## ')) {
      const id = (/문의\s+(\S+)/.exec(p) || [])[1] || null;
      const status = (/-\s*상태:\s*(\S+)/.exec(p) || [])[1] || '대기';
      entries.push({ id, status, text: p.replace(/\s+$/, '') });
    } else if (p.trim()) { header = p.replace(/\s+$/, ''); }
  }
  return { header: header || BACKLOG_HEADER, entries };
}

// dev 컨텍스트용 — 대기(미완료) 항목만. (순수)
export function openBacklog(md) {
  const { header, entries } = parseBacklog(md);
  const open = entries.filter((e) => e.status !== '완료');
  return `${header}\n\n${open.map((e) => e.text).join('\n\n') || '(대기 중인 태스크 없음)'}\n`;
}

// 특정 문의 태스크를 완료로 표시. 반환: 찾았는지.
export function markDone({ opsRoot, inquiryId }) {
  const p = join(opsRoot, 'tasks', 'dev-backlog.md');
  if (!existsSync(p)) return false;
  const { header, entries } = parseBacklog(readFileSync(p, 'utf8'));
  let found = false;
  for (const e of entries) {
    if (e.id === inquiryId && e.status !== '완료') {
      e.text = /-\s*상태:\s*\S+/.test(e.text)
        ? e.text.replace(/-\s*상태:\s*\S+/, '- 상태: 완료')
        : e.text.replace(/\n/, '\n- 상태: 완료\n');
      e.status = '완료'; found = true;
    }
  }
  writeFileSync(p, `${header}\n\n${entries.map((e) => e.text).join('\n\n')}\n`);
  return found;
}

// 완료 표시된 항목을 dev-backlog-done.md 로 아카이브 + 활성 백로그에서 제거. 반환: 아카이브 건수.
export function archiveDone({ opsRoot, now = () => new Date() }) {
  const p = join(opsRoot, 'tasks', 'dev-backlog.md');
  if (!existsSync(p)) return 0;
  const { header, entries } = parseBacklog(readFileSync(p, 'utf8'));
  const done = entries.filter((e) => e.status === '완료');
  const open = entries.filter((e) => e.status !== '완료');
  if (!done.length) return 0;
  const arch = join(opsRoot, 'tasks', 'dev-backlog-done.md');
  const ah = existsSync(arch) ? '' : '# dev 백로그 — 완료 아카이브\n\n';
  appendFileSync(arch, ah + done.map((e) => `${e.text}\n- 완료처리: ${now().toISOString()}`).join('\n\n') + '\n\n');
  writeFileSync(p, `${header}\n\n${open.map((e) => e.text).join('\n\n') || '(대기 중인 태스크 없음)'}\n`);
  return done.length;
}

// pending 문의 → support 초안 → CEO 검토(+개선/버그면 dev 태스크 발행) → 통과분만 draftAnswer 저장(DRAFTED).
// 답변에 결정적 허위약속 가드(overPromises) 적용 — 지어낸 조치/일정이면 저장 안 함.
export async function draftPendingInquiries({ opsRoot, projectId, credentialJson, router, fetchFn, draftFn, reviewFn, saveDraftFn, appendDevTaskFn, now = () => new Date() }) {
  const conn = await import('../connectors/inquiries.mjs');
  const res = await (fetchFn || conn.fetchPending)({ projectId, credentialJson });
  if (!res.ok) return { drafted: 0, skipped: 0, devTasks: 0, reason: res.reason };
  let drafted = 0, skipped = 0, devTasks = 0;
  for (const inq of res.inquiries) {
    const draft = draftFn ? await draftFn(inq) : await draftAnswer({ inquiry: inq, router, opsRoot });
    const review = reviewFn ? await reviewFn(inq, draft) : await ceoReview({ inquiry: inq, draft, router, opsRoot });
    const finalAnswer = review && typeof review.answer === 'string' ? review.answer.trim() : '';
    // 게이트: CEO 승인 + 본문 있음 + 허위약속 없음.
    if (!(review && review.approved === true) || !finalAnswer || overPromises(finalAnswer)) {
      skipped++; // 미승인/파싱실패/허위약속 → 저장 안 함(PENDING 유지, 다음 주기 재시도)
      continue;
    }
    await (saveDraftFn || conn.saveDraft)({ projectId, credentialJson, inquiryId: inq.id, draftAnswer: finalAnswer });
    drafted++;
    // 개선/버그 → CEO가 발행한 dev 태스크를 백로그에 기록(dev가 CEO 통해 받음).
    const isDev = inq.category === 'IMPROVEMENT' || inq.category === 'BUG';
    if (isDev && review.devTask && String(review.devTask).trim()) {
      const added = await (appendDevTaskFn || appendDevTask)({ opsRoot, inquiry: inq, devTask: String(review.devTask).trim(), now: now() });
      if (added) devTasks++;
    }
  }
  return { drafted, skipped, devTasks };
}

// CLI: node inquiry-flow.mjs             → pending 문의 초안(→CEO검토→Firestore draftAnswer)
//      node inquiry-flow.mjs done <id>   → dev 백로그 태스크 완료→아카이브
//      node inquiry-flow.mjs done        → 파일에서 수동 "완료" 표시분 아카이브
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const here = dirname(fileURLToPath(import.meta.url));
  const opsRoot = process.env.OPS_ROOT || join(here, '..');
  if (process.argv.includes('done')) {
    const id = process.argv[process.argv.indexOf('done') + 1];
    if (id) {
      const found = markDone({ opsRoot, inquiryId: id });
      const n = archiveDone({ opsRoot });
      console.log(found ? `완료 처리: 문의 ${id} → 아카이브 ${n}건` : `문의 ${id} 를 백로그에서 못 찾음`);
    } else {
      console.log(`수동 '완료' 표시분 아카이브: ${archiveDone({ opsRoot })}건`);
    }
    process.exit(0);
  }
  const { loadEnv } = await import('./env.mjs');
  loadEnv(opsRoot);
  const { createRouter } = await import('./router.mjs');
  const conf = JSON.parse(readFileSync(join(opsRoot, 'connectors', 'config.json'), 'utf8'));
  const projectId = conf.firebase?.projectId;
  const credRaw = process.env[conf.firebase?.credEnv || 'FIREBASE_SERVICE_ACCOUNT'] || '';
  const cred = credRaw && existsSync(credRaw) ? readFileSync(credRaw, 'utf8') : credRaw;
  const r = await draftPendingInquiries({ opsRoot, projectId, credentialJson: cred, router: createRouter() });
  console.log(`문의 초안(CEO 통과 저장): ${r.drafted}건 · 미승인/차단 보류 ${r.skipped}건 · dev 태스크 발행 ${r.devTasks}건${r.reason ? ` (${r.reason})` : ''}`);
}
