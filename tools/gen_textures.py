#!/usr/bin/env python3
"""Generates every PNG this mod ships.

Nothing here is traced from Minecraft, Just Dire Things or any modpack. Run from the project root:

    python tools/gen_textures.py

Output is deterministic, so CI can check the committed PNGs still match.
"""

from __future__ import annotations

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "cinderflask")
ITEM_DIR = os.path.join(ASSET_DIR, "textures", "item")
GUI_DIR = os.path.join(ASSET_DIR, "textures", "gui")
DOCS_DIR = os.path.join(ROOT, "docs")

# Near-black bodies, molten amber cores, gold rim-light, matching Prominence II's own items.
PALETTE = {
    ".": (0, 0, 0, 0),
    "K": (13, 10, 8, 255),        # outline
    "S": (34, 31, 28, 255),       # blackened steel, shadow
    "s": (94, 84, 71, 255),       # blackened steel, lit
    "G": (122, 84, 26, 255),      # gold, shadow
    "g": (186, 138, 45, 255),     # gold, mid
    "h": (243, 204, 102, 255),    # gold, highlight
    "C": (25, 27, 34, 255),       # cold glass, shadow
    "D": (38, 42, 52, 255),       # cold glass, mid
    "L": (45, 51, 63, 255),       # glass, shadow
    "l": (68, 77, 92, 255),       # glass, mid
    "w": (120, 134, 154, 255),    # glass, rim-light
    # Ember ramp, coldest to hottest. Wide enough that the swirl reads as colour, not brightness.
    "1": (86, 16, 20, 255),       # ember, crimson shadow
    "2": (140, 32, 12, 255),      # ember, deep red
    "3": (190, 62, 12, 255),      # ember, red-orange
    "4": (224, 104, 16, 255),     # ember, orange
    "5": (245, 150, 30, 255),     # ember, amber
    "6": (255, 196, 74, 255),     # ember, gold
    "7": (255, 231, 156, 255),    # ember, pale core
    "o": (198, 76, 15, 255),      # ember, mid (mote + bar)
    "a": (246, 156, 32, 255),     # ember, bright (mote + bar)
    "y": (255, 224, 138, 255),    # ember, core (mote + bar)
}

# Hottest last, so an index walks the ramp upwards.
EMBER_RAMP = "1234567"

# Outer outline columns per row, top to bottom. The centre walks right on the way up, which is
# what makes it lean.
PROFILE = [
    (9, 12, "stopper"),
    (9, 12, "stopper"),
    (9, 12, "neck"),
    (8, 13, "collar"),
    (8, 12, "neck"),
    (8, 12, "neck"),
    (7, 12, "neck"),
    (6, 13, "body"),
    (5, 13, "body"),
    (4, 13, "band"),
    (3, 13, "body"),
    (3, 13, "body"),
    (2, 13, "body"),
    (2, 12, "body"),
    (2, 12, "band"),
    (3, 11, "foot"),
]

# Body rows that hold embers, bottom-first, so a fill level is just a prefix of this list.
EMBER_ROWS = [13, 12, 11, 10]

# Spiral shaping: arms around the pool, bands out from the centre, and how much noise blurs them.
SWIRL_ARMS = 3.0
SWIRL_BANDS = 0.42
SWIRL_NOISE = 0.22

# Rows wide enough for a specular streak.
STREAK_ROWS = {10, 11, 12, 13}


def blank() -> list[list[str]]:
    return [["." for _ in range(16)] for _ in range(16)]


def glass_row(cells: list[str], left: int, right: int, streak: bool) -> None:
    """Dark left wall, mid through the middle, rim-light down the right."""
    for x in range(left + 1, right):
        if x == left + 1:
            cells[x] = "L"
        elif x == right - 1:
            cells[x] = "w"
        else:
            cells[x] = "l"

    # 1px streak, or the glass reads as flat metal.
    if streak and right - left > 5:
        cells[left + 3] = "w"


def metal_row(cells: list[str], left: int, right: int, tone: str) -> None:
    """Banding with rivets picked out, or the steel just reads as a shadow."""
    shadow, lit = ("S", "s") if tone == "steel" else ("G", "g")

    for x in range(left + 1, right):
        cells[x] = shadow if (x - left) % 3 == 0 else lit


def hash_jitter(x: int, y: int) -> float:
    """Deterministic per-pixel noise in [0, 1)."""
    h = (x * 73_856_093) ^ (y * 19_349_663)
    h = (h ^ (h >> 13)) * 1_274_126_177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65_536.0


