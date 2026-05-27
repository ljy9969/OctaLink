#!/bin/bash
# OctaLink promo 영상 녹화 — screenrecord(백그라운드) + Maestro(전경) 동시 실행
set -e

# MSYS path 변환 차단 (/sdcard/... 가 Windows 경로로 변환되는 거 방지)
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
ADB="/c/Users/Jeon2/AppData/Local/Android/Sdk/platform-tools/adb.exe"
DEVICE="${1:-emulator-5554}"
echo "TARGET DEVICE: $DEVICE"

OUT_RAW="scripts/promo-raw.mp4"
OUT_FINAL="scripts/promo.mp4"
TIME_LIMIT="90"

echo "[1/4] 기존 promo.mp4 디바이스/로컬 정리"
"$ADB" -s "$DEVICE" shell rm -f /sdcard/promo.mp4 || true
rm -f "$OUT_RAW" "$OUT_FINAL" || true

echo "[2/4] 폰 깨우기 (scrcpy --turn-screen-off 대응) + screenrecord 백그라운드 시작 (${TIME_LIMIT}s)"
"$ADB" -s "$DEVICE" shell input keyevent KEYCODE_WAKEUP
sleep 0.5
"$ADB" -s "$DEVICE" shell screenrecord --time-limit "$TIME_LIMIT" --bit-rate 8000000 /sdcard/promo.mp4 &
REC_PID=$!
sleep 2

echo "[3/4] Maestro 시나리오 실행"
maestro --device "$DEVICE" test scripts/promo.yaml || true

echo "[3.5/4] Maestro 종료, screenrecord 끝나길 대기"
wait "$REC_PID" || true
sleep 2

echo "[4/4] mp4 pull"
"$ADB" -s "$DEVICE" pull /sdcard/promo.mp4 "$OUT_RAW"
ls -la "$OUT_RAW"
