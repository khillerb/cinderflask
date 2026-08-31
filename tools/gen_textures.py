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
    "o": (198, 76, 15, 255),      # ember, mid (bar only)
    "a": (246, 156, 32, 255),     # ember, bright (bar only)
    "y": (255, 224, 138, 255),    # ember, core (bar only)

    # The liquid layer is drawn in greyscale and multiplied by the brew's colour at render time,
    # so white is "full colour here" and the darker steps are what give the pool its depth.
    "W": (255, 255, 255, 255),
    "M": (206, 206, 206, 255),
    "D": (162, 162, 162, 255),

    # Sump: what a brew turns into. Bilious, and deliberately unappetising.
    "m": (58, 62, 38, 255),
    "n": (86, 92, 52, 255),
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


def liquid(level: int) -> list[str]:
    """The liquid alone, greyscale, on a transparent field. Layer 1 of the item model.

    Only the body rows are filled, bottom up. The surface reads brightest and it darkens with depth,
    so a tinted brew still looks like a pool rather than a flat block of colour.
    """
    grid = blank()
    rows = EMBER_ROWS[:level]
    surface = rows[-1] if rows else -1

    for y in rows:
        left, right, _ = PROFILE[y]
        for x in range(left + 1, right):
            if y == surface:
                shade = "W"
            elif y == EMBER_ROWS[0]:
                shade = "D"
            else:
                shade = "M"
            grid[y][x] = shade

    return ["".join(row) for row in grid]


def shell() -> list[str]:
    """The vessel with nothing in it. Layer 0, never tinted."""
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

    return ["".join(row) for row in grid]


# Where the mote drifts, frame by frame: a small orbit inside the body, keeping clear of the steel
# band on row 9 and the foot on row 14, which both muddle it. It draws over the liquid rather than
# above it, so a full flask reads as a spirit swimming in the brew.
MOTE_PATH = [(6, 11), (7, 10), (8, 11), (7, 12)]


def mote_frames() -> Image.Image:
    """A two-pixel spirit with a halo, four frames tall, greyscale so it takes the flask's colour."""
    sheet = Image.new("RGBA", (16, 16 * len(MOTE_PATH)), (0, 0, 0, 0))

    for frame, (x, y) in enumerate(MOTE_PATH):
        top = frame * 16

        # Two pixels of core, so it has a direction as it moves rather than pulsing on the spot.
        sheet.putpixel((x, top + y), (255, 255, 255, 255))
        if x + 1 < 16:
            sheet.putpixel((x + 1, top + y), (226, 226, 226, 255))

        for hx, hy in ((x - 1, y), (x + 2, y), (x, y - 1), (x, y + 1), (x + 1, y - 1), (x + 1, y + 1)):
            if 0 <= hx < 16 and 0 <= hy < 16:
                sheet.putpixel((hx, top + hy), (200, 200, 200, 150))

    return sheet


SUMP = [
    "................",
    "................",
    "................",
    "....KKKKKKK.....",
    "...KmmmmmmmK....",
    "..KmnnmmnnmmK...",
    "..KmnKmmnnmmmK..",
    "..KmmnnmKmnnmK..",
    "..KmnnmmnnKmmK..",
    "..KmmnnmnnmmmK..",
    "...KmnnKmnnmK...",
    "...KmmnnmmmmK...",
    "....KmmnnnmK....",
    ".....KKKKKK.....",
    "................",
    "................",
]


def sump() -> Image.Image:
    """A jar of what a brew becomes. Deliberately unappetising."""
    return draw(SUMP)


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

    art = draw(shell()).resize((112, 112), Image.NEAREST)
    tinted = draw(liquid(4)).resize((112, 112), Image.NEAREST)
    tinted = Image.merge("RGBA", (*[
        channel.point(lambda v, m=multiplier: int(v * m / 255))
        for channel, multiplier in zip(tinted.split()[:3], (0xD9, 0x82, 0x2B))
    ], tinted.split()[3]))

    image.paste(art, (8, 8), art)
    image.paste(tinted, (8, 8), tinted)
    return image


