# 런북: YouTube Data API 커넥터 (선수/체육관 채널 발굴)

> research가 MMA 선수-관장들의 유튜브 채널을 실제로 찾도록. 무료(키 필요).

1. Google Cloud Console(console.cloud.google.com) → 프로젝트 생성/선택.
2. **YouTube Data API v3** 사용 설정.
3. 사용자 인증 정보 → **API 키** 생성(무료 할당량 10,000 units/일 — 검색 1회≈100).
4. `ops/mcp/.env`에 `YOUTUBE_API_KEY=...`
5. 조사 채널 쿼리는 `connectors/config.json`의 `youtube.channelQueries`에서 조정.
6. 실행: `node -e "import('./ops/connectors/youtube.mjs').then(m=>m.buildYoutubeContext({opsRoot:'ops',queries:JSON.parse(require('fs').readFileSync('ops/connectors/config.json')).youtube.channelQueries,apiKey:process.env.YOUTUBE_API_KEY}).then(n=>console.log(n)))"`
   → `context/research.md`에 채널 목록 추가 → research가 참고.
