"""
Play Store 피처 그래픽 1024x500 생성기.

검은 배경 (#0B0B0F Ink) + 흰색 로고 (원본 JPG 색반전).

실행:
    python tools/make-feature-graphic.py

출력:
    app/src/main/feature_graphic.png  (1024x500 RGBA PNG)
"""
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parent.parent
LOGO_SRC = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "logo_teamposse.jpg"
OUT = ROOT / "app" / "src" / "main" / "feature_graphic.png"

CANVAS_W = 1024
CANVAS_H = 500
LOGO_WIDTH_RATIO = 0.70  # 로고가 캔버스 가로의 70% 차지 (양옆 여백 15%)
BG_COLOR = (0, 0, 0, 255)  # 순검정 — 색반전 로고의 배경(JPG의 흰색→검정)과 정확히 일치해야 경계선 안 생김


def main() -> None:
    if not LOGO_SRC.exists():
        raise SystemExit(f"logo not found: {LOGO_SRC}")

    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG_COLOR)
    logo = Image.open(LOGO_SRC).convert("RGB")
    # 원본은 흰 배경 + 검은 로고 → 색반전하면 검은 배경 + 흰 로고
    # 검은 영역이 캔버스 색과 같아서 자연스럽게 합성됨
    logo = ImageOps.invert(logo)

    target_w = int(CANVAS_W * LOGO_WIDTH_RATIO)
    target_h = int(target_w * logo.height / logo.width)
    logo_resized = logo.resize((target_w, target_h), Image.LANCZOS)

    x = (CANVAS_W - target_w) // 2
    y = (CANVAS_H - target_h) // 2
    canvas.paste(logo_resized, (x, y))
    canvas.save(OUT, "PNG", optimize=True)
    print(f"saved {OUT.relative_to(ROOT)} {CANVAS_W}x{CANVAS_H}")


if __name__ == "__main__":
    main()
