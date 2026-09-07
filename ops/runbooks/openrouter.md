# 런북: OpenRouter 키 발급 (8 에이전트 저가 모델 — 가장 먼저 필요)

> 목적: 8개 에이전트가 실제로 결과를 내려면 저가 모델 provider 키가 필요합니다.
> 이 단계는 **사용자 본인**이 수행합니다(카드 등록 필요). 예상 비용 $5~10로 충분히 오래 씁니다.

1. https://openrouter.ai 가입(구글/깃허브 로그인 가능).
2. Settings → Credits 에서 **$5~10 충전**(카드).
3. Keys → **Create Key** → 키 복사(`sk-or-...`).
4. `ops/mcp/.env` 파일을 만들고(없으면 `.env.example` 복사) `OPENROUTER_API_KEY=` 뒤에 붙여넣기.
   - `.env`는 git에 올라가지 않습니다(자동 제외).
5. (선택) Settings → Privacy 에서 데이터 로깅/학습 opt-out, 특정 provider로 제한 가능(민감 에이전트용).
6. 검증: `node ops/engine/run-chain.mjs research` 실행 → `ops/reports/`에 실제 모델 리포트가 생기면 성공.

## Claude Haiku를 쓰는 에이전트(sales·support·social)는?
- 이들은 `config.model.provider="anthropic"`. 이 경로를 실가동하려면 `ANTHROPIC_API_KEY`(console.anthropic.com)도 `.env`에 넣으세요.
- 또는 해당 config를 openrouter 모델로 바꾸면 OpenRouter 키 하나로 전부 돌릴 수 있습니다.
- 키가 없으면 자동으로 dryrun 폴백(배선 점검은 계속 가능).
