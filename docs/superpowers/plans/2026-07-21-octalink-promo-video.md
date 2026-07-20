# OctaLink 베타 모집 홍보영상 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 승인된 스펙([2026-07-21-octalink-promo-video-design.md](../specs/2026-07-21-octalink-promo-video-design.md))대로 9:16·30초 인스타 릴스 홍보영상을 **2버전**(쉐도우 코치 v1 앱오버레이 / v2 원본) 렌더링한다.

**Architecture:** 결정적으로 재현되는 **ffmpeg 조립 파이프라인**을 먼저 플레이스홀더 소스로 완성·검증한 뒤(수동 촬영 리스크 격리), 실제 에뮬레이터/OBS 캡처로 소스만 교체해 최종 렌더한다. 모든 소스 클립은 `captures/`에 **고정 파일명**(매니페스트)으로 존재하고, 조립 스크립트는 `SHADOW` 변종만 바꿔 동일 타임라인을 2번 렌더한다.

**Tech Stack:** ffmpeg 8.1.1 (libx264/libmp3lame/libass), Python 3.12 + numpy (BGM 합성), ASS 자막(Malgun Gothic Bold), Android `adb screenrecord`, OBS Virtual Camera, Git Bash.

## Global Constraints

- 출력: **1080×1920, 30.000초, H.264(yuv420p), 30fps, AAC 오디오** — 2개 파일
- 파일명: `out/octalink_promo_v1_overlay.mp4`, `out/octalink_promo_v2_raw.mp4` (v1/v2는 쉐도우 구간만 차이, 나머지 동일)
- 세그먼트 길이(합=30.0): hook 3.0 / shadow 8.0 / routine 5.0 / growth 4.0 / montage 4.0 / logo 3.0 / cta 3.0
- 자막 폰트: **Malgun Gothic Bold** (`C:/Windows/Fonts/malgunbd.ttf`)
- 컬러: Ink `#0B0B0F` / Bone `#F5F2EC` / Blood `#C8102E`
- 세이프마진: 상단 12%(≥230px) / 하단 20%(≥384px) 안에 자막 배치
- 확정 카피(오타 금지): 훅 `내 자세가 맞는 걸까?` · 쉐도우 `AI가 실시간으로 자세 교정` · 루틴 `오늘 뭐 할지 AI가 짜줌` · 성장 `내 성장이 데이터로` · 몽타주 `출석·커뮤니티·토너먼트 한 곳에` · CTA `비공개 테스터 모집 中` / `프로필 링크로 참여`
- 작업 루트: `d:/source/JEON2/OctaLink/docs/promo/` (모든 스크립트는 이 디렉터리를 cwd로 실행 — Windows 드라이브 콜론 이스케이프 회피)
- 원본 쉐도우 클립: `G:/내 드라이브/작업/개발/OctaLink/shadow_1782295922101.mp4` (720×1280, 63s, 무음)
- 대용량 산출물(`captures/`, `work/`, `out/`)은 git 미추적. 커밋 대상은 **스크립트·자막·매니페스트·문서**뿐.
- BGM: A안 트랩(140 BPM). 저작권 안전한 자체 합성만 사용.

---

## File Structure

```
OctaLink/docs/promo/
├── .gitignore              # captures/ work/ out/ 제외
├── manifest.sh             # 소스 클립 파일명·세그먼트 인점(SS) 단일 진실
├── scripts/
│   ├── 00_prereqs.sh       # 도구/폰트/경로 점검 + 캡처 체크리스트 출력
│   ├── 10_bgm.py           # 30초 트랩 BGM 합성 → work/bgm_30s.wav
│   ├── 20_placeholders.sh  # 스탠드인 소스 클립 생성(실캡처 전 파이프라인 검증용)
│   ├── 30_captions.ass     # 자막(타임라인 전체)
│   ├── 40_assemble.sh      # v1|v2 조립 → out/*.mp4
│   └── 50_verify.sh        # ffprobe 검증 + 체크 프레임 추출
├── captures/               # (미추적) shadow_raw/shadow_v1/routine/profile/attendance/community/tournament .mp4
├── work/                   # (미추적) 중간물: fonts/, assets/, seg/, bgm_30s.wav, checkframes/
└── out/                    # (미추적) 최종 mp4 2개
```

**책임 분리:** `manifest.sh`=소스 계약(파일명+인점), `40_assemble.sh`=타임라인 로직(변종 파라미터), `30_captions.ass`=카피/타이밍/스타일, `10_bgm.py`=오디오, `50_verify.sh`=수용기준 검사. 촬영 태스크는 `captures/`의 파일만 교체하며 조립 로직을 건드리지 않는다.

---

### Task 1: 작업 디렉터리 스캐폴드 + 매니페스트 + 사전점검

**Files:**
- Create: `docs/promo/.gitignore`
- Create: `docs/promo/manifest.sh`
- Create: `docs/promo/scripts/00_prereqs.sh`

**Interfaces:**
- Produces: `manifest.sh` — 아래 변수를 export. 후속 모든 스크립트가 `source manifest.sh`로 소비.
  - `RAW_CLIP` (원본 절대경로), `CAP` (=captures), `WORK`, `OUT`, `FONT`
  - 세그 인점: `HOOK_SS`, `SHADOW_V1_SS`, `SHADOW_V2_SS`, `ROUTINE_SS`, `PROFILE_SS`, `ATTEND_SS`, `COMMUNITY_SS`, `TOURN_SS` (모두 초)
  - 캡처 파일명: `F_SHADOW_RAW`, `F_SHADOW_V1`, `F_ROUTINE`, `F_PROFILE`, `F_ATTEND`, `F_COMMUNITY`, `F_TOURN`

