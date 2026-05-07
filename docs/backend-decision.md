# 백엔드 선택 결정 노트

작성일: 2026-05-06

## 결론

**Firebase (Firestore + Auth + FCM + Storage) 채택.**

자체 서버는 회원 ~50명 규모 운영에 비용/유지보수 비효율. UI/UX → 백엔드 → 인증 순서 원칙에 따라 가장 빨리 인증·푸시·DB가 한 SDK에서 묶이는 Firebase가 적합.

## 선택지 비교

| 항목 | Firebase | 자체 서버 (Node/Spring + PostgreSQL) | Supabase |
|---|---|---|---|
| 초기 구축 비용 | $0 (Spark 무료 플랜) | VPS $5–20/월 + 도메인 + TLS 운영 | $0 ~ $25 |
| Auth | 카카오는 OIDC 직접 + Firebase Custom Token 브릿지 필요 | 직접 구현 (큰 작업) | 직접 구현 |
| DB 모델 | 문서 (Firestore) | RDB (관계형) | RDB (PostgreSQL) |
| 푸시 | FCM 1차 시민 | FCM 별도 연동 | FCM 별도 |
| 파일 업로드 | Storage 내장 | 별도 (S3/B2 + presigned) | Storage 내장 |
| 권한 룰 | Security Rules (DSL) | 미들웨어 직접 | RLS (PostgreSQL) |
| 운영 부담 | 낮음 (콘솔에서 룰만 관리) | 높음 (배포/모니터링/백업) | 중간 |
| 비용 (회원 50, 출석/코멘트 일 100건) | 무료 한도 내 | $5–20 + 트래픽 | $0–25 |

## 채택 근거

1. **인증 단가 최저** — 카카오 OAuth 토큰을 Firebase Custom Token 으로 교환하는 패턴이 표준화되어 있어 자체 세션 관리 불필요.
2. **FCM 무료 + 일관 SDK** — 푸시 알림(수업 리마인더, 코멘트 도착)이 별도 통합 비용 없이 동작.
3. **Security Rules 로 read-only 회원 권한 분리** — `MASTER` 만 쓰기 가능 룰을 콘솔에서 관리, 앱은 Read 만.
4. **운영자 1인 (관장)** — 인프라 모니터링 부담을 최소화.
5. **데이터 규모 작음** — 회원 ~50, 출석/매치 일 ~100건 수준은 Spark 무료 플랜으로 충분.

## 한계 및 회피

- **벤더 락인**: Firestore 의 `where().orderBy()` 제약, 복합 인덱스 필요. → Repository 인터페이스로 격리 (`MemberRepository`, `AttendanceRepository`).
- **트랜잭션 표현력 약함**: 토너먼트 다운스트림 리셋 같은 다중 문서 갱신은 Firestore Batch / Transaction 사용.
- **오프라인 동기화 충돌 시 last-write-wins** — 출결/매치 결과는 단일 작성자(체크인=본인, 결과=관장) 라 충돌 거의 없음.

## 다음 단계 의존성

- `[ ] Firebase 프로젝트 생성 + google-services.json 등록`
- `[ ] Auth: 카카오 OAuth → Firebase Custom Token 교환 함수 (Cloud Functions or 자체 엔드포인트)`
- `[ ] Firestore Security Rules 초안 (members read-all, write self / attendance create-self / comments create-master / scores create-master / tournaments write-master)`
- `[ ] Repository 추상화 — Schema.kt 의 *Doc 모델을 Firestore 직렬화로 매핑`

## 향후 재평가 트리거

- 회원 200명 이상 + 일 500건 이상 트랜잭션
- 복잡 통계 쿼리(체급별 승률 랭킹, 코치별 코멘트 분석) 비중이 70% 이상
- 다지점 운영 시작 (강남 + 추가 지점)
