#!/usr/bin/env python3
"""
split_sprites.py  —  OctaLink fighter sprite sheet splitter.

Sheet layout  : 5 cols × 2 rows
  Row 0 (top)    = male fighters
  Row 1 (bottom) = female fighters
  Cols left→right = feather / light / welter / middle / heavy

Outputs per character  (in OUT_DIR/):
  avatar_body_{g}_{w}.png   full character (all pixels including original belt)
  avatar_belt_{g}_{w}.png   white mask at belt Y zone, transparent outside

At render time (Compose):
  Layer 1  →  avatar_body   (ContentScale.Crop, Alignment.TopCenter)
  Layer 2  →  avatar_belt   colorFilter = ColorFilter.tint(belt.ringColor)   [skip for Belt.UNKNOWN]

HOW TO USE:
  1. pip install Pillow
  2. python tools/split_sprites.py path/to/sheet.png --preview
  3. Open _preview_*.png and check the red overlay aligns with the actual belt area
  4. Adjust BELT_Y_START / BELT_Y_END below, re-run until correct
  5. python tools/split_sprites.py path/to/sheet.png
  6. Copy out/*.png  →  app/src/main/res/drawable-nodpi/
"""

from __future__ import annotations
import argparse, os, sys
from PIL import Image

# ── Layout ────────────────────────────────────────────────────────────────────
COLS = 5
ROWS = 2

# ── Belt zone (pixels from the TOP of each character cell) ────────────────────
# Run with --preview to verify before final extraction.
# Typical pixel-art waist is 58-68 % from top; adjust to match your sprite.
BELT_Y_START = 258   # ← ADJUST: first row of belt pixels (within cell)
BELT_Y_END   = 292   # ← ADJUST: first row BELOW belt (exclusive)

# ── Labels ────────────────────────────────────────────────────────────────────
GENDERS = ["m", "f"]
WEIGHTS = ["feather", "light", "welter", "middle", "heavy"]


def make_col_row_breaks(w: int, h: int):
    """Return pixel boundary lists for even column/row splits."""
    cols = [round(w * i / COLS) for i in range(COLS + 1)]
    rows = [round(h * i / ROWS) for i in range(ROWS + 1)]
    return cols, rows


def whitify_strip(strip: Image.Image) -> Image.Image:
    """Replace every non-transparent pixel with white (preserves alpha)."""
    out = strip.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            *_, a = px[x, y]
            px[x, y] = (255, 255, 255, a) if a > 10 else (0, 0, 0, 0)
    return out


def split(sheet_path: str, out_dir: str, preview: bool) -> None:
    sheet = Image.open(sheet_path).convert("RGBA")
    sw, sh = sheet.size
    col_breaks, row_breaks = make_col_row_breaks(sw, sh)

    print(f"Sheet  {sw}×{sh}")
    print(f"Belt zone  y=[{BELT_Y_START}, {BELT_Y_END})  within each cell")
    os.makedirs(out_dir, exist_ok=True)

    for row, gender in enumerate(GENDERS):
        y0, y1 = row_breaks[row], row_breaks[row + 1]
        cell_h = y1 - y0

        for col, weight in enumerate(WEIGHTS):
            x0, x1 = col_breaks[col], col_breaks[col + 1]
            cell_w = x1 - x0

            cell = sheet.crop((x0, y0, x1, y1))
            tag = f"{gender}_{weight}"

            # Body PNG — full character
            body_name = f"avatar_body_{tag}.png"
            cell.save(os.path.join(out_dir, body_name))

            # Belt mask PNG — white silhouette at belt zone
            by_end = min(BELT_Y_END, cell_h)
            by_start = min(BELT_Y_START, by_end)
            strip = cell.crop((0, by_start, cell_w, by_end))
            white_strip = whitify_strip(strip)
            mask = Image.new("RGBA", cell.size, (0, 0, 0, 0))
            mask.paste(white_strip, (0, by_start))
            mask_name = f"avatar_belt_{tag}.png"
            mask.save(os.path.join(out_dir, mask_name))

            # Preview — red overlay on belt zone
            if preview:
                prev = cell.copy()
                red = Image.new("RGBA", (cell_w, by_end - by_start), (255, 0, 0, 110))
                prev.paste(red, (0, by_start), red)
                prev.save(os.path.join(out_dir, f"_preview_{tag}.png"))

            print(f"  {tag}:  {cell_w}×{cell_h}  →  {body_name}, {mask_name}")

    n = len(GENDERS) * len(WEIGHTS)
    print(f"\n{'Preview' if preview else 'Done'}!  {n * 2} files in ./{out_dir}/")
    if preview:
        print("Check _preview_*.png then re-run without --preview.")
    else:
        print(f"Copy *.png (not _preview) to app/src/main/res/drawable-nodpi/")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Split OctaLink sprite sheet into body + belt mask PNGs")
    ap.add_argument("sheet", help="Path to sprite sheet PNG (1106×861)")
    ap.add_argument("--out", default="out", help="Output directory (default: out/)")
    ap.add_argument("--preview", action="store_true", help="Add red overlay on belt zone for alignment check")
    args = ap.parse_args()

    if not os.path.isfile(args.sheet):
        print(f"Error: file not found: {args.sheet}", file=sys.stderr)
        sys.exit(1)

    split(args.sheet, args.out, args.preview)