- [ ] **Step 1: `.gitignore` 작성**

```gitignore
# docs/promo/.gitignore — 대용량 산출물 미추적
captures/
work/
out/
```

- [ ] **Step 2: `manifest.sh` 작성**

```bash
#!/usr/bin/env bash
# docs/promo/manifest.sh — 소스 계약 + 세그먼트 인점(단일 진실)
export RAW_CLIP="/g/내 드라이브/작업/개발/OctaLink/shadow_1782295922101.mp4"
export CAP="captures"; export WORK="work"; export OUT="out"
export FONT="/c/Windows/Fonts/malgunbd.ttf"

# 캡처 파일명 (captures/ 안)
export F_SHADOW_RAW="shadow_raw.mp4"     # 원본 클립 복사본 (Task 8에서 채움; 없으면 placeholder)
export F_SHADOW_V1="shadow_v1.mp4"       # OBS 오버레이 캡처 (Task 7)
export F_ROUTINE="routine.mp4"           # 에뮬 캡처 (Task 6)
export F_PROFILE="profile.mp4"
export F_ATTEND="attendance.mp4"
export F_COMMUNITY="community.mp4"
export F_TOURN="tournament.mp4"

# 세그먼트 인점(초) — 캡처 후 베스트 구간에 맞춰 조정 가능
export HOOK_SS=6          # 원본에서 훅용 3초 시작점(인물 프레임 꽉 찬 구간)
export SHADOW_V1_SS=2     # OBS 오버레이 캡처에서 8초 시작점
export SHADOW_V2_SS=12    # 원본에서 v2용 8초 시작점(선명·안정 구간)
export ROUTINE_SS=0
export PROFILE_SS=0
export ATTEND_SS=0
export COMMUNITY_SS=0
export TOURN_SS=0
```

- [ ] **Step 3: `scripts/00_prereqs.sh` 작성**

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."          # cwd = docs/promo
source manifest.sh
fail=0
need(){ command -v "$1" >/dev/null 2>&1 && echo "OK  $1" || { echo "MISSING $1"; fail=1; }; }
echo "== 도구 =="; need ffmpeg; need ffprobe; need python; need adb
echo "== 폰트 =="; [ -f "$FONT" ] && echo "OK  $FONT" || { echo "MISSING $FONT"; fail=1; }
echo "== numpy =="; python -c "import numpy" 2>/dev/null && echo "OK  numpy" || { echo "MISSING numpy"; fail=1; }
echo "== 원본 클립 =="; [ -f "$RAW_CLIP" ] && echo "OK  raw clip" || { echo "MISSING $RAW_CLIP"; fail=1; }
echo "== 로고 =="; [ -f "../../app/src/main/res/drawable-nodpi/mark_octalink.png" ] && echo "OK  mark" || { echo "MISSING mark_octalink.png"; fail=1; }
mkdir -p "$CAP" "$WORK/fonts" "$WORK/assets" "$WORK/seg" "$WORK/checkframes" "$OUT"
cp "$FONT" "$WORK/fonts/malgunbd.ttf"
cp "../../app/src/main/res/drawable-nodpi/mark_octalink.png" "$WORK/assets/mark.png" 2>/dev/null || true
cp "../../app/src/main/res/drawable-nodpi/logo_octalink.png" "$WORK/assets/logo.png" 2>/dev/null || true
cat <<'EOT'

== 캡처 체크리스트(에뮬레이터, 1080x1920) ==
 [ ] routine.mp4     : AI 맞춤 루틴 — 헥사곤+부족한부분+요일별 드릴 카드 스크롤 (화이트리스트 계정, 루틴 doc 사전 생성)
 [ ] profile.mp4     : 프로필 — 헥사곤 차트 + 승률
 [ ] attendance.mp4  : 출석 — 셀프 체크인(파랑) 탭
 [ ] community.mp4   : 커뮤니티 — 피드 스크롤
 [ ] tournament.mp4  : 매치 — 브래킷/챔피언 카드
 [ ] shadow_v1.mp4   : 쉐도우 코치 — OBS 가상카메라 입력으로 스켈레톤+피드백 (Task 7)
EOT
[ "$fail" = 0 ] && echo "PREREQS: PASS" || { echo "PREREQS: FAIL"; exit 1; }
```

- [ ] **Step 4: 실행해 통과 확인**

Run: `cd /d/source/JEON2/OctaLink/docs/promo && bash scripts/00_prereqs.sh`
Expected: 마지막 줄 `PREREQS: PASS`, `work/fonts/malgunbd.ttf`·`work/assets/mark.png` 생성.

- [ ] **Step 5: 커밋**

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/.gitignore docs/promo/manifest.sh docs/promo/scripts/00_prereqs.sh
git commit -m "chore(promo): 홍보영상 작업 스캐폴드 + 매니페스트 + 사전점검"
```

---

### Task 2: 플레이스홀더 소스 클립 생성

실제 촬영 전에 조립 파이프라인을 결정적으로 검증하기 위한 스탠드인. 각 캡처 파일명으로 라벨 슬레이트를 만든다. `shadow_raw.mp4`는 실제 원본이 있으면 복사.

**Files:**
- Create: `docs/promo/scripts/20_placeholders.sh`

**Interfaces:**
- Consumes: `manifest.sh`
- Produces: `captures/*.mp4` (매니페스트의 7개 파일; 실제 원본 존재 시 shadow_raw는 원본 복사)

