# OctaLink Loop Engineering 운영 시스템 — 설계 스펙

- 작성일: 2026-09-07
- 상태: 설계 확정(브레인스토밍 승인) → writing-plans 대기
- 참고: Boris Cherny "Loop Engineering"(sense–decide–act–check), 뉴스레터 및 `github.com/cocodedk/loop-engineering`

## 1. 목표

지금까지 구현된 OctaLink(안드로이드 + Firebase Functions/Firestore)를 **Loop Engineering 기반으로 "스스로 운영"** 되게 한다. 방향을 정하는 **CEO 루프 1개**(똑똑·비싼 모델) 아래에서, **8개 전담 서브 에이전트**(가볍·저렴한 오픈소스 모델)가 실행과 검증을 반복한다.

## 2. 확정된 핵심 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 실행 기반 | **하이브리드** — 지금은 Claude Code/로컬에서 이식 가능한 스킬/스크립트로 실행, 서버 계정 생기면 동일 코드를 클라우드로 승격 | 계정 없이 오늘 실제 루프 시연 가능 + 24/7로 점진 승격 |
| 모델 계층화 | **CEO 루프 = Claude(Opus/Sonnet) 고정**. **8 에이전트 = 프로필로 선택**: 버전 A OpenRouter 오픈소스(DeepSeek/Llama/Qwen) 또는 버전 C 로컬 Ollama(무료). `apply-profile`로 일괄 전환 | 방향·판단은 프런티어; 실행은 비용·품질 트레이드오프로 선택 |
| ~~버전 B (Claude Max OAuth)~~ | **폐기** — Max 구독은 API 호출 미포함(크레딧 필요)이라 에이전트 자동 실행 불가로 확인됨 | 검증 결과 반영 |
| 자율성 | **게이트형 자율운영** — 루프는 계속 돌되, 되돌릴 수 없거나 외부로 나가는 행동은 승인 대기 | 광고·결제·공개게시·고객응대 리스크 차단 |
| 계정 생성 | **제가 대신 생성 불가** — 서버·결제사 가입은 실명/KYC/카드/약관동의 필요. 저는 런북 + 자격증명 슬롯 + 연결코드 제공, 가입은 사용자 본인 | 신원 도용·법적 동의 불가 |
| 브랜치 | `main` 단일(feature 브랜치 없음), 격리는 **git worktree**로 | 사용자 정책 |

## 3. 아키텍처 (2계층 루프)

```
        ┌──────────────────────────────────────────────────┐
        │  CEO 루프   (Claude · 비싼 모델)                   │
        │  주기 트리거 →                                     │
        │   1. state/ 지표 읽기  2. 우선순위·방향 결정        │
        │   3. tasks/<agent>.md 로 이번 주기 목표 발행        │
        │   4. reports/ 검토 + 승인 게이트 처리              │
        │  종료조건: 8개 목표 발행 & 리포트 검토 완료         │
        └───────────────┬──────────────────────▲───────────┘
              tasks/*.md │ (지시)               │ reports/*.md (보고)
        ┌───────────────▼──────────────────────┴───────────┐
        │  LOOP ENGINE (Node 러너 + model router)            │
        └─┬────┬────┬────┬────┬────┬────┬────┬──────────────┘
OpenRouter→│개발│영업│광고│리서│버그│고객│결제│소셜│
           └────┴────┴────┴────┴────┴────┴────┴────┘
  각 루프 한 바퀴:
  ①방아쇠 → ②워크트리 → ③스킬 → ④MCP → 실행 → ⑤검증서브에이전트 → ⑥상태파일 → ⑦종료조건
```

## 4. 7부품 매핑 (사용자 정의 그대로)

| # | 부품 | 구현 |
|---|------|------|
| 1 | 방아쇠(Trigger) | `ops/triggers/` — 크론·이벤트 정의(매일 09시, 앱 업데이트 push, 버그 이벤트, 경쟁앱 조사 주기 등) |
| 2 | 워크트리(Worktree) | 에이전트 실행마다 `git worktree` 격리 |
| 3 | 스킬(Skill) | `ops/agents/<name>/skill.md` — 작업 규칙("숫자는 출처 링크", "국내1·해외1 균형", "형용사 남발 금지" 등) |
| 4 | MCP(Connector) | `ops/mcp/` — Firebase(지금), 소셜·광고·결제(자격증명 슬롯) |
| 5 | 서브에이전트(Verifier) | `ops/agents/<name>/verifier.md` — "출처 살아있나·사례 균형·지난주 중복 없나"를 **참/거짓** 판정 |
| 6 | 상태파일(State) | `ops/state/` (repo) — 지표·이력·ledger |
| 7 | 종료조건(Stop) | 각 `config.yaml` — "검증 통과 시 종료, 재시도 ≤3, 토큰비용 ≤$X" |

