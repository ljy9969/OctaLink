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

## 실행 (수동)
```
node ops/engine/run-chain.mjs research bug     # safe 에이전트 예시
```
`reports/`에 리포트가, gated 에이전트는 `approvals/`에 제안서가 생성됩니다.

## 트리거 자동화 (디스패처 + Windows 작업 스케줄러)
트리거의 **단일 출처 = 각 `agents/<name>/config.json`의 `trigger`(cron) + `ceo/cadence.json`.**
```
node ops/engine/dispatch.mjs --list     # 유효 스케줄(9개 잡) 확인
node ops/engine/dispatch.mjs            # 지금 실행해야 할 잡만 1회 처리
```
- `dispatch.mjs`가 cron을 읽어 "지금 발화할 CEO/에이전트"를 판정해 실행. `state/dispatch.json`의 last-run으로 **중복 방지 + 미실행 캐치업(24h 캡)**. 타임존 **KST 고정**.
- **작업 스케줄러 등록됨**: `OctaLink-Ops-Dispatcher` (15분마다 `ops/engine/dispatch.cmd` 실행 → 로그 `state/dispatch.log`). 로그인 세션에서 동작(로컬 Ollama 필요).
  - 해제: `schtasks /Delete /TN OctaLink-Ops-Dispatcher /F`  ·  주기 변경: `/Create ... /MO <분>`
- CEO는 로컬 모델(`ceo/config.json`)로 state·리포트를 읽어 `tasks/<agent>.md` 발행.
- **이벤트 트리거**: dev는 cron(화 14시)에 더해 **GitHub 이슈 배정 이벤트**로도 발화. 디스패처가 매 실행 `connectors/github-issues.mjs`로 새 배정 이슈를 폴링 → `tasks/dev.md` 발행 + dev 실행(이슈번호로 중복 방지). `GH_TOKEN` 필요(`runbooks/github-events.md`), 없으면 no-op. 커넥터 설정=`connectors/config.json`.

현재 주기: CEO 매일 09:00 / bug 00·08·16시 / support 10·13·17시 / payments 09:30 / research 월·목 21시 / social 월·수·금 08·18시 / sales·ads 월 11시 / dev 화 14시(+이슈 배정 이벤트).

## 커넥터 (앱데이터·이벤트) — `ops/connectors/`
- **GitHub 이슈**(`github-issues.mjs`): dev 이벤트 트리거(위). `GH_TOKEN` 필요.
- **Firebase Firestore**(`firebase.mjs`): 앱 실데이터를 읽어 `context/<agent>.md` 스냅샷 생성 → 해당 에이전트가 실행 시 참고(run-agent가 자동 주입). 설정 `connectors/config.json`의 `firebase.context`(에이전트→컬렉션). `node ops/connectors/firebase.mjs`로 갱신. 자격증명(`FIREBASE_SERVICE_ACCOUNT`)+`firebase-admin` 설치 필요(`runbooks/firebase.md`), 없으면 placeholder만.
- 소셜·광고 실집행 커넥터는 계정·API·(광고)비용이 필요해 보류 — 지금은 초안/제안서(`approvals/`)까지.

## 안전장치
- risk `safe`(research·bug) = 자동, `gated`(나머지) = 승인 대기
- 종료조건: 검증 통과 시 종료 · 재시도 ≤ config · 토큰비용 ≤ config
- 엔진 일일 총비용 kill-switch(기본 $5/day)
