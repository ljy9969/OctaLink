#!/usr/bin/env python3
"""
split_sprites.py  —  OctaLink fighter sprite sheet splitter.

Sheet layout  : 5 cols × 2 rows
  Row 0 (top)    = male fighters     (Featherweight → Heavyweight)
  Row 1 (bottom) = female fighters   (Featherweight → Heavyweight)

Outputs per character  (in OUT_DIR/):
  {g}_{w}.png   캐릭터 본체 PNG — 균일 정사각, 캐릭터 중앙 정렬, 신체 잘림 없음

처리 로직:
  1. 시트를 5×2 균등 분할
  2. 각 셀 상단 라벨 영역 제외 (`LABEL_HEIGHT` px 잘라냄)
  3. 셀 중앙 픽셀에서 시작해 flood-fill 로 메인 캐릭터 연결성분만 추출
     (이웃 셀에서 살짝 넘어온 픽셀이 bbox 를 오염시키는 것 방지)
  4. 모든 10개 캐릭터 bbox 의 max(width, height) 로 정사각 캔버스 결정
  5. 각 캐릭터를 캔버스 가운데(가로) + 하단 정렬(세로, 발이 바닥)
  6. 원본 배경색 그대로 유지 (AvatarTile 원형 클립 시 자연스러움)

HOW TO USE:
  1. pip install Pillow
  2. python tools/split_sprites.py path/to/sheet.png
  3. Copy out/*.png  →  app/src/main/res/drawable-nodpi/

벨트 색은 카드 좌측 스트라이프로만 표현. 캐릭터 PNG 에 런타임 색 변형 없음.
"""

from __future__ import annotations
import argparse, os, sys
from collections import deque
from PIL import Image

# ── Layout ────────────────────────────────────────────────────────────────────
COLS = 5
ROWS = 2

# 각 셀 상단의 라벨 ("1. Male Featherweight" 등) 높이
LABEL_HEIGHT = 70

# 캐릭터 픽셀 vs 배경 판정 임계값 (R+G+B 합이 이 값 이하면 캐릭터)
BG_THRESHOLD = 240 * 3 - 10  # ~710

# ── Labels ────────────────────────────────────────────────────────────────────
GENDERS = ["m", "f"]
WEIGHTS = ["feather", "light", "welter", "middle", "heavy"]


def make_col_row_breaks(w: int, h: int):
    cols = [round(w * i / COLS) for i in range(COLS + 1)]
    rows = [round(h * i / ROWS) for i in range(ROWS + 1)]
    return cols, rows


def is_character_pixel(px) -> bool:
    """RGB 픽셀이 배경(거의 흰) 이 아니면 True"""
    r, g, b = px[:3]
    return (r + g + b) < BG_THRESHOLD


def flood_fill_bbox(cell: Image.Image) -> tuple[int, int, int, int]:
    """
    셀 가운데 부근에서 시작해 BFS flood-fill 로 메인 캐릭터 연결성분의 bbox 를 반환.
    시작점이 배경이면 가운데에서 나선형으로 가까운 캐릭터 픽셀을 찾음.
    이웃 셀에서 넘어온 외딴 픽셀은 무시.
    """
    px = cell.load()
    w, h = cell.size
    cx, cy = w // 2, h // 2

    # 시작점 찾기 — 중앙에서 점차 반경을 늘려가며 캐릭터 픽셀 검색
    start = None
    for r in range(0, max(w, h) // 2, 2):
        for dy in range(-r, r + 1, 2):
            for dx in range(-r, r + 1, 2):
                if abs(dx) != r and abs(dy) != r:  # ring only
                    continue
                x, y = cx + dx, cy + dy
                if 0 <= x < w and 0 <= y < h and is_character_pixel(px[x, y]):
                    start = (x, y)
                    break
            if start: break
        if start: break

    if start is None:
        return (0, 0, w, h)

    visited = bytearray(w * h)
    q = deque([start])
    visited[start[1] * w + start[0]] = 1
    left, top, right, bottom = w, h, 0, 0

    while q:
        x, y = q.popleft()
        if x < left: left = x
        if x > right: right = x
        if y < top: top = y
        if y > bottom: bottom = y
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h:
                idx = ny * w + nx
                if not visited[idx] and is_character_pixel(px[nx, ny]):
                    visited[idx] = 1
                    q.append((nx, ny))

    return (left, top, right + 1, bottom + 1)


def split(sheet_path: str, out_dir: str) -> None:
    sheet = Image.open(sheet_path).convert("RGB")
    sw, sh = sheet.size
    col_breaks, row_breaks = make_col_row_breaks(sw, sh)

    print(f"Sheet  {sw}x{sh}")
    os.makedirs(out_dir, exist_ok=True)

    # 1차 — bbox 검출 + 캐릭터 crop
    cells = []  # (tag, cropped_character_image)
    for row, gender in enumerate(GENDERS):
        y0, y1 = row_breaks[row], row_breaks[row + 1]
        for col, weight in enumerate(WEIGHTS):
            x0, x1 = col_breaks[col], col_breaks[col + 1]
            cell = sheet.crop((x0, y0, x1, y1))
            cell_no_label = cell.crop((0, LABEL_HEIGHT, cell.width, cell.height))
            bbox = flood_fill_bbox(cell_no_label)
            char = cell_no_label.crop(bbox)
            tag = f"{gender}_{weight}"
            cells.append((tag, char))
            print(f"  {tag}: bbox {char.size}")

    # 2차 — 균일 정사각 캔버스에 중앙(가로) + 하단(세로) 배치
    max_w = max(c.width for _, c in cells)
    max_h = max(c.height for _, c in cells)
    canvas_size = int(max(max_w, max_h) * 1.08)  # 8% 여유

    print(f"\nMax char  {max_w}x{max_h}  ->  canvas {canvas_size}x{canvas_size}")

    bg = sheet.getpixel((0, 0))
    print(f"Background  {bg}")

    for tag, char in cells:
        canvas = Image.new("RGB", (canvas_size, canvas_size), bg)
        cx = (canvas_size - char.width) // 2
        cy = canvas_size - char.height - int(canvas_size * 0.03)  # 하단 3% 여백
        canvas.paste(char, (cx, cy))
        canvas.save(os.path.join(out_dir, f"{tag}.png"))
        print(f"  {tag}.png  saved")

    print(f"\nDone!  10 files in ./{out_dir}/")
    print(f"Copy *.png to app/src/main/res/drawable-nodpi/")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Split OctaLink sprite sheet into 10 body PNGs")
    ap.add_argument("sheet", help="Path to sprite sheet PNG")
    ap.add_argument("--out", default="out", help="Output directory (default: out/)")
    args = ap.parse_args()

    if not os.path.isfile(args.sheet):
        print(f"Error: file not found: {args.sheet}", file=sys.stderr)
        sys.exit(1)

    split(args.sheet, args.out)
