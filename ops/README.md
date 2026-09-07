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

## 모델 버전 (프로필, 한 줄로 전환)
`ops/profiles/`의 버전을 `apply-profile`로 8개 에이전트 config에 한 번에 반영합니다.

```
node ops/engine/apply-profile.mjs openrouter   # 버전 A (기본 활성)
node ops/engine/apply-profile.mjs ollama       # 버전 C (로컬 무료)
```

- **버전 A — OpenRouter (`profiles/openrouter.json`)**: 전 에이전트 `provider=openrouter`, 역할별 오픈소스. 한국어 중요한 sales/support/social은 **Qwen-2.5-72B**, 나머지는 DeepSeek-V3/Llama-3.3-70B. **키 1개(`OPENROUTER_API_KEY`)** 필요.
- **버전 C — 로컬 Ollama (`profiles/ollama.json`)**: 전 에이전트 `provider=ollama`, 내 PC에서 로컬 모델(qwen2.5). **완전 무료·카드/계정 불필요.** 소형 모델이라 품질은 낮음. 설치·모델 pull은 `runbooks/ollama.md` 참고.

> 참고: Claude Max 구독으로 돌리는 방안(구 버전 B)은 폐기됐습니다 — Max 구독은 API 호출을 포함하지 않아(크레딧 필요) 에이전트 자동 실행에 쓸 수 없습니다.

provider별 자격증명(`ops/mcp/.env`)이 없으면 자동으로 **dryrun 폴백**하여 배선은 계속 검증됩니다. 개별 에이전트만 바꾸려면 해당 `agents/<name>/config.json`의 `model`을 직접 수정해도 됩니다.

## 실행 (dry-run, 키 불필요)
```
node ops/engine/run-chain.mjs research bug     # safe 에이전트 예시
```
`reports/`에 리포트가, gated 에이전트는 `approvals/`에 제안서가 생성됩니다.

## 안전장치
- risk `safe`(research·bug) = 자동, `gated`(나머지) = 승인 대기
- 종료조건: 검증 통과 시 종료 · 재시도 ≤ config · 토큰비용 ≤ config
- 엔진 일일 총비용 kill-switch(기본 $5/day)
