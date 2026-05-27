# OctaLink — Android App

MMA 체육관 **Team Posse Striking 강남점** 회원 전용 앱 **OctaLink**.
개발사: **Unbound Apex Systems** · 개발자: **BlackCat Strike** (이지연).
Strava, Smashr를 참고. 궁극 목표는 **개인의 성장**.

## 진행 현황 (2026-05-26 기준)

**Phase 2 카카오 OAuth + Firestore 활성화 완료.** Firebase Blaze 전환 + Cloud Functions(`kakaoSignIn` / `completeSignup` / `leaveMembership` / `rejoinMembership` + FCM 트리거 5종 `notifyOn*`) 배포 + Firestore/Storage Rules 배포 + RepositoryProvider Phase 2 토글 모두 끝. **카카오 로그인 → 가입 폼(Kakao nickname 자동 prefill, 사용자 수정 가능) → Cloud Function 으로 `members/{uid}` 생성 → role 자동 부여 → 메인 앱 진입** end-to-end 검증 완료. **Phase 2 후속 Repository(Attendance / SkillScore / Comment / Post / Tournament) 모두 Firestore 구현체 활성 + ViewModel 영속화 연결 완료**. Post(좋아요·이미지 첨부 Phase A·영상 첨부 Phase B) + Comment(한 줄 코멘트 송수신) + Attendance(체크인 영속화 + 운영진 출결 검토 + 월간 캘린더 좌우 페이징) + SkillScore 풀스택 워크플로우(관장 직접 평가 `directApprove` + 코치 제안→관장 검토 `propose` + canonical = 시간 최신 / ProfileScreen 차트가 `skillScores` 직접 구독) + Tournament 풀스택(추첨/매치/챔피언 영속화 + 토너먼트 히스토리 화면 + 회원 프로필 전적 카드 + 관장 per-card 삭제) + 푸시 알림 (FCM) 풀스택(6종 NotificationType + 채널/권한/토큰/prefs 토글 + Cloud Functions 트리거 5종 + 클라이언트 WorkManager 기반 CLASS_REMINDER 평일×4시간대 그리드 + legacy 키 1회성 마이그레이션) + **UI 테마 선택 (다크 / 라이트)**(`AppThemeStore` 싱글톤 + SharedPreferences 영속 + 신규 설치 기본 LIGHT) + **프로필 설정 화면 분리**(톱니바퀴 trailing → ProfileSettings 라우트, 알림/테마/정책·링크/앱 정보/로그아웃/탈퇴 모음). 홈 화면도 정적 mock 청소(오늘의 커리큘럼 → `curriculumForToday` 실데이터, 휴무일 자동 숨김 / 스파링 매치 카드 실데이터 등) + 태그는 `TagChip` 공통 컴포넌트로 색깔 pill(킥복싱 블루 / 스트라이킹 오렌지 / 그래플링 브라운 / MMA 블러드 / 스파링 그린). **Play Store 비공개 베타 진행 중.** UI 완성 + 자체 OctaLink 브랜딩 완료 + Play 콘솔 기본 스토어 등록정보 완료(아이콘 / 피처 그래픽 / 폰·7"·10" 스크린샷 24장 / 앱 설명 / IARC 12+) + 타겟 API 35 + 비공개 테스트 트랙 0.2.0 → 0.3.0 → 0.6.0 (versionCode 5) → 0.7.0 (versionCode 8) → **0.9.0 (versionCode 9)** 게시 — 0.7.0 에 활성도·출석률 단계별 색 / 댓글 아이콘 채움·외곽선 / 정책·앱 정보 → 설정 화면 통합 / 토너먼트 "내 참가만" 필터, 0.9.0 에 사진/영상 전체화면 뷰어 + 갤러리 저장·카카오톡·인스타 공유 / 커뮤니티 본문 YouTube 링크 썸네일 카드 + 탭 시 YouTube 앱 연결 / 글 작성 사진 회전(EXIF+다운샘플) / 멘션 알림 본문에 글 제목 포함 / FCM 토큰 자동 등록(누락 케이스 보강) / 글 작성 폼 chip·버튼 디자인 정비 / 사진/영상 첨부 시 정중앙 멘션 안내 토스트 포함. 다음은 카카오 비즈 사업자 등록 후 동의 항목 재신청 + 베타 테스터 12명 모집(오픈채팅 + 체육관 포스터 부착).

저장소: https://github.com/ljy9969/OctaLink (브랜치: `main`, `teamposse` — USF4 자산 백업)
패키지: `com.unboundapex.octalink` (applicationId 동일, Play Store 등록 후 변경 불가)

| 탭 | 화면 | 상태 |
|---|---|---|
| 홈 | 로고 배너(우상단 ⓘ Info 진입) + 진입 시 1초 정권 임팩트 + 통계 2카드(체육관 활성도 · 내 주간 출석률) + 주간 미션 + 대진표 카드(케이지 아이콘) + 피드 3종 | UI 완성 |
| 커리큘럼 | 평일(월~금) 5일 그룹 수업 테마 + 드릴 + 코치 + 태그 칩 + 오늘 하이라이트 | UI 완성 |
| 출석 | 셀프 체크인(파랑) / 취소(빨강) + 동료 2열 그리드 + 본인 카드 자동 추가 + 휴무일 비활성화 | UI 완성 |
| 커뮤니티 | 컬러 칩 태그(공지/기록/팁) + 우측 정렬 절대 시각 | UI 완성 |
| 프로필 | 캐릭터(성별 2 × 체급 5 = 10종) + 헥사곤 차트 + 승률(세로 중앙) + 관장 코멘트 | UI 완성 |
| 매치 (Bracket) | 추첨 → 트리(8/4/2) + 벨트 색 스트라이프 + 승자 행 직접 클릭 + 불꽃 연결선 + 챔피언 카드 | UI 완성 |
| 추첨 (Draw) | 체급(필수) + 벨트(선택) 필터 칩 + 회원 선택 + 추첨 버튼 + 체급 안내 모달 | UI 완성 |

## 디자인 시스템

- **컬러:** Ink(`#0B0B0F`) / Canvas(`#15161B`) / Ash(`#3A3A42`) / Bone(`#F5F2EC`) / Blood(`#C8102E`)
- **타이포:** displayLarge / headlineMedium / titleMedium / titleSmall / bodyLarge / bodyMedium / labelMedium / labelSmall
- **로고:** `drawable-nodpi/logo_octalink.png` (1800×403, 4.47:1) — 옥타곤 outline + 내부 슬래시 + "ctaLink" 워드마크. **운영자 손제작판** (`feature_graphic2.png`에서 워드마크 추출, LANCZOS 리사이즈 + UnsharpMask 샤프닝). 다크 카드에 그릴 때 ColorMatrix 인버전
- **마크 단독:** `drawable-nodpi/mark_octalink.png` (1024×1024, 투명 배경) — 아이콘 마스터. 운영자 손제작 `mark_octalink.jpg` 에서 알파 추출 (이중 임계값 램프 HI=220/LO=100, JPG 노이즈 컷오프 + 안티앨리어스 보존)
- **파생 생성기:** `tools/make-octalink-logo.py` — 운영자 마스터(`mark_octalink.jpg` + `feature_graphic2.png`)를 입력으로 받아 mark_octalink.png + 런처 5밀도×3종 webp 일괄 출력. `tools/make-playstore-icon.py`는 mark_octalink.png를 LOGO_SRC로 받아 512×512 Play Store 아이콘 생성. 디자인 변경 X, 단순 리사이즈/배경 합성/샤프닝만
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
- **캐릭터 카탈로그** (`data/AvatarCatalog.kt`): 자체 파이터 스프라이트 10종 (5체급 × 남/여 2). 가입 시 성별 + 체급 조합으로 자동 부여 (사용자 선택 X)
- **회원 풀** (`data/Member.kt`): 5체급 × 8명 = **40명** (페더/라이트/웰터/미들/헤비)
- **세션 상태** (`data/session/SessionViewModel.kt`): name/belt/avatarId/role을 `StateFlow<SessionState>`로 노출. 프로필 변경이 collectAsState 하는 모든 화면에 자동 전파
- **토너먼트 상태** (`data/tournament/TournamentViewModel.kt`): round1/round2/final + weightClass + beltGroup을 `StateFlow<TournamentUiState>`로 노출
  - 추첨 시 인원수에 따라 자동 시작 라운드 결정 (2명→결승 / 3~4명→4강 / 5~8명→8강)
  - `autoResolveByes()` + `propagateRound2Bye()` — 부전승 자동 진출, 단 상위 매치 미결정 시 대기
- **VM 스코프**: `PosseApp` 루트에서 `viewModel()`로 1회 hoist → 화면별 파라미터로 전달. NavHost composable의 자체 BackStackEntry 스코프 회피 (탭 전환 시 동일 인스턴스 보장)

## 기술 스택

- Kotlin 2.2.10 (`org.jetbrains.kotlin.plugin.compose` 통합) / Jetpack Compose / Material 3
- Navigation Compose (5탭 + 대진표 + 추첨 디테일)
- minSdk 26 / targetSdk 35 / compileSdk 35 (2026-05-10 마이그레이션)
- AGP 9.2.1 / Compose BOM 2024.10.00
- `androidx.core:core-splashscreen:1.0.1`
- JDK: JBR 21 (17 호환 컴파일)

## 빌드

### 새 PC 첫 셋업

```powershell
# 1. ANDROID_HOME 환경변수 (User scope, admin 불필요) — Android Studio 설치 시 자동 설정되기도 함
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\<USER>\AppData\Local\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "C:\Users\<USER>\AppData\Local\Android\Sdk", "User")

# 2. local.properties 확인 (GDrive로 sync되어 이미 들어있을 것)
#    - HOLIDAY_API_KEY 값 있는지
#    - sdk.dir 라인 없는지 (있으면 삭제 — PC별 SDK 경로 달라 충돌 원인)
#    - 셋업 가이드는 OctaLink/local.properties.template 참조

# 3. Android Studio에서 D:\source\JEON2\OctaLink 열고 Run ▶
#    또는 CLI:
gradlew :app:assembleDebug
```

**핵심 원칙:** `local.properties`는 GDrive sync로 양 PC 공유하지만 **sdk.dir 라인은 들어가지 않음**. SDK 경로는 PC별 `ANDROID_HOME` 환경변수로 처리. Android Studio가 자동으로 `sdk.dir` 추가하면 삭제하세요 (GDrive sync 충돌 원인).

### 릴리즈 서명 (Play Store 업로드용)

```bat
:: 1. keystore 생성 (한 번만, app\octalink-release.jks 가 만들어짐)
tools\generate-release-keystore.bat

:: 2. local.properties 에 다음 4줄 추가
::    RELEASE_KEYSTORE_FILE=app/octalink-release.jks
::    RELEASE_KEYSTORE_PASSWORD=<keystore password>
::    RELEASE_KEY_ALIAS=octalink-release
::    RELEASE_KEY_PASSWORD=<key password>

:: 3. 서명된 빌드
gradlew :app:assembleRelease    :: APK
gradlew :app:bundleRelease      :: AAB (Play Console 업로드용)
```

- `*.jks`, `*.keystore`, `local.properties` 모두 gitignored — 절대 커밋 ❌
- `.jks` 파일을 잃으면 **이 앱을 Play Store에서 업데이트 영구 불가**. 안전한 곳(비밀번호 매니저 + 외장/암호화 드라이브)에 백업 필수

### scrcpy — 실 단말 + 에뮬레이터 동시 미러링

PC 한 화면에서 USB 디버깅 폰과 에뮬레이터를 나란히 띄워 비교 검증할 때. PowerShell 한 창에서 두 device 를 백그라운드 프로세스로 띄움:

```powershell
Start-Process scrcpy -ArgumentList '-s','emulator-5554','--window-title=Emulator','-m','1080','-K' -WindowStyle Hidden
Start-Process scrcpy -ArgumentList '-s','RFCW41APZ1X','--window-title=Phone','--no-audio','--turn-screen-off','-m','1080','-K' -WindowStyle Hidden
```

- `-s <serial>` : `adb devices` 출력의 serial. 에뮬은 보통 `emulator-5554`, 실 단말은 디바이스별 (예: Samsung `RFCW41APZ1X`).
- `-m 1080` : 최대 1080px (성능 부담↓)
- `-K` : 키보드 입력 전달 (한글 IME / 비밀번호 입력)
- `--turn-screen-off` : 폰 화면 끄고 PC 미러링만 (배터리/발열↓)
- `--no-audio` : 오디오 포워딩 끔 (지연 줄임)
- `-WindowStyle Hidden` (PowerShell) : 호스트 콘솔 창 숨김. scrcpy 의 GUI 미러 창은 그대로 뜸. 오류 로그 보고 싶으면 `-RedirectStandardError "$env:TEMP\scrcpy.err"` 추가.
- local.properties에 keystore 4개 키가 다 차 있으면 release 빌드가 자동 서명, 비어 있으면 unsigned debug-only 동작

## 폴더 구조

