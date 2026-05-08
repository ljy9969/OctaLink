# OctaLink — Android App

MMA 체육관 **Team Posse Striking 강남점** 회원 전용 앱 **OctaLink**.
개발사: **Unbound Apex Systems** · 개발자: **BlackCat Strike** (이지연).
Strava, Smashr를 참고. 궁극 목표는 **개인의 성장**.

## 진행 현황 (2026-05-08 기준)

UI 완성 + 백엔드 골격(스키마 + ViewModel/StateFlow + Firestore 결정) + 배포 인프라(서명 키 + 개인정보처리방침 GitHub Pages 호스팅 + 체육관 정보 화면) + Play 콘솔 개발자 계정 등록 완료 + GitHub 저장소 연동 + Play Store 등록 자산(아이콘 / 피처 그래픽) + 패키지 리네임(`com.unboundapex.octalink`). 다음은 Firebase 프로젝트 생성 + Repository 매핑 + 인증 연동 + 스크린샷 + 앱 설명.

저장소: https://github.com/ljy9969/OctaLink (브랜치: `main`, `teamposse` — USF4 자산 백업)
패키지: `com.unboundapex.octalink` (applicationId 동일, Play Store 등록 후 변경 불가)

| 탭 | 화면 | 상태 |
|---|---|---|
| 홈 | 로고 배너(우상단 ⓘ Info 진입) + 진입 시 1초 정권 임팩트 + 통계 2카드(체육관 활성도 · 내 주간 출석률) + 주간 미션 + 대진표 카드(케이지 아이콘) + 피드 3종 | UI 완성 |
| 커리큘럼 | 평일(월~금) 5일 그룹 수업 테마 + 드릴 + 코치 + 태그 칩 + 오늘 하이라이트 | UI 완성 |
| 출석 | 셀프 체크인(파랑) / 취소(빨강) + 동료 2열 그리드 + 본인 카드 자동 추가 + 휴무일 비활성화 | UI 완성 |
| 커뮤니티 | 컬러 칩 태그(공지/기록/팁) + 우측 정렬 절대 시각 | UI 완성 |
| 프로필 | 캐릭터(44명 중) + 벨트 색 링 + 헥사곤 차트 + 승률(세로 중앙) + 관장 코멘트 | UI 완성 |
| 매치 (Bracket) | 추첨 → 트리(8/4/2) + 벨트 색 스트라이프 + 승자 행 직접 클릭 + 불꽃 연결선 + 챔피언 카드 | UI 완성 |
| 추첨 (Draw) | 체급(필수) + 벨트(선택) 필터 칩 + 회원 선택 + 추첨 버튼 + 체급 안내 모달 | UI 완성 |

## 디자인 시스템

- **컬러:** Ink(`#0B0B0F`) / Canvas(`#15161B`) / Ash(`#3A3A42`) / Bone(`#F5F2EC`) / Blood(`#C8102E`)
- **타이포:** displayLarge / headlineMedium / titleMedium / titleSmall / bodyLarge / bodyMedium / labelMedium / labelSmall
- **로고:** `drawable-nodpi/logo_teamposse.jpg` (966×300, 3.22:1) — 다크 카드에 그릴 때 ColorMatrix 인버전
- **레이아웃 스캐폴드:** `PosseScreen(title, subtitle, header)` + `PosseCard(modifier, padding)`
  - `header` 슬롯이 풀 너비 (홈 로고 배너용)
  - title이 있으면 같은 행에 우측 정렬 subtitle (`maxLines = 2` + `\n` 지원)
- **하단 네비게이션:** 커스텀 Row 44dp · 아이콘만 중앙 정렬 · 활성 = primary, 비활성 = onSurfaceVariant
- **칩 패턴:** 커뮤니티 TagBadge / Bracket ActionChip / Draw DrawActionChip / Belt+Weight 필터 칩 — 색 배경 + Bold 텍스트 + 클릭 영역 명확
- **날짜 포맷 통일:** `M/D 요일` (예: `5/5 화`) — 홈 피드 / 커뮤니티 / 프로필 코멘트 / 출석 헤더 모두 동일

## 데이터 / 도메인