def pour_embers(grid: list[list[str]], fill: int) -> None:
    """Fills the bottom `fill` body rows with a swirling, dithered ember mass.

    Colour comes from a spiral: an arm term from the angle around the pool centre, a band term from
    the radius, plus noise to break up the banding. The top filled row is biased hot so the fill
    level still reads at a glance in an inventory slot.
    """
    rows = EMBER_ROWS[:fill]
    surface = rows[-1]

    spans = {y: PROFILE[y] for y in rows}
    centre_x = sum((left + right) / 2 for left, right, _ in spans.values()) / len(spans)
    centre_y = sum(rows) / len(rows)

    for y in rows:
        left, right, _ = spans[y]

        for x in range(left + 1, right):
            dx = x - centre_x
            # The pool is much wider than tall, so stretch y or the spiral reads as stripes.
            dy = (y - centre_y) * 2.6

            radius = math.hypot(dx, dy)
            angle = math.atan2(dy, dx) / (2 * math.pi)

            swirl = (angle * SWIRL_ARMS + radius * SWIRL_BANDS + hash_jitter(x, y) * SWIRL_NOISE) % 1.0

            # The spiral picks the colour; heat only nudges it, or a shallow pool blows out white.
            index = int(swirl * (len(EMBER_RAMP) - 2))

            if y == surface:
                # Floored, not just lifted: at one row deep the whole pool is surface, and a dark
                # swirl there reads as empty.
                index = max(index + 1, 3)

            if radius > 3.4:
                index -= 1

            grid[y][x] = EMBER_RAMP[max(0, min(len(EMBER_RAMP) - 1, index))]


def flask(fill: int = 0, mote: bool = False, cold: bool = False) -> list[str]:
    grid = blank()

    for y, (left, right, kind) in enumerate(PROFILE):
        row = grid[y]
        row[left] = "K"
        row[right] = "K"

        if kind == "stopper":
            for x in range(left + 1, right):
                row[x] = "h" if x == right - 1 else "g"
        elif kind == "collar":
            metal_row(row, left, right, "gold")
            row[right - 1] = "h"
        elif kind == "band":
            metal_row(row, left, right, "steel")
        elif kind == "foot":
            for x in range(left + 1, right):
                row[x] = "S"
        else:
            glass_row(row, left, right, streak=y in STREAK_ROWS)

    if fill > 0:
        pour_embers(grid, fill)

    if mote:
        grid[12][7] = "y"
        grid[12][6] = "a"
        grid[12][8] = "a"
        grid[11][7] = "o"
        grid[13][7] = "o"

    if cold:
        chill = {"l": "D", "L": "C", "w": "L", "h": "g", "g": "G"}
        for y, row in enumerate(grid):
            for x, key in enumerate(row):
                if key in chill:
                    grid[y][x] = chill[key]

    return ["".join(row) for row in grid]


def draw(rows: list[str]) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()

    for y, row in enumerate(rows):
        assert len(row) == 16, f"row {y} is {len(row)} px wide, expected 16"
        for x, key in enumerate(row):
            pixels[x, y] = PALETTE[key]

    return image


# Dark slate-violet panel with amber corner accents, matching the containers Prominence II's UI
# pack reskins.
PANEL_W, PANEL_H = 176, 166

EDGE = (18, 18, 24, 255)
PANEL = (43, 43, 56, 255)
BORDER = (74, 74, 92, 255)
WELL = (31, 31, 41, 255)
WELL_DARK = (22, 22, 30, 255)
WELL_LIGHT = (60, 60, 74, 255)
ACCENT = (224, 138, 42, 255)

BAR_W, BAR_H = 104, 6
BAR_V_EMPTY, BAR_V_FULL = 166, 172


def slot(drawing: ImageDraw.ImageDraw, x: int, y: int) -> None:
    """An 18x18 well whose 16x16 interior starts at (x, y), matching Slot coordinates."""
    drawing.rectangle([x - 1, y - 1, x + 16, y + 16], fill=WELL)
    drawing.line([(x - 1, y - 1), (x + 16, y - 1)], fill=WELL_DARK)
    drawing.line([(x - 1, y - 1), (x - 1, y + 16)], fill=WELL_DARK)
    drawing.line([(x - 1, y + 16), (x + 16, y + 16)], fill=WELL_LIGHT)
    drawing.line([(x + 16, y - 1), (x + 16, y + 16)], fill=WELL_LIGHT)


