# 런북(개요): 서버 승격 — 24/7 운영(Phase 3)

> 하이브리드: 지금은 로컬/Claude Code에서 실행. 서버 계정 준비되면 동일 엔진을 배포.

1. 호스트 선택: **Google Cloud Run**(권장, Firebase와 동일 프로젝트) 또는 소형 VM.
2. 계정/결제 등록(사용자 본인, KYC/카드).
3. `ops/engine`를 컨테이너화(Node 22) 또는 Cloud Run Job으로.
4. 크론: Cloud Scheduler로 `ceo` + 각 에이전트 트리거(`ops/triggers/schedule.json`) 연결.
5. 자격증명은 Secret Manager로 주입(`.env` 대신).
6. 일일 비용 kill-switch(`DAILY_CAP_USD`)와 알림 설정.
