# 런북: GitHub 이벤트 트리거 (dev issue.assigned)

> dev 에이전트를 "이슈가 배정되면" 자동 실행. 무료(토큰만 필요).

1. GitHub → Settings → Developer settings → **Fine-grained personal access token** 발급
   - Repository access: `ljy9969/OctaLink`
   - Permissions: **Issues → Read-only** (충분)
2. 토큰을 `ops/mcp/.env`의 `GH_TOKEN=`에 넣기(.env는 git 제외).
3. 확인: `GH_TOKEN=... node ops/engine/dispatch.mjs` 실행 시, 열린 배정 이슈가 있으면
   `tasks/dev.md`가 생성되고 dev가 실행됨(중복 이슈는 재발화 안 함).
- 토큰 없으면 이벤트 체크는 no-op(크론 트리거만 동작).
- 디스패처(15분 작업 스케줄러)가 매 실행마다 이슈를 폴링하므로 별도 웹훅 불필요.
