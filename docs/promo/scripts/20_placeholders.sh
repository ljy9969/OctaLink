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
