"""
Play Store 피처 그래픽 1024x500 — v3.

원본 로고(흰 패널 유지) + 슬로건. Canva 옵션 2와 같은 컨셉이지만 더 타이트한 정렬.

실행:
    python tools/make-feature-graphic-v3.py

출력:
    app/src/main/feature_graphic_v3.png  (1024x500 RGBA PNG)
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
LOGO_SRC = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "logo_teamposse.jpg"
OUT = ROOT / "app" / "src" / "main" / "feature_graphic_v3.png"

FONT_PATH = "C:/Windows/Fonts/malgunbd.ttf"

CANVAS_W = 1024
CANVAS_H = 500
LOGO_WIDTH_RATIO = 0.62      # 패널 자체가 시각 무게 가지므로 v2보다 살짝 큼
SLOGAN_FONT_SIZE = 44
LOGO_TO_SLOGAN_GAP = 24      # 더 타이트하게

BG_COLOR = (0, 0, 0, 255)
DIM_COLOR = (158, 156, 151, 255)   # Mist 톤
ACCENT_COLOR = (200, 16, 46, 255)  # Blood

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

    # 원본 로고 (흰 배경 유지) — option 2 스타일
    logo = Image.open(LOGO_SRC).convert("RGB")
    target_w = int(CANVAS_W * LOGO_WIDTH_RATIO)
    target_h = int(target_w * logo.height / logo.width)
    logo = logo.resize((target_w, target_h), Image.LANCZOS)

    font = ImageFont.truetype(FONT_PATH, SLOGAN_FONT_SIZE)
    draw = ImageDraw.Draw(canvas)
    bbox_sample = draw.textbbox((0, 0), "한글", font=font)
    slogan_h = bbox_sample[3] - bbox_sample[1]

    # 로고+슬로건 블록 세로 중앙
    block_h = target_h + LOGO_TO_SLOGAN_GAP + slogan_h
    block_y = (CANVAS_H - block_h) // 2

    logo_x = (CANVAS_W - target_w) // 2
    logo_y = block_y
    canvas.paste(logo, (logo_x, logo_y))

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