def corner_accents(drawing: ImageDraw.ImageDraw) -> None:
    for cx, cy, dx, dy in ((3, 3, 1, 1), (PANEL_W - 4, 3, -1, 1),
                           (3, PANEL_H - 4, 1, -1), (PANEL_W - 4, PANEL_H - 4, -1, -1)):
        drawing.line([(cx, cy), (cx + dx * 4, cy)], fill=ACCENT)
        drawing.line([(cx, cy), (cx, cy + dy * 4)], fill=ACCENT)


def gui() -> Image.Image:
    image = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    drawing = ImageDraw.Draw(image)

    drawing.rectangle([0, 0, PANEL_W - 1, PANEL_H - 1], fill=PANEL, outline=EDGE)
    drawing.rectangle([2, 2, PANEL_W - 3, PANEL_H - 3], outline=BORDER)
    corner_accents(drawing)

    slot(drawing, 80, 26)

    for row in range(3):
        for column in range(9):
            slot(drawing, 8 + column * 18, 84 + row * 18)

    for column in range(9):
        slot(drawing, 8 + column * 18, 142)

    # Ember bar, empty then full, stacked below the panel for the screen to blit from.
    drawing.rectangle([0, BAR_V_EMPTY, BAR_W - 1, BAR_V_EMPTY + BAR_H - 1], fill=WELL_DARK)
    drawing.line([(0, BAR_V_EMPTY), (BAR_W - 1, BAR_V_EMPTY)], fill=EDGE)

    # Same churn as the flask, so the bar looks like what is inside it.
    for x in range(BAR_W):
        for y in range(BAR_H):
            swirl = (x * SWIRL_BANDS + y * SWIRL_ARMS * 0.5 + hash_jitter(x, y) * SWIRL_NOISE) % 1.0
            index = int(swirl * (len(EMBER_RAMP) - 3)) + (3 if y <= 1 else 1)
            key = EMBER_RAMP[max(0, min(len(EMBER_RAMP) - 1, index))]
            image.putpixel((x, BAR_V_FULL + y), PALETTE[key])

    drawing.line([(0, BAR_V_FULL), (BAR_W - 1, BAR_V_FULL)], fill=PALETTE["7"])

    return image


def icon() -> Image.Image:
    """128x128 mod icon: the full flask on the same panel the GUI uses."""
    image = Image.new("RGBA", (128, 128), PANEL)
    drawing = ImageDraw.Draw(image)
    drawing.rectangle([0, 0, 127, 127], outline=EDGE)
    drawing.rectangle([3, 3, 124, 124], outline=BORDER)

    art = draw(flask(fill=4)).resize((112, 112), Image.NEAREST)
    image.paste(art, (8, 8), art)
    return image


def preview(states: dict[str, Image.Image]) -> Image.Image:
    """The README image, generated with the art so it cannot drift."""
    labels = [
        ("empty_cinderflask", "empty"),
        ("cinderflask", "sparked"),
        ("cinderflask_quarter", "quarter"),
        ("cinderflask_half", "half"),
        ("cinderflask_3quarter", "3/4"),
        ("cinderflask_full", "full"),
    ]

    scale = 80
    gap = 10
    width = len(labels) * (scale + gap) + gap

    image = Image.new("RGBA", (width, scale + gap * 3), PANEL)
    drawing = ImageDraw.Draw(image)

    for index, (key, label) in enumerate(labels):
        art = states[key].resize((scale, scale), Image.NEAREST)
        x = gap + index * (scale + gap)
        image.paste(art, (x, gap), art)
        drawing.text((x + 2, scale + gap + 4), label, fill=(196, 196, 208, 255))

    return image


def main() -> None:
    os.makedirs(ITEM_DIR, exist_ok=True)
    os.makedirs(GUI_DIR, exist_ok=True)
    os.makedirs(DOCS_DIR, exist_ok=True)

    states = {
        "empty_cinderflask": draw(flask(cold=True)),
        "cinderflask": draw(flask(mote=True)),
        "cinderflask_quarter": draw(flask(fill=1)),
        "cinderflask_half": draw(flask(fill=2)),
        "cinderflask_3quarter": draw(flask(fill=3)),
        "cinderflask_full": draw(flask(fill=4)),
    }

    written = {os.path.join(ITEM_DIR, name + ".png"): art for name, art in states.items()}
    written[os.path.join(GUI_DIR, "cinderflask.png")] = gui()
    written[os.path.join(ASSET_DIR, "icon.png")] = icon()
    written[os.path.join(DOCS_DIR, "preview.png")] = preview(states)

    for path, image in written.items():
        image.save(path, optimize=True)
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
