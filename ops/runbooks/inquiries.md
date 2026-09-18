# 런북: 1:1 문의 답변 초안 흐름 (support → CEO 검토 → 어드민 게시)

스펙: `docs/superpowers/specs/2026-09-17-faq-qna-design.md`. 앱 화면·스키마는 app/.

## 흐름 (자동 + 인앱)
```
회원 문의 작성(PENDING·확인 중)
  → [cron: support 주기] support 에이전트 답변 초안
  → CEO 검토(무조건) — 답변 승인/수정 + (개선/버그면) dev 태스크 발행
  → [결정적 허위약속 가드] 답변에 없는 조치/일정 주장 있으면 저장 거부
  → 통과분만 Firestore inquiries.draftAnswer 저장(status=DRAFTED)
  → 개선/버그면 CEO devTask를 tasks/dev-backlog.md 에 기록 → dev 컨텍스트로 주입(dev가 CEO 통해 받음)
  → 어드민 "1:1 문의 관리" 답변창에 초안 사전 입력 → 운영자 검토/수정 → 인앱 게시(ANSWERED)
```
- **대상**: `status=PENDING`(아직 초안 없는 "확인 중") 문의만. ANSWERED 제외, DRAFTED 재작성 안 함.
- **CEO 게이트 필수**: 미승인/파싱실패/허위약속 초안은 저장 안 함 → PENDING 유지, 다음 주기 재시도.
- **허위약속 가드(결정적)**: `overPromises()` — "지시했다/보고했다/개선 예정/곧 업데이트/수정 완료" 등 없는 조치·확정 일정 표현이 답변에 있으면 코드가 차단(로컬 LLM 자가검열 불신). 답변은 최대 "검토해 개선을 고려하겠습니다" 수준.
- **개선/버그 → dev 지시**: CEO 검토가 `devTask`(구체 지시)를 발행 → `tasks/dev-backlog.md`(문의 id로 중복 방지) → `defaultRefresh`가 dev 컨텍스트('문의 개선·버그 백로그' 구획)로 주입해 **dev가 CEO 통해 태스크 수령**.
  - **UI/화면/디자인/테마 변경**이면 devTask에 "각 관련 화면 **as-is/to-be(개선 전·후) 스크린샷 비교 첨부해 CEO 보고**" 요건이 자동 포함(ceoReview 규칙).

## dev 백로그 생애주기 (수동 완료)
`tasks/dev-backlog.md`는 **누적**(append). 각 항목은 `- 상태: 대기`. dev 컨텍스트에는 **대기 항목만** 주입(완료분 제외 → 누적 방지).
- **완료 처리**: 작업이 끝나면(운영자가 dev의 gated PR 승인·머지 후) 수동으로:
  ```
  node ops/engine/inquiry-flow.mjs done <문의id>
  ```
  → 해당 항목 `상태: 완료` + `tasks/dev-backlog-done.md` 로 아카이브 + 활성 백로그에서 제거.
- 또는 `tasks/dev-backlog.md`에서 직접 `상태: 대기`→`완료`로 고친 뒤 `node ops/engine/inquiry-flow.mjs done`(인자 없이) 실행 → 완료 표시분 일괄 아카이브.
- (dev-backlog*.md는 gitignore 런타임 파일.)

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
