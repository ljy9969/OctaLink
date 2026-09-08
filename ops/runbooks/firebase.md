# 런북: Firebase(Firestore) 읽기 커넥터

> 에이전트가 앱 실데이터를 참고하도록 Firestore를 읽어 `context/<agent>.md`에 스냅샷 저장.
> 무료(Firebase 무료 티어, 읽기). 서비스 계정은 사용자 본인이 발급.

1. Firebase 콘솔 → 프로젝트 `octalink-28088` → ⚙️ 프로젝트 설정 → **서비스 계정** → **새 비공개 키 생성** → JSON 다운로드.
2. JSON을 안전한 곳에 저장(git 밖). `ops/mcp/.env`에 경로 지정:
   `FIREBASE_SERVICE_ACCOUNT=C:\경로\serviceAccount.json`  (또는 JSON 내용 자체)
3. 커넥터 의존성 설치(엔진과 분리):
   ```
   cd ops/connectors && npm init -y && npm i firebase-admin
   ```
4. 조회 컬렉션 조정: `ops/connectors/config.json`의 `firebase.context`에서 에이전트→컬렉션 매핑 수정(실제 컬렉션명에 맞게).
5. 컨텍스트 갱신:
   ```
   node ops/connectors/firebase.mjs      # context/<agent>.md 생성
   ```
   이후 해당 에이전트 실행 시 `run-agent`가 `context/<agent>.md`를 자동 참고.
- 자격증명 없으면 placeholder만 생성(배선 검증 가능), 실데이터는 미조회.
- 정기 갱신은 작업 스케줄러에 위 명령을 별도 등록하거나, 에이전트 실행 전에 호출.
