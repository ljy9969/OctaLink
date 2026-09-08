# 런북: research 웹 검색 (무료)

research 에이전트는 실행 전 `connectors/websearch.mjs`로 웹 검색 → `context/research.md`.

## 두 가지 provider
- **기본: DuckDuckGo (키 불필요, 무료)** — 단, **연속/과다 요청 시 DDG가 봇 차단(HTTP 202 anomaly)** 하여 결과가 비어질 수 있음. **best-effort**(소량·간헐적엔 OK, 지속 운영엔 불안정).
- **권장(안정적): Brave Search API (무료)** — `BRAVE_API_KEY` 있으면 커넥터가 자동으로 Brave 사용(JSON, 차단 없음).
  1. https://brave.com/search/api 가입 → **Free 플랜(월 2,000 쿼리, 카드 불필요)**.
  2. `ops/mcp/.env`에 `BRAVE_API_KEY=...`
  3. 끝. `node ops/connectors/websearch.mjs`가 Brave로 안정 조회.

## 쿼리 조정
`ops/connectors/config.json`의 `websearch.queries.research`에서 조사 주제를 수정.

## 선수/체육관 SNS 계정 조사
- 검색 스니펫만으로 특정 선수의 인스타/유튜브 "계정"을 정확히 찾긴 한계가 있음.
- 더 적합: **YouTube Data API(무료 키)**로 채널 검색 — 별도 커넥터로 추가 가능(원하면 작업).