- **운영시간** (`data/Schedule.kt`): 평일 11:00–22:00 / 토요일 11:00–17:00 (PT 전용) / 일·공휴일 휴무
  - 평일 슬롯 6개: 오픈 매트(2) + 복싱·킥복싱·MMA(4)
  - `currentOrNextClassLabel()` 헬퍼: 진행 중 / 다음 / 종료 / 휴무 동적 라벨. **KST 강제** (`ZoneId.of("Asia/Seoul")`)
  - 표시 위치: **Info 화면**(체육관 정보 카드)으로 일원화. 별도 Schedule 탭 ❌
- **주간 커리큘럼** (`data/Curriculum.kt`): 평일 5일 × CurriculumDay(theme + drills + coach + tag). `curriculumForToday()` 헬퍼로 홈 피드 + 커리큘럼 화면이 같은 source 공유
- **공휴일** (`data/HolidayRepository.kt`): 공공데이터 특일정보 API 연동
  - 메모리 + SharedPreferences 영속 캐시 + 하드코딩 폴백 3단
  - MainActivity.onCreate에서 `init()` 호출 → 백그라운드로 현재+다음 해 갱신
  - API 키는 `local.properties`의 `HOLIDAY_API_KEY` → BuildConfig 주입
- **벨트** (`data/BeltColors.kt`): 화이트/블루/퍼플/브라운/블랙. 텍스트 표기 ❌ → 아바타 링 + 매치카드 스트라이프 컬러로 표현
- **캐릭터 카탈로그** (`data/AvatarCatalog.kt`): USF4 44명. 이미지 미존재 시 컬러 + 이니셜 자동 폴백
- **회원 풀** (`data/Member.kt`): 5체급 × 8명 = **40명** (페더/라이트/웰터/미들/헤비)
- **세션 상태** (`data/session/SessionViewModel.kt`): name/belt/avatarId/role을 `StateFlow<SessionState>`로 노출. 프로필 변경이 collectAsState 하는 모든 화면에 자동 전파
- **토너먼트 상태** (`data/tournament/TournamentViewModel.kt`): round1/round2/final + weightClass + beltGroup을 `StateFlow<TournamentUiState>`로 노출
  - 추첨 시 인원수에 따라 자동 시작 라운드 결정 (2명→결승 / 3~4명→4강 / 5~8명→8강)
  - `autoResolveByes()` + `propagateRound2Bye()` — 부전승 자동 진출, 단 상위 매치 미결정 시 대기
- **VM 스코프**: `PosseApp` 루트에서 `viewModel()`로 1회 hoist → 화면별 파라미터로 전달. NavHost composable의 자체 BackStackEntry 스코프 회피 (탭 전환 시 동일 인스턴스 보장)

## 기술 스택

- Kotlin 1.9.24 / Jetpack Compose / Material 3
- Navigation Compose (5탭 + 대진표 + 추첨 디테일)
- minSdk 26 / targetSdk 34 / compileSdk 34
- AGP 8.5.0 / Compose BOM 2024.06.00 / kotlinCompilerExtensionVersion 1.5.14
- `androidx.core:core-splashscreen:1.0.1`
- JDK: JBR 21 (17 호환 컴파일)

## 빌드

```bash
# 1. local.properties에 다음 키 등록 (gitignored)
HOLIDAY_API_KEY=<공공데이터 특일정보 serviceKey>

# 2. Android Studio에서 d:\source\OctaLink 열고 Run ▶
# 또는 CLI:
./gradlew :app:assembleDebug
```

### 릴리즈 서명 (Play Store 업로드용)

```bat
:: 1. keystore 생성 (한 번만, app\teamposse-release.jks 가 만들어짐)
tools\generate-release-keystore.bat

:: 2. local.properties 에 다음 4줄 추가
::    RELEASE_KEYSTORE_FILE=app/teamposse-release.jks
::    RELEASE_KEYSTORE_PASSWORD=<keystore password>
::    RELEASE_KEY_ALIAS=teamposse-release
::    RELEASE_KEY_PASSWORD=<key password>

:: 3. 서명된 빌드
gradlew :app:assembleRelease    :: APK
gradlew :app:bundleRelease      :: AAB (Play Console 업로드용)
```

