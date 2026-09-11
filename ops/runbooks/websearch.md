# 런북: research 웹 검색 (무료)

research 에이전트는 실행 전 `connectors/websearch.mjs`로 웹 검색 → `context/research.md`의 `웹 검색` 구획.

## provider 우선순위 (`search()`)
1. **SEARXNG_URL (self-host SearXNG)** — 권장·가장 안정. 키 불필요.
2. Google CSE(`GOOGLE_CSE_ID`+키) — **사실상 불가**: Custom Search JSON API는 신규 고객 발급 중단(콘솔엔 "사용 설정됨"으로 보여도 호출 시 `403 PERMISSION_DENIED "does not have the access to Custom Search JSON API"`). 기존 접근권 있는 프로젝트에서만 동작.
3. `BRAVE_API_KEY` — Brave(무료 플랜은 카드 요구로 보류).
4. **DuckDuckGo (기본·키 불필요)** — 아무 설정 없으면 여기로 폴백. 소량엔 OK, 단 연속/과다 요청 시 간헐 봇 차단(HTTP 202).
5. 공개 SearXNG 인스턴스 — 대부분 JSON 비활성/429라 신뢰 불가(최후 폴백).

## 권장: 로컬 SearXNG self-host (Docker)
공개 인스턴스가 죽은 이유(JSON 비활성·봇 리미터)를 로컬에선 우리가 켜서 해결. 무료·무제한·차단 없음.

설정 파일 `ops/searxng/settings.yml` (git 제외, secret_key 포함). 핵심은 JSON 허용 + 리미터 끔:
```yaml
use_default_settings: true
server:
  secret_key: "<랜덤 32바이트 hex>"
  limiter: false
search:
  formats: [html, json]   # ★ JSON 응답 허용(기본은 html만)
```

실행 (Docker Desktop 켠 뒤):
```powershell
docker run -d --name searxng --restart unless-stopped -p 8888:8080 `
  -v "d:/source/JEON2/OctaLink/ops/searxng:/etc/searxng" searxng/searxng:latest
```
확인:
```
curl "http://localhost:8888/search?format=json&q=test"   # "results" 나오면 OK
```
`ops/mcp/.env`에 `SEARXNG_URL=http://localhost:8888` → 커넥터가 최우선 사용.

운영: 재시작 `docker restart searxng` / 중지 `docker stop searxng` / 로그 `docker logs searxng`.
(`--restart unless-stopped`이라 Docker Desktop 시작 시 자동 기동. PC 재부팅 후 Docker Desktop만 떠 있으면 됨.)

## 쿼리 조정
`ops/connectors/config.json`의 `websearch.queries.research`에서 조사 주제 수정.

## 선수/체육관 채널 조사
YouTube Data API(무료 키, `YOUTUBE_API_KEY`)로 채널 검색 → `connectors/youtube.mjs`가 `선수 유튜브 채널` 구획에 추가. (runbooks/youtube.md)