## 5. 8개 전담 에이전트 명세

| 에이전트 | 목적 | risk | 주요 방아쇠 | 스킬 규칙(예) | 검증(참/거짓 예) | 종료조건 기본값 |
|---|---|---|---|---|---|---|
| **개발 dev** | 이슈/개선 구현·PR 초안 | gated(머지) | 앱 업데이트 주기, 버그 티켓 | 테스트 먼저, 컨벤션 준수, 작은 단위 | 빌드/테스트 통과? 회귀 없나? | 검증 통과 시 종료·재시도≤3·≤$0.5 |
| **영업 sales** | 체육관/제휴 리드 발굴·콜드 아웃리치 초안 | gated | 주간 | 과장 금지·팩트 중심, 개인정보 보호 | 연락처 출처 합법? 톤 적절? | 승인 대기 후 종료 |
| **광고 ads** | 광고 소재·타겟·예산안 | gated(집행=돈) | 주간/캠페인 이벤트 | 청구 지표 링크, 예산 상한 준수 | 예산 상한 내? 클레임 근거? | 제안서만·집행은 승인 후 |
| **리서치 research** | 경쟁앱·시장·기능 조사 리포트 | **safe** | 주 2회 | 숫자=출처 링크, 국내1·해외1 균형, 형용사 남발 금지 | 링크 전부 생존? 사례 균형? 지난주 중복 없나? | 검증 통과 시 종료·≤$0.3 |
| **버그감시 bug** | 크래시/에러 로그 감시·트리아지 | **safe** | 실시간/시간별(로그 이벤트) | 재현경로 명시, 심각도 라벨 | 실제 재현 가능? 중복 티켓 아님? | 티켓화 후 종료·≤$0.2 |
| **고객응대 support** | 문의/리뷰 답변 초안 | gated(공개) | 신규 문의 이벤트 | 존댓말, 약속 남발 금지, 정책 링크 | 정책과 일치? 개인정보 노출 없나? | 초안→승인 후 게시 |
| **결제 payments** | 매출/환불/구독 대사·이상 감지 | gated(돈) | 일별 | 숫자 출처=결제사 원장, 반올림 규칙 | 원장과 합치? 이상거래 근거? | 리포트+이상시 승인요청 |
| **소셜 social** | SNS 피드/스토리 기획·초안 | gated(공개) | 주 3회/캠페인 | 브랜드 톤, 저작권 안전 소재만, 사실 기반 | 저작권 안전? 사실 정확? 중복 아님? | 초안→승인 후 게시 |

## 6. 레포 폴더 구조 (신규 `ops/`)

```
ops/
  README.md              운영 시스템 개요·실행법
  ceo/
    ceo.md               CEO 루프 프롬프트(방향·우선순위·게이트)
    cadence.yaml         CEO 주기(예: 매일 08:00 KST + 주간 월요일 전략)
    inbox/               승인 대기 항목(사람이 OK/반려)
  engine/
    run-agent.mjs        단일 에이전트 루프 러너(7부품 실행)
    run-chain.mjs        여러 에이전트 오케스트레이션
    model-router.mjs     provider 선택(claude/openrouter/ollama)
    gate.mjs             risk=gated → approvals/ 로 라우팅
  agents/<name>/         (dev sales ads research bug support payments social)
    config.yaml          trigger·model·budget·stop-condition·risk
    skill.md             규칙
    verifier.md          참/거짓 감독관
  triggers/              크론·이벤트 정의
  state/                 지표·이력·ledger (JSON/MD)
  reports/               에이전트→CEO 보고
  tasks/                 CEO→에이전트 지시
  approvals/             gated 제안서(사람 승인 대기)
  mcp/                   커넥터 설정 + .env.example(자격증명 슬롯)
  runbooks/              서버·결제 가입 런북(사용자가 수행)
```

## 7. 에이전트 해부 예시 — `social`

- **config.yaml**: `trigger: cron "0 10 * * 1,3,5"`(월수금 10시), `model: openrouter/deepseek-chat`, `risk: gated`, `budget.max_retries: 3`, `budget.max_usd: 0.3`, `stop: verifier_pass && drafted`
- **skill.md**: 브랜드 톤(OctaLink), 저작권 안전 소재만(무료·상업가능), 사실 기반, 존댓말, 해시태그 규칙
- **verifier.md**: `저작권_안전==true` && `사실_정확==true` && `지난_게시와_중복==false` → 참
- **흐름**: 방아쇠 발화 → worktree 생성 → skill 로드 → (MCP) 자산/지표 조회 → 초안 생성 → verifier 판정(거짓이면 재시도 ≤3) → 통과 시 `approvals/social-YYYYMMDD.md` 생성 → 사람 승인 → 게시 → `state/social.json` 기록 → 종료