- `*.jks`, `*.keystore`, `local.properties` 모두 gitignored — 절대 커밋 ❌
- `.jks` 파일을 잃으면 **이 앱을 Play Store에서 업데이트 영구 불가**. 안전한 곳(비밀번호 매니저 + 외장/암호화 드라이브)에 백업 필수
- local.properties에 keystore 4개 키가 다 차 있으면 release 빌드가 자동 서명, 비어 있으면 unsigned debug-only 동작

## 폴더 구조

```
app/src/main/
├── AndroidManifest.xml
├── assets/avatars/README.txt          # 캐릭터 이미지 드롭존 + 저작권 경고
├── java/com/unboundapex/octalink/
│   ├── MainActivity.kt                # installSplashScreen + HolidayRepository.init
│   ├── navigation/PosseApp.kt         # 커스텀 Row 44dp 네비게이션 + NavHost
│   ├── data/
│   │   ├── AvatarCatalog.kt           # USF4 44명 카탈로그
│   │   ├── BeltColors.kt              # 벨트 → 컬러 매핑
│   │   ├── GymInfo.kt                 # 체육관 운영 정보 + 외부 링크
│   │   ├── HolidayRepository.kt       # 공공데이터 API + 캐시
│   │   ├── Curriculum.kt              # 평일 5일 그룹 수업 커리큘럼
│   │   ├── Match.kt                   # 매치 in-memory 모델
│   │   ├── Member.kt                  # 회원 풀 40명 (5체급 × 8명) + WeightClass
│   │   ├── Schedule.kt                # 운영 스케줄 + currentOrNextClassLabel
│   │   ├── schema/Schema.kt           # Firestore 영속화 도메인 모델 (*Doc)
│   │   ├── session/SessionViewModel.kt   # 현재 회원 세션 (StateFlow)
│   │   └── tournament/TournamentViewModel.kt # 토너먼트 상태 + 부전승 자동 처리
│   ├── ui/theme/
│   │   ├── Color.kt                   # Ink/Canvas/Ash/Bone/Blood/Mist
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── ui/components/
│   │   ├── HexagonSkillChart.kt       # 6축 레이더 (Canvas)
│   │   ├── AvatarTile.kt              # 원형 아바타 + 벨트 링 + 흰 배경 통일
│   │   ├── AvatarPickerSheet.kt       # 4열 그리드 ModalBottomSheet
│   │   ├── CageIcon.kt                # MMA 옥타곤 케이지 (Canvas, top-view)
│   │   └── PosseScaffolds.kt          # PosseScreen / PosseCard
│   └── ui/screens/
│       ├── home/HomeScreen.kt         # 로고 배너 + 정권 임팩트 + 통계/미션/케이지/피드 + ⓘ Info 진입
│       ├── curriculum/CurriculumScreen.kt # 평일 5일 커리큘럼 카드 + 오늘 하이라이트
│       ├── attendance/AttendanceScreen.kt # 체크인 토글 + 동료 2열 + 본인 카드
│       ├── community/CommunityScreen.kt   # 컬러 칩 태그 + 우측 시각
│       ├── profile/ProfileScreen.kt   # 캐릭터 + 헥사곤 + 승률 + 코멘트
│       ├── info/InfoScreen.kt         # 체육관 정보 (주소/전화/운영시간/정책/앱 버전)
│       └── bracket/
│           ├── BracketScreen.kt       # 트리(EIGHT/FOUR/FINAL_ONLY) + 직접 클릭 advance + 불꽃 라인
│           └── BracketDrawScreen.kt   # 추첨 (체급/벨트 필터, 회원 선택, 모달)
└── res/
    ├── drawable-nodpi/
    │   ├── logo_teamposse.jpg              # 원본 로고 (홈 배너)
    │   └── avatar_<id>.png × 44             # USF4 캐릭터
    ├── mipmap-*/ic_launcher_foreground.webp # 런처 아이콘
    ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
    └── values/{colors,strings,themes,ic_launcher_background}.xml
```

## 결정된 사항

