"""
운영자 손제작 마스터에서 OctaLink 아이콘 파생 자산을 일괄 생성.

소스 마스터 (모두 운영자 손제작, 디자인 절대 변경 X):
  app/src/main/res/drawable-nodpi/mark_octalink.jpg   (1040x992, JPG)
  app/src/main/feature_graphic2.png                    (1024x500, 워드마크+슬로건)

생성 자산 (단순 리사이즈/샤프닝/배경 합성만, 디자인 손대지 않음):
  app/src/main/res/drawable-nodpi/mark_octalink.png    (1024x1024, 투명 배경 클린업)
  app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.webp  (5종, 어댑티브 전경, 투명 + 안전여백)
  app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.webp             (5종, 레거시 정사각, BONE 배경)
  app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.webp       (5종, 원형, BONE 배경)

실행:
    python tools/make-octalink-logo.py

후처리:
    python tools/make-playstore-icon.py    # ic_launcher-playstore.png 재생성 (mark_octalink.png 활용)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# === 팔레트 ===============================================================
BONE = (0xF5, 0xF2, 0xEC, 255)

# === 경로 =================================================================
ROOT = Path(__file__).resolve().parent.parent
SRC_MARK = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "mark_octalink.jpg"
OUT_MARK_PNG = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "mark_octalink.png"
RES = ROOT / "app" / "src" / "main" / "res"

LAUNCHER_DENSITIES = {
    "mdpi":    108,
    "hdpi":    162,
    "xhdpi":   216,
    "xxhdpi":  324,
    "xxxhdpi": 432,
}

MARK_MASTER_SIZE = 1024


def jpg_to_transparent_rgba(jpg_path: Path) -> Image.Image:
    """JPG (흰 배경 + 검은 마크) → RGBA (투명 배경 + 검은 마크).

    JPG 압축 노이즈로 "흰색"이 정확히 255가 아니라 240~254 범위로 흩어짐 → 단순
    `alpha = 255 - gray` 식은 합성 시 얇은 노이즈가 BONE 배경 위에 떠 보임.

    두 임계값 램프로 해결:
      - gray > HI (220): 완전 투명 (JPG 노이즈 + 클린 흰색)
      - gray < LO (100): 완전 불투명 (마크 본체)
      - 그 사이: 선형 안티앨리어스
    """
    img = Image.open(jpg_path).convert("RGB")
    arr = np.array(img, dtype=np.float32)
    gray = arr.mean(axis=2)

    HI = 220.0
    LO = 100.0
    alpha = np.clip((HI - gray) / (HI - LO), 0.0, 1.0) * 255.0
    alpha = alpha.astype(np.uint8)

    rgba = np.zeros((arr.shape[0], arr.shape[1], 4), dtype=np.uint8)
    rgba[..., 3] = alpha   # RGB는 0,0,0 (검정)으로 둠
    return Image.fromarray(rgba, "RGBA")


def tight_crop(rgba: Image.Image) -> Image.Image:
    """투명 픽셀 제외한 마크 bounding box로 타이트 크롭."""
    bbox = rgba.getbbox()
    return rgba.crop(bbox) if bbox else rgba


def fit_centered_square(mark: Image.Image, canvas_size: int, fill_ratio: float, bg=(0, 0, 0, 0)) -> Image.Image:
    """마크를 정사각 캔버스에 fill_ratio 크기로 중앙 배치 (LANCZOS + UnsharpMask).

    fill_ratio = 마크가 캔버스 단변에서 차지하는 비율. 0.55 = 어댑티브 안전여백, 0.70 = 레거시.
    """
    target = int(canvas_size * fill_ratio)
    w, h = mark.size
    if w >= h:
        new_w = target
        new_h = max(1, int(target * h / w))
    else:
        new_h = target
        new_w = max(1, int(target * w / h))
    resized = mark.resize((new_w, new_h), Image.LANCZOS)
    # 다운스케일 시 부드러워지는 가장자리 살짝 살림
    resized = resized.filter(ImageFilter.UnsharpMask(radius=1.0, percent=60, threshold=2))

    canvas = Image.new("RGBA", (canvas_size, canvas_size), bg)
    canvas.paste(resized, ((canvas_size - new_w) // 2, (canvas_size - new_h) // 2), resized)
    return canvas


def render_mark_master(mark: Image.Image) -> None:
    """투명 배경 + 검은 마크, 1024x1024 정사각으로 보존 (다운스트림 LOGO_SRC)."""
    canvas = fit_centered_square(mark, MARK_MASTER_SIZE, fill_ratio=0.85)
    canvas.save(OUT_MARK_PNG, "PNG", optimize=True)
    print(f"  saved {OUT_MARK_PNG.relative_to(ROOT)} {MARK_MASTER_SIZE}x{MARK_MASTER_SIZE}")


def render_launcher_icons(mark: Image.Image) -> None:
    """5밀도 × 3종 (foreground / legacy / round) webp 생성."""
    for density, size in LAUNCHER_DENSITIES.items():
        out_dir = RES / f"mipmap-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)

        # foreground — 어댑티브 전경, 투명 + 안전여백 (66/108 = 0.61, 좀 더 보수적으로 0.55)
        fg = fit_centered_square(mark, size, fill_ratio=0.55, bg=(0, 0, 0, 0))
        fg.save(out_dir / "ic_launcher_foreground.webp", "WEBP", quality=100, method=6)

        # legacy 정사각 — BONE 배경 + 마크 70%
        legacy = fit_centered_square(mark, size, fill_ratio=0.70, bg=BONE)
        legacy.convert("RGB").save(out_dir / "ic_launcher.webp", "WEBP", quality=100, method=6)

        # round — BONE 원형 + 마크 70%
        circle_bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(circle_bg).ellipse([0, 0, size - 1, size - 1], fill=BONE)
        legacy_alpha = fit_centered_square(mark, size, fill_ratio=0.70, bg=(0, 0, 0, 0))
        round_img = Image.alpha_composite(circle_bg, legacy_alpha)
        round_img.convert("RGB").save(out_dir / "ic_launcher_round.webp", "WEBP", quality=100, method=6)

        print(f"  saved mipmap-{density}/{{ic_launcher_foreground, ic_launcher, ic_launcher_round}}.webp ({size}x{size})")


def main() -> None:
    if not SRC_MARK.exists():
        raise SystemExit(f"source mark not found: {SRC_MARK}")

    print(f"[1/3] Load mark master: {SRC_MARK.relative_to(ROOT)}")
    mark_rgba = jpg_to_transparent_rgba(SRC_MARK)
    mark_tight = tight_crop(mark_rgba)
    print(f"  source: {Image.open(SRC_MARK).size}, tight crop: {mark_tight.size}")

    print("[2/3] Render mark master (mark_octalink.png, transparent)")
    render_mark_master(mark_tight)

    print("[3/3] Render launcher icons (5 densities x 3 variants)")
    render_launcher_icons(mark_tight)

    print("\nDone. Next:")
    print("  python tools/make-playstore-icon.py    # mark_octalink.png 활용해서 512x512 재생성")


if __name__ == "__main__":
    main()
