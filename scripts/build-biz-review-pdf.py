# -*- coding: utf-8 -*-
"""
카카오 비즈 검수 재신청용 통합 서비스 화면 PDF 생성.

입력: app/src/main/screenshots/phone_v2/light_theme/*.png (8 장)
출력: scripts/out/octalink-biz-review.pdf (1 page, A3-ish)

처리:
 1. PII 마스킹 — 실명 위치를 흰색/카드색 박스로 덮고 더미 이름 그리기.
 2. C 안 레이아웃 — 8 장을 "가입 동선 / 일상 사용 / 부가" 3 그룹으로 합성.
 3. 각 화면 옆/아래 짧은 설명 + 그룹 라벨 + 화살표 (이용 동선 시각화).

실행: python scripts/build-biz-review-pdf.py
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "app", "src", "main", "screenshots", "phone_v2", "light_theme")
OUT_DIR = os.path.join(ROOT, "scripts", "out")
os.makedirs(OUT_DIR, exist_ok=True)

# ─── 폰트 ──────────────────────────────────────────────
FONT_REGULAR = "C:/Windows/Fonts/malgun.ttf"
FONT_BOLD = "C:/Windows/Fonts/malgunbd.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


# ─── 마스킹 작업 정의 ────────────────────────────────────
# 좌표는 원본 1080×2400 기준. (x1, y1, x2, y2, fill_color, text, text_color, text_size)
# fill_color 는 박스가 덮을 배경색에 맞춤 (자연스럽게).
MASKS = {
    "회원가입 폼.png": [
        # 이름 입력칸 안 "이지연" — 입력칸 흰색.
        (60, 555, 320, 660, (255, 255, 255), "홍길동", (140, 140, 150), 50),
    ],
    "홈 대시보드.png": [
        # 오늘의 커리큘럼 카드 우측 meta "관장 김파시 · 스트라이킹" — 흰 카드.
        # 잽 거리감 라인은 y≥1395 라 하단 y=1380 까지 안전.
        (580, 1280, 1050, 1380, (255, 255, 255), "관장 OOO · 스트라이킹", (60, 60, 70), 34),
    ],
    "커뮤니티.png": [
        # 첫째 카드 작성자 "이지연" (밑줄 underline 까지 포함)
        (830, 550, 1020, 630, (255, 255, 255), "홍길동", (40, 40, 40), 38),
        # 둘째 카드 작성자 "이지연"
        (830, 1670, 1020, 1750, (255, 255, 255), "홍길동", (40, 40, 40), 38),
    ],
    "프로필.png": [
        # subtitle "이지연 · 화이트 벨트 · 1개월 차" 우상단 — 페이지 배경 (연회색)
        (560, 190, 1050, 245, (245, 245, 247), "홍길동 · 화이트 벨트 · 1개월 차", (130, 130, 140), 28),
        # 회원 카드 본문 이름 "이지연" — 흰 카드. "이" 첫 자가 x=475 부터라 x=455 까지 확장.
        (455, 380, 730, 470, (255, 255, 255), "홍길동", (40, 40, 40), 60),
    ],
}


def apply_masks(img, mask_list):
    """이미지 위에 마스크 박스 + 더미 텍스트를 그려 PII 제거."""
    draw = ImageDraw.Draw(img)
    for (x1, y1, x2, y2, fill, txt, tcolor, tsize) in mask_list:
        draw.rectangle([x1, y1, x2, y2], fill=fill)
        f = font(tsize)
        # 텍스트 중앙 정렬
        bbox = draw.textbbox((0, 0), txt, font=f)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
        cx = (x1 + x2) // 2 - tw // 2
        cy = (y1 + y2) // 2 - th // 2 - bbox[1]
        draw.text((cx, cy), txt, fill=tcolor, font=f)
    return img


def load_and_mask(filename):
    src = os.path.join(SRC_DIR, filename)
    img = Image.open(src).convert("RGB")
    if filename in MASKS:
        img = apply_masks(img, MASKS[filename])
    return img


# ─── PDF 합성 ──────────────────────────────────────────
# 페이지: 가로 2400 × 세로 3200 (디지털 뷰어 친화, A3 비율 근사)
PAGE_W, PAGE_H = 2400, 3200
PAGE_BG = (252, 251, 248)   # 살짝 따뜻한 흰색
SECTION_BG = (245, 245, 247)
PRIMARY = (200, 16, 46)
INK = (20, 20, 28)
GRAY = (110, 110, 120)
LIGHT_GRAY = (200, 200, 210)

# 각 phone shot 의 표시 크기
SHOT_W = 360
SHOT_H = SHOT_W * 20 // 9   # 800
SHOT_GAP = 40

CAPTION_H = 90
SECTION_LABEL_H = 60


def fit_phone(img):
    """원본을 SHOT_W × SHOT_H 로 리사이즈."""
    return img.resize((SHOT_W, SHOT_H), Image.LANCZOS)


def draw_shot(canvas, draw, shot_img, x, y, idx, caption_line1, caption_line2=None):
    """phone shot + 번호 배지 + 캡션 한두 줄을 합성."""
    # 약한 그림자 효과 — 박스 한 줄
    draw.rectangle(
        [x - 2, y - 2, x + SHOT_W + 2, y + SHOT_H + 2],
        outline=LIGHT_GRAY, width=2,
    )
    canvas.paste(shot_img, (x, y))
    # 번호 배지 (왼쪽 상단)
    badge_size = 56
    bx, by = x - 8, y - 8
    draw.ellipse([bx, by, bx + badge_size, by + badge_size], fill=PRIMARY)
    f_badge = font(30, bold=True)
    bbox = draw.textbbox((0, 0), str(idx), font=f_badge)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(
        (bx + badge_size // 2 - tw // 2, by + badge_size // 2 - th // 2 - bbox[1]),
        str(idx), fill=(255, 255, 255), font=f_badge,
    )
    # 캡션 (shot 아래)
    f_cap_bold = font(26, bold=True)
    f_cap = font(22)
    cap_y = y + SHOT_H + 14
    draw.text((x, cap_y), caption_line1, fill=INK, font=f_cap_bold)
    if caption_line2:
        draw.text((x, cap_y + 32), caption_line2, fill=GRAY, font=f_cap)


def draw_arrow(draw, x, y_center, length=70):
    """수평 화살표 (한 shot 옆에서 다음 shot 으로)."""
    y = y_center
    # 본체 라인
    draw.line([x, y, x + length - 20, y], fill=PRIMARY, width=6)
    # 화살촉
    head = [(x + length - 20, y - 14), (x + length, y), (x + length - 20, y + 14)]
    draw.polygon(head, fill=PRIMARY)


def draw_section_header(draw, x, y, label, w):
    """[가입 동선] 같은 섹션 헤더 — 좌측 색 막대 + 라벨."""
    bar_w = 12
    draw.rectangle([x, y, x + bar_w, y + SECTION_LABEL_H], fill=PRIMARY)
    f = font(36, bold=True)
    draw.text((x + bar_w + 20, y + 8), label, fill=INK, font=f)


def build():
    # ─── 1. 모든 이미지 로드 + 마스킹 + 리사이즈 ─────────────────
    files_in_flow = [
        # (filename, caption_line1, caption_line2)
        ("카카오 로그인.png",       "① 카카오 로그인",     "체육관 회원 진입점"),
        ("회원가입 폼.png",         "② 회원가입 폼",       "카카오 동의 정보 자동 prefill"),
        ("홈 대시보드.png",         "③ 홈 대시보드",       "활성도·미션·대진표·코멘트"),
        ("출석 체크인.png",         "④ 출석 체크인",       "수업 시간대 셀프 체크인"),
        ("커뮤니티.png",            "⑤ 커뮤니티",          "기록·팁·공지·이미지·영상"),
        ("토너먼트.png",            "⑥ 토너먼트 대진표",   "체급별 추첨 + 매치 결과"),
        ("프로필.png",              "⑦ 프로필 + 스킬 차트", "6축 평가 + 코멘트 + 전적"),
        ("체육관 정보.png",         "⑧ 체육관 정보",       "주소·전화·운영시간·소셜"),
    ]
    shots = []
    for fname, c1, c2 in files_in_flow:
        masked = load_and_mask(fname)
        shots.append((fit_phone(masked), c1, c2))
    print(f"  loaded + masked {len(shots)} shots")

    # ─── 2. 캔버스 생성 + 타이틀 ─────────────────────────────
    canvas = Image.new("RGB", (PAGE_W, PAGE_H), PAGE_BG)
    draw = ImageDraw.Draw(canvas)

    # 헤더 (브랜드 + 부제목)
    margin = 80
    title_y = margin
    f_title = font(80, bold=True)
    draw.text((margin, title_y), "OctaLink", fill=INK, font=f_title)
    f_sub = font(34)
    draw.text((margin, title_y + 90), "MMA 체육관 회원 앱 — 가입부터 일상 사용까지 이용 동선",
              fill=GRAY, font=f_sub)
    # 우측: 운영 정보
    f_meta = font(24)
    meta_lines = [
        "운영: Team Posse Striking 강남점 / 제작: Unbound Apex Systems",
        "Android · Kakao OAuth · 카카오 동의 항목 사용처: 회원 정보 prefill + 캐릭터 자동 부여",
    ]
    for i, t in enumerate(meta_lines):
        bbox = draw.textbbox((0, 0), t, font=f_meta)
        tw = bbox[2] - bbox[0]
        draw.text((PAGE_W - margin - tw, title_y + 20 + i * 36), t, fill=GRAY, font=f_meta)

    # 헤더 아래 구분선
    sep_y = title_y + 160
    draw.line([margin, sep_y, PAGE_W - margin, sep_y], fill=LIGHT_GRAY, width=2)

    # ─── 3. 섹션 1: 가입 동선 (#1, #2, #3) ───────────────────
    sec1_y = sep_y + 50
    draw_section_header(draw, margin, sec1_y, "가입 동선", PAGE_W - 2 * margin)
    sec1_shot_y = sec1_y + SECTION_LABEL_H + 30
    # 3 개 shot, 가운데 정렬, 사이에 화살표
    arrow_gap = 90
    sec1_total_w = 3 * SHOT_W + 2 * arrow_gap
    sec1_x_start = (PAGE_W - sec1_total_w) // 2
    for i in range(3):
        x = sec1_x_start + i * (SHOT_W + arrow_gap)
        draw_shot(canvas, draw, shots[i][0], x, sec1_shot_y, i + 1, shots[i][1], shots[i][2])
        if i < 2:
            ax = x + SHOT_W + 10
            ay = sec1_shot_y + SHOT_H // 2
            draw_arrow(draw, ax, ay, length=arrow_gap - 20)

    # ─── 4. 섹션 2: 일상 사용 (#4 출석, #5 커뮤니티, #6 토너먼트, #7 프로필) ───
    sec2_y = sec1_shot_y + SHOT_H + CAPTION_H + 70
    draw_section_header(draw, margin, sec2_y, "일상 사용", PAGE_W - 2 * margin)
    sec2_shot_y = sec2_y + SECTION_LABEL_H + 30
    # 4 개 shot, 균등 분배
    sec2_total_w = 4 * SHOT_W + 3 * SHOT_GAP
    sec2_x_start = (PAGE_W - sec2_total_w) // 2
    for i in range(4):
        x = sec2_x_start + i * (SHOT_W + SHOT_GAP)
        idx = 3 + i  # shots[3..6] = 출석/커뮤니티/토너먼트/프로필
        draw_shot(canvas, draw, shots[idx][0], x, sec2_shot_y, idx + 1,
                  shots[idx][1], shots[idx][2])

    # ─── 5. 섹션 3: 부가 (#8 체육관 정보) ──────────────────────
    sec3_y = sec2_shot_y + SHOT_H + CAPTION_H + 70
    draw_section_header(draw, margin, sec3_y, "부가 정보", PAGE_W - 2 * margin)
    sec3_shot_y = sec3_y + SECTION_LABEL_H + 30
    # 단일 shot — 좌측에 두고 우측에 설명 텍스트 추가
    x = margin + 40
    draw_shot(canvas, draw, shots[7][0], x, sec3_shot_y, 8, shots[7][1], shots[7][2])
    # 우측 설명 박스
    tx = x + SHOT_W + 100
    ty = sec3_shot_y + 40
    f_h = font(36, bold=True)
    f_b = font(28)
    draw.text((tx, ty), "실제 도장 연계 운영 정보", fill=INK, font=f_h)
    info_lines = [
        "• 주소: 네이버 지도 연동 (탭 → 길찾기)",
        "• 전화: 다이얼 직접 연결",
        "• 운영시간: 평일 6 슬롯 / 토 PT 전용 / 일·공휴일 휴무",
        "• 소셜: 인스타그램 / 네이버 블로그 진입점",
        "",
        "↳ Team Posse Striking 강남점 회원 전용",
        "    체육관 자체 운영 정보를 앱 내에서 확인 가능",
    ]
    for i, t in enumerate(info_lines):
        draw.text((tx, ty + 60 + i * 44), t, fill=GRAY if i > 0 else INK, font=f_b)

    # ─── 6. 푸터 ─────────────────────────────────────────
    foot_y = PAGE_H - 80
    f_foot = font(20)
    draw.text((margin, foot_y), "본 문서는 카카오 비즈니스 정보 심사 재신청용입니다. 실명 등 개인정보는 모두 마스킹 처리되었습니다.",
              fill=GRAY, font=f_foot)

    # ─── 7. PDF 저장 ─────────────────────────────────────
    out_path = os.path.join(OUT_DIR, "octalink-biz-review.pdf")
    canvas.save(out_path, "PDF", resolution=150.0, quality=85, optimize=True)
    size_mb = os.path.getsize(out_path) / 1024 / 1024
    print(f"\n[OK] PDF saved: {out_path}  ({size_mb:.2f} MB)")
    # 미리보기용 PNG 도 같이 저장
    preview = os.path.join(OUT_DIR, "octalink-biz-review.png")
    canvas.thumbnail((1600, 2200))
    canvas.save(preview, "PNG", optimize=True)
    print(f"  preview: {preview}")


if __name__ == "__main__":
    try:
        build()
    except Exception as e:
        print(f"FAILED: {e}", file=sys.stderr)
        raise
