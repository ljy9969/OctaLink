"""
Play Store 피처 그래픽 1024x500 — 슬로건 포함 버전.

옵션 1(앱 홈 배너 스타일: 색반전 흰 로고 / 검정 배경) + 슬로건
"개인의 성장, 함께하는 진화"
- "성장" / "진화" 는 Blood 컬러 강조
- "개인의" / "함께하는" 은 약간 흐린 회색으로 빼서 강조어 부각

실행:
    python tools/make-feature-graphic-v2.py

출력:
    app/src/main/feature_graphic_v2.png  (1024x500 RGBA PNG)
"""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
LOGO_SRC = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "logo_teamposse.jpg"
OUT = ROOT / "app" / "src" / "main" / "feature_graphic_v2.png"

# Windows 기본 한글 굵은 고딕 (Pretendard 미설치 환경 대응)
FONT_PATH = "C:/Windows/Fonts/malgunbd.ttf"

CANVAS_W = 1024
CANVAS_H = 500
LOGO_WIDTH_RATIO = 0.55  # 슬로건 공간 확보 위해 옵션1보다 줄임
SLOGAN_FONT_SIZE = 48
LOGO_TO_SLOGAN_GAP = 36  # 로고 하단과 슬로건 사이

BG_COLOR = (0, 0, 0, 255)
DIM_COLOR = (158, 156, 151, 255)   # 약한 회색 (Mist 톤)
ACCENT_COLOR = (200, 16, 46, 255)  # Blood — "성장" / "진화" 강조

SLOGAN_PARTS = [
    ("개인의 ", DIM_COLOR),
    ("성장", ACCENT_COLOR),
    (", 함께하는 ", DIM_COLOR),
    ("진화", ACCENT_COLOR),
]


def main() -> None:
    if not LOGO_SRC.exists():
        raise SystemExit(f"logo not found: {LOGO_SRC}")

    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG_COLOR)

    logo = Image.open(LOGO_SRC).convert("RGB")
    logo = ImageOps.invert(logo)
    target_w = int(CANVAS_W * LOGO_WIDTH_RATIO)
    target_h = int(target_w * logo.height / logo.width)
    logo = logo.resize((target_w, target_h), Image.LANCZOS)

    # 로고 + 슬로건 전체 블록을 세로 중앙 정렬
    font = ImageFont.truetype(FONT_PATH, SLOGAN_FONT_SIZE)
    draw = ImageDraw.Draw(canvas)
    bbox_sample = draw.textbbox((0, 0), "한글", font=font)
    slogan_h = bbox_sample[3] - bbox_sample[1]
    block_h = target_h + LOGO_TO_SLOGAN_GAP + slogan_h
    block_y = (CANVAS_H - block_h) // 2

    logo_x = (CANVAS_W - target_w) // 2
    logo_y = block_y
    canvas.paste(logo, (logo_x, logo_y))

    # 슬로건 가로 폭 측정 → 가로 중앙 정렬
    total_w = 0
    for text, _ in SLOGAN_PARTS:
        b = draw.textbbox((0, 0), text, font=font)
        total_w += b[2] - b[0]
    x = (CANVAS_W - total_w) // 2
    y = logo_y + target_h + LOGO_TO_SLOGAN_GAP
    for text, color in SLOGAN_PARTS:
        draw.text((x, y), text, font=font, fill=color)
        b = draw.textbbox((0, 0), text, font=font)
        x += b[2] - b[0]

    canvas.save(OUT, "PNG", optimize=True)
    print(f"saved {OUT.relative_to(ROOT)} {CANVAS_W}x{CANVAS_H}")


if __name__ == "__main__":
    main()
