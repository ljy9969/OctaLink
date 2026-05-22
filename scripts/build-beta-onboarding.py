# -*- coding: utf-8 -*-
"""
비공개 테스트 참여 가이드 한 장 PNG 생성 — 카카오 오픈채팅 공유용.

입력: scripts/beta-onboarding/0{1..4}_*.png (4 장, 사용자가 직접 저장)
출력: scripts/out/octalink-beta-onboarding.png (+ .pdf)

레이아웃: 세로 4행, 각 행에 [왼쪽 스크린샷 | 오른쪽 번호+설명].
사진 비율 다양 (브라우저 landscape 3장 + 폰 portrait 1장) → 각 행의 좌측 박스에
Fit 으로 비율 보존하며 letterbox.

실행: python scripts/build-beta-onboarding.py
"""
import os
import sys
from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "scripts", "beta-onboarding")
OUT_DIR = os.path.join(ROOT, "scripts", "out")
os.makedirs(OUT_DIR, exist_ok=True)

# 폰트
FONT_REGULAR = "C:/Windows/Fonts/malgun.ttf"
FONT_BOLD = "C:/Windows/Fonts/malgunbd.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


# 페이지 — 5단계 컴팩트 fit (header 압축으로 세로 단축)
PAGE_W, PAGE_H = 1400, 2520
PAGE_BG = (252, 251, 248)
PANEL_BG = (255, 255, 255)
PANEL_BORDER = (220, 220, 226)
INK = (20, 20, 28)
GRAY = (110, 110, 120)
LIGHT_GRAY = (200, 200, 210)
PRIMARY = (200, 16, 46)
ACCENT_BLUE = (30, 136, 229)

# 행 사이즈 — 5행 적재 + 텍스트 영역 확보용 컴팩트화
ROW_GAP = 28
ROW_PADDING = 22
SHOT_BOX_W = 540   # 620 → 540 — 오른쪽 텍스트 cut-off 회피
SHOT_BOX_H = 360
TEXT_GAP = 32

MARGIN_X = 70
HEADER_H = 235  # 메타 텍스트를 subtitle 우측으로 이동시켜 헤더 압축.
FOOTER_H = 90


# 입력 파일 정의 — (filename, step_title, body_lines)
STEPS = [
    (
        "01_become_tester.png",
        "테스터 초대 수락",
        [
            "카카오 오픈채팅 공지에 첨부된",
            "Google Play 테스터 초대 링크를 누르세요.",
            "",
            "OctaLink 안내 페이지가 열리면",
            "「Become a tester」 버튼 클릭.",
            "* 먼저 운영자에게 Gmail 계정을 알려주셔야",
            "앱 설치 권한이 부여됩니다. *",
        ],
    ),
    (
        "02_tester_confirmed.png",
        "테스터 확정",
        [
            "「You are a tester.」 메시지 확인.",
            "이미 설치되어 있으면 자동 업데이트.",
            "",
            "처음 설치하는 경우",
            "「download it on Google Play」 링크 클릭.",
        ],
    ),
    (
        "03_play_install.png",
        "Play 스토어에서 설치",
        [
            "OctaLink 앱 페이지가 열립니다.",
            "「Install on more devices」 버튼 클릭.",
            "",
            "기기 선택 다이얼로그가 뜨면 본인 폰을 선택.",
        ],
    ),
    (
        "04_install_dialog.png",
        "권한 확인 후 설치",
        [
            "앱이 사용하는 권한 목록을 확인합니다.",
            "(인터넷 / 네트워크 / 자동 시작 등 기본만)",
            "",
            "「Install」 버튼을 누르면 설치 진행.",
            "(30초~1분 소요)",
        ],
    ),
    (
        "05_home_icon.png",
        "앱 실행",
        [
            "핸드폰에 「OctaLink」 아이콘 생성.",
            "탭하여 실행 후 카카오 계정으로 로그인.",
            "",
            "가입 신청서 작성 → 운영자 승인 → 정상 이용.",
            "(승인까지 짧은 대기 시간 발생 가능)",
        ],
    ),
]


