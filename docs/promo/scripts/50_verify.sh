#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source manifest.sh
chk(){ # file
  local f="$1"; [ -f "$f" ] || { echo "FAIL 없음: $f"; exit 1; }
  local dur w h acodec
  dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f" | tr -d '\r')
  read w h < <(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "$f" | tr ',' ' ' | tr -d '\r')
  acodec=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of csv=p=0 "$f" | tr -d '\r')
  local ok=1
  awk "BEGIN{exit !($dur>29.85 && $dur<30.15)}" || { echo "  dur=$dur (기대 30.0)"; ok=0; }
  [ "$w" = 1080 ] && [ "$h" = 1920 ] || { echo "  res=${w}x${h} (기대 1080x1920)"; ok=0; }
  [ -n "$acodec" ] || { echo "  오디오 없음"; ok=0; }
  [ "$ok" = 1 ] && echo "PASS $f  (dur=$dur res=${w}x${h} audio=$acodec)" || { echo "FAIL $f"; exit 1; }
  local b; b=$(basename "$f" .mp4)
  for t in 1 7 13 18 22 25 28.5; do
    ffmpeg -y -ss $t -i "$f" -frames:v 1 "$WORK/checkframes/${b}_${t}s.png" -loglevel error
  done
}
chk "$OUT/octalink_promo_v1_overlay.mp4"
chk "$OUT/octalink_promo_v2_raw.mp4"
echo "VERIFY: PASS — 체크 프레임: work/checkframes/"
