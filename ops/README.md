# OctaLink 운영 시스템 (Loop Engineering)

CEO 루프(Claude) 아래 8개 전담 에이전트가 실행·검증을 반복하며 앱을 운영합니다.
엔진은 `ops/engine/`(별도 문서), 이 폴더는 **운영 레이어**입니다.

## 구조
- `ceo/` — CEO 루프(방향·우선순위·승인 게이트)와 cadence, 승인 inbox
- `agents/<name>/` — 8 에이전트: `config.json`(트리거·모델·예산·종료조건·risk), `skill.md`(작업 규칙), `verifier.md`(참/거짓 감독관)
- `triggers/` — 크론·이벤트 스케줄
- `state/` — 지표·이력·비용 원장(ledger.jsonl) *(런타임 생성)*
- `tasks/` — CEO→에이전트 지시(`<agent>.md`) *(런타임 생성)*
- `reports/` — 에이전트→CEO 보고 *(런타임 생성)*
- `approvals/` — gated 제안서(사람 승인 대기) *(런타임 생성)*
- `mcp/` — 커넥터 설정 + `.env`(자격증명, git 제외)
- `runbooks/` — 서버·결제·API 가입 런북(사용자가 수행)

## 모델 선택 (에이전트별 자유)
각 `agents/<name>/config.json`의 `model`에서 provider를 고릅니다:
- `{"provider":"openrouter","name":"openrouter/deepseek/deepseek-chat"}` — 저가 오픈소스
- `{"provider":"openrouter","name":"openrouter/meta-llama/llama-3.3-70b-instruct"}`
- `{"provider":"anthropic","name":"anthropic/claude-haiku-4-5-20251001"}` — Claude 저가(한국어 카피 품질↑)
- `{"provider":"dryrun","name":"dryrun"}` — 키 없이 배선 점검
키가 없으면 openrouter/anthropic는 자동으로 dryrun으로 폴백합니다(배선은 계속 검증 가능).

## 실행 (dry-run, 키 불필요)
```
node ops/engine/run-chain.mjs research bug     # safe 에이전트 예시
```
`reports/`에 리포트가, gated 에이전트는 `approvals/`에 제안서가 생성됩니다.

## 안전장치
- risk `safe`(research·bug) = 자동, `gated`(나머지) = 승인 대기
- 종료조건: 검증 통과 시 종료 · 재시도 ≤ config · 토큰비용 ≤ config
- 엔진 일일 총비용 kill-switch(기본 $5/day)
