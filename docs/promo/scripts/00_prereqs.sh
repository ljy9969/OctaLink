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