- [ ] **Step 1: `scripts/20_placeholders.sh` 작성**

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source manifest.sh
gen(){ # text color dur outfile
  ffmpeg -y -f lavfi -i "color=c=$2:s=1080x1920:d=$3:r=30" \
    -vf "drawtext=fontfile=$WORK/fonts/malgunbd.ttf:text='$1':fontcolor=white:fontsize=64:x=(w-tw)/2:y=(h-th)/2:box=1:boxcolor=black@0.4:boxborderw=20" \
    -c:v libx264 -pix_fmt yuv420p -crf 20 "$CAP/$4" -loglevel error
  echo "placeholder: $CAP/$4"
}
# 실제 원본 있으면 shadow_raw 는 원본 복사(훅+v2 소스), 없으면 슬레이트
if [ -f "$RAW_CLIP" ]; then cp "$RAW_CLIP" "$CAP/$F_SHADOW_RAW"; echo "copied raw -> $CAP/$F_SHADOW_RAW"; else gen "SHADOW RAW" 0x222222 12 "$F_SHADOW_RAW"; fi
gen "SHADOW COACH v1"  0x3A0A12 10 "$F_SHADOW_V1"
gen "AI 루틴"          0x0B0B0F 8  "$F_ROUTINE"
gen "PROFILE 헥사곤"   0x121821 8  "$F_PROFILE"
gen "출석 체크인"      0x0A1420 6  "$F_ATTEND"
gen "커뮤니티"         0x141018 6  "$F_COMMUNITY"
gen "토너먼트"         0x1A0A0A 6  "$F_TOURN"
echo "PLACEHOLDERS: DONE"
```

- [ ] **Step 2: 실행 후 파일 확인**

Run: `bash scripts/20_placeholders.sh && ls -la captures/`
Expected: 7개 `.mp4` 생성, `PLACEHOLDERS: DONE`.

- [ ] **Step 3: 커밋** (스크립트만; captures/는 gitignore)

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/scripts/20_placeholders.sh
git commit -m "chore(promo): 파이프라인 검증용 플레이스홀더 소스 생성기"
```

---

### Task 3: 30초 트랩 BGM 합성

**Files:**
- Create: `docs/promo/scripts/10_bgm.py`

**Interfaces:**
- Produces: `work/bgm_30s.wav` (모노, 44100Hz, 30.0초). 컷 지점(0/3/11/16/20/24/27)에 다운비트·임팩트 정렬.

- [ ] **Step 1: `scripts/10_bgm.py` 작성**

```python
import numpy as np, wave, os
SR=44100; OUT=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..","work","bgm_30s.wav")
def env(n,a,d,sl,r):
    e=np.zeros(n); ai=min(max(1,int(a*SR)),n); e[:ai]=np.linspace(0,1,ai)
    di=min(max(1,int(d*SR)),n-ai)
    if di>0: e[ai:ai+di]=np.linspace(1,sl,di)
    if n-ai-di>0: e[ai+di:]=sl
    ri=min(max(1,int(r*SR)),n)
    e[-ri:]*=np.linspace(1,0,ri); return e
def noise(d): return np.random.rand(int(d*SR))*2-1
def kick(d=0.35):
    n=int(d*SR); t=np.arange(n)/SR; f=120*np.exp(-t*35)+45
    body=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*7)
    c=noise(0.006)*np.exp(-np.arange(int(0.006*SR))/SR*400); body[:len(c)]+=c*0.6
    return np.tanh(body*1.4)*0.95
def sub(f,d):
    n=int(d*SR); t=np.arange(n)/SR
    o=(np.sin(2*np.pi*f*t)+0.25*np.sin(2*np.pi*2*f*t))*env(n,0.008,d*0.9,0.2,0.06)
    return np.tanh(o*1.3)*0.8
def snare(d=0.22):
    n=int(d*SR); t=np.arange(n)/SR
    tone=(np.sin(2*np.pi*180*t)+np.sin(2*np.pi*330*t))*0.4*np.exp(-t*22)
    nz=np.diff(noise(d),prepend=0)*np.exp(-t*16); return np.tanh(tone+nz*1.2)*0.85
def clap(d=0.25):
    n=int(d*SR); t=np.arange(n)/SR; nz=np.diff(noise(d),prepend=0); e=np.exp(-t*18)
    for off in (0.01,0.02,0.03):
        i=int(off*SR); e[i:]+=np.exp(-np.arange(n-i)/SR*18)*0.7
    return np.tanh(nz*e*1.1)*0.8
def hat(open=False):
    d=0.14 if open else 0.05; n=int(d*SR); t=np.arange(n)/SR
    nz=np.diff(np.diff(noise(d),prepend=0),prepend=0); return nz*np.exp(-t*(30 if open else 90))*0.5
def pluck(f,d):
    n=int(d*SR); t=np.arange(n)/SR; saw=2*(t*f-np.floor(0.5+t*f))
    return saw*np.exp(-t*9)*env(n,0.003,d,0.0,0.04)*0.35
def punch(d=0.4):
    n=int(d*SR); t=np.arange(n)/SR; f=180*np.exp(-t*40)+70
    thud=np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*11)
    s=np.diff(noise(0.02),prepend=0)*np.exp(-np.arange(int(0.02*SR))/SR*300); thud[:len(s)]+=s*0.8
    return np.tanh(thud*1.6)*0.98
def place(buf,sig,t,g=1.0):
    i=int(t*SR); j=min(len(buf),i+len(sig))
    if i<len(buf): buf[i:j]+=sig[:j-i]*g
np.random.seed(7)
BPM=140; beat=60/BPM; step=beat/4; bar=beat*4; TOTAL=30.0
a=np.zeros(int(TOTAL*SR))
# 섹션별 강도(엔벨로프): 훅(0-3) 미니멀 → 쉐도우(3-11) 풀드랍 → 루틴/성장(11-20) 유지 → 몽타주(20-24) 가장 강 → 로고/CTA(24-30) 정리
def intensity(tt):
    if tt<3: return 0.5
    if tt<20: return 1.0
    if tt<24: return 1.15
    return 0.9
nb=int(TOTAL/bar)+1
roots=[55,55,43.65,49.0]
for b in range(nb):
    t0=b*bar
    if t0>=TOTAL: break
    g=intensity(t0)
    for s in [0,6,10]: place(a,kick(),t0+s*step,0.95*g)
    place(a,snare(),t0+8*step,0.9*g); place(a,clap(),t0+8*step,0.5*g)
    place(a,sub(roots[b%4],bar*0.98),t0,0.9*g)
    for s in range(16):
        place(a,hat(open=(s==14)),t0+s*step,(0.9 if s%2==0 else 0.5)*0.5*g)
    if b%2==0:
        for s,f in [(0,220),(3,261.6),(6,329.6),(10,246.9)]: place(a,pluck(f,beat*0.9),t0+s*step,0.6*g)
# 임팩트 악센트: 시작·전환(3s 직전)·CTA(27s)
place(a,punch(),0.0,1.0); place(a,punch(),2.85,1.0); place(a,punch(0.6),27.0,1.0); place(a,sub(55,1.4),27.0,0.6)
a=a/(np.max(np.abs(a))+1e-9)*0.97
# 30.0초 정확히 자르고 인/아웃 페이드
a=a[:int(TOTAL*SR)]; fi=int(0.05*SR); fo=int(0.8*SR)
a[:fi]*=np.linspace(0,1,fi); a[-fo:]*=np.linspace(1,0,fo)
pcm=(np.clip(a,-1,1)*32767).astype(np.int16)
os.makedirs(os.path.dirname(OUT),exist_ok=True)
with wave.open(OUT,'w') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print("wrote",OUT,f"{len(a)/SR:.3f}s")
```