| 영역 | 결정 |
|---|---|
| 출석 체크 | 회원 셀프 (관장 수동 ❌). 1인칭 화면, 큰 토글 버튼 |
| 체크인 색상 | **체크인 = 파랑(#1E88E5)** / **체크인 취소 = 빨강(#C8102E)** |
| 휴무일 | 체크인 비활성 + 동료 명단 비표시 (subtitle "오늘 휴무") |
| 홈 헤더 | 텍스트 대신 로고 배너 (90% 폭 + 진입 시 정권 1초 임팩트) |
| 캐릭터 | USF4 44명 중 선택. 모든 아바타 흰 배경으로 통일 |
| 벨트 표시 | 아바타 링 색 / 매치 카드 스트라이프 색 (텍스트 ❌) |
| 스킬 차트 평가 | **관장 단독 평가** (자기/상호 평가 ❌). 차후 관장 전용 입력 화면 필요 |
| 시간대 | 모든 시간 연산은 KST(`Asia/Seoul`) 강제 |
| 날짜 표기 | `M/D 요일` 통일 (예: `5/5 화`). 연도 생략 |
| 탭 네비게이션 | saveState/restoreState=false → 탭 클릭 시 항상 루트로 |
| 스플래시 | 시스템 스플래시(런처 로고) 사용. Compose 추가 스플래시 ❌ (콜드 스타트 단축) |
| 토너먼트 추첨 | 체급 필수 + 벨트 옵션. 인원수에 따라 시작 라운드 자동 (2/4/8) |
| 토너먼트 결과 입력 | **매치 카드의 승자 행 직접 클릭** (다이얼로그 ❌). 다른 이름 클릭으로 승자 전환 가능, 다운스트림 자동 리셋 |
| 부전승 처리 | 자동 advance (단, 상위 라운드가 미정인 "?"는 부전승 아니라 대기로 판정) |
| 한 줄 코멘트 | 본인 수신 전용. 받는 사람 표기 ❌. 작성일 + 작성자만 (예: `5/1 금 관장 김파시`) |
| 백엔드 | **Firebase (Firestore + Auth + FCM + Storage)**. 운영자 1인 + 회원 ~50명 규모에 자체 서버 비효율. 근거: `docs/backend-decision.md` |
| 도메인 모델 | `data/schema/Schema.kt` — Member/ClassDef/Attendance/Comment/SkillScore/Tournament/Match 의 *Doc 클래스. 화면용 in-memory 모델과 분리 |

## Play Store 출시 체크리스트

배포 직전에 점검할 항목. 인프라 코드는 준비되어 있고, 실제 등록/호스팅/이미지만 운영자가 채우면 됨.

- [x] **`RELEASE_KEYSTORE_*` 4개 키 생성 + 백업** — `app/teamposse-release.jks` 생성, AAB 빌드 검증 완료. 비밀번호 매니저 + 외장 / 암호화 드라이브 백업 권장 (분실 시 업데이트 영구 불가)
- [x] **`docs/privacy-policy.html` 호스팅** — GitHub Pages 발행, `https://ljy9969.github.io/OctaLink/privacy-policy.html` 에서 렌더링 확인 완료
- [x] **`GymInfo.PRIVACY_POLICY_URL` 갱신** — Pages URL 반영 완료
- [x] **앱 아이콘 512×512 PNG** — `app/src/main/ic_launcher-playstore.png` (흰 배경 + 로고 92% 폭). 생성 스크립트: `tools/make-playstore-icon.py`
- [x] **피처 그래픽 1024×500** — `app/src/main/feature_graphic3.png` (Canva 제작, 흰 배경 + 원본 로고 + 슬로건). 후보 4종 보존(`feature_graphic1.png` ~ `feature_graphic_v3.png`)
- [ ] **스크린샷 최소 2장** — 홈(로고 배너 + 피드) / 매치(불꽃 라인 트리) 추천. 에뮬레이터(Pixel 8) 캡처 권장
- [ ] **앱 설명** — 단문 80자 + 장문 4000자. 단문 예: "강남 MMA 체육관 회원 전용 출결·매치 관리 앱"
- [x] **Play 콘솔 개발자 계정 등록** ($25) — 2026-05-08 본인 명의 개인 계정으로 완료
- [ ] **콘텐츠 등급 IARC 설문** — 격투기 콘텐츠 12+ 가능성
- [ ] **타겟 API Level 35** 업그레이드 — 2026-08-31부터 신규 앱 의무 (현재 34)
- [ ] **체육관 측 상표 / 로고 사용 동의** — 카카오톡 등 텍스트 기록. "Team Posse Striking" 명칭과 `logo_teamposse.jpg` 사용 허락
- [ ] **앱 내 비공식 표기** — "본 앱은 회원이 자체 제작한 비공식 도구입니다" 같은 푸터 추가 (체육관 공식 앱 오해 방지)
- [x] **USF4 캐릭터 이미지 자산 백업** — `teamposse` 브랜치(원격 보존)로 보관. 배포 빌드 전 main에서 제거 + 자체 일러스트 / 유저 업로드 + 이니셜 폴백 중 선택

## 알려진 함정

- **Image Asset Studio** 가 새 XML 파일 생성 시 라이선스 주석을 `<?xml?>` 선언 위에 삽입함 → mergeResources 빌드 실패. 마법사 사용 후 빌드 깨지면 신규 XML 첫 줄 확인.
- `Image(painter, ...)` 오버로드에는 `filterQuality` 파라미터 없음 (`Image(bitmap, ...)` 전용).
- 어댑티브 아이콘 foreground는 spec상 외곽 33%가 안전 여백 → 본문 재활용 시 작게 보임. 본문용은 `drawable-nodpi/`에 원본 별도 배치.
- 에뮬레이터 마우스 휠 스크롤이 터치 이벤트로 변환되지 않을 수 있음 → 클릭+드래그로 시뮬레이션.
- 공휴일 API 응답이 비어있을 때 폴백(2026 하드코딩) 사용 → 매년 갱신 또는 다년치 폴백 필요.
- `Text.softWrap = false`는 hard `\n`까지 무력화시킴 → 줄바꿈 필요 시 softWrap 기본값 유지하고 maxLines로만 제한.
- 토너먼트 상위 매치가 미결정인데 round2 슬롯의 "?"를 부전승으로 자동 advance 하면 안 됨 — `propagateRound2Bye`에서 "더블 바이 vs 미결정 실경기" 구분 필수.
- **Kotlin 블록 주석은 nested**: 백틱 ` 안의 `/*` 도 nested comment 시작으로 파싱됨 → 닫히지 않으면 EOF에서 "Unclosed comment" 에러. KDoc에 경로 예시 쓸 때 `/*` 패턴 회피.

## 내일 이어서 할 일

쉽고 중요한 것부터 위 → 어렵거나 후순위는 아래.

### 완료
- [x] (05-06) `배포 준비` **앱 서명 키** 인프라 — gradle release `signingConfig` + `tools/generate-release-keystore.bat` + README 가이드. keystore 파일 실제 생성/백업은 운영자 수동
- [x] (05-06) `배포 준비` **개인정보처리방침 페이지** — `docs/privacy-policy.html` (운영자 정보 채움 완료, GitHub Pages 등에 호스팅만 남음)
- [x] (05-06) `배포 준비` **체육관 정보 페이지** — `ui/screens/info/InfoScreen.kt` + 홈 화면 우상단 ⓘ 진입 버튼. 주소(네이버 지도) / 전화 / 메일 / 운영시간 / 정책 / 앱 버전
- [x] (05-06) `백엔드 / 데이터 (메인)` **데이터 모델** — `data/schema/Schema.kt` 7개 *Doc + Role/MembershipStatus/TournamentRound enum + Firestore Collections 경로 규약
- [x] (05-06) `백엔드 / 데이터 (메인)` **백엔드 선택** — Firebase Firestore 확정 (`docs/backend-decision.md`)
- [x] (05-06) `백엔드 / 데이터 (메인)` **ViewModel + StateFlow 도입** — `SessionViewModel` / `TournamentViewModel` 도입, 옛 `CurrentUser` / `TournamentState` 싱글톤 제거. PosseApp 루트에서 hoist → 화면별 파라미터 전달
- [x] (05-08) `배포 준비` **Play 콘솔 개발자 계정 등록** — $25 결제 + 신원 확인 완료
- [x] (05-08) `배포 준비` **개인정보처리방침 호스팅** — GitHub Pages 발행, `GymInfo.PRIVACY_POLICY_URL = https://ljy9969.github.io/OctaLink/privacy-policy.html` 갱신, 브라우저 렌더링 확인 완료
- [x] (05-08) `배포 준비` **앱 서명 키 생성 + 서명된 AAB 빌드** — `app/teamposse-release.jks` 발급, `gradlew :app:bundleRelease` 통과
- [x] (05-08) `배포 준비` **앱 아이콘 + 피처 그래픽 확정** — `ic_launcher-playstore.png` 신규 + `feature_graphic3.png` 확정. 생성 스크립트 `tools/make-playstore-icon.py`, `tools/make-feature-graphic*.py`
- [x] (05-08) `배포 준비` **앱 / 회사 / 개발자 네이밍 정리** — 앱 OctaLink, 회사 Unbound Apex Systems, 개발자 BlackCat Strike. 문서·UI·privacy-policy 일괄 갱신
- [x] (05-08) `배포 준비` **GitHub 저장소 + 디렉토리 + 패키지 리네임** — repo `Team-Posse → OctaLink`, dir `teamposse-app → OctaLink`, package `com.teamposse.striking → com.unboundapex.octalink` (applicationId 동일)
- [x] (05-08) `UX 디테일` **출석 30분 윈도우 게이팅** — `Schedule.kt:checkInWindow()` + `CheckInWindow` enum. 휴무 / 수업 종료 / 30분 초과 모두 비활성

### 남은 일
- [ ] `배포 준비` **스크린샷 최소 2장** — 에뮬레이터 Pixel 8 캡처. 홈 / 매치 권장
- [ ] `배포 준비` **앱 설명** — 단문 80자 + 장문 4000자
- [ ] `배포 준비` **콘텐츠 등급 IARC 설문** — 격투기 콘텐츠 12+
- [ ] `배포 준비` **타겟 API Level 35 업그레이드** — 2026-08-31 의무
- [ ] `배포 준비` **체육관 측 상표 / 로고 사용 동의** — 텍스트 기록
- [ ] `배포 준비` **앱 내 비공식 표기 푸터** — "본 앱은 회원이 자체 제작한 비공식 도구입니다"
- [ ] `백엔드 / 데이터 (메인)` **Firebase 프로젝트 생성** — google-services.json 등록 + Auth/Firestore/FCM/Storage 활성화
- [ ] `백엔드 / 데이터 (메인)` **Repository 추상화** — `*Doc` ↔ Firestore 직렬화 매핑. 화면은 인터페이스에만 의존
- [ ] `백엔드 / 데이터 (메인)` **Firestore Security Rules 초안** — members read-all + write-self / attendance create-self / comments·scores·tournaments write-master
- [ ] `UX 디테일` **토너먼트 히스토리** — 종료된 토너먼트 보관 + 회원별 전적 누적
- [ ] `UX 디테일` **관장 권한 분리** — 한 줄 코멘트 작성, 스킬 점수 입력, 출결 검토 화면 (회원에게는 read-only). `Role.MASTER` 분기
- [ ] `UX 디테일` **회원 가입 / 입관 신청 플로우** — 관장 승인 단계 (`MembershipStatus.PENDING → APPROVED`)
- [ ] `백엔드 / 데이터 (메인)` **인증** — 카카오 OAuth → Firebase Custom Token 교환 (Cloud Functions or 자체 엔드포인트), 또는 네이버
- [ ] `UX 디테일` **체크인 위치 검증** — GPS 체육관 반경 옵션 (시간 30분 윈도우는 05-08 완료)
- [ ] `배포 준비` **푸시 알림 (FCM)** — 수업 리마인더, 한 줄 코멘트 도착, 대진표 업데이트
- [ ] `UX 디테일` **커뮤니티 글 작성 / 영상 업로드** — Firebase Storage 또는 Cloudinary
- [ ] `배포 준비` **저작권 정리** — USF4 캐릭터 이미지는 캡콤 IP라 배포 빌드에서 제거 또는 라이선스 취득 / 자체 일러스트 / 유저 업로드 + 이니셜 폴백 중 선택
