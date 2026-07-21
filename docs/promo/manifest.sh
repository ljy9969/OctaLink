#!/usr/bin/env bash
# docs/promo/manifest.sh — 소스 계약 + 세그먼트 인점(단일 진실)
export RAW_CLIP="/g/내 드라이브/작업/개발/OctaLink/promo_shadow_clip/shadowbox_asian_7987304.mp4"  # Pexels 7987304 (아시아인 정면, CC0, 세로4K)
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
export HOOK_SS=0.5        # 새 클립(10s)에서 훅용 3초 시작점
export SHADOW_V1_SS=2     # OBS 오버레이 캡처에서 8초 시작점
export SHADOW_V2_SS=1.5   # 새 클립에서 v2용 8초 시작점(1.5~9.5s)
export ROUTINE_SS=0
export PROFILE_SS=0
export ATTEND_SS=0
export COMMUNITY_SS=0
export TOURN_SS=2.2   # tournament.mp4 앞부분(홈→대진표 전환) 건너뛰고 챔피언+컨페티 구간
