# 런북: 1:1 문의 답변 초안 흐름 (support → CEO 검토 → 어드민 게시)

스펙: `docs/superpowers/specs/2026-09-17-faq-qna-design.md`. 앱 화면·스키마는 app/.

## 흐름 (자동 + 인앱)
```
회원 문의 작성(PENDING·확인 중)
  → [cron: support 주기] support 에이전트 답변 초안
  → CEO 검토(무조건) — 승인/수정
  → 통과분만 Firestore inquiries.draftAnswer 저장(status=DRAFTED)
  → 어드민 "1:1 문의 관리" 답변창에 초안이 placeholder + "AI 초안 불러오기(CEO 검토 완료)" 버튼
  → 운영자 검토/수정 → 인앱 게시(ANSWERED)
```
- **대상**: `status=PENDING`(아직 초안 없는 "확인 중") 문의만. ANSWERED는 제외, DRAFTED는 재작성 안 함(낭비 방지).
- **CEO 게이트 필수**: CEO 미승인/파싱 실패 초안은 저장 안 함 → PENDING 유지, 다음 주기 재시도.

## 구성 (ops)
- `connectors/inquiries.mjs` — `fetchPending`(PENDING) · `saveDraft`(draftAnswer+DRAFTED) · `postAnswer`(ANSWERED) · `setStatus`. no-op 가드.
- `engine/inquiry-flow.mjs` — `draftAnswer`(support) · `ceoReview`(CEO 승인/수정) · `draftPendingInquiries`(fetch→draft→CEO→save) + CLI.
- `engine/dispatch.mjs` — **support 잡이 cron 발화할 때마다** `draftPendingInquiries` 자동 실행(`defaultDraftInquiries`). 자격증명 없으면 no-op.

## 수동 실행(선택)
```
node ops/engine/inquiry-flow.mjs   # PENDING 문의 → 초안 → CEO 검토 → 통과분 draftAnswer 저장
```
출력: `문의 초안(CEO 검토 통과 저장): N건 · 미승인 보류 M건`.

## 게시(운영자, 인앱)
어드민 > 운영 탭 > 1:1 문의 관리 → 카드 답변창에 CEO 검토 완료 초안이 뜸 → "AI 초안 불러오기"로 채워 수정 → **게시**(status=ANSWERED). 인앱 게시 권한은 Firestore rules(isMaster) 강제.

## 활성화
`FIREBASE_SERVICE_ACCOUNT`(설정됨) + 로컬 Ollama(support/CEO=exaone). 실제 문의가 쌓여야 동작. support cron: 매일 10·13·17시(KST).