- [ ] **Step 2: 실행**

Run: `python scripts/10_bgm.py`
Expected: `wrote ...work/bgm_30s.wav 30.000s`

- [ ] **Step 3: 길이 검증**

Run: `ffprobe -v error -show_entries format=duration -of csv=p=0 work/bgm_30s.wav`
Expected: `30.0` 근처(±0.05).

- [ ] **Step 4: 커밋**

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/scripts/10_bgm.py
git commit -m "feat(promo): 30초 트랩 BGM 합성 스크립트(섹션 강도+컷 임팩트)"
```

---

### Task 4: ASS 자막 작성

**Files:**
- Create: `docs/promo/scripts/30_captions.ass`

**Interfaces:**
- Consumes: `work/fonts/malgunbd.ttf` (Task 1이 복사)
- Produces: `scripts/30_captions.ass` — 30초 타임라인 전체 자막. 조립 시 `subtitles` 필터로 번인.

스타일: `Head`(대형 중앙 훅/성장), `Line`(상단 설명), `Pop`(Blood 박스 강조 팝업), `Cta`(하단 CTA). PlayRes 1080×1920. 색상은 ASS `&HAABBGGRR`: Bone=`&H00ECF2F5`, Blood 박스=`&H002E10C8`, Ink 외곽=`&H000F0B0B`.

- [ ] **Step 1: `scripts/30_captions.ass` 작성**

```
[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 2
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Head,Malgun Gothic,96,&H00ECF2F5,&H000000FF,&H000F0B0B,&H00000000,-1,0,0,0,100,100,0,0,1,6,3,5,80,80,0,1
Style: Line,Malgun Gothic,72,&H00ECF2F5,&H000000FF,&H000F0B0B,&H00000000,-1,0,0,0,100,100,0,0,1,5,2,8,80,80,240,1
Style: Pop,Malgun Gothic,64,&H00FFFFFF,&H000000FF,&H00000000,&H002E10C8,-1,0,0,0,100,100,0,0,3,0,0,2,80,80,470,1
Style: Cta,Malgun Gothic,70,&H00ECF2F5,&H000000FF,&H000F0B0B,&H00000000,-1,0,0,0,100,100,0,0,1,5,2,2,80,80,470,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.20,0:00:02.80,Head,,0,0,0,,내 자세가 맞는 걸까?
Dialogue: 0,0:00:03.30,0:00:10.80,Line,,0,0,0,,AI가 실시간으로 자세 교정
Dialogue: 0,0:00:04.20,0:00:05.60,Pop,,0,0,0,,가드 올려!
Dialogue: 0,0:00:06.40,0:00:07.80,Pop,,0,0,0,,턱 내려
Dialogue: 0,0:00:08.60,0:00:10.20,Pop,,0,0,0,,회전 부족
Dialogue: 0,0:00:11.20,0:00:15.80,Head,,0,0,0,,오늘 뭐 할지\NAI가 짜줌
Dialogue: 0,0:00:16.20,0:00:19.80,Head,,0,0,0,,내 성장이\N데이터로
Dialogue: 0,0:00:20.20,0:00:23.80,Line,,0,0,0,,출석·커뮤니티·토너먼트 한 곳에
Dialogue: 0,0:00:27.20,0:00:29.80,Cta,,0,0,0,,{\b1}비공개 테스터 모집 中{\b0}\N프로필 링크로 참여
```

- [ ] **Step 2: 자막 렌더 스모크 테스트** (검정 배경 3초에 번인 → 프레임 추출)

Run:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
ffmpeg -y -f lavfi -i "color=c=0x0B0B0F:s=1080x1920:d=1:r=30" -ss 0 -t 1 \
  -vf "subtitles=scripts/30_captions.ass:fontsdir=work/fonts" \
  work/checkframes/cap_smoke.mp4 -loglevel error
ffmpeg -y -ss 0.5 -i work/checkframes/cap_smoke.mp4 -frames:v 1 work/checkframes/cap_hook.png -loglevel error
echo done
```
Expected: `work/checkframes/cap_hook.png` 에 한글 `내 자세가 맞는 걸까?`가 **깨짐 없이(두부 없이)** 중앙에 렌더. (프레임을 열어 눈으로 확인)

- [ ] **Step 3: 커밋**

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/scripts/30_captions.ass
git commit -m "feat(promo): ASS 자막(훅/설명/팝업/CTA, Malgun Gothic)"
```

---

### Task 5: 조립 스크립트 + 검증 (플레이스홀더로 2버전 산출)

**Files:**
- Create: `docs/promo/scripts/40_assemble.sh`
- Create: `docs/promo/scripts/50_verify.sh`

**Interfaces:**
- Consumes: `manifest.sh`, `captures/*.mp4`, `work/bgm_30s.wav`, `scripts/30_captions.ass`, `work/assets/mark.png`
- Produces: `out/octalink_promo_v1_overlay.mp4`, `out/octalink_promo_v2_raw.mp4`
- `40_assemble.sh v1|v2` — 인자로 쉐도우 변종 선택. `norm()`으로 각 소스를 1080×1920·정확한 길이 세그로 정규화 → concat → 자막·화이트플래시 번인 → BGM mux.

- [ ] **Step 1: `scripts/40_assemble.sh` 작성**

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source manifest.sh
VAR="${1:?usage: 40_assemble.sh v1|v2}"
W=1080; H=1920; FPS=30
S="$WORK/seg"; mkdir -p "$S"
enc="-r $FPS -pix_fmt yuv420p -c:v libx264 -crf 18 -preset veryfast -an"

# norm: infile start dur outfile [extra_vf]  (증가스케일+크롭으로 9:16 꽉 채움)
norm(){
  local vf="scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,fps=$FPS,setsar=1"
  [ -n "${5:-}" ] && vf="$vf,$5"
  ffmpeg -y -ss "$2" -t "$3" -i "$1" -vf "$vf" $enc "$4" -loglevel error; }

# 1) 세그먼트 정규화 (화이트 플래시는 훅 끝·쉐도우 시작에만 — 전체 타임라인 fade 금지: fade=out은 이후 프레임을 흰색으로 고정함)
norm "$CAP/$F_SHADOW_RAW" "$HOOK_SS" 3.0 "$S/hook.mp4" "fade=t=out:st=2.82:d=0.18:c=white"
if [ "$VAR" = v1 ]; then norm "$CAP/$F_SHADOW_V1" "$SHADOW_V1_SS" 8.0 "$S/shadow.mp4" "fade=t=in:st=0:d=0.18:c=white"
else                    norm "$CAP/$F_SHADOW_RAW" "$SHADOW_V2_SS" 8.0 "$S/shadow.mp4" "fade=t=in:st=0:d=0.18:c=white"; fi
norm "$CAP/$F_ROUTINE"  "$ROUTINE_SS"   5.0 "$S/routine.mp4"
norm "$CAP/$F_PROFILE"  "$PROFILE_SS"   4.0 "$S/growth.mp4"
# 몽타주: 3컷 x 1.334/1.333/1.333 = 4.0
norm "$CAP/$F_ATTEND"    "$ATTEND_SS"    1.334 "$S/m1.mp4"
norm "$CAP/$F_COMMUNITY" "$COMMUNITY_SS" 1.333 "$S/m2.mp4"
norm "$CAP/$F_TOURN"     "$TOURN_SS"     1.333 "$S/m3.mp4"
printf "file 'm1.mp4'\nfile 'm2.mp4'\nfile 'm3.mp4'\n" > "$S/montage.txt"
ffmpeg -y -f concat -safe 0 -i "$S/montage.txt" -c copy "$S/montage.mp4" -loglevel error

# 2) 로고/CTA 카드 (Ink 배경 + 마크) — 다크 카드용 마크 반전(negate)으로 밝게
ffmpeg -y -f lavfi -i "color=c=0x0B0B0F:s=${W}x${H}:d=3.0:r=$FPS" -i "$WORK/assets/mark.png" \
  -filter_complex "[1]scale=560:-1,negate[m];[0][m]overlay=(W-w)/2:(H-h)/2" $enc "$S/logo.mp4" -loglevel error
ffmpeg -y -f lavfi -i "color=c=0x0B0B0F:s=${W}x${H}:d=3.0:r=$FPS" -i "$WORK/assets/mark.png" \
  -filter_complex "[1]scale=300:-1,negate[m];[0][m]overlay=(W-w)/2:360" $enc "$S/cta.mp4" -loglevel error
# (CTA 텍스트는 자막 ASS가 27.2~29.8에 올림)

# 3) concat (동일 코덱/해상도/fps)
printf "file 'hook.mp4'\nfile 'shadow.mp4'\nfile 'routine.mp4'\nfile 'growth.mp4'\nfile 'montage.mp4'\nfile 'logo.mp4'\nfile 'cta.mp4'\n" > "$S/all.txt"
ffmpeg -y -f concat -safe 0 -i "$S/all.txt" -c copy "$S/video_concat.mp4" -loglevel error

# 4) 자막 번인 (화이트 플래시는 세그먼트 단계에서 이미 적용됨)
ffmpeg -y -i "$S/video_concat.mp4" \
  -vf "subtitles=scripts/30_captions.ass:fontsdir=$WORK/fonts" \
  $enc "$S/video_final.mp4" -loglevel error

# 5) BGM mux (30초로 자름)
name="octalink_promo_${VAR}"; [ "$VAR" = v1 ] && name="octalink_promo_v1_overlay" || name="octalink_promo_v2_raw"
ffmpeg -y -i "$S/video_final.mp4" -i "$WORK/bgm_30s.wav" \
  -map 0:v:0 -map 1:a:0 -t 30 -c:v copy -c:a aac -b:a 192k -shortest "$OUT/$name.mp4" -loglevel error
echo "ASSEMBLED: $OUT/$name.mp4"
```

- [ ] **Step 2: `scripts/50_verify.sh` 작성**

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source manifest.sh
chk(){ # file
  local f="$1"; [ -f "$f" ] || { echo "FAIL 없음: $f"; exit 1; }
  local dur w h acodec   # Windows ffprobe 출력의 \r 제거 필수
  dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f" | tr -d '\r')
  read w h < <(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "$f" | tr ',' ' ' | tr -d '\r')
  acodec=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of csv=p=0 "$f" | tr -d '\r')
  local ok=1
  awk "BEGIN{exit !($dur>29.85 && $dur<30.15)}" || { echo "  dur=$dur (기대 30.0)"; ok=0; }
  [ "$w" = 1080 ] && [ "$h" = 1920 ] || { echo "  res=${w}x${h} (기대 1080x1920)"; ok=0; }
  [ -n "$acodec" ] || { echo "  오디오 없음"; ok=0; }
  [ "$ok" = 1 ] && echo "PASS $f  (dur=$dur res=${w}x${h} audio=$acodec)" || { echo "FAIL $f"; exit 1; }
  # 체크 프레임(각 세그 대표 시점)
  local b; b=$(basename "$f" .mp4)
  for t in 1 7 13 18 22 25 28.5; do
    ffmpeg -y -ss $t -i "$f" -frames:v 1 "$WORK/checkframes/${b}_${t}s.png" -loglevel error
  done
}
chk "$OUT/octalink_promo_v1_overlay.mp4"
chk "$OUT/octalink_promo_v2_raw.mp4"
echo "VERIFY: PASS — 체크 프레임: work/checkframes/"
```

- [ ] **Step 3: 두 버전 조립 실행**

Run:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
bash scripts/40_assemble.sh v1 && bash scripts/40_assemble.sh v2
```
Expected: `ASSEMBLED: out/octalink_promo_v1_overlay.mp4` / `..._v2_raw.mp4`

- [ ] **Step 4: 검증 실행**

Run: `bash scripts/50_verify.sh`
Expected: `PASS` 2줄 + `VERIFY: PASS`. `work/checkframes/*.png` 확인 시 자막이 각 구간에 정확히 뜨고(훅/쉐도우 팝업/루틴/성장/몽타주/CTA) 세이프마진 안에 위치.

- [ ] **Step 5: 커밋**

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/scripts/40_assemble.sh docs/promo/scripts/50_verify.sh
git commit -m "feat(promo): 조립(v1/v2 변종)+검증 파이프라인 — 플레이스홀더로 30s 2버전 산출"
```

---

### Task 6: 에뮬레이터 화면 캡처 (routine/profile/attendance/community/tournament)

수동 캡처. 조립 로직은 불변 — `captures/`의 파일만 교체한다.

**Files:**
- Replace: `captures/routine.mp4`, `captures/profile.mp4`, `captures/attendance.mp4`, `captures/community.mp4`, `captures/tournament.mp4`

**Interfaces:**
- Consumes: 실행 중인 에뮬레이터 + OctaLink 앱(카카오 로그인·Firebase 통과, AI 루틴 화이트리스트 계정, 루틴 doc 사전 생성)
- Produces: 위 5개 캡처(세로, 앱 UI). 각 매니페스트 파일명으로 저장.

- [ ] **Step 1: 에뮬레이터 준비**

Android Studio에서 Pixel 계열 AVD(해상도 1080×1920 이상) 실행 → OctaLink 실행 → 로그인 → 대상 화면 진입 가능 확인. 상태바 정리:
```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```
> ⚠️ 실패 조건: 로그인/화이트리스트/네트워크로 특정 화면 진입 불가 → 해당 파일은 **Play Store 스크린샷**으로 대체(스틸 이미지를 5초 클립으로: `ffmpeg -y -loop 1 -t 5 -i shot.png -vf "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,fps=30" -c:v libx264 -pix_fmt yuv420p captures/<name>.mp4`).

- [ ] **Step 2: 각 화면 녹화 헬퍼**

각 화면에서 아래를 실행(녹화 중 해당 제스처 수행: 루틴/커뮤니티=천천히 스크롤, 출석=체크인 탭, 프로필=헥사곤 보이게 정지, 매치=브래킷/챔피언). `Ctrl+C`로 종료.
```bash
cap(){ # name seconds
  adb shell screenrecord --size 1080x1920 --bit-rate 12000000 --time-limit "$2" /sdcard/$1.mp4
  adb pull /sdcard/$1.mp4 "captures/$1.mp4"; adb shell rm /sdcard/$1.mp4
  ffprobe -v error -show_entries stream=width,height -of csv=p=0 -select_streams v:0 "captures/$1.mp4"
}
# 예) cd docs/promo 후:
cap routine 8; cap profile 6; cap attendance 6; cap community 6; cap tournament 6
```
> 참고: 일부 기기/에뮬은 `--size 1080x1920` 미지원 → 옵션 빼고 녹화 후 `norm()`이 크롭하므로 무방(단 세로여야 함).

- [ ] **Step 3: 조립·검증 재실행(실캡처 반영, 쉐도우는 아직 플레이스홀더/원본)**

Run: `bash scripts/40_assemble.sh v2 && bash scripts/50_verify.sh`
Expected: `VERIFY: PASS`. 체크 프레임 13s(루틴)·18s(성장)·22s(몽타주)에 실제 앱 화면이 보임.

- [ ] **Step 4: 커밋** (캡처는 미추적 — 커밋할 변경 없음; 인점 조정 시 manifest만)

```bash
cd /d/source/JEON2/OctaLink
git add -A docs/promo/manifest.sh 2>/dev/null || true
git diff --cached --quiet && echo "커밋할 텍스트 변경 없음(캡처는 gitignore)" || git commit -m "chore(promo): 캡처 인점(SS) 조정"
```

---

### Task 7: 쉐도우 코치 v1 캡처 (OBS 가상카메라 → 에뮬레이터)

**Files:**
- Replace: `captures/shadow_v1.mp4`

**Interfaces:**
- Consumes: OBS Virtual Camera(원본 클립 재생) + 에뮬레이터 카메라=OBS Virtual Camera + OctaLink 쉐도우 코치 화면
- Produces: `captures/shadow_v1.mp4` — 앱의 스켈레톤+실시간 피드백 오버레이가 보이는 세로 캡처

- [ ] **Step 1: OBS 가상카메라 세팅**

1. OBS Studio 설치(미설치 시). 
2. OBS에 Media Source로 원본 클립(`G:/내 드라이브/작업/개발/OctaLink/shadow_1782295922101.mp4`) 추가, 반복 재생.
3. 캔버스를 세로(예: 720×1280)로 맞추고 클립을 채움.
4. **Start Virtual Camera**.

- [ ] **Step 2: 에뮬레이터 카메라를 가상카메라로**

AVD 설정에서 Back/Front Camera를 **Webcam0**(=OBS Virtual Camera)로 지정 후 콜드부트. (Android Studio ▸ Device Manager ▸ AVD ▸ Edit ▸ Camera ▸ Webcam0)
> ⚠️ 실패 조건: 에뮬이 가상카메라를 못 잡거나 MediaPipe가 포즈를 못 뽑음 → 실기기(USB, `scrcpy`/`adb screenrecord`)로 쉐도우 코치를 촬영해 `shadow_v1.mp4`로 저장하는 폴백. 그것도 불가 시 v1 생략(스펙 리스크표).

- [ ] **Step 3: 녹화**

OctaLink ▸ 쉐도우 코치 진입 → 콤비 선택 → 원본 영상이 카메라 입력으로 들어가 **관절 스켈레톤 + 피드백 문구**가 뜨는지 확인 → 녹화:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
adb shell screenrecord --bit-rate 12000000 --time-limit 12 /sdcard/shadow_v1.mp4
adb pull /sdcard/shadow_v1.mp4 captures/shadow_v1.mp4 && adb shell rm /sdcard/shadow_v1.mp4
```
스켈레톤·피드백이 잘 보이는 8초+ 구간을 확보. 필요 시 `manifest.sh`의 `SHADOW_V1_SS`로 시작점 조정.

- [ ] **Step 4: v1 조립·검증**

Run: `bash scripts/40_assemble.sh v1 && bash scripts/50_verify.sh`
Expected: `VERIFY: PASS`. 체크 프레임 7s에서 **스켈레톤+피드백 오버레이**가 보임.

- [ ] **Step 5: 커밋** (텍스트 변경분만)

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/manifest.sh
git diff --cached --quiet && echo "변경 없음" || git commit -m "chore(promo): 쉐도우 v1 인점 조정"
```

---

### Task 8: 쉐도우 원본(v2/훅) 구간 정제

원본 클립에서 훅 3초·v2 8초 베스트 구간을 고르고, 흔들림 보정.

**Files:**
- Replace(선택): `captures/shadow_raw.mp4` (스태빌라이즈본), `manifest.sh`(인점)

**Interfaces:**
- Consumes: `captures/shadow_raw.mp4`(=원본 복사본, Task 2에서 생성)
- Produces: 안정화된 `shadow_raw.mp4` + 확정된 `HOOK_SS`/`SHADOW_V2_SS`

- [ ] **Step 1: 후보 구간 스캔(2초 간격 썸네일)**

Run:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
for t in 4 6 8 10 12 14 16 20 30 40; do ffmpeg -y -ss $t -i captures/shadow_raw.mp4 -frames:v 1 work/checkframes/raw_${t}s.png -loglevel error; done
echo "work/checkframes/raw_*.png 확인해 인물 꽉 차고 선명한 시작점 2곳 선택"
```
Expected: 훅용(임팩트 큰 동작)·v2용(선명·안정) 시작 초 선택 → `manifest.sh`의 `HOOK_SS`, `SHADOW_V2_SS` 갱신.

- [ ] **Step 2: 흔들림 보정(vidstab 2-pass)**

Run:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
ffmpeg -y -i captures/shadow_raw.mp4 -vf vidstabdetect=shakiness=8:accuracy=15 -f null - -loglevel error
ffmpeg -y -i captures/shadow_raw.mp4 -vf "vidstabtransform=smoothing=30:zoom=2,unsharp=5:5:0.8" \
  -c:v libx264 -crf 18 -preset medium -an captures/shadow_raw_stab.mp4 -loglevel error
mv captures/shadow_raw_stab.mp4 captures/shadow_raw.mp4
echo "stabilized"
```
> vidstab 미빌드 시(필터 없음 에러) 이 단계 생략 — 원본 그대로 사용(핸드헬드 감성 허용).

- [ ] **Step 3: v2 재조립·검증**

Run: `bash scripts/40_assemble.sh v2 && bash scripts/50_verify.sh`
Expected: `VERIFY: PASS`. 훅(1s)·쉐도우(7s) 프레임이 선명.

- [ ] **Step 4: 커밋**

```bash
cd /d/source/JEON2/OctaLink
git add docs/promo/manifest.sh
git diff --cached --quiet && echo "변경 없음" || git commit -m "chore(promo): 쉐도우 원본 훅/v2 인점 확정"
```

---

### Task 9: 최종 렌더 + 전체 수용기준 검증

**Files:**
- Produce: `out/octalink_promo_v1_overlay.mp4`, `out/octalink_promo_v2_raw.mp4` (실소스 기반 최종본)

**Interfaces:**
- Consumes: Task 6·7·8의 실캡처가 반영된 `captures/*`
- Produces: 수용기준(스펙 §9) 충족 2버전

- [ ] **Step 1: 두 버전 최종 조립**

Run:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
bash scripts/40_assemble.sh v1 && bash scripts/40_assemble.sh v2 && bash scripts/50_verify.sh
```
Expected: `VERIFY: PASS`.

- [ ] **Step 2: 수용기준 체크(스펙 §9) — 체크 프레임 육안 확인**

`work/checkframes/`에서 두 버전 모두 확인:
- [ ] 길이 30.0초 / 1080×1920 / 오디오 존재 (verify가 자동 검사)
- [ ] 쉐도우 코치(7s)·AI 루틴(13s) 구간이 명확
- [ ] 훅 `내 자세가 맞는 걸까?`(1s), CTA `비공개 테스터 모집 中`/`프로필 링크로 참여`(28.5s) 오타 없이 표기
- [ ] 컷 전환이 비트와 어긋나지 않음(3s 화이트플래시 포함)
- [ ] 자막이 세이프마진 안, 잘림 없음
- [ ] v1/v2 차이는 3–11초 구간뿐

- [ ] **Step 3: 최종본 사본을 작업폴더(G드라이브)로 복사(공유 편의)**

Run:
```bash
cd /d/source/JEON2/OctaLink/docs/promo
DEST="/g/내 드라이브/작업/개발/OctaLink/promo_out"; mkdir -p "$DEST"
cp out/octalink_promo_v1_overlay.mp4 out/octalink_promo_v2_raw.mp4 "$DEST/"
ls -la "$DEST"
```
Expected: G드라이브 `promo_out/`에 2개 mp4.

- [ ] **Step 4: 최종 커밋(문서/스크립트 상태 고정)**

```bash
cd /d/source/JEON2/OctaLink
git add -A docs/promo docs/superpowers
git commit -m "chore(promo): 홍보영상 v1/v2 최종 렌더 파이프라인 확정" || echo "변경 없음"
```

- [ ] **Step 5: 사용자 리뷰**

두 버전을 재생해 채택본 선택 요청. 피드백(자막 타이밍/구간 길이/BGM 등)은 해당 스크립트만 수정 후 `40_assemble.sh` 재실행으로 반영.

---

## Self-Review

**1. Spec coverage:**
- 결과물 2버전·9:16·30s·1080×1920 → Task 5/9 + Global Constraints ✓
- 스토리보드 8구간·확정 카피 → Task 4(자막)+Task 5(세그 길이) ✓
- BGM A안 트랩·컷 정렬 → Task 3 ✓
- 캡처(에뮬)·쉐도우 v1(OBS)·v2(원본) → Task 6/7/8 ✓
- 자막 스타일·세이프마진·브랜드 컬러 → Task 4 + Global ✓
- 파일 배치/네이밍 → File Structure + manifest ✓
- 리스크 폴백(스크린샷 대체·OBS 미작동·vidstab 없음) → Task 6/7/8 ⚠️ 인라인 명시 ✓
- 수용기준 §9 → Task 9 Step 2 체크리스트 ✓

**2. Placeholder scan:** "TBD/이후구현/적절히" 없음. 모든 스크립트는 실행 가능한 완본. 수동 캡처 태스크는 정확한 adb/OBS 절차 + 검증 명시.

**3. Type consistency:** 매니페스트 변수명(`F_*`, `*_SS`)이 `40_assemble.sh`·`50_verify.sh`에서 동일하게 사용. 세그 길이 합=30.0(3+8+5+4+4+3+3) 확인. 출력 파일명 v1=`octalink_promo_v1_overlay.mp4`, v2=`octalink_promo_v2_raw.mp4` 일관.

_주의(비-TDD 적응):_ 영상 제작 특성상 "실패하는 테스트" 대신 **결정적 산출→ffprobe/체크프레임 검증**으로 각 태스크를 닫는다. 파이프라인을 플레이스홀더로 먼저 완성(Task 1–5)해 수동 촬영 리스크를 격리한 뒤 실소스로 교체(Task 6–9)한다.