```
app/src/main/
├── AndroidManifest.xml
├── assets/avatars/README.txt          # 캐릭터 이미지 드롭존 + 저작권 경고
├── java/com/unboundapex/octalink/
│   ├── MainActivity.kt                # installSplashScreen + setContent (init은 Application 으로 이전)
│   ├── OctaLinkApplication.kt         # KakaoSdk.init + Utility.getKeyHash 로그 + HolidayRepository.init + RepositoryProvider.init
│   ├── navigation/PosseApp.kt         # 세션 phase + status 라우팅 + 커스텀 Row 44dp nav + NavHost
│   ├── data/
│   │   ├── AvatarCatalog.kt           # 파이터 스프라이트 10종 카탈로그 (5체급×남/여)
│   │   ├── BeltColors.kt              # 벨트 → 컬러 매핑
│   │   ├── GymInfo.kt                 # 체육관 운영 정보 + 외부 링크
│   │   ├── HolidayRepository.kt       # 공공데이터 API + 캐시
│   │   ├── Curriculum.kt              # 평일 5일 그룹 수업 커리큘럼
│   │   ├── Match.kt                   # 매치 in-memory 모델
│   │   ├── Member.kt                  # 회원 풀 40명 (MockSeed 입력, BracketDraw 는 Repository 경유)
│   │   ├── RoleAllowlist.kt           # creators/masters/coaches 사전 등록 명단 (Phase 1 mock 전용)
│   │   ├── Schedule.kt                # 운영 스케줄 + currentOrNextClassLabel + checkInWindow
│   │   ├── repo/                      # ── Repository 추상화 패키지 (Phase 2 활성) ──
│   │   │   ├── AuthRepository.kt          # 인증 인터페이스 + KakaoIdentity (name/phone/email/gender/age/birthday/birthyear)
│   │   │   ├── MemberRepository.kt        # 회원 CRUD 인터페이스 + SignupRequest + leaveMembership
│   │   │   ├── AttendanceRepository.kt    # 출석 CRUD + observeByDate (collectionGroup) + observeByMember
│   │   │   ├── RepositoryProvider.kt      # 싱글톤 컨테이너 (Phase 2 활성 — Kakao + Firestore)
│   │   │   ├── inmemory/                  # Phase 1 — mock 폴백
│   │   │   │   ├── InMemoryAuthRepository.kt       # mock 카카오 + currentDisplayName
│   │   │   │   ├── InMemoryMemberRepository.kt     # MutableStateFlow 기반 CRUD + leaveMembership
│   │   │   │   ├── InMemoryAttendanceRepository.kt # MockAttendanceSeed 6건 시드 + 본인 체크인 idempotent
│   │   │   │   └── MockSeed.kt                     # memberPool 40명 + 김파시 + 2 PENDING 시드
│   │   │   ├── kakao/                     # Phase 2 — 활성
│   │   │   │   └── KakaoAuthRepository.kt # 카카오 SDK → Cloud Function → Firebase Custom Token, displayName 동기화
│   │   │   └── firestore/                 # Phase 2 — 활성
│   │   │       ├── FirestoreMemberRepository.kt    # members/{uid} 직접 doc get + completeSignup/leaveMembership Cloud Function 호출
│   │   │       ├── MemberDocMapping.kt             # MemberDoc ↔ Firestore Map (kakao 동의 항목 5필드 포함)
│   │   │       ├── FirestoreAttendanceRepository.kt # members/{uid}/attendance/{classDate} + collectionGroup 쿼리
│   │   │       └── AttendanceDocMapping.kt         # AttendanceDoc ↔ Firestore Map
│   │   ├── schema/Schema.kt           # Firestore 영속화 도메인 모델 (*Doc, Role 4단계 enum, MemberDoc 카카오 5필드 포함)
│   │   ├── session/SessionViewModel.kt   # auth(uid+displayName) + member 합성 → SessionState.Phase + .catch{} 안전망
│   │   └── tournament/TournamentViewModel.kt # 토너먼트 상태 + 부전승 자동 처리
│   ├── messaging/
│   │   └── OctaLinkMessagingService.kt # FCM 메시지/토큰 핸들러 (현재 로그만, 알림 표시는 추후)
│   ├── ui/theme/
│   │   ├── Color.kt                   # Ink/Canvas/Ash/Bone/Blood/Mist
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── ui/components/
│   │   ├── HexagonSkillChart.kt       # 6축 레이더 (Canvas)
│   │   ├── AvatarTile.kt              # 원형 아바타 (성별+체급 자동 파생, 정적 PNG, 벨트 색 변형 없음)
│   │   ├── CageIcon.kt                # MMA 옥타곤 케이지 (Canvas, top-view)
│   │   ├── ConfettiOverlay.kt         # 챔피언 확정 시 3연발 색종이 폭죽 (Canvas + withFrameNanos, ~6초)
│   │   └── PosseScaffolds.kt          # PosseScreen / PosseCard (subtitleEmphasis + leftStripeBrush 지원)
│   └── ui/screens/
│       ├── home/HomeScreen.kt         # 로고 배너 + 정권 임팩트 + 성장/진화 강조 + 통계/미션/케이지/피드
│       ├── curriculum/CurriculumScreen.kt # 평일 5일 커리큘럼 카드 + 오늘 하이라이트
│       ├── attendance/
│       │   ├── AttendanceScreen.kt       # 체크인 토글 + 동료 2열 (todayPeers Firestore 실시간) + 본인 카드
│       │   └── AttendanceViewModel.kt    # combine(attendance.observeByDate, members.observeAll) → TodayPeer + .catch{}
│       ├── community/CommunityScreen.kt   # 컬러 칩 태그 + 우측 시각
│       ├── profile/ProfileScreen.kt   # 캐릭터 + 헥사곤 + 승률 + 코멘트 + 로그아웃 + 회원 탈퇴 (확인 다이얼로그)
│       ├── info/InfoScreen.kt         # 체육관 정보 (주소/전화/운영시간/정책/앱 버전)
│       ├── onboarding/OnboardingScreens.kt # LoginScreen / SignupScreen(name+phone prefill) / PendingApprovalScreen / RejectedScreen
│       ├── admin/                      # 운영 탭 진입점 (하단 nav, isStaff 만 노출)
│       │   ├── AdminScreen.kt             # 권한별 카드 분기 + 가입 승인 큐 (활성) + 카드별 스트라이프 색상 차별화
│       │   └── MemberApprovalViewModel.kt # PENDING 목록 + 승인/거부 액션
│       ├── creator/                    # 창조자 전용
│       │   ├── CreatorScreen.kt           # 회원 역할 부여 UI + 블랙홀 스트라이프 + 개발용 seed 버튼
│       │   └── RoleGrantViewModel.kt      # APPROVED 회원 목록 + setRole + seedTestData Cloud Function 호출
│       └── bracket/
│           ├── BracketScreen.kt       # 트리(EIGHT/FOUR/FINAL_ONLY) + 직접 클릭 advance + 불꽃 라인 + ConfettiOverlay
│           └── BracketDrawScreen.kt   # 추첨 (MemberRepository APPROVED 풀, MASTER 제외, 체급/벨트 필터)
└── res/
    ├── drawable-nodpi/
    │   ├── logo_octalink.png               # 마스터 로고 (홈 배너용 워드마크)
    │   ├── mark_octalink.png                # 마크 단독 (Play Store 아이콘 마스터)
    │   └── {m,f}_{feather,light,welter,middle,heavy}.png × 10  # 자체 파이터 스프라이트 (성별×체급)
    ├── mipmap-{m,h,xh,xxh,xxxh}dpi/
    │   ├── ic_launcher.webp                  # 레거시 정사각 (BONE bg)
    │   ├── ic_launcher_round.webp            # 원형 (BONE bg)
    │   └── ic_launcher_foreground.webp       # 어댑티브 전경 (투명)
    ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
    └── values/{colors,strings,themes,ic_launcher_background}.xml

screenshots/                                  # Play Store 등록 자산
├── phone/01_home.png ~ 08_info.png           # 1080×2400 (Pixel 8 native)
├── tablet_7/01_home.png ~ 08_info.png        # 1080×1920 (9:16)
└── tablet_10/01_home.png ~ 08_info.png       # 2560×1440 (16:9)

(프로젝트 루트)
firestore.rules                               # Firestore Security Rules (권한 강제)
firebase.json                                 # Firebase CLI 설정 (firestore + functions)
firestore.indexes.json                        # 복합 쿼리 인덱스 (현재 비어있음, 필요 시 추가)
settings.gradle.kts                           # 카카오 Maven 저장소(devrepo.kakao.com) + google + mavenCentral
functions/                                    # Cloud Functions (TypeScript Gen 2, Node 20, asia-northeast3)
├── package.json                              # firebase-admin + firebase-functions deps
├── tsconfig.json
├── .eslintrc.js                              # linebreak-style: off (Windows CRLF 호환)
└── src/index.ts                              # kakaoSignIn / completeSignup / leaveMembership / seedTestData(개발용)
tools/                                        # 빌드/디자인 보조 스크립트 + 단발 export
├── make-octalink-logo.py                     # 로고/마크/런처 webp 일괄 생성
├── make-playstore-icon.py                    # 512×512 Play 아이콘 생성
├── kakao_icon_128.png                        # 카카오 Developers 콘솔 앱 등록용 (128×128, 6.8KB)
└── generate-release-keystore.bat             # release 서명 키 생성
```

## 결정된 사항