# Spawn-egg colours: an allay, a blaze, a bee and a warden. What the mote is seeded from.
MOTE_COLOURS = [0x56CCF2, 0xF6B201, 0xEDC343, 0x0F4649]

BREW_COLOURS = {
    "choleric": 0xD9822B,
    "melancholic": 0x4A2C6B,
    "sanguine": 0xA31E28,
    "phlegmatic": 0x8FA88C,
}


def tint(sprite: Image.Image, colour: int) -> Image.Image:
    """What the game does at render time: multiply the greyscale liquid by the brew's colour."""
    red, green, blue, alpha = sprite.split()
    channels = []
    for channel, shift in ((red, 16), (green, 8), (blue, 0)):
        multiplier = (colour >> shift) & 0xFF
        channels.append(channel.point(lambda v, m=multiplier: int(v * m / 255)))
    return Image.merge("RGBA", (*channels, alpha))


def preview(states: dict[str, Image.Image]) -> Image.Image:
    """The README image: the same four fill levels under each of the four humours."""
    scale = 64
    gap = 8
    labels = list(BREW_COLOURS)

    width = gap + 4 * (scale + gap)
    height = gap + len(labels) * (scale + gap) + gap

    image = Image.new("RGBA", (width, height), PANEL)
    drawing = ImageDraw.Draw(image)
    shell_art = states["cinderflask"].resize((scale, scale), Image.NEAREST)

    for row, name in enumerate(labels):
        y = gap + row * (scale + gap)
        for level in range(1, 5):
            x = gap + (level - 1) * (scale + gap)
            image.paste(shell_art, (x, y), shell_art)

            pool = states["cinderflask_liquid_" + str(level)].resize((scale, scale), Image.NEAREST)
            pool = tint(pool, BREW_COLOURS[name])
            image.paste(pool, (x, y), pool)

            spirit = states["cinderflask_mote"].crop((0, 0, 16, 16)).resize((scale, scale), Image.NEAREST)
            spirit = tint(spirit, MOTE_COLOURS[row % len(MOTE_COLOURS)])
            image.paste(spirit, (x, y), spirit)

        drawing.text((gap + 2, y + scale - 8), name, fill=(196, 196, 208, 255))

    return image


def main() -> None:
    os.makedirs(ITEM_DIR, exist_ok=True)
    os.makedirs(GUI_DIR, exist_ok=True)
    os.makedirs(DOCS_DIR, exist_ok=True)

    states = {"cinderflask": draw(shell()), "cinderflask_mote": mote_frames()}
    for level in range(1, 5):
        states["cinderflask_liquid_" + str(level)] = draw(liquid(level))

    # An empty flask still needs a liquid layer so the mote stays at tint index 2 in every model.
    states["cinderflask_liquid_0"] = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    written = {os.path.join(ITEM_DIR, name + ".png"): art for name, art in states.items()}
    written[os.path.join(ITEM_DIR, "cinderflask_mote.png")] = mote_frames()
    written[os.path.join(ITEM_DIR, "sump.png")] = sump()
    written[os.path.join(GUI_DIR, "cinderflask.png")] = gui()
    written[os.path.join(ASSET_DIR, "icon.png")] = icon()
    written[os.path.join(DOCS_DIR, "preview.png")] = preview(states)

    for path, image in written.items():
        image.save(path, optimize=True)
        print("wrote", os.path.relpath(path, ROOT))

    # The mote is a sprite sheet, so it needs an animation descriptor beside it.
    meta = os.path.join(ITEM_DIR, "cinderflask_mote.png.mcmeta")
    with open(meta, "w", encoding="utf-8", newline="\n") as handle:
        handle.write('{\n  "animation": {\n    "frametime": 7\n  }\n}\n')
    print("wrote", os.path.relpath(meta, ROOT))


if __name__ == "__main__":
    main()