def crop_to_content(img, threshold=15, padding=24):
    """배경(좌상단 코너 색)과 다른 픽셀의 bbox 로 자동 크롭. 브라우저 스크린샷처럼 외곽
    여백이 큰 경우 핵심 카드 영역만 확대해 보여줄 수 있음. 전체가 균일색 이면 원본 반환.

    threshold: 픽셀 차이가 이 값 이하면 bg 로 간주 (JPEG noise 무시).
    padding: bbox 주변 추가 여백 (px).
    """
    img_rgb = img.convert("RGB")
    bg = img_rgb.getpixel((5, 5))
    bg_layer = Image.new("RGB", img_rgb.size, bg)
    diff = ImageChops.difference(img_rgb, bg_layer).convert("L")
    mask = diff.point(lambda p: 255 if p > threshold else 0)
    bbox = mask.getbbox()
    if bbox is None:
        return img
    x0, y0, x1, y1 = bbox
    x0 = max(0, x0 - padding)
    y0 = max(0, y0 - padding)
    x1 = min(img_rgb.size[0], x1 + padding)
    y1 = min(img_rgb.size[1], y1 + padding)
    return img_rgb.crop((x0, y0, x1, y1))


def fit_into_box(img, box_w, box_h):
    """원본 비율 유지하면서 box 안에 fit. 남는 공간은 흰 배경."""
    iw, ih = img.size
    scale = min(box_w / iw, box_h / ih)
    new_w = int(iw * scale)
    new_h = int(ih * scale)
    resized = img.resize((new_w, new_h), Image.LANCZOS)
    canvas = Image.new("RGB", (box_w, box_h), PANEL_BG)
    canvas.paste(resized, ((box_w - new_w) // 2, (box_h - new_h) // 2))
    return canvas


def draw_step_badge(draw, x, y, n, size=72):
    """번호 원형 배지 (파란색)."""
    draw.ellipse([x, y, x + size, y + size], fill=ACCENT_BLUE)
    f = font(40, bold=True)
    bbox = draw.textbbox((0, 0), str(n), font=f)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(
        (x + size // 2 - tw // 2, y + size // 2 - th // 2 - bbox[1]),
        str(n), fill=(255, 255, 255), font=f,
    )


def draw_row(canvas, draw, y, idx, shot_img, title, body_lines):
    """한 행 그리기 — 좌측 스크린샷 박스, 우측 번호+제목+본문."""
    row_h = SHOT_BOX_H + ROW_PADDING * 2

    # 패널 배경
    px = MARGIN_X
    py = y
    pw = PAGE_W - 2 * MARGIN_X
    draw.rounded_rectangle(
        [px, py, px + pw, py + row_h],
        radius=20, fill=PANEL_BG, outline=PANEL_BORDER, width=2,
    )

    # 좌측 스크린샷
    shot_x = px + ROW_PADDING
    shot_y = py + ROW_PADDING
    fit = fit_into_box(shot_img, SHOT_BOX_W, SHOT_BOX_H)
    canvas.paste(fit, (shot_x, shot_y))
    # 스크린샷 박스 테두리
    draw.rectangle(
        [shot_x, shot_y, shot_x + SHOT_BOX_W, shot_y + SHOT_BOX_H],
        outline=LIGHT_GRAY, width=1,
    )

    # 우측 텍스트 영역
    tx = shot_x + SHOT_BOX_W + TEXT_GAP
    ty = shot_y

    # 번호 배지
    draw_step_badge(draw, tx, ty, idx)

    # 제목 — 배지 우측
    f_title = font(38, bold=True)
    draw.text((tx + 90, ty + 12), title, fill=INK, font=f_title)

    # 본문 — 배지 아래로
    f_body = font(26)
    body_y = ty + 100
    for i, line in enumerate(body_lines):
        if line == "":
            body_y += 16
            continue
        draw.text((tx, body_y), line, fill=INK if i == 0 else GRAY, font=f_body)
        body_y += 40


def build():
    # 입력 파일 로드
    shots = []
    missing = []
    for fname, _, _ in STEPS:
        path = os.path.join(SRC_DIR, fname)
        if not os.path.exists(path):
            missing.append(fname)
            continue
        # 자동 크롭 — 브라우저 스크린샷의 흰 여백 제거. 폰 풀블리드 스크린샷은 변화 없음.
        raw = Image.open(path).convert("RGB")
        shots.append(crop_to_content(raw))

    if missing:
        print("[ERR] 다음 파일이 없습니다 — scripts/beta-onboarding/ 에 저장 후 다시 실행:")
        for m in missing:
            print(f"  - {m}")
        sys.exit(1)

    print(f"  loaded {len(shots)} screenshots")

    # 캔버스 + 헤더
    canvas = Image.new("RGB", (PAGE_W, PAGE_H), PAGE_BG)
    draw = ImageDraw.Draw(canvas)

    # 헤더 — 브랜드 + 안내
    f_brand = font(72, bold=True)
    draw.text((MARGIN_X, 70), "OctaLink", fill=INK, font=f_brand)
    f_sub = font(36, bold=True)
    draw.text((MARGIN_X, 160), "비공개 테스트 참여 방법", fill=PRIMARY, font=f_sub)
    # meta — subtitle 우측에 right-align. subtitle baseline 과 시각적으로 비슷한 위치(y=170).
    f_meta = font(24)
    meta_text = "아래 5단계만 따라하시면 됩니다. (총 1~2분 소요)"
    meta_bbox = draw.textbbox((0, 0), meta_text, font=f_meta)
    meta_w = meta_bbox[2] - meta_bbox[0]
    draw.text(
        (PAGE_W - MARGIN_X - meta_w, 174),
        meta_text, fill=GRAY, font=f_meta,
    )

    # 헤더 구분선 — subtitle/meta(y≈200 끝) 아래로 충분히 띄움.
    divider_y = 218
    draw.line(
        [MARGIN_X, divider_y, PAGE_W - MARGIN_X, divider_y],
        fill=LIGHT_GRAY, width=2,
    )

    # 5 행 — 헤더 직후부터 top-align (중앙 정렬 시 상단 공백 발생)
    row_h = SHOT_BOX_H + ROW_PADDING * 2
    start_y = HEADER_H + 16

    for i, ((fname, title, body), shot) in enumerate(zip(STEPS, shots)):
        y = start_y + i * (row_h + ROW_GAP)
        draw_row(canvas, draw, y, i + 1, shot, title, body)

    # 푸터
    f_footer = font(22)
    footer_y = PAGE_H - 70
    draw.text(
        (MARGIN_X, footer_y),
        "문의: 카카오 오픈채팅 「팀파시 강남 어플」",
        fill=GRAY, font=f_footer,
    )
    # 우측 정렬: 제작사
    footer_right = "제작: Unbound Apex Systems · BlackCat Strike (이지연)"
    bbox = draw.textbbox((0, 0), footer_right, font=f_footer)
    tw = bbox[2] - bbox[0]
    draw.text(
        (PAGE_W - MARGIN_X - tw, footer_y),
        footer_right, fill=GRAY, font=f_footer,
    )

    # 저장 — PNG 는 원본 크기 (카카오/온라인 공유용)
    out_png = os.path.join(OUT_DIR, "octalink-beta-onboarding.png")
    canvas.save(out_png, "PNG", optimize=True)
    print(f"[OK] PNG saved: {out_png}  ({os.path.getsize(out_png) / 1024:.0f} KB)")

    # PDF 는 A4 portrait 페이지 안에 콘텐츠 중앙 정렬 + scale-to-fit (인쇄용).
    a4_dpi = 150
    a4_w_px = int(8.27 * a4_dpi)   # 1240
    a4_h_px = int(11.69 * a4_dpi)  # 1753
    a4_margin = 60
    cw, ch = canvas.size
    avail_w = a4_w_px - 2 * a4_margin
    avail_h = a4_h_px - 2 * a4_margin
    scale = min(avail_w / cw, avail_h / ch)
    new_w = int(cw * scale)
    new_h = int(ch * scale)
    a4_resized = canvas.resize((new_w, new_h), Image.LANCZOS)
    a4_page = Image.new("RGB", (a4_w_px, a4_h_px), (255, 255, 255))
    a4_page.paste(a4_resized, ((a4_w_px - new_w) // 2, (a4_h_px - new_h) // 2))
    out_pdf = os.path.join(OUT_DIR, "octalink-beta-onboarding.pdf")
    a4_page.save(out_pdf, "PDF", resolution=float(a4_dpi))
    print(f"[OK] PDF saved (A4 fit): {out_pdf}  ({os.path.getsize(out_pdf) / 1024:.0f} KB)")


if __name__ == "__main__":
    try:
        build()
    except Exception as e:
        print(f"FAILED: {e}", file=sys.stderr)
        raise