| 영역 | 결정 |
|---|---|
| 출석 체크 | 회원 셀프 (관장 수동 ❌). 1인칭 화면, 큰 토글 버튼 |
| 체크인 색상 | **체크인 = 파랑(#1E88E5)** / **체크인 취소 = 빨강(#C8102E)** |
| 휴무일 | 체크인 비활성 + 동료 명단 비표시 (subtitle "오늘 휴무") |
| 홈 헤더 | 텍스트 대신 로고 배너 (90% 폭 + 진입 시 정권 1초 임팩트) |
| 캐릭터 | 자체 파이터 스프라이트 10종 (5체급 × 남/여). 가입 폼의 성별 + 체급 조합으로 자동 부여 (사용자 직접 선택 ❌) |
| 벨트 표시 | 아바타 링 색 / 매치 카드 스트라이프 색 (텍스트 ❌) |
| 스킬 차트 평가 | **코치 입력 (PROPOSED) → 관장 검토 → 확정 (APPROVED)** 워크플로. 자기/상호 평가 ❌. `SkillScoreDoc.status` enum (PROPOSED/APPROVED/REJECTED) |
| 권한 4단계 | MEMBER (회원, 본인 출결/프로필/커뮤니티 글) · COACH (부관리자, 일상 운영) · MASTER (관장, 운영 전권 — 단 권한 부여 ❌) · **CREATOR (앱 제작자, 회원 역할 부여 단독 + MASTER 권한 자동 포함)**. `Role.isStaff = MASTER+COACH+CREATOR`, `Role.isMaster = MASTER+CREATOR`, `Role.isCreator = CREATOR`. UI 진입점은 **하단 nav "운영" 탭** (`AdminScreen`, isStaff에게만 동적 표시), 그 안에서 권한별 카드 노출 + 창조자는 권한 부여 페이지(`CreatorScreen`)로 진입. 창조자 권한 격리 이유: 관장 계정 탈취 시 무차별 코치 승격 공격 차단 |
| 역할 결정 정책 | **사용자 직접 선택 ❌**. `data/RoleAllowlist.kt`의 사전 등록 명단(creators/masters/coaches)을 카카오 OAuth 표시 이름으로 매칭해 자동 부여. 우선순위 CREATOR → MASTER → COACH → MEMBER. 명단에 있으면 가입 승인 단계 skip + APPROVED 즉시 부여. 그 외엔 MEMBER + PENDING으로 등록되어 관장 승인 대기. 명단 변경은 **코드 수정 + 새 빌드 배포 필요** — 앱 제작자만 코드 + Firebase 양 채널 접근 가능 (SPOF 보호). 추후 Firestore + Cloud Functions 로 런타임 업데이트 가능하게 확장 |
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
| Firestore 컬렉션 구조 | `members/{uid}` 본체 + 종속 데이터(`attendance`/`comments`/`skillScores`)는 서브컬렉션. `tournaments/{id}/matches/{id}` 도 서브컬렉션. `classDefs` / `posts` 는 top-level. Security Rules에서 본인/운영진/관장 권한별로 분기 |
| 회원 가입 시 권한 부여 | Cloud Function (auth onCreate trigger) 가 server-side allowlist 조회 → role + status 결정해서 `members/{uid}` 생성. 클라이언트 직접 create는 차단 (rule `allow create: if false`). 클라이언트는 본인 안전 필드(name/belt/avatarId/phone)만 update 가능 |

## Play Store 출시 체크리스트

배포 직전에 점검할 항목. 인프라 코드는 준비되어 있고, 실제 등록/호스팅/이미지만 운영자가 채우면 됨.

- [x] **`RELEASE_KEYSTORE_*` 4개 키 생성 + 백업** — `app/octalink-release.jks` (CN=OctaLink, O=Unbound Apex Systems). AAB 빌드 검증 완료. 비밀번호 매니저 + 외장 / 암호화 드라이브 백업 권장 (분실 시 업데이트 영구 불가)
- [x] **`docs/privacy-policy.html` 호스팅** — GitHub Pages 발행, `https://ljy9969.github.io/OctaLink/privacy-policy.html` 에서 렌더링 확인 완료
- [x] **`GymInfo.PRIVACY_POLICY_URL` 갱신** — Pages URL 반영 완료
- [x] **앱 아이콘 512×512 PNG** — `app/src/main/ic_launcher-playstore.png` (흰 배경 + 로고 92% 폭). 생성 스크립트: `tools/make-playstore-icon.py`
- [x] **피처 그래픽 1024×500** — `app/src/main/feature_graphic2.png` (운영자 손제작, 흰 배경 + OctaLink 워드마크 + 슬로건 "개인의 성장, 함께하는 진화"). PIL 자동생성판(`feature_graphic_v3.png`)과 옛 Canva 버전(`feature_graphic3.png`)은 사용 중지
- [x] **폰 스크린샷 8장 신규 OctaLink 로고로 재캡처** — `screenshots/phone/01_home.png` ~ `08_info.png` (1080×2400 portrait, Pixel 8 native). **Play 콘솔 기본 스토어 등록정보에 업로드 완료** (2026-05-10)
- [x] **7" 태블릿 스크린샷 8장** — `screenshots/tablet_7/01_home.png` ~ `08_info.png` (1080×1920, 정확히 9:16, PNG, 모두 8MB 이하). Nexus 7 2013 에뮬레이터(1200×1920) 캡처 후 좌우 60px씩 중앙 크롭으로 9:16 보정 (2026-05-10). **Play 콘솔 기본 스토어 등록정보에 업로드 완료**
- [x] **10" 태블릿 스크린샷 8장** — `screenshots/tablet_10/01_home.png` ~ `08_info.png` (2560×1440, 정확히 16:9 landscape, PNG, 모두 8MB 이하). Pixel Tablet 에뮬레이터(2560×1600 16:10) 캡처 후 위아래 80px 중앙 크롭 (2026-05-10). **Play 콘솔 기본 스토어 등록정보에 업로드 완료**
- [x] **앱 설명** — 단문(75자, 체육관별 채널 + 교류전 확장 컨셉) + 장문(4000자 한도 내, OctaLink 브랜드 + 6개 주요 기능 + 채널 모델 + 교류전 확장 vision) 작성, **Play 콘솔에 입력 완료** (2026-05-10)
- [x] **Play 콘솔 개발자 계정 등록** ($25) — 2026-05-08 본인 명의 개인 계정으로 완료
- [x] **콘텐츠 등급 IARC 설문** — 12+ 확정 (러시아 14, 미국 ESRB TEEN, 독일 USK 12, IARC 12+, 호주 PG 등). 2026-05-10 Play 콘솔 입력 완료
- [x] **타겟 API Level 35 마이그레이션** — `compileSdk` / `targetSdk` 34 → 35, Compose BOM 2024.06.00 → 2024.10.00, `themes.xml`의 `statusBarColor`/`navigationBarColor` 제거 (edge-to-edge에서 무시됨). `MainActivity.enableEdgeToEdge()` + `Scaffold` 자동 inset 처리. **2026-05-10 작업 PC에서 빌드 통과 + Pixel 8 API 37 에뮬레이터 시각 검증 완료** (status bar / bottom nav / InfoScreen 푸터 모두 정상)
- [x] **체육관 로고 우회 (자체 OctaLink 브랜딩)** — Team Posse Striking 로고 사용 불가 → 자체 옥타곤+슬래시 마크 + "OctaLink" 워드마크로 교체 (2026-05-09). 체육관 명칭 텍스트는 InfoScreen에 유지 (카카오톡 등 사용 동의 별도 권장)
- [x] **앱 내 비공식 표기** — `InfoScreen.kt` 최하단에 "본 앱은 {체육관명} 회원이 자체 제작한 비공식 도구입니다. 체육관 공식 앱이 아닙니다." 푸터 추가 (labelSmall + onSurfaceVariant + 중앙 정렬, 카드 외부 footnote 스타일) (2026-05-10)
- [x] **캐릭터 자산 자체 제작 확정** — USF4 자산은 저작권 회피 위해 main 브랜치에서 제거(`teamposse` 브랜치 원격 백업). 대체로 **자체 파이터 스프라이트 10종** (5체급 × 남/여) 제작. 사용자 선택 ❌, 가입 폼의 성별 + 체급 조합으로 자동 부여 → 동일 성별·체급 회원이 같은 아바타를 공유하는 단순화된 정체성 모델

## 알려진 함정

- **Image Asset Studio** 가 새 XML 파일 생성 시 라이선스 주석을 `<?xml?>` 선언 위에 삽입함 → mergeResources 빌드 실패. 마법사 사용 후 빌드 깨지면 신규 XML 첫 줄 확인.
- `Image(painter, ...)` 오버로드에는 `filterQuality` 파라미터 없음 (`Image(bitmap, ...)` 전용).
- 어댑티브 아이콘 foreground는 spec상 외곽 33%가 안전 여백 → 본문 재활용 시 작게 보임. 본문용은 `drawable-nodpi/`에 원본 별도 배치.
- 에뮬레이터 마우스 휠 스크롤이 터치 이벤트로 변환되지 않을 수 있음 → 클릭+드래그로 시뮬레이션.
- 공휴일 API 응답이 비어있을 때 폴백(2026 하드코딩) 사용 → 매년 갱신 또는 다년치 폴백 필요.
- `Text.softWrap = false`는 hard `\n`까지 무력화시킴 → 줄바꿈 필요 시 softWrap 기본값 유지하고 maxLines로만 제한.
- 토너먼트 상위 매치가 미결정인데 round2 슬롯의 "?"를 부전승으로 자동 advance 하면 안 됨 — `propagateRound2Bye`에서 "더블 바이 vs 미결정 실경기" 구분 필수.
- **Kotlin 블록 주석은 nested**: 백틱 ` 안의 `/*` 도 nested comment 시작으로 파싱됨 → 닫히지 않으면 EOF에서 "Unclosed comment" 에러. KDoc에 경로 예시 쓸 때 `/*` 패턴 회피.
- **Play 콘솔 스토어 등록정보는 7"/10" 태블릿 스크린샷이 필수(`*`)** — 폰 스크린샷만 가지고는 "기본 스토어 등록정보 만들기" 저장 자체가 막힘. 폰만 타겟이어도 태블릿 에뮬레이터로 별도 캡처 필요.
- **Firebase BOM 33.0+ 부터 `-ktx` 접미사 라이브러리 폐지** — `firebase-auth-ktx` / `firebase-firestore-ktx` 등은 더 이상 publish되지 않음. KTX 확장은 메인 라이브러리(`firebase-auth`, `firebase-firestore`)에 통합됨. 옛 가이드 따라 `-ktx` 쓰면 `Could not find com.google.firebase:firebase-xxx-ktx:.` 에러 발생.
- **Firebase Storage는 2024 Q3+ Blaze(종량제) 필수** — 무료 Spark 요금제로는 활성화 안 됨 ("프로젝트 요금제를 업그레이드하세요" 메시지). MVP에선 Storage 사용 안 하고 deferred, 회원 사진/커뮤니티 영상 도입 시 Blaze 전환 + 예산 알람($1/월) 설정 권장.
- **카카오 SDK는 Maven Central 미게시** — `com.kakao.sdk:v2-user` 등은 카카오 자체 Nexus 저장소 (`https://devrepo.kakao.com/nexus/content/groups/public/`) 에서만 받음. `settings.gradle.kts` 의 `dependencyResolutionManagement.repositories` 에 추가 필수. 빠뜨리면 `Could not find com.kakao.sdk:v2-user:X.X.X` 에러 + Google Maven / Maven Central 검색 실패 로그.
- **Cloud Functions Gen 2 첫 배포 시 Cloud Build SA IAM 권한 필요** — 2024+ GCP 신규 프로젝트는 Compute Engine 기본 SA (`{projectNumber}-compute@developer.gserviceaccount.com`) 에 `Cloud Build SA` / `Artifact Registry Writer` / `Logs Writer` 역할이 자동 부여되지 않음. 첫 `firebase deploy --only functions` 가 `Build failed... missing permission on the build service account` 에러로 끝남. GCP IAM 콘솔에서 세 역할 수동 부여 후 재배포.
- **Node 24 + Windows 에서 firebase-tools 의 `$RESOURCE_DIR` predeploy 변수 치환 깨짐** — `firebase.json` 의 `"predeploy": ["npm --prefix \"$RESOURCE_DIR\" run build"]` 가 `%RESOURCE_DIR%` 로 변환된 뒤 spawn 에서 ENOENT. 우회: predeploy 훅 제거 + `cd functions; npm run build` 수동 실행 후 `firebase deploy --only functions`.
- **ESLint Google preset 의 `linebreak-style: lf` 룰이 Windows CRLF 체크아웃과 충돌** — `git core.autocrlf=true` 기본 설정에선 함수 ts 파일이 CRLF 로 풀려나와 lint 142 에러. `functions/.eslintrc.js` 에서 `"linebreak-style": "off"` 추가. Cloud Functions 는 Linux 컨테이너 실행이라 줄바꿈 형식 의미 없음.

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
- [x] (05-08) `배포 준비` **앱 서명 키 생성 + 서명된 AAB 빌드** — `app/octalink-release.jks` 발급, `gradlew :app:bundleRelease` 통과
- [x] (05-08) `배포 준비` **앱 아이콘 + 피처 그래픽 확정** — `ic_launcher-playstore.png` 신규 + `feature_graphic3.png` 확정. 생성 스크립트 `tools/make-playstore-icon.py`, `tools/make-feature-graphic*.py`
- [x] (05-08) `배포 준비` **앱 / 회사 / 개발자 네이밍 정리** — 앱 OctaLink, 회사 Unbound Apex Systems, 개발자 BlackCat Strike. 문서·UI·privacy-policy 일괄 갱신
- [x] (05-08) `배포 준비` **GitHub 저장소 + 디렉토리 + 패키지 리네임** — repo `Team-Posse → OctaLink`, dir `teamposse-app → OctaLink`, package `com.teamposse.striking → com.unboundapex.octalink` (applicationId 동일)
- [x] (05-08) `UX 디테일` **출석 30분 윈도우 게이팅** — `Schedule.kt:checkInWindow()` + `CheckInWindow` enum. 휴무 / 수업 종료 / 30분 초과 모두 비활성
- [x] (05-09) `배포 준비` **자체 OctaLink 브랜딩 완료** — Team Posse 로고 사용 불가 → 옥타곤+슬래시+ctaLink 자체 컨셉 확정. 운영자 손제작 마스터 2장(`feature_graphic2.png` + `mark_octalink.jpg`)을 PIL 파이프라인에 투입해 18종 자산 일괄 생성: `logo_octalink.png` + `mark_octalink.png` + 런처 5밀도×3종 webp + `ic_launcher-playstore.png`. 디자인은 손대지 않고 리사이즈/배경 합성/샤프닝만 적용
- [x] (05-09) `배포 준비` **체육관 로고 우회** — Team Posse 로고 사용 불가 → 자체 OctaLink 브랜딩으로 회피. 체육관 명칭 텍스트 사용 동의는 카카오톡 등으로 별도 권장
- [x] (05-10) `배포 준비` **폰 스크린샷 8종 재캡처** — 신규 OctaLink 로고로 Pixel 8(1080×2400)에서 8화면(홈/추첨/매치/커리큘럼/출석/커뮤니티/프로필/Info) 재캡처. `screenshots/phone/`로 정리. Play 콘솔 업로드 완료
- [x] (05-10) `배포 준비` **7" 태블릿 스크린샷 8장** — Nexus 7 2013 에뮬레이터(1200×1920)에서 8화면 캡처 → 좌우 60px 중앙 크롭으로 1080×1920 (9:16) 보정. 모두 PNG, 8MB 이하, Play Store 규정 통과
- [x] (05-10) `배포 준비` **10" 태블릿 스크린샷 8장** — Pixel Tablet 에뮬레이터(2560×1600 16:10) 캡처 후 위아래 80px 중앙 크롭으로 2560×1440 (16:9 landscape) 보정. PowerShell System.Drawing 사용 (집 PC, miniconda Pillow 미설치 환경)
- [x] (05-10) `배포 준비` **Play 콘솔 기본 스토어 등록정보 — 폰/7"/10" 스크린샷 업로드** — 폰 8 + 7" 8 + 10" 8 = 24장 모두 업로드
- [x] (05-10) `배포 준비` **앱 설명 작성 + Play 콘솔 입력** — 채널 모델 + 체육관 간 교류전 확장 비전 반영. 단문 75자, 장문 4000자 한도 내. 개인정보처리방침도 같은 컨셉으로 1.1 갱신
- [x] (05-10) `배포 준비` **콘텐츠 등급 IARC 설문 완료** — 12+ (러시아 14, 미국 TEEN, 독일 USK 12, IARC 12+, 호주 PG)
- [x] (05-10) `배포 준비` **앱 내 비공식 표기 푸터** — `InfoScreen.kt`에 footnote 스타일로 추가, 체육관 공식 앱 아님 명시
- [x] (05-10) `배포 준비` **저작권 정리 (USF4 캐릭터 이미지)** — 옵션 결정: **유저 업로드 + 이니셜 폴백**. main 브랜치에서 USF4 이미지 제거 (백업은 `teamposse` 브랜치 원격 보존). `AvatarCatalog.kt`의 기존 폴백(`이미지 미존재 시 컬러 + 이니셜`) 활용 + 향후 회원이 본인 사진 업로드 가능하게 확장 (Firebase Storage 도입 시)
- [x] (05-10) `배포 준비` **타겟 API Level 35 빌드 검증 통과** — 회사 PC Android Studio에서 빌드 성공 + Pixel 8 API 37 에뮬레이터에서 시각 검증 완료 (status bar / bottom nav / InfoScreen 푸터 모두 정상). 코드 변경(compileSdk/targetSdk 35 + Compose BOM 2024.10.00 + themes.xml edge-to-edge)이 forward-compat 환경에서도 동작 확인됨
- [x] (05-10) `배포 준비` **Play 콘솔 내부 테스트 트랙 첫 출시** — `app-release.aab` (versionCode 1, 0.1.0) 업로드, 13/13 앱 콘텐츠 폼 완료, 내부 테스터 추가, 출시 노트 작성, 출시 시작 → 자동 검토 진입
- [x] (05-10) `백엔드 / 데이터 (메인)` **Firebase 프로젝트 생성 완료** — Phase 1-4 끝. (1) `OctaLink-prod` 콘솔 프로젝트 + Android 앱 등록(`com.unboundapex.octalink`) + google-services.json 다운로드 (2) gradle plugin 4.4.2 + Firebase BOM 34.13.0 + Auth/Firestore/Messaging/Analytics 의존성 (3) SHA-1 debug+release 등록 → google-services.json 재다운로드 + 빌드 통과 (4) Firestore (asia-northeast3 서울, 프로덕션 모드) + Auth (익명 활성) + FCM 기반(POST_NOTIFICATIONS 권한 + `OctaLinkMessagingService` skeleton, Cloud Messaging 콘솔 테스트 메시지 송수신 검증 완료). **Storage는 deferred** (Blaze 유료 요금제 필수, 회원 사진/커뮤니티 영상 도입 시 전환)
- [x] (05-10) `UX 디테일` **관장/코치/회원 권한 3단계 분리 (UI)** — `Role` enum 문서화 + `Role.isStaff` (MASTER+COACH) / `Role.isMaster` (MASTER) 확장 함수. `SkillScoreDoc.status` enum (PROPOSED/APPROVED/REJECTED) 추가 (코치 입력 → 관장 검토 워크플로). UI 분기: **운영진 전용** 카드 (회원 코멘트 / 출결 검토 / 토너먼트 관리 / 공지 작성 / 스킬 점수 입력 - 코치+관장 가능) + **관장 전용** 카드 (스킬 점수 검토·확정 / 회원 가입 승인 / 권한 부여). `CommunityScreen`에 글 쓰기 버튼(전원) + 공지 작성 버튼(운영진).
- [x] (05-10) `보안` **역할 결정 보안 강화** — `data/RoleAllowlist.kt` 사전 등록 명단(masters/coaches) 도입. `SessionState.role`이 이름 → allowlist 자동 매핑으로 결정. 사용자 직접 변경 불가능 (Profile의 디버그 역할 토글 삭제, `SessionViewModel.updateRole()` 메서드 제거). 명단 등록자는 카카오 OAuth 가입 시 PENDING 단계 skip → 즉시 APPROVED. 명단 외엔 MEMBER + PENDING으로 관장 승인 대기. 권한 부여는 코드 수정 + 새 빌드 배포 필요
- [x] (05-10) `백엔드 / 데이터 (메인)` **Firestore Security Rules 초안** — `firestore.rules` 작성. 권한 모델(MEMBER/COACH/MASTER) → 데이터 레이어 강제. members write는 Cloud Function 전용(privilege escalation 차단), 본인 프로필은 안전 필드(name/belt/avatarId/phone)만 수정. 출석/코멘트/스킬/토너먼트/posts 모두 권한별 read·write 분리 + skillScores PROPOSED→APPROVED 전환은 관장만(점수 자체 immutable). 매칭 안 된 경로는 명시적 거부(allowlist 모델). 배포: Firebase Console 붙여넣기 또는 `firebase deploy --only firestore:rules`. `firebase.json` + `firestore.indexes.json` 같이 생성
- [x] (05-11) `보안` **권한 4단계 확장 — CREATOR 분리** — 관장 계정 탈취 시 무차별 권한 상승 공격 차단 위해 회원 역할 부여(코치 승격) 권한을 앱 제작자 단독(`CREATOR`)으로 격리. `Role.CREATOR` enum 추가 + `Role.isCreator` / `isMaster`(MASTER+CREATOR) / `isStaff`(3개 포함) 확장 함수 갱신. `RoleAllowlist.creators` Set 신규(이지연만), masters에서 이지연 제거. `firestore.rules` v2: `isCreator()` 함수 + `roleUnchanged()` + members update 3단계 분기 (본인 안전 필드 / 관장 role 외 / 창조자 전체). delete는 CREATOR만. Repository 도입 후 `db.collection('members').document(uid).update('role', ...)` 실제 호출
- [x] (05-11) `UX 디테일` **운영 진입점을 별도 nav 탭으로 분리** — 이전엔 Profile 하단에 운영진/관장/창조자 카드 누적 → 하단 nav에 "운영" 탭(`AdminScreen`) 신규. `isStaff` 회원에게만 동적 표시(MEMBER에는 안 보임). AdminScreen에서 권한별 카드 분기(운영진 공통 / 관장 / 창조자). 창조자 카드의 "권한 부여 페이지" 버튼 → `CreatorScreen` 진입. CreatorScreen mock 회원 6명(이지연·김파시 제외) + 벨트 5단계 모두 포함(WHITE/BLUE/PURPLE/BROWN/BLACK) + 체급/벨트 내림차순 정렬 + 가입일 표시 제거(체급·벨트만)
- [x] (05-11) `백엔드 / 데이터 (메인)` **Repository 추상화 + 가입 플로우 + Auth 인터페이스 (Phase 1 — InMemory)** — `data/repo/` 패키지 신규. (1) `AuthRepository` + `KakaoIdentity` / `MemberRepository` + `SignupRequest` 인터페이스 정의. (2) `inmemory/InMemoryAuthRepository` (mock 카카오 - 이지연 자동 로그인 + signInWithKakao() 호출 시 새 fake uid 발급으로 신규 가입 시뮬레이션), `inmemory/InMemoryMemberRepository` (signup → RoleAllowlist 매칭 즉시 APPROVED / 그 외 PENDING, setStatus/setRole/updateProfile), `inmemory/MockSeed` (이지연 CREATOR + 김파시 MASTER + 6명 APPROVED + 2명 PENDING). (3) `RepositoryProvider` 싱글톤 — MainActivity 에서 1회 init. (4) `SessionViewModel` 재작성 — auth.currentUid + members.observeByAuthProviderId 합성으로 `SessionState.Phase` (LOADING/UNAUTHENTICATED/PENDING_SIGNUP/AUTHENTICATED) + member.status 분기. (5) `ui/screens/onboarding/OnboardingScreens.kt` 신규 — `LoginScreen`(카카오 로그인 버튼) / `SignupScreen`(이름·벨트·체급·아바타·연락처 폼) / `PendingApprovalScreen` / `RejectedScreen`. (6) `PosseApp` 라우팅 분기 (phase + status 별) — APPROVED 가 아닌 단계는 메인 앱 접근 차단. (7) `AdminScreen` 의 "회원 가입 승인" 카드 활성화 — `MemberApprovalViewModel` 로 PENDING 큐 + 승인/거부 액션. (8) `CreatorScreen` 의 mock 명단 제거 → `RoleGrantViewModel` 로 실제 MemberRepository 데이터 + setRole() 실제 동작. (9) Profile 에 로그아웃 버튼 (가입 플로우 테스트용). **Phase 2 (deferred):** 실제 카카오 SDK 통합 + Firebase Custom Token 교환 Cloud Function + `FirestoreXxxRepository` 실제 구현체 — UI/VM 변경 없이 RepositoryProvider 만 교체
- [x] (05-11) `UX 디테일` **MockSeed 풀 확장 + draw 풀 MASTER 제외** — `memberPool` 40명을 MockSeed 로 흡수(이지연 CREATOR + 김파시 MASTER + 38 MEMBER + 2 PENDING). `BracketDrawScreen` 정적 `memberPool` 의존 제거 → `MemberRepository.observeByStatus(APPROVED)` 구독 + `MemberDoc → Member` 어댑팅. 가입 승인된 신규 회원이 즉시 추첨 풀에 합류. 관장(MASTER)은 추첨 대상이 아니라 운영자이므로 풀에서 제외(`role != MASTER` 필터)
- [x] (05-11) `UX 디테일` **HomeScreen subtitle 성장/진화 강조 + 토너먼트 챔피언 폭죽** — `PosseScreen.subtitleEmphasis: List<String>` 파라미터 신규 → `AnnotatedString` 으로 키워드만 primary 색 + ExtraBold 인라인 렌더링. HomeScreen 의 "개인의 성장, 함께하는 진화" 에서 "성장"/"진화" 강조. titleSmall + onSurface 로 폰트 강화 + 로고와 간격 -16dp 오프셋. `ConfettiOverlay.kt` 신규 — 3연발 색종이 폭죽(35입자/burst, 부채꼴 솟구침 + 중력 낙하 + 회전 + 페이드, ~6초 자동 종료). `BracketScreen.ChampionBanner` 챔피언 확정 시 이름 0.55x → 1.25x bouncy spring → 1.0x + 골드(#FFD54F) ExtraBold 전환 + 골드 보더
- [x] (05-11) `버그 / 호환성` **SignupScreen 한글 IME 입력 지원** — `OutlinedTextField` String 기반 + `onValueChange` 안 변환(`.take(20)`)이 한글 조합 영역(composition region)을 매 키마다 리셋시켜 첫 자모 입력이 무시되는 Compose 알려진 이슈. `TextFieldValue` 사용 + onValueChange 변환 완전 제거(길이 제한은 제출 시점 `.take(20)` 으로 이전) + `KeyboardType.Text` 명시. 전화번호 필드는 숫자 전용(`KeyboardType.Number` + `filter { isDigit() }` + 최대 11자리)
- [x] (05-11) `백엔드 / 데이터 (메인)` **Phase 2 카카오 OAuth + Cloud Functions + Firestore 스캐폴드** — 통합 구현체 작성 완료. (1) 카카오 Developers 앱 등록(OctaLink, ID 1453976, 비즈앱) + 카카오 로그인 활성화 + 닉네임 필수 동의 + 네이티브 앱 키 발급 → `local.properties` 의 `KAKAO_NATIVE_APP_KEY`. (2) `app/build.gradle.kts`: BuildConfig + `manifestPlaceholders["KAKAO_NATIVE_APP_KEY"]` + `com.kakao.sdk:v2-user:2.20.6` + `firebase-functions` + `kotlinx-coroutines-play-services:1.8.1`. (3) `AndroidManifest.xml`: `OctaLinkApplication` Application 클래스 신규 + `com.kakao.sdk.auth.AuthCodeHandlerActivity` intent-filter(`kakao${KAKAO_NATIVE_APP_KEY}://oauth`). (4) `OctaLinkApplication.onCreate` 에서 `KakaoSdk.init` + `RepositoryProvider.init(context)` 1회 — MainActivity 의 init 제거. (5) `data/repo/kakao/KakaoAuthRepository.kt`: `UserApiClient.loginWithKakaoTalk` / `loginWithKakaoAccount` → Cloud Function `kakaoSignIn` → `FirebaseAuth.signInWithCustomToken` 4단계. uid 형식 `kakao:{kakaoUserId}` 가 MemberDoc.authProviderId 와 매칭. (6) `data/repo/firestore/FirestoreMemberRepository.kt` + `MemberDocMapping.kt`: `members/{uid}` 컬렉션 callbackFlow 기반 observe + `Timestamp` ↔ `Instant` / enum `.name` 매핑. signup 은 client create 차단으로 `completeSignup` Cloud Function 호출 패턴. (7) `functions/` 디렉토리 신규(TypeScript, Node 20): `kakaoSignIn(accessToken)` — Kakao `/v2/user/me` 토큰 검증 → Firebase Auth user ensure → Custom Token 발급. `completeSignup` — server-side RoleAllowlist 매칭(이지연 CREATOR / 김파시 MASTER) → `members/{uid}` 문서 생성. (8) `settings.gradle.kts` 카카오 자체 Maven 저장소(`https://devrepo.kakao.com/nexus/content/groups/public/`) 추가 — Maven Central 미게시. **알려진 함정**: Node 24 + Windows 에서 firebase-tools 의 `$RESOURCE_DIR` predeploy 변수 치환이 spawn 단에서 깨짐 → `firebase.json` predeploy 훅 제거 + 수동 `npm run build` 로 우회. ESLint Google preset 의 `linebreak-style: lf` 가 Windows CRLF 와 충돌 → `linebreak-style: off`
- [x] (05-12) `백엔드 / 데이터 (배포)` **Phase 2 활성화 완료 + 부트스트랩/Cloud Run 권한 함정 해결 + KakaoIdentity prefill** — 활성화 풀 스택. (1) Firebase Blaze 플랜 전환 (Cloud Functions 외부 fetch 위해 필수). (2) Cloud Build 서비스 계정(`{프로젝트번호}-compute@developer.gserviceaccount.com`)에 IAM 역할 부여 — `Cloud Build SA` + `Artifact Registry Writer` + `Logs Writer` + `Service Account Token Creator`(`createCustomToken` 의 signBlob 권한). (3) `cd functions; npm install; npm run build` 후 `firebase deploy --only functions` 배포. (4) Cloud Functions Gen 2 가 Cloud Run 으로 deployed 라서 `kakaosignin` / `completesignup` 서비스에 `allUsers + roles/run.invoker` 부여 필요(클라이언트는 로그인 전이라 인증 헤더 없음, 함수 자체가 카카오 토큰 검증). (5) `firebase deploy --only firestore:rules`. (6) `RepositoryProvider.init()` 의 InMemory → KakaoAuthRepository + FirestoreMemberRepository 두 줄 토글. (7) `OctaLinkApplication.onCreate` 에 `Utility.getKeyHash(this)` 로그 추가 — 카카오 콘솔 키해시 자동 등록 검증. (8) **부트스트랩 룰 함정**: 신규 가입자가 자기 `members/{uid}` 문서 없는데 `isApproved()` → `memberDoc()` → `get()` 시 evaluation error 발생해 모든 read 거부 → 앱 크래시. 수정: `firestore.rules` 의 members read 를 `allow get: if isSelf(uid) || isApproved();` + `allow list: if isApproved();` 로 분리해 self 가 자기 doc 부재 케이스도 통과. (9) `FirestoreMemberRepository.observeByAuthProviderId` 를 collection list 쿼리 → 직접 doc get(`observeById`) 으로 단순화 (Cloud Function 이 doc id = uid 로 생성하므로 동일). (10) `SessionViewModel` `combine(uid, displayName, member)` flow + `.catch{}` 로 Firestore 예외 시 UNAUTHENTICATED 폴백(앱 죽지 않음). (11) `AuthRepository.currentDisplayName` 인터페이스 추가, `KakaoAuthRepository` 가 `firebaseAuth.currentUser?.displayName` 추적(Cloud Function 이 Auth user 생성 시 nickname 세팅) → 앱 재시작 시 `signInWithKakao()` 호출 없이도 SignupScreen 이름 prefill. (12) `SignupScreen` 에 두 `LaunchedEffect` 추가 — kakao nickname/phone 으로 prefill (사용자 입력 시작 시 덮어쓰지 않음). **end-to-end 검증**: 이지연 카카오 가입 → SignupScreen 자동 이름 입력 → 제출 → `completeSignup` Cloud Function → Firestore `members/kakao:4891520650` 문서 생성 (role=CREATOR, status=APPROVED) → 메인 앱 진입
- [x] (05-12) `백엔드 / 데이터` **카카오 비즈앱 동의 항목 확장 + MemberDoc 풀스택 7필드 추가** — 비즈 인증 통과 후 카카오 동의 항목 확장(name 필수 / phone_number 필수 / email 선택 / gender 필수 / age_range 필수 / birthday 필수 / birthyear 필수). 닉네임/프로필 사진은 "사용 안 함"으로 비활성화하고 `name` (실명) 을 displayName 으로 사용. (1) `MemberDoc` 에 `email/gender/ageRange/birthday/birthyear` 5필드 추가. (2) `KakaoIdentity` 동일 확장 + `KakaoAuthRepository` 가 Kakao SDK `User.kakaoAccount.{name/phoneNumber/email/gender/ageRange/birthday/birthyear}` 읽기. `displayName` 우선순위: `name` (실명) → `profile.nickname` (fallback). (3) `SignupRequest` 5 필드 추가 + `SessionViewModel.completeSignup` 이 `state.kakaoIdentity` 에서 합산해 전달. (4) `MemberDocMapping` read/write 양쪽에 5필드 추가. (5) `FirestoreMemberRepository.signup` 이 Cloud Function 호출 시 같이 전달. (6) Cloud Function `completeSignup` 이 5필드 persist (null 허용). (7) `SignupScreen` 의 prefill `LaunchedEffect` 가 이제 전화번호도 자동 입력 (`+82 10-...` → `010...` 정규화, 11자 cap). **end-to-end**: 카카오 로그인 → 가입 폼에 이름+전화 자동 입력 → 제출 → Firestore `members/{uid}` 에 7필드 모두 채워짐
- [x] (05-12) `백엔드 / 데이터` **AttendanceRepository — Phase 2 후속 첫 번째 (출석 체크인 영속화)** — Member 와 동일 패턴으로 확장. (1) `AttendanceRepository` 인터페이스(observeByDate / observeByMember / checkIn / cancelCheckIn) + `InMemoryAttendanceRepository`(MockAttendanceSeed 6건) + `FirestoreAttendanceRepository`. Firestore 경로 `members/{memberId}/attendance/{classDate}` — `classDate` ISO 문자열이 doc ID 라 하루 1 체크인 idempotent. (2) 동료 출석 조회는 `db.collectionGroup("attendance").whereEqualTo("classDate", today)` — 모든 회원 서브컬렉션 한 번에 스캔. `firestore.indexes.json` 에 `attendance.classDate` COLLECTION_GROUP 인덱스 명시. (3) `firestore.rules` 의 attendance: `allow read: if isApproved()` (동료 가시 필요), `create/update: if isSelf(uid) && memberId == uid`, `delete: if isSelf(uid) || isStaff()`. (4) `AttendanceViewModel` — `combine(attendance.observeByDate, members.observeAll)` 으로 attendance docs + 회원 마스터 join → `TodayPeer(attendance, member)` 리스트. `.catch{}` 로 Firestore 예외 시 빈 목록 폴백. (5) `AttendanceScreen` mock `alreadyCheckedIn` 제거 → 실시간 Firestore 데이터. 본인 체크인 여부는 `todayPeers.firstOrNull { it.member.id == myMemberId }` 로 derive. (6) `RepositoryProvider.attendance` 필드 추가. Phase 1 InMemory / Phase 2 Firestore 양쪽 다 동작
- [x] (05-12) `UX 디테일` **회원 탈퇴 (leaveMembership) + AdminScreen 카드별 색상 차별화 + 개발용 seed 도구** — (1) 프로필 화면 "로그아웃" 아래 **"회원 탈퇴"** 카드(빨강) + 확인 다이얼로그 ("앱 이용 중단 / 도장 명단 완전 삭제는 관장님께 요청 / 출결·스킬 등 기록 보존"). `MemberRepository.leaveMembership(memberId)` + Cloud Function `leaveMembership`(server-side `status=LEFT` 갱신, audit trail) + `SessionViewModel.leaveMembership()`(Repository → signOut). status=LEFT 는 PosseApp 의 `RejectedScreen` 라우팅. (2) `AdminScreen` 카드 4개 스트라이프 색상 차별화: 운영진 공통=코치 블루(`#1E88E5`), 가입 승인=앰버(`#FBC02D`, 액션 큐), 관장 전용=관장 빨강(`#C8102E`), **창조자 전용=블랙홀 그라데이션**(보라→검정→주황). `PosseCard` 에 `leftStripeBrush: Brush?` 파라미터 신규 — 단일 Color 대신 그라데이션 사용 가능. (3) 개발용 `seedTestData` Cloud Function — CREATOR 만 호출, 테스트 회원 5명(박정호/최민서/김상혁/한도윤/신예린) + 오늘 출석 doc 멱등 생성. CreatorScreen 의 노란색 "개발용 — 테스트 시드" 카드 버튼에서 호출(`RoleGrantViewModel.seedTestData`). 운영 출시 전 제거 예정. (4) 디버그 로깅 강화: `OctaLink.KakaoAuth` 단계별 로그, `OctaLink.Attendance` checkIn/cancel/flow 결과, `OctaLink.KeyHash` 시작 시 출력 → Logcat 진단성 대폭 개선
- [x] (05-14) `UX 디테일` **캐릭터 자동 파생 + 픽커 제거** — 가입 시점 frozen 된 `MemberDoc.avatarId` 가 Kakao gender 누락 시 `m_*` 로 굳어 FEMALE 회원에게도 남자 스프라이트가 렌더되던 버그(이지연/신예린). 해결: avatarId 를 저장하지 않고 **렌더 시점에 `avatarFor(gender, weightClass)` 로 매번 파생**. `SessionState.avatarId` getter / `BracketDrawScreen` Member 어댑터 / 신규 가입 시 `SignupRequest.avatarId` 전부 동일 규칙. `AvatarPickerSheet.kt` + `SessionViewModel.updateAvatar()` 삭제, 프로필/가입 폼의 "탭해서 변경" 안내 제거 후 "성별 · 체급에 따라 자동 부여" 라벨로 교체. `AvatarTile` 에 `showBeltRing: Boolean = true` 파라미터 추가 — DRAW MemberRow 는 좌측 스트라이프와 중복이라 false 로 호출
- [x] (05-14) `UX 디테일` **AdminScreen 카드 색 4 → 5 분리 + 미등급 벨트 칩 균등 너비 + 스킬 입력 2열** — (1) 회원 가입 승인(앰버)과 미등급 벨트 지정이 같은 앰버 스트라이프를 공유하던 문제 → **미등급 벨트 카드를 티얼(`#00897B`)로 분리** (`StripeUnknownBelt` 신규). 카드 5종 모두 고유 색. (2) `UnknownBeltRow` 의 5단 벨트 칩이 텍스트 길이로 너비가 흔들리던 문제 → `Box(weight=1f)` + `RoundedCornerShape` + 중앙정렬 텍스트로 재구성, 행을 균등 분할. (3) `회원 스킬 점수 입력` 카드의 회원 목록을 1열 → **2열 그리드**(`chunked(2)` + 셀별 `Modifier.weight(1f)`, 홀수 마지막 행은 빈 Spacer 로 폭 균형). `SkillTargetRow` 는 `modifier: Modifier = Modifier` 파라미터 받아 부모가 폭 결정
- [x] (05-14) `배포 준비` **versionCode 1→2 / versionName 0.2.0 + 서명된 2.aab 빌드** — `app/build.gradle.kts` versionCode 2, versionName "0.2.0". Android Studio `Generate Signed App Bundle` → `octalink-release.jks` 서명 → `app/release/app-release.aab` (16.1MB, 다운로드 9초). Play 콘솔 비공개 테스트 트랙의 라이브러리에 업로드 완료(`2.aab (0.2.0)`)
- [x] (05-14) `백엔드 / 데이터` **collectionGroup 보안 룰 + 인덱스 함정 해결 (AttendanceScreen "오늘 동료" 빈 목록 버그)** — `db.collectionGroup("attendance")` 쿼리가 `PERMISSION_DENIED` 로 실패해 todayPeers 가 빈 상태. 원인: Firestore 는 collectionGroup 쿼리를 일반 doc 액세스와 다르게 평가, 특정 부모 경로 룰(`match /members/{uid}/attendance/...`)은 적용되지 않음. 해결: `firestore.rules` 에 root 레벨 recursive wildcard 룰 `match /{path=**}/attendance/{attendanceId} { allow read: if isApproved(); }` 추가. 기존 nested 룰은 create/update/delete 보존. 후속 SkillScore/Comment collectionGroup 쿼리에도 동일 패턴 적용 예정
- [x] (05-14) `정리` **개발용 테스트 시드 제거** — CreatorScreen 의 "개발용 — 테스트 시드" 카드 + `RoleGrantViewModel.seedTestData()` + Cloud Function `seedTestData` 소스 삭제. 운영 배포 직전 자료 격리. **운영자 후속**: 이미 배포된 함수는 `firebase functions:delete seedTestData --region asia-northeast3` 로 제거 + 기존 테스트 회원(test-bak-jh / test-choi-ms / test-kim-sh / test-han-dy / test-shin-yr / test-jang-tj) 6개 Firestore doc 콘솔에서 수동 정리
- [x] (05-14) `UX 디테일` **캐릭터 스프라이트 10장 자체 일러스트로 교체 + 벨트 동적 색 로직 완전 제거** — ChatGPT 생성 5×2 그리드 시트(1536×1024)에서 `tools/split_sprites.py` flood-fill 알고리즘으로 셀 중앙에서 메인 캐릭터 연결성분만 추출 → 균일 388×388 정사각, 가로 중앙 + 발 하단 정렬, 신체 잘림 없음. `m_*/f_* {feather,light,welter,middle,heavy}.png` 10장. AvatarTile 에서 belt 링 border + ColorFilter.tint 마스크 오버레이 + `ringColor/ringWidth/showBeltRing/belt` 파라미터 전부 삭제. 벨트 색은 카드 좌측 스트라이프로만 표현. `Avatar.beltMaskResourceName` 필드 + `belt_*.png` 마스크 10장 + AvatarTile 의 `Belt` import 모두 제거
- [x] (05-14) `UX 디테일` **AttendanceScreen 운영진 출결 검토 활성화** — 운영진 모드 카드의 "준비 중" placeholder 를 실제 라우팅으로 교체. 신규 `AttendanceReviewScreen` + `AttendanceReviewViewModel` 도입 — APPROVED 회원 list (이름순 2열 그리드, MASTER 만 제외 — 도장 운영자라 출결 추적 대상 아님. COACH/CREATOR 는 본인 운동/스파링 참여하므로 포함) → 회원 선택 → attendance 시계열 (DESC, classDate + dayOfWeek + 체크인 시각 + 삭제 칩 + confirm 다이얼로그). **05-16 추가 정리**: verified Switch 제거 (현재 화면에서 활용처 없어 UI 노이즈만 됨, Repository/Schema API 는 보존). 삭제는 우발 클릭 방지 위해 AlertDialog confirm 단계 추가.
- [x] (05-16) `UX 디테일` **AttendanceReviewScreen 주간 출석 뱃지 + 월간 캘린더 추가 → 디자인 재정비** — 운영진이 회원 출결을 한눈에 파악하도록 두 가지 시각 강화. 백엔드: `AttendanceRepository.observeSince(classDate)` 인터페이스 + InMemory(filter)/Firestore(collectionGroup + whereGreaterThanOrEqualTo "classDate") 구현 — N개 회원 listener 폭증 회피. VM 은 단일 `observeSince(min(weekStart, monthStart))` 쿼리로 fetch 후 client-side 두 derivation: `weeklyCountByMember: Map<String, Int>` + `monthlyCountByDate: Map<LocalDate, Int>`. UI: (1) **`WeeklyRateBadge`**: 회원 카드 우측 — 상단 `N%` (6일 운영 기준 `GYM_DAYS_PER_WEEK` 분모), 하단 `N / 6`. 0회면 surfaceVariant 비활성, 1회+면 primary 18% 알파 강조. (2) **`MonthCalendar` heatmap**: REVIEW 최초 화면(picker view) 최상단에 도장 전체 일자별 출석 활성도. 일요일 시작 7열 그리드 (일=빨강·토=파랑 헤더). 각 셀 출석 건수 비례 primary alpha (0.2~0.85 보간), 0건은 투명, 오늘 셀 1.5dp border 강조, "도장 활성도" 타이틀. 회원 선택 후 detail view 에서는 캘린더 제거 (전 회원 대상이라 picker 에만 의미). (3) 회원 카드 텍스트: 좌측 belt stripe 가 이미 벨트 색을 나타내므로 `${weightClass.displayName}` 만 표시 (이전 `· 퍼플 벨트` 중복 제거). 선택 detail 요약 카드도 동일 정리. 휴무/공휴일 표기 미반영 (MVP). `AttendanceRepository.setVerified(memberId, classDate, verified)` 인터페이스 + InMemory + Firestore 구현. `firestore.rules` 의 attendance update 를 본인(체크인 갱신) + 운영진(verified 토글, `memberId` 불변 강제) 으로 분기. PosseApp 라우팅 `Route.AttendanceReview` 신규 + `AttendanceScreen.onOpenReview` 콜백 신설
- [x] (05-14) `UX 디테일` **CommunityScreen 글쓰기 + 이미지 1장 업로드 + 좋아요 활성화 (Phase A)** — 백엔드: `PostDoc` (id/authorId/authorName/authorBelt/title/body/tag/imageUrl?/likedBy/createdAt) + `PostTag enum` (NOTICE/RECORD/TIP/QUESTION) + `Collections.POSTS`. `PostRepository` 인터페이스 + `InMemoryPostRepository` (시드 3건) + `FirestorePostRepository` (orderBy createdAt DESC + arrayUnion/Remove 기반 토글 like) + `PostDocMapping`. `RepositoryProvider.posts` 추가. 미디어: `data/media/ImageUploader` — content URI → BitmapFactory inSampleSize 2단계 디코드 → 1920px 리사이즈 → JPEG 85% 압축 → Firebase Storage `posts/images/{uuid}.jpg` 업로드 → download URL 반환. 룰: `storage.rules` 신규 (5MB cap + image/* MIME 강제) + `firestore.rules` posts 블록 갱신 (NOTICE 태그는 isStaff 만, likedBy-only update 는 모든 APPROVED 허용, 기타 update 는 본인/운영진 + authorId/createdAt 불변). UI: `CommunityScreen` 전면 재작성 — 시간 상대 표시(N분/시간/일 전), 본인/운영진 삭제 칩, 좋아요 토글 ♥/♡ + 카운트, NOTICE 자동 상단 고정 (client-side `compareByDescending(tag==NOTICE)`). `WritePostDialog` — 카테고리 칩 / 제목(80자) / 본문(2000자) / 이미지 picker (`ActivityResultContracts.PickVisualMedia`) / 업로드 진행 표시. Coil(`io.coil-kt:coil-compose:2.7.0`) 의존성 추가 — Storage download URL 비동기 렌더. `PostsViewModel` + `WriteState` sealed class. **Phase B (영상)** 보류 — ffmpeg-kit 트랜스코딩 + ExoPlayer + 50MB Storage 비용이 별도 분량
- [x] (05-16) `배포` **Firebase 룰/Functions/Storage 재배포 완료** — Storage 버킷 `asia-northeast3` 활성화 + `firebase deploy --only firestore:rules,storage,functions` 실행. storage 룰 신규 (posts/images 5MB cap + image/* MIME), firestore 룰 attendance update 분기 + posts 룰 신규, functions 에서 `seedTestData` 제거 (live 함수 4개 - kakaoSignIn/completeSignup/leaveMembership/rejoinMembership/kakaoAccountWebhook - 보존). 함정: `firebase deploy` 가 stale 컴파일 결과(`functions/lib/index.js`) 캐싱해서 live 함수도 삭제 후보로 잡는 경우 발생 → Ctrl+C 후 `npm run build` 재실행 + deploy 재시도로 우회. README 의 후속 정리 항목(`functions:delete seedTestData` + test-* 회원 doc 6개 콘솔 수동 정리) 도 같이 처리
- [x] (05-16) `백엔드 / UX 디테일` **CommentRepository + ProfileScreen 실데이터 + AdminScreen 한 줄 코멘트 작성 활성화** — 백엔드: `CommentDoc.byMasterName` 필드 추가(비정규화, 작성 시점 운영진 이름 스냅샷). `CommentRepository` 인터페이스(observeByMember/create/delete) + `InMemoryCommentRepository`(MockCommentsSeed 3건) + `FirestoreCommentRepository`(`members/{toMemberId}/comments/{commentId}` 경로, orderBy classDate DESC) + `CommentDocMapping`. `RepositoryProvider.comments` 추가. UI: `MyCommentsViewModel`(`flatMapLatest` 로 session memberId 변경 시 자동 재구독) — ProfileScreen 의 hardcoded `coachComments` 제거 → 실시간 Firestore 데이터 + 빈 상태 안내 ("아직 받은 코멘트가 없습니다"). 운영진 진입점: `CoachCommentScreen` + `CoachCommentViewModel` — APPROVED MEMBER 2열 그리드 → 회원 선택 → 코멘트 시계열 (classDate DESC) + "+ 코멘트 작성" 다이얼로그 (text 300자 + 카운터, classDate 기본 today). 본인 작성건만 삭제 칩 노출. PosseApp `Route.CoachComment` 신규 + `AdminScreen.onOpenCoachComment` 콜백. AdminScreen 운영진 공통 카드의 "회원 한 줄 코멘트 작성" 항목을 라이브로 와이어링 + 출결검토/공지작성 항목은 다른 탭 진입점 안내로 정리 (5개 → 3개 항목)
- [x] (05-16) `UX 디테일` **SignupScreen 성별 직접 선택 fallback (카카오 비즈 검수 대기 우회)** — 카카오 비즈 검수가 리셋되어 `gender` scope 가 콘솔 미등록(`권한 없음`) 상태가 되면서 캐릭터 자동 부여(성별+체급)의 입력값 gender 가 항상 null → 모든 회원이 남자 캐릭터로 가입되던 문제. 가입 폼에 **성별 chip 카드 신규** (남/여 2-chip, 필수 선택, primary color 강조). `effectiveGender = kakaoGender ?: pickedGender` 우선순위 — 카카오 검수 통과 시 자동 prefill + chip 잠금(locked=true, 시각적 비활성), 미통과 시 사용자 직접 선택. avatarFor() / completeSignup() 모두 effectiveGender 사용. `canSubmit` gate 에 `effectiveGender != null` 추가 — 미선택 시 가입 신청 버튼 비활성. `SessionViewModel.completeSignup` 에 `pickedGender: String?` 파라미터 추가. **검수 통과 후 자동 전환**: 카카오 응답에 gender 가 들어오기 시작하면 코드 변경 없이 자동으로 카카오 값 우선 사용 + chip 잠김
- [x] (05-16) `UX 디테일` **AttendanceReviewScreen 히트맵 회원 풀 일관성** — REVIEW 캘린더 히트맵이 `recentAttendance` 전체를 카운트하던 문제: 5/16 기준 6명 회원이 모두 0/6 인데 5/12 만 진한 빨강으로 강조되는 모순 발생. 원인은 관장(MASTER) 본인 체크인 / APPROVED 가 아닌 회원의 attendance 가 히트맵에는 잡히지만 회원 뱃지에는 0 으로 표시되는 풀 불일치. `monthlyCountByDate` 를 `combine(recentAttendance, approvedMembers)` 로 묶어 표시 대상 회원 풀(APPROVED 비-MASTER) 의 attendance 만 집계 — 히트맵 색상과 회원 뱃지 0/6 이 항상 같은 도메인을 본다
- [x] (05-16) `백엔드 / 데이터` **SkillScoreRepository + TournamentRepository (Phase 2 후속 Repository 마무리)** — AttendanceRepository / PostRepository / CommentRepository 와 동일 패턴 적용. (1) `SkillScoreRepository` 인터페이스 (observeByMember / observeLatestApproved / observePendingAcrossAllMembers / propose / setStatus / delete) + `InMemorySkillScoreRepository` (시드 1건: 이지연 APPROVED) + `FirestoreSkillScoreRepository` + `SkillScoreDocMapping`. 경로 `members/{memberId}/skillScores/{scoreId}` — Firestore 자동 doc ID. 관장 리뷰 큐는 `db.collectionGroup("skillScores").whereEqualTo("status", "PROPOSED").orderBy("evaluatedAt", ASC)` — 전 회원 PROPOSED 한 번에 스캔. (2) `TournamentRepository` 인터페이스 (observeAll / observeById / observeMatches / create / setMatchWinner / setMatchSlots / finish / delete) + `InMemoryTournamentRepository` (시드 없음 — 추첨 시점 생성이 정상 흐름) + `FirestoreTournamentRepository` + `TournamentDocMapping`. 경로 `tournaments/{tournamentId}/matches/{matchId}` — `create` / `delete` 는 writeBatch 로 원자적 처리(부분 실패 방지). (3) `firestore.rules` 에 `match /{path=**}/skillScores/{scoreId} { allow read: if isStaff(); }` recursive wildcard 추가 (관장 리뷰 큐 collectionGroup 쿼리 통과). (4) `firestore.indexes.json` 에 composite index 3개 추가: skillScores(status ASC + evaluatedAt ASC, COLLECTION_GROUP) / skillScores(status ASC + evaluatedAt DESC, COLLECTION) / matches(round ASC + slotIndex ASC, COLLECTION). (5) `RepositoryProvider.skillScores` / `tournaments` 필드 신규. UI: `SkillScoreProposeViewModel` + `SkillScoreProposeScreen` — 운영진(코치+관장) 진입, 회원 풀(APPROVED 비-MASTER) 2열 그리드 → 회원 선택 → 점수 시계열(status 칩 색 PROPOSED/APPROVED/REJECTED 구분) + "+ 새 점수 제안" 6축 슬라이더 다이얼로그 → `propose()` 로 status=PROPOSED doc 생성. AdminScreen 운영진 공통 카드의 "토너먼트 추첨/대진 관리" 와 "스킬 점수 입력 (제안 → 관장 검토)" 두 항목 라이브 와이어링 + PosseApp `Route.SkillScorePropose` 신규
- [x] (05-17) `UX 디테일` **AttendanceReviewScreen 월간 캘린더 좌우 페이징 + Firestore attendance 백필 스크립트** — 도장 활성도 캘린더가 이번 달만 표시되던 것을 좌우 ◀▶ 페이징 버튼으로 *단일 월 표시 + 월 이동* 으로 재구성. 범위는 올해 1월(하한) ~ 이번 달(상한), 경계에서 버튼 비활성(alpha 0.25). ViewModel: `displayedMonth: StateFlow<LocalDate>` + `goPrevMonth/goNextMonth`, `queryStart` 를 `yearStart` 까지 당기고 `monthlyCountByDate` 는 연중 전체 일자 집계, 캘린더 heatmap 의 `maxCount` 는 *표시 월* 만 계산하도록 분리 — 저활성 월이 너무 옅게 보이는 정규화 문제 회피. 추가로 회원 출석 누락분 15건(4/13 ~ 5/13, KST 19:00 기준 1건은 5/4 11:30)을 `scripts/add-attendance.js` 1회성 admin SDK 스크립트로 백필 — `classDate` ISO 문자열이 doc ID 라 idempotent
- [x] (05-17) `배포` **firestore.rules / firestore.indexes.json 재배포** — 05-16 SkillScore/Tournament 룰+인덱스 + 05-17 관장 직접 APPROVED create 분기까지 누적된 변경을 `firebase deploy --only firestore:rules,firestore:indexes` 로 반영. 추가로 룰에 **MASTER 출석 체크인 차단** 분기 신설 (`match /attendance/{attendanceId}` create 에 `memberDoc().role != 'MASTER'` — 관장은 영업일 상시 운영이라 출석 추적 대상 아님; UI 도 동일 가드)
- [x] (05-17) `UX / 백엔드` **스킬 점수 워크플로우 풀스택 정리** — 관장 검토/직접 평가 + canonical 결정 + 차트 실시간 구독 + Firestore 정밀도/표시까지 일괄 정리. (1) 관장 스킬 점수 **리뷰 큐 화면** + `SkillScoreReviewViewModel.transition()` 이 setStatus 후 `refreshMemberSkillsSnapshot()` 으로 `MemberDoc.skills` 백필. (2) `SkillScoreRepository.directApprove(memberId, byUserId, skills)` — 관장이 PROPOSED 단계 생략하고 status=APPROVED 로 즉시 작성. `firestore.rules` 의 `match /skillScores/{scoreId}` create 룰에 master 직접 APPROVED 분기 추가 (`isMaster() && status=='APPROVED' && reviewedByMasterId == auth.uid`). (3) `rejectAllPending(memberId, byMasterId)` — 관장 직접 평가 시 같은 회원의 코치 PROPOSED 일괄 REJECTED (WriteBatch). (4) `getCanonicalApproved` + `observeCanonicalApproved` — **APPROVED 중 evaluatedAt 최신** 단일 기준 (관장/코치 구분 없이 시간 우선; 중간에 시도했던 master-direct 우선순위는 결과적으로 제거). (5) `MySkillsViewModel` + ProfileScreen 차트가 `MemberDoc.skills` 스냅샷 대신 `skillScores` 컬렉션을 직접 구독 — Firebase 콘솔에서 doc 삭제/수정해도 listener emit → 차트 즉시 반영. (6) `MemberApprovalViewModel.assignSkills` 가 `members.updateProfile` 직접 호출에서 `scoresRepo.directApprove + rejectAllPending + getCanonicalApproved` 경로로 교체 — AdminScreen "회원 스킬 점수 입력" 진입점도 반드시 `skillScores` doc 을 남기고 `member.skills` 와 일치. 이전 구현은 `members/{uid}.skills` 만 덮어써서 admin 카드(50점)와 프로필 차트(40점) 가 불일치하던 버그. (7) `SkillScoreProposeScreen` 의 `isMaster` 분기 제거 — 운영진 공통 카드는 역할 무관 항상 `propose()` → PROPOSED. 관장 직접 평가는 AdminScreen 관장 전용 카드 단독 진입. (8) **Firestore 6축 저장 정밀도** `floor(v * 100) / 100` (0.01 단위 truncate) — 슬라이더 raw float (예: 0.5086712837219238) 대신 0.50 으로 저장, UI 의 `(value * 100).toInt()` 정수 표시와 1:1. `FirestoreSkillScoreRepository` 의 propose/directApprove + `FirestoreMemberRepository.updateProfile` 의 `member.skills` write 양쪽에 적용. (9) SKILL 화면 회원별 점수 행 표시 정비 — 날짜 `yyyy-MM-dd` → `M/d (요일)` (예: `5/17 (일)`), 평균 점수 옆에 **제안자 이름** 표기 (`5/17 (일) · 평균 50점 · 이지연`). `SkillScoreProposeViewModel.nameById: StateFlow<Map<String, String>>` 신규 — 전체 회원(MASTER 포함) id → name 매핑, ScoreRow 가 byUserId 로 lookup
- [x] (05-17) `백엔드 / UX` **TournamentViewModel → TournamentRepository 영속화** — in-memory `MutableStateFlow<TournamentUiState>` 자체 보유 패턴에서 Repository 구독형으로 전환. (1) `_currentTournamentId: MutableStateFlow<String?>` — 앱 시작 시 `tournamentsRepo.observeAll()` 의 최신 doc 자동 선택, 외부에서 doc 삭제(콘솔 등) 시 자동 클리어. (2) 단일 `viewModelScope.launch` collector 가 `_currentTournamentId` 변경 시 `combine(observeById, observeMatches)` 로 `_tournament + _matches` 두 raw StateFlow 를 populate — UI 의 `state` 와 매치 갱신 메서드의 `(round, slotIndex) → matchId` 매핑 양쪽이 같은 source 공유. **VM lifetime 동안 항상 활성** (초기 구현 `matchesCache: WhileSubscribed(5000)` 가 아무도 collect 안 해서 결승 승자 클릭이 silently 무시되던 버그 수정). (3) `draw()` 시 인원수별 매치 슬롯 + 부전승 사전 해결한 `List<MatchDoc>` 생성 → `tournamentsRepo.create(title, weightClass, beltGroup, matches)` writeBatch 로 원자적 영속화. (4) `setRound1Winner/setRound2Winner/setFinalWinner` 가 name → id 변환 후 `setMatchWinner(byMasterId=currentUserId)` + 다음 라운드 슬롯 `setMatchSlots` 갱신; 다운스트림 winner 자동 리셋. 결승 승자 = `tournaments.finish(championId)` 호출 (`finishedAt` + `champion` 기록). (5) `reset()` = `tournamentsRepo.delete()` (writeBatch — 매치 전체 + tournament doc 동시 삭제). 향후 토너먼트 히스토리 도입 시 완료 분기 추가 가능. (6) `currentUserId` 필드 + `PosseApp` 의 `LaunchedEffect(session.member?.id)` 동기화 — 매치 결과의 `resolvedByMasterId` audit 필드에 들어감. (7) `TournamentUiState.finished` 필드 추가 (`tournament.finishedAt != null`). UI(`BracketScreen`, `BracketDrawScreen`) 시그니처 호환 — 호출부 변경 없음
- [x] (05-17) `UX` **토너먼트 히스토리 화면** — `TournamentHistoryScreen` + `TournamentHistoryViewModel` 신규. (1) `observeAll() + observeMatches(t.id)` 매 토너먼트 join + `members.observeAll()` 의 id → MemberDoc 맵으로 `TournamentHistoryItem(tournament, championName, championBelt, participantCount, isMyParticipation)` derive. 본인 참가 여부는 매치들의 redMemberId/blueMemberId distinct set 에 session.member.id 포함 여부. (2) 카드: 제목 + 진행/완료 상태칩 + 본인 참가 배지(파란 "내 참가") + 추첨/완료일 (`MM/dd (요일)`) + 참가자수 + 우승자 (이름 아래 벨트색 underline). (3) 카드 탭 → `Route.BracketView/{tournamentId}` 진입 → `tournamentVm.selectTournament(id)` → BracketScreen read-only 모드 (`canManage=false`, `showHistoryButton=false` 로 재진입 루프 차단). (4) BracketScreen 의 액션 row + EmptyState 양쪽에 "히스토리" 진입점 추가. (5) `TournamentViewModel` 정비: `_overrideTournamentId` 추가(히스토리 진입 중일 때 자동 선택 우회), `_allTournaments` 캐시 + `pickActive(tournaments)` 헬퍼(`firstOrNull { finishedAt == null }`)로 init/selectTournament/reset 모두 동일 규칙. init 의 auto-pick 이 단순 "최신 doc" 에서 "최신 *미완료*" 로 변경 — 완료 토너먼트가 active 로 다시 뜨지 않음. (6) **reset 가드**: 현재 토너먼트가 완료된 상태면 Firestore 삭제 금지하고 `pickActive` 로 그 다음 미완료를 active 로 전환(없으면 EmptyState). 완료된 토너먼트는 히스토리에서만 접근 가능, 명시적 삭제 액션은 추후 per-card 분리. 회원 프로필의 전적 카드(우승/준우승/8강 누적)는 별도 후속 항목
- [x] (05-17) `UX` **회원 프로필 토너먼트 전적 카드** — ProfileScreen 의 mock 승률 카드(`64% · 스파링 25전 16승 9패`)를 실데이터 기반 토너먼트 전적 카드로 교체. `MyTournamentRecordViewModel` 신규 — `tournamentsRepo.observeAll() + observeMatches(t.id)` 매 *완료* 토너먼트(`finishedAt != null`) 만 join 후 `TournamentRecord(participated, wins, runnerUps)` derive. 본인 참가 = red/blue 슬롯에 자기 memberId 등장, 우승 = `champion == 내 id`, 준우승 = 우승 아님 + FINAL 매치 슬롯에 본인. 카드 좌측: "우승 N회" 큰 숫자, 우측: `토너먼트 N전 · 준우승 M회` + `히스토리 보기 →` 안내. 참가 0 일 땐 "참가한 토너먼트 없음" 폴백. 카드 탭 → `Route.TournamentHistory` 진입 (`onOpenTournamentHistory` 콜백 — PosseApp 의 ProfileScreen 호출부에 와이어링)
- [x] (05-17) `UX` **새 추첨 시 진행 중 토너먼트 자동 정리** — `TournamentViewModel.draw()` 가 `tournamentsRepo.create()` 호출 *직전* 에 현재 active 가 미완료(`finishedAt == null`) 상태면 `tournamentsRepo.delete(curId)` 로 폐기. 완료 토너먼트는 보존(히스토리에 남김). 도메인 가정: 한 도장에서 동시에 두 토너먼트가 active 상태인 상황은 비현실적 — 새 추첨은 곧 기존 미완료 폐기를 의미. BracketDrawScreen 의 "추첨하기" 버튼이 이미 deliberate 액션이라 별도 confirm 다이얼로그는 미도입
- [x] (05-17) `UX` **홈 화면 오늘의 커리큘럼 카드 실데이터 매핑 + 휴무일 자동 숨김** — top-level 정적 `todayClass = FeedItem("오늘의 커리큘럼", "관장 김파시", "잽-스트레이트 거리감 + 인사이드 로우킥")` 제거. HomeScreen 안에서 `curriculumForToday(today)` 로 평일 그룹 수업 데이터 가져와 `FeedItem(title="오늘의 커리큘럼", meta="${coach} · ${tag}", body=theme)` 로 변환 — Curriculum 탭과 단일 source 공유. **휴무일 분기**: 표시 조건 `todayCurriculum != null && !isClosed(today)` — 토·일 (curriculumForToday 가 null) 또는 평일 공휴일(isClosed 가 true) 일 때 카드 자체를 LazyColumn 에서 제외. 위 "오늘 체육관 활성도" 카드의 "오늘은 휴무" 분기와 같은 휴무 정책 공유
- [x] (05-17) `UX` **히스토리 카드 per-card 삭제 액션** — TournamentHistoryScreen 의 카드에 관장(`session.role.isMaster`) 전용 "삭제" 칩 추가. 카드 내부 칩은 자체 clickable 로 부모 카드 onClick(BracketView 진입) 으로 전파 안 됨. 삭제 칩 → AlertDialog confirm (제목 + 우승자 + 참가자수 + 영구 삭제 경고) → `TournamentHistoryViewModel.delete(id)` → `tournamentsRepo.delete()` (matches + tournament doc writeBatch). 완료/미완료 무관 삭제 가능 — 미완료는 BracketAdmin 의 초기화 칩과 중복 경로, 완료는 명시적 히스토리 삭제 전용 경로. Firestore 룰이 isStaff 강제 — 회원에게는 칩 자체 비노출
- [x] (05-17) `배포` **storage.rules 재배포** — 영상 업로드 (`posts/videos/{filename}`, 100MB + video/* MIME) 룰 추가분 `firebase deploy --only storage` 로 콘솔 반영. 미배포 시 클라이언트의 `VideoUploader.uploadPostVideo` putFile 이 PERMISSION_DENIED 로 silently 실패하던 문제 해소
- [x] (05-17) `UX / 백엔드` **CommunityScreen Phase B — 영상 첨부** — 이미지와 동일 패턴으로 영상 업로드 지원. (1) `PostDoc.videoUrl: String?` 필드 추가 (`imageUrl` 과 상호 배타). `PostDocMapping` / `PostRepository.create` / `FirestorePostRepository` / `InMemoryPostRepository` 모두 videoUrl 흐름. (2) `data/media/VideoUploader.kt` 신규 — `MediaMetadataRetriever` 로 재생 시간 추출 + content URI 의 파일 사이즈 측정 → **1분 30초 / 100MB cap** 검증 후 `posts/videos/{uuid}.mp4` 로 `putFile()` (메모리 안 올림). 100MB 는 1.1MB/s 예산으로 1080p30 phone-native 영상이 무압축으로 통과하도록 산정. 검증 실패 시 한국어 안내 메시지로 throw (`formatDuration` 헬퍼가 "1분 30초" 같이 가독성 있게 포맷) → caller 가 그대로 UI 노출. (3) `storage.rules` 에 `match /posts/videos/{filename}` 신설 (100MB + video/* MIME). (4) `PostsViewModel.submitPost` 에 `videoUri: Uri?` 파라미터 + 이미지/영상 동시 첨부 차단 + **일일 영상 업로드 한도 1건/하루** (이미지와 독립 카운트, KST 기준). (5) `CommunityScreen.WritePostDialog` 에 `VideoPickerRow` 추가 — 이미지/영상 mutual exclusion (한쪽 set 시 다른 쪽 picker disabled). picker URI 즉시 cache 복사로 권한 만료 회피.
- [x] (05-17) `UX / 백엔드` **영상 720p 재인코딩 (Media3 Transformer) + ExoPlayer 인라인 재생** — Phase B v1 의 VideoView/no-transcode 정책 업그레이드. (1) `androidx.media3:media3-{exoplayer,ui,transformer,effect,common}:1.4.1` 의존성 5종 추가. ffmpeg-kit deprecated (2025) 이후 Media3 가 사실상 표준. (2) `data/media/VideoTranscoder.kt` 신규 — `Transformer.Builder` + `Effects(emptyList(), listOf(Presentation.createForHeight(720)))` + `setVideoMimeType(H264)` + `setAudioMimeType(AAC)`. `suspendCancellableCoroutine` 로 Listener.onCompleted/onError 를 suspend 함수로 wrapping. Transformer 는 main thread 강제라 `withContext(Dispatchers.Main)` 으로 switch. 출력은 `cacheDir/post_uploads/transcoded_{uuid}.mp4` — OS 자동 회수. (3) `VideoUploader.uploadPostVideo` 흐름 재구성: ① 길이 검증 먼저(트랜스코딩 비용 들이기 전 fail-fast) → ② 720p 재인코딩 시도 → ③ 실패 시 원본 fallback → ④ 최종 사이즈 검증 → ⑤ Storage 업로드. 통상 50-70% 사이즈 감축 (1080p 5Mbps → 720p ~3Mbps). (4) `PostVideoPlayer` 가 `android.widget.VideoView` 에서 `androidx.media3.exoplayer.ExoPlayer` + `androidx.media3.ui.PlayerView` 로 교체. `remember(videoUrl)` 로 player 인스턴스 생성, `DisposableEffect.onDispose` 로 LazyColumn 스크롤 아웃 시 release — 메모리 leak 방지. PlayerView 의 controller 가 seek/pause/fullscreen UI 제공. 여전히 자동 재생 ❌. (5) Media3 의 `@UnstableApi` (Java `@RequiresOptIn`) 는 `@androidx.annotation.OptIn(UnstableApi::class)` 로 caller 까지 전파 안 되게 캡슐화 (`VideoUploader.uploadPostVideo`, `PostVideoPlayer` 함수 레벨)
- [x] (05-17) `백엔드 / UX` **푸시 알림 (FCM) 풀스택 — 클라이언트 + Cloud Function 트리거 + CLASS_REMINDER 스케줄링** — 기존 `OctaLinkMessagingService` skeleton 을 본 구현으로 확장. (1) `Schema.kt` 에 `NotificationType` enum 6종 (`COMMENT` / `TOURNAMENT_DRAWN` / `NEW_NOTICE` / `SIGNUP_RESULT` / `CLASS_REMINDER` / `SKILL_UPDATED`) — 각각 channelId/displayName/description/defaultEnabled. CLASS_REMINDER 만 `defaultEnabled = false` (잦은 알림이라 옵트인). `MemberDoc` 에 `fcmToken: String?` + `notificationPrefs: Map<String, Boolean>` 필드 추가. (2) `MemberRepository.updateFcmToken / updateNotificationPrefs` 신규 — InMemory + Firestore 양쪽 구현 + `MemberDocMapping` 양방향. `firestore.rules` 의 `memberSelfEditableOnly` 화이트리스트에 `fcmToken`/`notificationPrefs` 추가. (3) `messaging/NotificationChannels.kt` — 모든 `NotificationType` 의 OS 채널을 Android 8.0+ 에 1회 등록. `OctaLinkApplication.onCreate` 에서 `ensureRegistered()` + `ClassReminderScheduler.scheduleDailyRollover()` 호출. (4) `OctaLinkMessagingService.onNewToken` 가 Firebase Auth uid 있으면 `members/{uid}.fcmToken` 으로 영속화. `onMessageReceived` 가 FCM payload 의 `data.type` 으로 channel 라우팅 + `members/{uid}.notificationPrefs` 확인 → OFF 시 drop, ON 시 `NotificationCompat` 으로 로컬 표시. POST_NOTIFICATIONS 미허가 시 silently drop. (5) `messaging/ClassReminderScheduler.kt` 신규 — WorkManager 기반 클라이언트 사이드 스케줄링. `scheduleAll()` 이 `weeklyPlan` 의 오늘 슬롯 중 `start - 30분` 이 미래인 것만 `ClassReminderWorker` OneTimeWork 로 enqueue (휴무일 / pref OFF / uid 없음 시 skip). `DailyReschedulerWorker` 가 매일 04:00 KST fire (PeriodicWork, KEEP 정책 멱등) → 그 날 분 다시 schedule. `androidx.work:work-runtime-ktx:2.9.1` 의존성 추가. (6) `NotificationPrefsViewModel` — 본인 prefs 의 default-fill StateFlow + `setEnabled(type, enabled)` 가 Firestore 갱신 + 첫 ON 전환 시 FCM 토큰 강제 fetch 영속화 + CLASS_REMINDER 토글 시 즉시 `scheduleAll`/`cancelAll` 호출. (7) `ProfileScreen` "알림 설정" 카드 — 클릭 시 모달 다이얼로그로 5종 토글(`SIGNUP_RESULT` 는 Profile 진입자에겐 무의미해 UI 제외, CF 트리거는 보존) + Android 13+ POST_NOTIFICATIONS 권한 launcher (ON 토글 시 권한 없으면 요청). (8) `functions/src/index.ts` 에 `sendNotificationTo(memberIds, type, title, body)` helper (fcmToken + prefs filter + invalid token 자동 cleanup) + 5개 Firestore 트리거: `notifyOnCommentCreated` (한 줄 코멘트) / `notifyOnTournamentCreated` (참가자 전원) / `notifyOnNoticeCreated` (NOTICE 태그 글 → 작성자 외 모든 APPROVED) / `notifyOnSignupResult` (PENDING → APPROVED/REJECTED) / `notifyOnSkillsUpdated` (skills 필드 diff). CLASS_REMINDER 만 서버 트리거 없이 클라이언트 WorkManager fire
- [x] (05-18) `배포` **FCM Cloud Functions 배포 완료 + Eventarc 권한 함정 + background channelId fix + seedTestData 정리** — (1) `firebase deploy --only functions` 첫 시도 시 5개 trigger 함수 (`notifyOn*`) 가 **Eventarc Service Agent 권한 전파 지연** 으로 모두 실패 (기존 `onCall`/`onRequest` 5개는 update 성공). 2nd gen Cloud Functions + Firestore trigger 의 first-use quirk — 5~10분 대기 후 재배포로 자동 해결, 수동 IAM 부여 불필요. (2) **stale build 함정** 재발 — `firebase deploy` 가 `lib/index.js` (컴파일 산출물) 만 보고 새 함수가 source 에 "없다" 고 판단해 deletion prompt 띄움. **반드시 `npm run build` 선행** 후 `firebase deploy`. (3) **background/killed 상태 알림 누락 fix** — 첫 deploy 후 `sent=1/1` 성공인데 폰에 알림 안 뜨는 케이스 발견. 원인: payload 에 `android.notification.channelId` 누락 → OS 가 default(없는) 채널로 dispatch 후 silently drop. `CHANNEL_ID: Record<NotificationTypeKey, string>` 테이블 (`octalink_comment` / `octalink_tournament` / `octalink_notice` / `octalink_signup` / `octalink_skill`, 클라이언트 `NotificationType.channelId` 와 정확 일치) 추가 + `android.notification.channelId` 명시. (4) **seedTestData 콘솔 잔재 정리** — 소스에선 05-14 에 제거됐지만 콘솔의 배포본 미정리로 매 deploy 시 deletion prompt 반복. 재배포 시 `y` 로 정리 완료. (5) `notifyOnSkillsUpdated` body 텍스트 "6축 차트" → "스킬 차트" 자연스럽게 표현 변경. **end-to-end 검증 완료** — 코멘트/스킬/공지/토너먼트 모두 background 에서 정상 dispatch
- [x] (05-18) `UX` **앱 UI 테마 선택 (DARK / LIGHT)** — 회원이 ProfileScreen 에서 직접 토글하는 다크/라이트 테마. (1) `ui/theme/AppTheme.kt` 신규 — `enum class AppTheme { DARK, LIGHT }` + `AppThemeViewModel(application)` (AndroidViewModel, SharedPreferences `octalink_ui_theme` 키 로 영속). 기기 단위 설정 — Firestore 동기화 ❌ (사용자가 디바이스 별 다른 선호 가능 + 알림 채널 같은 device-local 설정과 일관성). (2) 테마 팔레트 분리 — DARK 는 기존 Ink/Canvas/Bone/Blood (도장 무드), LIGHT 는 Paper/Cloud/Slate/Blood (Notion 풍 깔끔). (3) `MainActivity` 에서 `AppThemeViewModel` 1회 hoist → `OctaLinkTheme(theme = currentTheme)` 로 전체 앱 트리에 전달 → 토글 즉시 모든 화면 재컴포지션. (4) ProfileScreen 에 "테마 선택" 카드 — 두 가지 옵션을 각 테마의 background/surface/primary 3색 미니 swatch + 라벨 한 행으로 비교 노출, 탭 시 즉시 전체 앱 적용 (사용자가 결과 미리 보고 선택 가능)
- [x] (05-18) `UX / 백엔드` **CLASS_REMINDER 슬롯별 세분화** — 단일 ON/OFF 토글로는 회원의 출석 의향(요일/시간대)을 반영 못함 → 슬롯 단위로 옵트인 가능하도록 고도화. (1) `MemberDoc.classReminderSlots: List<String>` 신규 필드 — 각 항목은 `"MONDAY_19:30"` 형식의 `요일_HH:mm` 키. 빈 list = 알림 없음 (옵트인). (2) `Schedule.kt` 에 `allWeeklyClassSlots(): List<Pair<DayOfWeek, ClassSlot>>` (월~토 모든 슬롯 31개 = 평일 5일×6 + 토 1) + `classSlotKey(day, slot)` 헬퍼. (3) `MemberRepository.updateClassReminderSlots(memberId, slots)` 인터페이스 + InMemory/Firestore 구현체 + `MemberDocMapping` 양방향. `firestore.rules` 의 `memberSelfEditableOnly` 화이트리스트에 `classReminderSlots` 추가. (4) `ClassReminderScheduler` 가 `prefs[CLASS_REMINDER]` boolean 체크 제거 → `member.classReminderSlots` 셋 멤버십 체크. 오늘 요일 슬롯들 중 set 에 포함된 key 만 enqueue + Worker fire 시점에도 재확인 (스케줄 후 사용자가 해제했으면 drop). worker input 에 `slotKey` 추가. (5) `NotificationPrefsViewModel` 에 `classReminderSlots: StateFlow<Set<String>>` + `updateClassReminderSlots(slots)` setter — 비어있지 않으면 FCM 토큰 fetch + scheduleAll, 비어있으면 cancelAll. (6) ProfileScreen 알림 다이얼로그의 CLASS_REMINDER 행을 단순 Switch → `ClassReminderConfigRow` (탭 시 슬롯 선택 모달 진입, "N개 슬롯 활성화" 또는 "비활성화" 서브텍스트). (7) `ClassReminderConfigDialog` 신규 — LazyColumn 으로 31개 슬롯을 요일별 그룹 표시. 각 요일 헤더에 "전체 선택"/"전체 해제" 토글 + 슬롯마다 Checkbox + "시간 · 수업명". 저장 시 선택 셋을 ViewModel 에 push + 비어있지 않으면 Android 13+ POST_NOTIFICATIONS 권한 확보 + 알림 다이얼로그로 복귀
- [x] (05-18) `UX` **라이트 테마 도입 + 신규 설치 기본 LIGHT + 다수 가독성 fix** — 다크 일변도였던 UI 를 두 테마 선택형으로 확장. (1) `ui/theme/AppTheme.kt` 의 `AppThemeViewModel` 을 **`AppThemeStore` object 싱글톤** 으로 리팩터 — NavBackStackEntry scope vs Activity scope 의 ViewModel store 차이로 ProfileScreen 의 토글이 MainActivity 의 collectAsState 에 반영 안 되던 버그 fix. (2) `Color.kt` 라이트 팔레트 3단 명도 분리: `Paper #FFFFFF` (카드) > `Cloud #E9E9ED` (배경) > `Frost #DCDCE1` (칩) — Apple iOS systemGroupedBackground 톤. (3) `DEFAULT_THEME = AppTheme.LIGHT` — 신규 설치 시 라이트로 시작 (일반 사용자 친숙도 ↑). 기존 사용자는 SharedPreferences 유지. (4) **SplashScreen 다크 톤 고정** — 시스템 splash(themes.xml: `Theme.OctaLink.Starting` ink 배경) 와 Compose splash 의 핸드오프 점멸 회피. (5) **라이트 테마 가독성 fix**: HomeHeader 로고는 `logoKeyDarkFilter` (출력 RGB = Slate `#1A1A1F`) 로 분기, BracketScreen `FighterRow` 의 winner 색을 `Color.White` 하드코딩 → `onSurface` 통일 (양 테마 한 번에 해결). (6) ProfileScreen `ThemePickerCard` — 두 옵션 미니 미리보기 swatch + 즉시 적용
- [x] (05-18) `UX` **수업 리마인더 다이얼로그 그리드 재설계 + legacy 키 1회성 마이그레이션** — 31개 슬롯 LazyColumn 으로 길어진 다이얼로그를 평일 × 시간대 그리드로 압축. (1) `ClassReminderConfigDialog` 가 *평일 5일 × 그룹 수업 4시간대* (12/18/19:30/21:00, 복싱·킥복싱·MMA) 만 5×4 그리드로 노출 — 오픈 매트 / 토요일 PT 슬롯은 제외 (해당 시간대 리마인더 가치 낮음). 헤더 요일 / 시간 라벨 탭 = 그 행/열 전체 토글, 셀 탭 = 개별 토글. (2) `Schedule.kt` 에 `validClassReminderKeys: Set<String>` (= 그리드 노출 키 20개) top-level val 추가. (3) `NotificationPrefsViewModel.observeFor` 가 member id set 시 **1회성 영속 마이그레이션** 실행 — `stored ∩ validKeys` 가 `stored` 와 다르면 cleaned 셋으로 `updateClassReminderSlots` write back + `scheduleAll` 재호출. 이전 다이얼로그 (31 슬롯 노출) 에서 저장된 오픈매트/PT 키가 그리드에선 해제 불가 + WorkManager 가 무효 시간 fire 위험을 한 번에 정리. Idempotent. (4) UI 안전망 — `classReminderSlots` flow 가 항상 `intersect validClassReminderKeys` 적용, 마이그레이션 write 완료 전 raw 노출 차단. (5) 안내 문구 "N개 슬롯 활성화" → "N개 수업 알림 ON" — "슬롯" 기술 용어 제거
- [x] (05-18) `UX` **다수 picky fix** — 베타 출시 직전 자질구레한 정리. (1) **SignupScreen verticalScroll** — 폼 카드 누적 + 작은 디스플레이 / 큰 폰트 설정에서 "신청 후 승인 시..." 푸터 잘리던 문제. (2) 성별 라벨 우측 "카카오 자동" 텍스트 제거 — 카카오 검수 미통과 상태에선 의미 없는 노이즈. (3) **알림 항목 표시 순서 명시** — `NotificationType.values()` 자동 순서 → 명시 `listOf(COMMENT, SKILL_UPDATED, TOURNAMENT_DRAWN, NEW_NOTICE, CLASS_REMINDER)`. enum 선언 순서와 분리. (4) **홈 오늘의 커리큘럼** — 정적 `todayClass` FeedItem mock 제거 + `curriculumForToday` 실데이터 매핑 + 휴무일(`isClosed`) 시 카드 자체 숨김. (5) **폭죽 origin 동적화** — `ConfettiOverlay.origin: Offset?` 파라미터 + `rememberUpdatedState` 로 burst 마다 최신 위치 read. BracketScreen 이 ChampionBanner 의 `onGloballyPositioned` 로 outer Box 좌표계 기준 위치 캡처 → 8/4/결승 모드 무관 챔피언 카드 정중앙에서 폭죽. (6) **CommunityScreen 본문 토글 stale fix** — `bodyDidOverflow` 가 sticky true 만 set 하던 버그 → `onTextLayout` 매 호출마다 `bodyOverflows = result.hasVisualOverflow` 양방향 갱신. 본문 수정으로 짧아져도 토글 정확히 사라짐. `BODY_PREVIEW_LINES = 2`
- [x] (05-18) `배포 준비` **versionCode 3→4 / 0.3.0 서명된 AAB 빌드 완료** — `app/build.gradle.kts` versionCode 4 (3 은 내부 앱 공유에서 이미 사용됨 — Play 가 중복 거부). `local.properties` 에 `RELEASE_KEYSTORE_FILE=D:/keystores/octalink-release.jks` + 비밀번호 4종 세팅 → `gradlew :app:bundleRelease` 가 `signReleaseBundle` task 자동 실행. 출력 `app/build/outputs/bundle/release/app-release.aab` (20.8MB, `jarsigner -verify` "jar verified"). versionName 은 0.3.0 유지 — 사용자 표시는 같은 버전이고 versionCode 만 내부 카운터 증가. self-signed cert 만료 2053-09-23
- [x] (05-18) `홍보` **베타 테스터 모집 포스터 (A4 docx) + 생성 스크립트** — `tools/make-beta-poster.py` (python-docx 기반) + `docs/beta-tester-poster.docx`. 구성: OctaLink 로고타입(빨강 44pt) → 부제 → "비공개 테스터 모집 (선착순 12명)" → 왜 테스트가 필요한가 → 주요 기능 7항목 → QR 자리(권장 7~8cm, Word 에서 직접 삽입 또는 `--qr` 옵션으로 자동) → 안내사항 → 푸터. 체육관 벽보용 인쇄 가능. 같이 카카오 오픈채팅방(`팀파시 강남`) 개설 + 채팅방 소개에 핵심 기능 6 bullets 노출
- [x] (05-19) `배포 / 정책` **운영 카카오 앱(1453976) 동의 항목 축소 대응 + 0.6.0 / versionCode 5 빌드 + 개인정보처리방침 v1.4** — Play Console 비공개 트랙 출시 직전 카카오 디벨로퍼스 콘솔에서 "추가 동의 항목 신청은 사업자 정보 등록된 비즈 앱만 가능" 차단. 사업자등록증 없이 베타 진행하려고 가용 동의 항목(닉네임 / 이메일) 만으로 제한. (1) `KakaoAuthRepository.BIZ_SCOPES` 주석 명시 — `requestNewScopesIfMissing` 가 *콘솔 등록 ∩ 미동의* 만 골라 `loginWithNewScopes` 호출하므로 미등록 항목은 silently skip → 사업자 등록 후 콘솔에 추가되면 코드 변경 없이 자동 활성. (2) `SignupScreen` 이름 필드 read-only 해제 — 카카오 닉네임은 실명 아닐 수 있어 prefill 후 자유 수정 허용. 성별은 chip 직접 선택(이미 적용된 fallback). (3) `local.properties.KAKAO_NATIVE_APP_KEY` 를 운영 앱(1453976) 키로 swap + 주석으로 두 앱 비교 + fallback 동작 설명. (4) **AD_ID 권한 머지 제거** — `AndroidManifest.xml` 에 `tools:node="remove"` 로 Firebase Analytics 가 자동 머지하던 `com.google.android.gms.permission.AD_ID` 차단 → Play Console "광고 ID 사용" 질문 "아니요" 응답과 일치. (5) `docs/privacy-policy.html` v1.4 — 1번 표 재구성(필수 카카오 기본 = 닉네임만 / 회원 직접 입력 = 이름·성별·벨트·체급·캐릭터·입관일 / 선택 카카오 = 이메일), 2번 연령대 매칭 항목 삭제, 3·5번 PII 자동 삭제 대상 정정(이름·성별·이메일), 9번 변경 이력 추가. (6) `versionCode 4 → 5 → 6 → 7` / `versionName 0.3.0 → 0.5.0 → 0.6.0 → 0.6.1` (5 는 Play Console 이미 소진, 6 은 AD_ID 권한 머지 제거 + 이름 잠금 해제 반영 빌드, 7 은 후속 fix 통합).
- [x] (05-19) `UX` **프로필 설정 화면 분리 (톱니바퀴 → 신규 라우트)** — ProfileScreen 의 알림 / UI 테마 / 로그아웃 / 회원 탈퇴 카드 + 다이얼로그를 별도 `ProfileSettingsScreen` 으로 추출. INFO 화면의 정책·링크 / 앱 정보 카드도 같이 이동 (체육관 정보 화면은 도장 안내만 유지). (1) `PosseScreen` 에 `trailing` 슬롯 신규 — subtitle 과 동시 또는 단독 가능. (2) `Route.ProfileSettings` 추가 + 네비 와이어링. (3) `ProfileScreen` 상단 subtitle "이지연 · 화이트 벨트 · 1개월 차" 제거 → 톱니바퀴 IconButton trailing. 입관 기간(membership) 은 프로필 카드 안 belt 텍스트 옆 inline 합류("여 웰터급 · 화이트 벨트 · 1개월 차"). (4) `ProfileSettingsScreen` (신규 파일) — 알림·UI 테마·정책/링크(개인정보처리방침·Play 스토어)·앱 정보(버전/패키지)·로그아웃·회원 탈퇴 6 카드 + 알림/리마인더/탈퇴 다이얼로그 + 헬퍼(SocialCell / NotificationToggleRow / ClassReminderConfigRow / ClassReminderConfigDialog / ThemePickerCard / ThemeOptionTile). (5) `InfoScreen` — 두 카드 + 미사용 import(`PrivacyTip`/`Shop`/`BuildConfig`) 제거. 비공식 앱 디스클레이머 + 운영시간/주소/전화/소셜만 유지
- [x] (05-19) `UX` **TagChip 공통화 + 홈 오늘의 커리큘럼 색깔 칩** — CurriculumScreen 의 private `TagChip` / `tagColor` 를 `ui/components/TagChip.kt` 공용 모듈로 빼내 HomeScreen 의 "오늘의 커리큘럼" 카드와 시각 어휘 통일. (1) HomeScreen 의 `FeedItem.meta` 문자열 `"코치 박 · 킥복싱"` 분리 → dedicated `TodayCurriculumCard(coach, tag, theme)` 가 coach 라벨 + `TagChip(tag)` 색깔 pill 로 렌더. (2) `tagColor` 매핑 그대로(스트라이킹 오렌지 / 킥복싱 블루 / 그래플링 브라운 / MMA 블러드 / 스파링 그린). 두 화면 모두 같은 `CurriculumDay.tag` 필드 → 동일 색·동일 라벨 보장
- [x] (05-19) `UX / 운영` **AdminScreen 가입 승인 큐 성별 노출** — 카카오 비즈 미통과 환경에선 성별이 회원 가입 폼 직접 입력값이라 관장이 승인 전 확인하고 싶음. `PendingMemberRow` 의 이름 하단 부제를 `"${weightClass} · ${belt} 벨트"` → `"$gender · $weightClass · $belt 벨트"` (gender: `MALE/FEMALE` → `남/여`, null 시 `성별 미입력`)
- [x] (05-19) `bugfix` **스킬 점수 정밀도 round vs floor — DB 1점 어긋남 실명 버그 fix** — 슬라이더 "70" 위치에서 저장하면 Firestore 에 `0.69` 가 들어가 차트에 69 로 표시되던 문제. 원인: `Float 0.7f.toDouble() = 0.69999998807...` → ×100 = `69.999...` → `floor` 가 69 로 내림. `FirestoreSkillScoreRepository.toFirestoreScore` + `FirestoreMemberRepository.toFirestoreScore` 의 `floor` → `round` 로 양자화 정책 통일. UI 측 `HexagonSkillChart` 축 라벨 + `SkillScoreProposeScreen` 평균/슬라이더 우측 표시 / `skillSummary` 4 개소의 `(value * 100).toInt()` 도 `roundToInt()` 로 정리 — 표시·저장 양쪽 동일 반올림 규칙
- [x] (05-19) `bugfix` **Kakao signIn Activity context refactor** — `AuthRepository.signInWithKakao()` 가 ApplicationContext 로 동작하던 구조 → Activity context 필수로 시그니처 변경. 카카오 SDK 의 `loginWithKakaoTalk / loginWithKakaoAccount` 가 카톡 앱(또는 웹 OAuth WebView) 띄우려고 `startActivity` 호출하는데, ApplicationContext 사용 시 `Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag` 런타임 예외로 실패. `AuthRepository` 인터페이스에 `activity: Activity` 파라미터 추가 → `InMemoryAuthRepository` / `KakaoAuthRepository` / `SessionViewModel` / `LoginScreen` 호출 사슬 전체 전파. `LoginScreen` 이 `LocalContext.current as Activity` 로 호스트 액티비티 캐스팅 후 ViewModel 에 전달
- [x] (05-21) `UX` **활성도·출석률 단계별 색 / 댓글 아이콘 채움-외곽선 / 정책·앱 정보 통합 / 토너먼트 필터 (0.7.0 배포)** — 홈 활성도·주간 출석률 %를 저(<30%)회색/중(30~69%)앰버/고(≥70%)그린 3단계 색 분기. 커뮤니티 댓글 카운트를 이모지 💬 → Material vector `ChatBubble(Outline)` + chevron, 댓글 유무에 따라 채움·외곽선·색 전환(좋아요 하트 시각 어휘 통일). INFO 화면의 정책·링크 / 앱 정보 카드 → 프로필 설정 화면(ProfileSettings) 통합. 토너먼트 히스토리에 "내 참가만" 필터 + WeightClassChip 그라데이션. 댓글 입력창 BasicTextField 로 직접 그려 40dp 고정 (Material OutlinedTextField 기본 56dp 컴팩트 한계 회피). 알림 토글에 NEW_POST_COMMENT / MENTION 추가 (총 7종). 날짜 포맷 앱 전체 `M/d(EEE)` 통일 + 시간 `오후 8:59`
- [x] (05-22) `UX / bugfix` **사진/영상 전체화면 뷰어 + 갤러리 저장·카카오톡·인스타 공유** — 커뮤니티 글의 이미지/영상 영역 탭 시 검정 배경 Dialog 로 전체화면 진입. 영상은 우상단 ⛶ 오버레이 버튼(PlayerView 컨트롤러 영역 회피)으로 진입, autoPlay + compactControls(이전/다음만 숨김, 5초전/15초후 유지). 시스템 바 영역 회피를 위해 하드코딩 패딩(top 48dp/bottom 64dp) — Dialog 내부 `WindowInsets` 0 반환 케이스 보강. 이미지 전체화면 좌측 하단에 Material vector 원형 버튼 3종: `FileDownload`(MediaStore Pictures/OctaLink 저장) / `Chat`(카카오톡 ACTION_SEND) / `PhotoCamera`(인스타 ACTION_SEND). FileProvider authority `${applicationId}.fileprovider` 신규 + `xml/file_paths.xml` cache-path. `AndroidManifest <queries>` 에 com.kakao.talk / com.instagram.android 등록 (Android 11+ 패키지 가시성)
- [x] (05-22) `bugfix` **사진 회전 EXIF 합산 + 다운샘플 — silent fail fix** — 글 작성 시 사진 회전이 적용 안 된 채 업로드되던 두 원인 동시 처리. (1) EXIF orientation 미합산: Coil(프리뷰)는 EXIF 적용해 표시하지만 `BitmapFactory.decodeStream` 은 raw 만 디코딩 → 사용자 입력 90° 회전만 적용해 저장하면 EXIF 회전이 빠진 채 업로드되어 뷰어가 다른 orientation 으로 표시. `readExifRotation` 헬퍼(`android.media.ExifInterface`) + `rotateImageFile` 가 `totalRotation = exif + userInput` 합산 후 baking. (2) OOM silent fail: 12MP 원본 디코딩 + `createBitmap` 회전 ~100MB heap → OOM 으로 null 반환 → `?: src` fallback 으로 원본 업로드. inSampleSize 로 긴 변 ~2400px 까지 다운샘플 + OOM 명시 catch + 단계별 로깅(`OctaLink.Rotate`)
- [x] (05-22) `UX` **글 작성 폼 chip · 버튼 디자인 정비** — 4개 이미지 액션 chip(↻ 그린 / ↺ 보라 / 변경 블루 / 제거 빨강)이 각기 다른 색의 outlined + tinted bg chip 스타일. ↻↺ 좌측 나란히, 변경/제거 우측. 영상 첨부 = 그린(#27AE60), 이미지 첨부 = 블루(#1E88E5) 시각 분리. "+ 회원 선택" = 보라(#7E57C2). "@ 멘션" 라벨 = 토스트 강조와 동일 amber(#FFD54F) + ExtraBold. 게시 = filled red 버튼 / 취소 = outlined 회색 버튼. AlertDialog text Column 에 `verticalScroll` 추가 — 멘션 칩 누적으로 하단 잘림 해소
- [x] (05-22) `UX` **사진/영상 첨부 시 정중앙 멘션 안내 토스트** — 첨부 직후 6초간 in-dialog overlay (검정 반투명 bubble + 흰 텍스트 3줄 자연 분배). Popup 은 AlertDialog 보다 z-order 낮아 가려져 — Box.align(Center) 로 다이얼로그 내부 오버레이. `'@ 멘션'` 부분만 `buildAnnotatedString` 으로 amber(#FFD54F) + ExtraBold 강조. Android Toast 대비 자동 dismiss 시간 + 텍스트 잘림 회피
- [x] (05-22) `백엔드` **멘션 알림 본문 글 제목 포함 + Cloud Function 진단 로깅** — `notifyOnPostMention` body 에 `\n글 제목: "..."` 추가(제목 빈 글이면 생략). `sendNotificationTo` 의 단계별 skip 사유(`member doc 없음 / fcmToken 없음 / prefs OFF`) + 송신 시 token prefix 16자 로깅 — 수신자 미수신 진단 시 추적 가능. 신규 로그인 시 `members.{uid}.fcmToken` 자동 영속화(PosseApp 의 `LaunchedEffect(session.member?.id)`) — `onNewToken` 콜백 누락(첫 설치/토큰 캐시) 케이스 보강
- [x] (05-22) `UX` **커뮤니티 본문 YouTube 링크 → 썸네일 카드** — 글 본문에 `youtube.com/watch?v=` / `youtu.be/` / `shorts/` / `embed/` URL 이 포함되면 `extractYouTubeVideoId` 정규식으로 video id 추출 → `YouTubeThumbnail` 카드에서 `img.youtube.com/vi/{id}/hqdefault.jpg` 썸네일 + 재생 ▶ 오버레이 노출. 카드 탭 시 `ACTION_VIEW` 인텐트로 YouTube 앱(없으면 브라우저) 으로 `youtube.com/watch?v={id}` 진입
- [x] (05-22) `홍보` **비공개 베타 온보딩 가이드 PNG/PDF 생성 스크립트** — `scripts/build-beta-onboarding.py` (PIL 기반) + `scripts/beta-onboarding/` 입력 디렉토리. 5단계(Google Play 테스터 초대 → 테스터 확정 → Play 스토어 설치 → 권한 확인 → 앱 실행) 좌측 스크린샷 + 우측 번호·제목·본문 레이아웃. `crop_to_content` 가 좌상단 코너 픽셀을 bg 로 sampling 해 흰 여백 자동 크롭(브라우저 스크린샷 카드 영역만 확대). PNG(카카오 공유용 원본 1400×2620) + PDF(A4 portrait centered fit) 동시 출력 — 카카오 오픈채팅 공지 + 체육관 벽보 인쇄 겸용
- [x] (05-22) `bugfix / 배포` **실기기 카카오 로그인 무반응 — Activity context 핫픽스 분기 처리 완료** — 0.6.0 AAB 비공개 트랙 업로드 직후 실제 폰 설치본에서 카카오 로그인 버튼 클릭부터 무반응이던 차단 이슈. 원인 두 가지 — (1) `Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag` 런타임 예외 (`AuthRepository.signInWithKakao()` 가 ApplicationContext 로 카카오 SDK 띄우려 한 케이스). (2) Google Play App Signing 으로 재서명된 릴리스 빌드의 키 해시가 비즈 앱(1453976) 카카오 콘솔에 미등록. 0.6.1 에서 `AuthRepository.signInWithKakao(activity: Activity)` 시그니처 리팩터 → `InMemoryAuthRepository` / `KakaoAuthRepository` / `SessionViewModel` / `LoginScreen` 호출 사슬 전체 전파(`LocalContext.current as Activity` 캐스팅) + 카카오 콘솔에 Play App Signing SHA-1 의 Base64 키 해시 등록 완료. 0.7.0 / 0.9.0 빌드에 fix 통합되어 실기기 정상 로그인 확인
- [x] (05-22) `배포` **Play 콘솔 비공개 테스트 트랙 게시 — 0.6.1 / 0.7.0 / 0.9.0 누적** — 0.6.1 (versionCode 7) 게시 시점에 카카오 비즈 앱 키 해시 등록 + 실기기 카카오 로그인 무반응 fix 통합. 이후 0.7.0 (versionCode 8) UX 묶음 + 0.9.0 (versionCode 9) 미디어 뷰어·공유·YouTube·회전·멘션 알림 보강 묶음 순차 업로드. 내부/비공개 트랙에 모두 0.9.0 적용 — 베타 테스터 12명 모집 단계 진입
- [x] (05-26) `보안` **출석 체크인 백도어 차단 풀스택 적용** — 클라이언트 UI 윈도우 좁힘 (수업 시작 30분 전 ~ 시작 +10분) + Cloud Function `recordAttendance` / `cancelAttendance` (서버 시각 + role + 윈도우 검증 후 admin SDK doc 처리) + `FirestoreAttendanceRepository.checkIn / cancelCheckIn` 가 직접 Firestore set/delete 대신 Function 호출로 전환 + `firestore.rules` attendance 락다운 (`allow create: if false`, update/delete 는 운영진만) 배포 완료. 옛 APK 직접 write 경로 완전 차단 — 신규 함수 사용 빌드(`0.9.x+attendance-function`) 로 업데이트되지 않은 베타 회원은 즉시 체크인 불가, APK 강제 업데이트 안내 필요
- [x] (05-24~26) `기능 / AI` **AI 코치의 맞춤 루틴 Phase 1 MVP 배포 완료 (베타 화이트리스트)** — 회원의 헥사곤 6축 점수 + 관장 한 줄 코멘트(최근 5건) + belt/체급/입관기간/성별 + 사용자 선택 난이도(초/중/고급) 컨텍스트로 LLM 이 주간 보강 루틴 큐레이팅. 참고: RISE 앱 UX.
  - **루틴 형태**: 요일별 정확히 2개 드릴 × 최대 3일 = 주 6개, 일별 총 ≤20분.
  - **드릴 제약 (도장 환경 반영)**: ✅ 솔로 동작 (셰도우 복싱·킥복싱 / 솔로 BJJ 무브 / 스프롤·풋워크 / 맨몸 컨디셔닝) + 도장 비치 5종 (덤벨 / 케틀벨 / 바벨 / TRX / 로잉머신). ❌ 파트너 필요 (스파링·미트·테이크다운) / 헤비백·점프로프·기타 머신 / 코치 큐잉 필요한 고급 기술.
  - **시각 자료 흐름**: ExerciseDB 폐기(웨이트만 매핑 됨) → LLM `youtubeQuery` (영문 솔로 시연 위주) → Cloud Function 이 YouTube Data API v3 `search.list` 로 상위 1개 영상의 `videoId` 매핑 → 클라이언트가 `img.youtube.com/vi/{videoId}/hqdefault.jpg` 썸네일 + ▶ 오버레이 표시, 탭 시 `youtube.com/watch?v={id}` 진입. 검색 실패 시 빨강 ▶ + 검색 결과 페이지 폴백.
  - **Cloud Function 스택**: `generateWeeklyRoutine` + Gemini 2.5 Flash (Vertex AI us-central1, thinkingBudget=0, maxOutputTokens=8192, responseMimeType=application/json). `WeeklyRoutineDoc` 경로 `members/{uid}/weeklyRoutines/{yyyy-W##}`, weekId idempotent. 드릴 필드: `koName / youtubeQuery / videoId / desc / sets / durationMin / targetAxis`. doc 에 `difficulty` 도 저장 → UI 칩 노출. 호출 시간 ~7초 (Gemini 응답 ~6초가 병목).
  - **UI**: 헥사곤 카드 240dp 고정 (빈 루틴 상태도 한 화면) + 이번 주 부족한 부분 칩 + 내 난이도 칩 + 코치 피드백. DayCard 에 요일 + 카테고리 칩 (TagChip 팔레트 6축 + 난이도 = 9 색) + 총 분. 드릴 카드 좌측 72dp YouTube 썸네일. EmptyRoutineCard 에 초/중/고급 그라데이션 칩 + 오로라 그라데이션 "이번 주 루틴 받기" 버튼.
  - **한 주 1회 생성 정책 (비용 통제)**: doc 생성 후 그 주 동안 재요청 불가. 난이도 셀렉터 + `force=true` 재생성 경로 모두 제거. 다음 주 weekId 바뀌면 자동으로 EmptyRoutineCard 재노출.
  - **권한 게이트 (베타 화이트리스트)**: CREATOR + `AI_ROUTINE_BETA_UIDS` (현재 `kakao:4892939648` 1명) 만 통과. 3 곳 동일하게 유지 — 클라이언트 `data/AiRoutineAccess.kt` / 서버 `AI_ROUTINE_BETA_UIDS` / Firestore rules `canUseAiRoutine()`.
  - **사용자 부담 최소화**: 했음/건너뜀 토글 UI 에서 제거 (Phase 2 가중치 루프 깔리기 전에 사용자에게 "다 해야 한다" 부담만 주는 상태 회피). 백엔드 필드/Repository/Rules 는 보존.
- [x] (05-27) `bugfix` **토너먼트 — 완료된 대진표 자동 사라지던 이슈 fix** — `TournamentViewModel.pickActive()` 가 `finishedAt == null` (미완료) 만 active 로 선택해서, 챔피언 선정 완료 직후 BracketScreen 재진입 시 자동으로 EmptyState 로 떨어지던 문제. 미완료 우선 → 없으면 가장 최근 완료된 토너먼트 fallback 으로 변경 → 다음 추첨 전까지 트리 + 챔피언 카드 유지.
- [x] (05-27) `bugfix` **출석 동료 카드 아바타 — 가입 당시 stored avatarId 대신 현재 gender+weightClass 동적 도출** — `AttendanceScreen` 만 `peer.member.avatarId` 직접 사용해서 가입 후 성별/체급 변경한 회원도 옛 캐릭터로 노출되던 이슈. `avatarFor(gender, weightClass)` 로 홈/프로필 화면과 동일하게 통일.
- [x] (05-27) `dev tools` **scrcpy `-WindowStyle Hidden` 옵션 + Maestro promo 시나리오 (`scripts/promo.yaml` + `scripts/record-promo.sh`)** — scrcpy 백그라운드 실행 시 호스트 콘솔 창 숨김. 베타 홍보 영상 자동화용 Maestro 시나리오는 8 화면 (홈 / 대진표 / AI 루틴 생성 / 커리큘럼 / 출석 / 커뮤니티 / 프로필) 순회 + `adb screenrecord` 동시 녹화 스크립트. 폰 6 탭 좌표 / 한국어 selector wildcard / UTF-8 JVM options 다 검증 끝.

### 남은 일

중요도 높은 순 ▶ 같은 중요도 안에서는 난이도 낮은 순.

**[중요도 ★★★]**
- [ ] `배포` **카카오 비즈 검수 재신청** *(난이도 2, 외부 1~3 영업일)* — 콘솔에서 `gender/age_range/birthday/birthyear/name/phone_number/account_email` 동의 항목이 "권한 없음" 상태. 비즈니스 정보 등록 + 검수 신청.
  - **비공개 테스터 12명 = 비즈 앱(1453976) 필수**: 외부 회원이 Play Console 비공개 테스트로 받아 쓰려면 반드시 비즈 앱 키 사용. 테스트 앱(1455062) 은 팀 Owner/Manager 만 로그인 가능 → 일반 테스터는 즉시 거절. **검수 통과 전이라도 비즈 앱으로 배포는 가능**하지만, 카카오 응답에 prefill 가능한 필드가 거의 없는 상태(아래 항목)
  - **검수 통과 전까지 SignupScreen 사용자 입력 필드**: 이름 / 성별 / 입관일 / 벨트 / 체급 — 모두 사용자가 직접 가입 폼에서 입력. 검수 통과 시 gender 만 카카오 자동 prefill 로 전환 (성별 chip 자동 잠김). 이름/입관일/벨트/체급은 영구적으로 사용자 입력 (카카오 동의 범위가 아니거나 도장 운영 정보)
  - **카카오 앱 2개 운영 구조**: (a) 비즈 앱 ID `1453976` — 운영용, 검수 대기 중, `local.properties.KAKAO_NATIVE_APP_KEY` 기본값. (b) 테스트 앱 ID `1455062` — 이미 모든 동의 항목 활성, 단 팀 멤버(Owner/Manager)만 로그인 가능. 일반 회원 운영 배포는 반드시 비즈 앱 키 사용. 개발자 본인이 풀 데이터 흐름 즉시 검증하려면 `local.properties` 키만 1455062 로 임시 교체 (운영 빌드 전 1453976 복원 필수)

**[중요도 ★★]**
- [~] `기능 / AI` **AI 코치의 맞춤 루틴 — Phase 2/3/4-A 진행 예정** (Phase 1 MVP 는 완료 섹션 참고). 베타 화이트리스트(`kakao:4892939648`) 단독 운영 중.
  - **Phase 2 자동화 + 피드백 (예정)** — Cloud Scheduler 매주 일요일 23시 KST batch + 회원 자발적 자기 기록 UX 새로 설계 (현재 했음/건너뜀 토글은 부담 회피 위해 UI 제거 / 백엔드 필드는 보존).
  - **Phase 3 안전망 (예정)** — 일별 총 시간 20분 초과 시 LLM 재호출 + 드릴명 부적절 어휘 차단 + Gemini 응답 streaming (체감 -3~5초).
  - **Phase 4-A 큐레이션 라이브러리 (정식 운영 100+명 도달 시)** — 직접 촬영 대신 YouTube 영상 큐레이션. `mmaTechniqueLibrary/{techniqueId}` 컬렉션에 카테고리별 80개 사전 검증된 영상 (BJJ / 복싱 / 무에타이 / 레슬링 / 컨디셔닝) + 한국어 메타데이터. Gemini prompt 에 화이트리스트 inject → LLM 이 그 안에서만 techniqueId 선택. 앱 내 `android-youtube-player` (MIT) 풀스크린 임베드 재생. 저작권 0원, 큐레이션 1~2일 + 코드 1.5일.
  - **전 회원 개방 조건**: 베타 화이트리스트 1~2명 운영 4~6주 + 추천 품질 검증 + Vertex AI 사용량 모니터링 + 캐시 (`{youtubeQuery → videoId}` Firestore 컬렉션) 도입 후 화이트리스트 비우기.
  - **비용**: Gemini 베타 1명 주1회 ≈ $0.0004/주, YouTube Data API 600 quota/generate (무료 10,000/일 → 16회/일). 베타 quota 여유 충분.

**[중요도 ★]**
- [~] `수익화 / 광고` **AdMob 배너 + 네이티브 광고 통합** *(난이도 3, 베타 MVP 3~4일 / 풀 6.5~7.5일)* — 정식 운영 100+명 도달 시 의미있는 수익(~$30~50/월). 베타 12명 규모에선 인프라 미리 깔아두는 가치.
  - **적용 위치 (UX 저해 적은 곳만)**: Home 하단 / Curriculum 하단 / Info 하단 = 배너 (320×50). 커뮤니티 피드 N=7글마다 1개 네이티브 인-피드 + "광고" 라벨 명시.
  - **회피 위치**: 출석 체크인 / 프로필 / 설정 / 글 작성 다이얼로그 / 알림 다이얼로그 / 미디어 전체화면 / 토너먼트 대진표 / 로그인·가입 진입 (집중 또는 신뢰 영역). Interstitial 전체 비추 — high-frequency 사용자라 churn 위험.
  - **Phase 1 배너 MVP (코드 완료, 실제 광고 단위 ID swap = Play Store 정식 출시 후)** —
    - ✅ `play-services-ads:23.6.0` 의존성, `BuildConfig.SHOW_ADS` 토글 + `BANNER_AD_UNIT_ID`.
    - ✅ `MobileAds.initialize` (`OctaLinkApplication.onCreate`, SHOW_ADS=true 분기).
    - ✅ Compose `AdBanner` 래퍼 (`AndroidView` + `AdView`, 320×50 BANNER).
    - ✅ Home / Curriculum / Info 하단 LazyColumn 마지막 item 으로 적용.
    - ✅ AndroidManifest `AD_ID` 권한 복원 (`tools:node="remove"` 제거) + `APPLICATION_ID` 운영 ID (`ca-app-pub-6425033183875029~9928178677`) 설정.
    - 🕒 **`BANNER_AD_UNIT_ID` 는 여전히 Google 공식 테스트 ID** (`ca-app-pub-3940256099942544/6300978111`). 베타 단계 자기 광고 클릭 정책 위반 회피 + Play Store 미공개 상태에서 fill 제한 우회.
    - 🕒 **Play Store 정식 출시 의존**: AdMob 광고 단위 ID 발급 자체가 "광고 게재가 제한됨, 스토어를 추가하여 한도 해제" 상태. Play Console 비공개 테스트 트랙은 검색 결과 미노출이라 AdMob "스토어 추가" 통과 불가. 프로덕션 출시 (또는 공개 테스트 트랙) → AdMob 검토 통과 → 실제 광고 단위 ID 발급 → `BuildConfig.BANNER_AD_UNIT_ID` swap.
    - 🕒 Play Console "광고 ID 사용" 선언 "아니오" → "예" 변경 (정식 출시 단계).
  - **Phase 2 네이티브 (2일)**: 피드 index % 7 == 0 위치에 NativeAd 삽입 + "광고" 라벨 + 로딩 실패 시 자리 무시. 베타 후 100명 규모에서 활성.
  - **Phase 3 개인정보처리방침 + 사전 고지 (1일)**: `docs/privacy-policy.html` v1.5 — 광고 ID + AdMob 데이터 수집 항목 추가 + 변경 이력 표 + 시행일. 정책상 30일 전 앱 내 공지(`PostTag.NOTICE`) 발행 후 광고 활성화.
  - **Phase 4 한국/EU 동의 다이얼로그 (1.5일)**: Google UMP SDK + 첫 실행 시 맞춤 광고 동의 dialog + 거부 시 비맞춤 광고만. 베타 종료 후 정식 출시 시점에 검토.
  - **베타 권장**: Phase 1 + 3 만 (3~4일) + `BuildConfig.SHOW_ADS` flag — 회원 50명 미만일 땐 광고 비활성. 50명 넘으면 토글 ON.
  - **수익 추정**: 베타 12명 ~$3~5/월 / 정식 100명 ~$30~50/월 / 500명 ~$150~250/월 (배너 + 네이티브 풀 시 1.5~2배).
- [ ] `UX` **체크인 위치 검증** *(난이도 3, ~반나절, 옵션)* — GPS 체육관 반경 검증. `AttendanceDoc` 에 `checkInLat/Lng` 필드는 이미 정의됨. `ACCESS_FINE_LOCATION` 권한 + FusedLocationProvider + 거리 계산. 시간 30분 윈도우(05-08)는 이미 적용. 운영자가 ON/OFF 토글 가능하게 설계 권장
