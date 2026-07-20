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

# 1) 세그먼트 정규화 (화이트 플래시는 훅 끝·쉐도우 시작에만 — 전체 타임라인 fade 금지)
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
[ "$VAR" = v1 ] && name="octalink_promo_v1_overlay" || name="octalink_promo_v2_raw"
ffmpeg -y -i "$S/video_final.mp4" -i "$WORK/bgm_30s.wav" \
  -map 0:v:0 -map 1:a:0 -t 30 -c:v copy -c:a aac -b:a 192k -shortest "$OUT/$name.mp4" -loglevel error
echo "ASSEMBLED: $OUT/$name.mp4"
