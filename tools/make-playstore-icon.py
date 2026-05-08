"""
Play Store 512x512 icon generator.

기존 ic_launcher-playstore.png 는 검은 프레임이 두꺼워 192x192 등 축소 시 로고가 작아 보임.
이 스크립트는 흰 배경 + 로고 92% 폭 배치로 로고가 시각적으로 우세한 버전을 생성.

실행:
    python tools/make-playstore-icon.py

출력:
    app/src/main/ic_launcher-playstore.png  (512x512 RGBA PNG)
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
LOGO_SRC = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "logo_teamposse.jpg"
OUT = ROOT / "app" / "src" / "main" / "ic_launcher-playstore.png"

CANVAS_SIZE = 512
LOGO_WIDTH_RATIO = 0.92  # 로고가 캔버스 가로의 92% 차지
BG_COLOR = (255, 255, 255, 255)  # 순백 (Bone 톤보다 깔끔하게)


def main() -> None:
    if not LOGO_SRC.exists():
        raise SystemExit(f"logo not found: {LOGO_SRC}")

    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), BG_COLOR)
    logo = Image.open(LOGO_SRC).convert("RGBA")

    target_w = int(CANVAS_SIZE * LOGO_WIDTH_RATIO)
    target_h = int(target_w * logo.height / logo.width)
    logo_resized = logo.resize((target_w, target_h), Image.LANCZOS)

    x = (CANVAS_SIZE - target_w) // 2
    y = (CANVAS_SIZE - target_h) // 2
    canvas.paste(logo_resized, (x, y), logo_resized)
    canvas.save(OUT, "PNG", optimize=True)
    print(f"saved {OUT.relative_to(ROOT)} {CANVAS_SIZE}x{CANVAS_SIZE}")


if __name__ == "__main__":
    main()
