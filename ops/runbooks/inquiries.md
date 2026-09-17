# 런북: 1:1 문의 답변 흐름 (support 에이전트)

스펙: `docs/superpowers/specs/2026-09-17-faq-qna-design.md` (3단계). 앱 화면·스키마는 앱 저장소(app/).
흐름: 회원 문의(PENDING) → **support 초안**(approvals/) → **운영자 승인**(파일 편집) → **Firestore 게시**(ANSWERED).

## 구성 (ops)
- `connectors/inquiries.mjs` — Firestore 읽기/쓰기: `fetchPending`(status=PENDING) · `postAnswer`(ANSWERED) · `setStatus`. 자격증명 없으면 no-op.
- `engine/inquiry-flow.mjs` — `renderProposal`/`parseProposal`(순수) + `answerPending`/`applyApproved` + CLI.

## 사용법
1. **초안 작성** (support 모델 exaone):
   ```
   node ops/engine/inquiry-flow.mjs
   ```
   → PENDING 문의마다 `ops/approvals/inquiry-<id>.md` 생성(문의 + 답변 초안), Firestore 상태 DRAFTED.
2. **운영자 검토·승인**: `ops/approvals/inquiry-<id>.md` 를 열어 답변 초안을 수정하고,
   `- 상태: 대기` 를 **`- 상태: 승인`**(게시) 또는 **`- 상태: 반려`**(폐기)로 변경.
3. **게시**:
   ```
   node ops/engine/inquiry-flow.mjs apply
   ```
   → "승인" 표기분을 Firestore 에 게시(status=ANSWERED, answeredBy=ops). 앱의 작성자에게 "답변 완료"로 노출.
   처리된 파일은 `inquiry-<id>.done.md` 로 보관, "반려"는 게시 없이 .done 처리.

## 안전장치
- **자동 게시 없음** — 항상 사람이 파일에서 "승인"으로 바꿔야 게시됨(게이트).
- 초안은 `APP_GROUNDING`(실기능·정책)만 근거 — 없는 기능/일정/가격 약속 금지. 그래도 게시 전 운영자 확인 필수.
- 활성화 = `FIREBASE_SERVICE_ACCOUNT`(이미 설정됨). 실제 문의가 쌓여야 동작.

## (선택) 자동화
디스패처 cron 에 `inquiry-flow.mjs`(초안)를 support 주기에 맞춰 걸 수 있음. 게시(apply)는 운영자 수동 권장.
