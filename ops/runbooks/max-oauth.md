# 런북: Claude Max 구독으로 에이전트 돌리기 (버전 B, OAuth)

> 버전 B는 API 키가 아니라 **Claude Max 구독을 OAuth로** 사용합니다.
> 장점: 종량 과금 없음(구독에 포함). 주의: 구독은 대화형 사용 한도가 있어 **24/7·대량은 부적합** → 프로필의 역할별 주기를 지킵니다.

## 1) ant CLI 로그인 (최초 1회)
```
ant auth login          # 브라우저로 Anthropic 계정(=Max) 로그인
ant auth status         # 활성 프로필 확인
```

## 2) 버전 B 적용
```
node ops/engine/apply-profile.mjs max
```

## 3) 실행 전 토큰 주입 (토큰은 단기 — 실행 세션마다 갱신)
OAuth 액세스 토큰을 환경변수로 넣습니다. 라우터가 `Authorization: Bearer`로 사용합니다.
```
# Git Bash
export ANTHROPIC_AUTH_TOKEN=$(ant auth print-credentials --access-token)
node ops/engine/run-chain.mjs research
```
```
# PowerShell
$env:ANTHROPIC_AUTH_TOKEN = (ant auth print-credentials --access-token)
node ops/engine/run-chain.mjs research
```
- 토큰이 만료되면 위 한 줄을 다시 실행해 갱신합니다.
- `ANTHROPIC_AUTH_TOKEN`이 없으면 자동으로 dryrun 폴백(배선 점검은 계속 가능).

## 4) 비용/한도
- 구독 사용이라 **달러 과금 없음** → 엔진 비용 원장/일일캡은 $0으로 집계됩니다(정상).
- 제약은 **구독 rate limit**입니다. 프로필의 주기(하루 몇 회/주 몇 회)를 넘겨 몰아 돌리지 마세요.

## 버전 A로 되돌리기
```
node ops/engine/apply-profile.mjs openrouter
```