## 8. 게이트형 자율운영 규칙

- `risk: safe` (research, bug) → 검증 통과 즉시 자동 반영, 사람 개입 0
- `risk: gated` (나머지 6) → 결과물은 `approvals/`에 제안서로 생성, 사람이 OK해야 외부 실행(게시/집행/머지)
- 돈이 나가는 액션(ads 집행, payments 이동)은 **항상** 게이트 + 예산 상한(config) 이중 안전

## 9. 모델 라우팅 (프로필로 전환, `apply-profile`)

에이전트 실행 모델은 `ops/profiles/`의 버전을 `apply-profile <name>`로 8개 config에 일괄 반영한다.

| 버전 | provider | 모델 | 비용 | 필요물 |
|---|---|---|---|---|
| **A (기본)** | openrouter | 역할별 오픈소스(DeepSeek-V3 / Llama-3.3-70B / 한국어 Qwen-2.5-72B) | 저렴(종량) | OpenRouter 키 |
| **C** | ollama | 로컬 qwen2.5(7B/14B) | **무료** | Ollama 설치+pull(카드·계정 X) |
| CEO 루프 | (별도) Claude | Opus/Sonnet | — | 방향·판단, 고정 |

**폐기: 구 버전 B(Claude Max 구독 OAuth, `anthropic-oauth`)** — Max 구독은 API 호출을 포함하지 않아(`api.anthropic.com`이 크레딧 요구) 에이전트 자동 실행에 사용 불가함이 확인됨. 관련 프로필·provider·런북 제거.

**엔진**: provider는 `config.model.provider`를 권위로 사용, 검증 호출 비용도 예산/원장/일일캡에 합산. provider 자격증명 없으면 dryrun 폴백.

## 10. 단계별 구축 계획

**Phase 1 — 엔진 + 골격 (계정 없이 오늘 구축·시연)**
1. `ops/` 스캐폴딩 + `engine/`(run-agent, run-chain, model-router, gate)
2. CEO 루프(`ceo/`) + 8개 에이전트 정의(config/skill/verifier) 전부
3. `triggers/ state/ reports/ tasks/ approvals/ mcp/(.env.example) runbooks/`
4. **safe 에이전트 2개(research, bug)를 실제 end-to-end 가동**(OpenRouter 키 있으면 실주행, 없으면 dry-run)으로 참조 구현 완성
5. 서버·결제·소셜·광고 **가입 런북 + 자격증명 슬롯** 작성

**Phase 2 — gated 에이전트 순차 연결 (자격증명 도착 시)**
- social → support → ads → payments → sales → dev 순으로 MCP 커넥터 연결 + 승인 플로우 실전화

**Phase 3 — 서버 승격**
- 동일 엔진을 Cloud Run/VM에 배포해 24/7. (서버 계정 준비 후)

## 11. 사용자님께 필요한 것 (필요 시점에 요청)

| 시점 | 필요물 | 용도 |
|---|---|---|
| Phase 1 실주행 | **OpenRouter 계정 + API 키 + $5~10 크레딧** | 8 에이전트 실행 |
| 이미 보유 | Firebase 서비스 계정(있음) | 앱 데이터 MCP |
| Phase 2 | 소셜 API 토큰(Instagram/Threads/X), 고객 채널 접근 | social/support |
| Phase 2 | 광고 계정(Meta/Google Ads) | ads (집행은 게이트) |
| Phase 2 | 결제사 가입(Toss Payments/Stripe) — 사업자·정산계좌·KYC | payments |
| Phase 3 | 클라우드 호스트(Cloud Run/VM) 계정 | 24/7 승격 |

각 항목은 `ops/runbooks/`에 "무엇을·어디서·어떻게" 단계로 정리하고, 자격증명은 `.env`(git 제외) 슬롯에 사용자님이 직접 넣습니다.

## 12. 종료조건·예산 기본값

- 전 에이전트 공통: **검증 통과 시 종료**, **재시도 최대 3회**, **에이전트 1회 실행 토큰비용 상한(config `budget.max_usd`)**
- 일일 전체 비용 상한(엔진 레벨 kill-switch): 기본 **$5/day** (조정 가능)
- CEO 루프: 8개 목표 발행 + 리포트 검토 완료 시 종료

## 13. 비범위(YAGNI) / 리스크

- **비범위**: 실제 계정 자동 생성, 무승인 자동 광고집행/자동 공개게시/자동 결제이동, 자체 LLM 파인튜닝
- **리스크**: 저가 모델 품질 편차 → verifier 게이트로 방어; 외부 API 비용 폭주 → 예산 kill-switch; 자격증명 유출 → `.env` git 제외 + 슬롯화
