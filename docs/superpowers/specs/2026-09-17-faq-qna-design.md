# 설정 화면 FAQ + Q&A(1:1 문의) 설계

- 날짜: 2026-09-17
- 상태: 승인됨(브레인스토밍, FAQ는 CEO 검토 완료) → 구현
- 위치: 프로필 > 설정 화면, **UI 테마 카드 바로 아래**
- 앱: Android Kotlin/Compose (`app/.../ui/screens/profile/ProfileSettingsScreen.kt`)

## 1. FAQ (정적)

UI 테마 아래 **"자주 묻는 질문" 카드**(아코디언). 질문 탭 → 답변 펼침. 화면 이동 없음. 내용은 support+dev 에이전트 초안 → Opus 그라운딩 검수 → CEO 승인.

8문항(존댓말·간결·정확·겸손): ①가입 승인 대기 ②출석 체크 ③벨트/실력 승급 ④교류전 참여 ⑤수업 리마인더 설정 ⑥알림 미수신(권한/배터리) ⑦로그아웃 데이터 보존 ⑧탈퇴 기록 보존(완전삭제는 운영자 요청). (문구는 구현 코드에 상수로.)

## 2. Q&A (1:1 문의 게시판)

FAQ 카드 아래 **"1:1 문의" 행 → 전용 화면**. 사용자가 문의를 남기면 support 에이전트가 답변 초안을 만들고, 운영자/CEO 승인 후 게시된다.

### 2.1 정책
- **공개 범위: 1:1 비공개** — 작성자 본인과 운영자만 열람. (공개 게시판 아님)
- **카테고리: 작성자가 선택**(자동분류 X, 리소스 절감): `칭찬` / `개선 제안` / `문의` / `버그 신고`.
- **답변 흐름**: 사용자 문의 → **support 에이전트 답변 초안(gated)** → **운영자/CEO 승인·수정** → 게시(status=answered) → 사용자 열람.

### 2.2 데이터 모델 (Firestore)
- `inquiries/{inquiryId}`: `{ authorId, authorName, gymId, category, text, status: 'pending'|'drafted'|'answered', answer?, answeredBy?, createdAt, answeredAt? }`
  - 보안 규칙: 작성자는 본인 문서만 읽기/생성. 운영자(MASTER/CREATOR)는 전체 읽기 + answer/status 쓰기. 일반 회원은 타인 문서 접근 불가.
  - 상태: `pending`(작성됨) → `drafted`(초안 있음, 미승인) → `answered`(게시됨).

### 2.3 앱 UI (Compose)
- 설정 "1:1 문의" 행 → `InquiryScreen`.
- `InquiryScreen`: 상단 작성 폼(카테고리 드롭다운 + 텍스트 + 보내기) + 내 문의 목록(카테고리 배지·상태·답변). 커뮤니티 게시판 패턴(`ui/screens/community`) 참고.
- 답변 대기 중이면 "확인 후 답변드리겠습니다" 안내. 답변되면 답변 표시.

### 2.4 ops 연동 (support 에이전트)
- `connectors/firebase.mjs`가 `status='pending'` 문의를 support 컨텍스트로 주입(기존 contextMap에 `support: ['inquiries(pending)']` 추가 — 쿼리 필터는 reader에서).
- support 에이전트: 각 문의에 **답변 초안**(존댓말·간결·겸손, 정책 링크 근거) 작성 → `approvals/`(gated).
- 운영자/CEO 승인 → 답변을 `inquiries` 문서에 기록(`status='answered'`). **자동 게시 안 함**(사람 승인).
- support 스킬은 이미 "문의 답변 초안·게시 게이트" 역할과 일치. 입력 소스만 연결.

## 3. 구현 단계
| 단계 | 범위 | 위치 |
|---|---|---|
| 1. FAQ 카드 | 정적 아코디언 + 상수 문구 | 앱(ProfileSettingsScreen) — **이번** |
| 2. Q&A 앱 | InquiryScreen + Firestore repo + 보안규칙 + 설정 진입행 | 앱 — **이번/다음** |
| 3. Q&A ops | support 컨텍스트에 pending 문의 주입 + 답변 초안 게이트 | ops |

## 4. 범위 밖
- 실시간 채팅, 첨부파일, 공개 게시판, 답변 자동게시(항상 사람 승인).

## 5. 테스트
- 앱: repo/보안규칙(본인만 읽기) — 기존 테스트 패턴. ops: support 컨텍스트 주입·초안 형식.
