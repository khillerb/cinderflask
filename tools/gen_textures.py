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
EFFECT_DIR = os.path.join(ASSET_DIR, "textures", "mob_effect")
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
    "X": (108, 108, 108, 255),

    # Sand, for the sinter. A flask packed in it and fired is how a cracked one is mended.
    "A": (150, 128, 88, 255),     # sand, shadow
    "B": (198, 176, 126, 255),    # sand, mid
    "E": (228, 210, 166, 255),    # sand, lit
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


# Each tier swaps the metal it is bound with, keeping the silhouette. Read left to right in a
# hotbar these go gold, iron, blackened iron, amethyst.
TIER_PALETTES = {
    "cinderflask": {},
    "bound_cinderflask": {"G": (104, 104, 110, 255), "g": (162, 162, 170, 255),
                          "h": (214, 214, 222, 255)},
    "witch_iron_cinderflask": {"G": (44, 42, 48, 255), "g": (86, 82, 92, 255),
                               "h": (140, 134, 150, 255),
                               "S": (26, 24, 30, 255), "s": (70, 64, 80, 255)},
    "aetherglass_cinderflask": {"G": (92, 58, 128, 255), "g": (152, 104, 196, 255),
                                "h": (214, 178, 240, 255),
                                "L": (58, 46, 78, 255), "l": (86, 72, 112, 255),
                                "w": (150, 132, 186, 255)},
}






def recoloured(rows: list[str], swaps: dict) -> Image.Image:
    """The shell drawn through a tier's own palette."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = image.load()

    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            pixels[x, y] = swaps.get(key, PALETTE[key])

    return image


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




# Two silhouettes that have to be told apart at sixteen pixels: dregs settle into a heap, sump
# spreads into a puddle. They used to share both a shape and a palette, and read as the same blob.
DREGS_MASK = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......####......",
    ".....######.....",
    "....########....",
    "...##########...",
    "..############..",
    "..############..",
    "...##########...",
    "....########....",
    "................",
]

SUMP_MASK = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....######.....",
    "...##########...",
    ".##############.",
    "################",
    "################",
    "################",
    ".##############.",
    "...##########...",
    ".....######.....",
    "................",
]

# Bubbles rising through it, and the wet highlight that stops it reading as a lump of dirt.
BUBBLES = [(4, 11), (11, 12)]
GLEAM = [(4, 8), (5, 8), (4, 9)]


def outline_and_fill(mask: list[str], grainy: bool) -> tuple[list[str], list[str]]:
    """Splits a solid shape into an untinted outline and a greyscale interior.

    <p>The interior is the layer the game tints, exactly as the flask's liquid layer is, so dregs
    left by a choleric brew come out amber and a kelpwine's come out green. It is shaded by depth so
    a tinted shape still reads as a form and not a flat patch of colour.
    """
    outline = blank()
    fill = blank()

    filled = [[cell == "#" for cell in row] for row in mask]
    rows = [y for y, row in enumerate(mask) if "#" in row]
    top, bottom = (rows[0], rows[-1]) if rows else (0, 0)
    depth = max(1, bottom - top)

    for y, row in enumerate(mask):
        for x, cell in enumerate(row):
            if cell != "#":
                continue

            edge = any(not inside(filled, x + dx, y + dy)
                       for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)))

            if edge:
                outline[y][x] = "K"
                continue

            # Brightest along the top, where the light lands, falling away with depth.
            shade = (y - top) / depth
            step = "W" if shade < 0.2 else "M" if shade < 0.55 else "D" if shade < 0.85 else "X"

            # Grain, so a heap of sediment does not read as a smooth jelly.
            if grainy and hash_jitter(x, y) > 0.62:
                step = {"W": "M", "M": "D", "D": "X", "X": "D"}[step]

            fill[y][x] = step

    return ["".join(r) for r in outline], ["".join(r) for r in fill]


def inside(filled: list[list[bool]], x: int, y: int) -> bool:
    return 0 <= x < 16 and 0 <= y < 16 and filled[y][x]


def sump_layers() -> tuple[list[str], list[str]]:
    """A wet mass. Bubbles rising through it, and a highlight so it reads as liquid, not soil."""
    outline, fill = outline_and_fill(SUMP_MASK, grainy=False)
    outline = [list(row) for row in outline]
    fill = [list(row) for row in fill]

    for x, y in BUBBLES:
        if fill[y][x] != ".":
            outline[y][x] = "K"
            fill[y][x] = "."

    # Specular, kept in the tinted layer so it takes the brew's own colour rather than going white.
    for x, y in GLEAM:
        if fill[y][x] != ".":
            fill[y][x] = "W"

    return ["".join(r) for r in outline], ["".join(r) for r in fill]


def sinter() -> Image.Image:
    """A cracked flask caked in sand, ready for the fire.

    Built on the flask's own shell so the silhouette stays a flask — a sand block with something
    buried in it reads as a picture frame, which is what the first attempt looked like.
    """
    grid = [list(row) for row in shell()]

    # Sand packed over it: heavier towards the foot, thinning out near the neck, so the shape still
    # shows through at the top.
    for y in range(16):
        for x in range(16):
            # The outline is never buried, or the flask stops being a flask.
            if grid[y][x] in (".", "K"):
                continue

            packed = hash_jitter(x, y) < 0.12 + 0.34 * (y / 15)
            if packed:
                grade = hash_jitter(x + 7, y + 3)
                grid[y][x] = "E" if grade > 0.7 else "A" if grade < 0.3 else "B"

    # And a heap of it round the base, so it is sitting in sand rather than dusted with it.
    for y, (left, right) in ((14, (2, 13)), (15, (0, 15))):
        for x in range(left, right + 1):
            grade = hash_jitter(x, y * 3)
            grid[y][x] = "E" if grade > 0.72 else "A" if grade < 0.28 else "B"

    for x in range(16):
        grid[15][x] = "K" if x in (0, 15) else grid[15][x]

    return draw(["".join(row) for row in grid])


def almanac() -> Image.Image:
    """The book. Blackened leather, a gold spine, and the same ember mark the flask carries."""
    grid = blank()
    left, right, top, bottom = 2, 13, 2, 13

    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            grid[y][x] = "s"

    for x in range(left, right + 1):
        grid[top][x] = "K"
        grid[bottom][x] = "K"
    for y in range(top, bottom + 1):
        grid[y][left] = "K"
        grid[y][right] = "K"

    # Spine: gold bands down the left, then the fold.
    for y in range(top + 1, bottom):
        grid[y][left + 1] = "h" if y % 3 == 0 else "g"
        grid[y][left + 2] = "K"

    # Page edges down the right, so it reads as a closed book rather than a tile.
    for y in range(top + 1, bottom):
        grid[y][right - 1] = "S"

    # The ember, centred on the cover.
    for (x, y), shade in {
        (8, 6): "5", (9, 6): "5",
        (7, 7): "5", (8, 7): "7", (9, 7): "7", (10, 7): "5",
        (7, 8): "5", (8, 8): "7", (9, 8): "7", (10, 8): "5",
        (8, 9): "5", (9, 9): "5",
    }.items():
        grid[y][x] = shade

    return draw(["".join(row) for row in grid])


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


# ---------------------------------------------------------------------------------------------
# Effect icons
# ---------------------------------------------------------------------------------------------

# The same four humour colours the game blends at render time, plus the pale aether. Kept here so
# an icon can never disagree with the liquid that produces it.
HUMOUR_RGB = [0xD9822B, 0x4A2C6B, 0xA31E28, 0x8FA88C]
AETHER_RGB = 0xE8E0C0

# Landmark, wheel position, and how it is built: on its own, leaning into the next humour, or
# carried outward on reach. Mirrors Landmarks.java, which generates the same twelve the same way.
LANDMARKS = [
    ("deadmans_draught", 0, "pure"),
    ("ironroot_tonic", 1, "pure"),
    ("sap_sworn_mead", 2, "pure"),
    ("nightcap", 3, "pure"),
    ("bramblewine", 0, "lean"),
    ("deepdelve", 1, "lean"),
    ("kelpwine", 2, "lean"),
    ("quickstep_draught", 3, "lean"),
    ("emberflask", 0, "carried"),
    ("riposte_cordial", 1, "carried"),
    ("honeyed_restorative", 2, "carried"),
    ("gravemead", 3, "carried"),
]

# One glyph per role. Ten by ten, centred on an eighteen-pixel tile.
#   -  outline      +  body      #  highlight
GLYPHS = {
    # Berserker: a blade driven downwards.
    "deadmans_draught": [
        "...-##-...",
        "...-##-...",
        "-########-",
        "-#-####-#-",
        "...#++#...",
        "...#++#...",
        "...#++#...",
        "....##....",
        "....##....",
        ".....-....",
    ],
    # Bulwark: a shield.
    "ironroot_tonic": [
        "-########-",
        "-#++++++#-",
        "-#++##++#-",
        "-#++##++#-",
        "-#++++++#-",
        ".-#++++#-.",
        "..-#++#-..",
        "...-##-...",
        "....--....",
        "..........",
    ],
    # Reaver: a drop, and it is going the wrong way.
    "sap_sworn_mead": [
        "....--....",
        "....##....",
        "...-##-...",
        "...####...",
        "..-####-..",
        "..######..",
        ".-##++##-.",
        ".-##++##-.",
        "..-####-..",
        "...----...",
    ],
    # Assassin: a dagger, held low.
    "nightcap": [
        "........--",
        ".......-#-",
        "......-#+-",
        ".....-#+-.",
        "....-#+-..",
        "..--#+-...",
        ".-####-...",
        "-#+-#-....",
        "-+-..-....",
        "--........",
    ],
    # Retaliator: a hedge that does not want to be touched.
    "bramblewine": [
        "..#....#..",
        ".-#-..-#-.",
        "-##-..-##-",
        "-##-##-##-",
        "-##+##+##-",
        "-#++++++#-",
        "-#++++++#-",
        ".-######-.",
        "..-####-..",
        "...----...",
    ],
    # Miner: everything that falls, stopping at the floor.
    "deepdelve": [
        "....##....",
        "....##....",
        "....##....",
        "-########-",
        ".-######-.",
        "..-####-..",
        "...-##-...",
        "..........",
        "##########",
        "-########-",
    ],
    # Diver: water, closing over.
    "kelpwine": [
        "..........",
        "-##-..-##-",
        "#++####++#",
        "-..-##-..-",
        "..........",
        "-##-..-##-",
        "#++####++#",
        "-..-##-..-",
        "..........",
        "..........",
    ],
    # Skirmisher: two chevrons, going somewhere.
    "quickstep_draught": [
        "..........",
        "-#-...-#-.",
        "-##-...##-",
        ".-##-..-##",
        "..-##-..-#",
        "..-##-..-#",
        ".-##-..-##",
        "-##-...##-",
        "-#-...-#-.",
        "..........",
    ],
    # Alchemist: a flame.
    "emberflask": [
        ".....-....",
        "....-#-...",
        "...-##-...",
        "..-###-...",
        "..-#+#-...",
        ".-##+##-..",
        "-##+++##-.",
        "-#+++++#-.",
        "-##+-+##-.",
        ".-#-.-#-..",
    ],
    # Duelist: two blades, crossed.
    "riposte_cordial": [
        "-#-....-#-",
        "-##-..-##-",
        ".-##--##-.",
        "..-####-..",
        "...-##-...",
        "...-##-...",
        "..-####-..",
        ".-##--##-.",
        "-##-..-##-",
        "-#-....-#-",
    ],
    # Healer: a cross, and it does not stop at the edge.
    "honeyed_restorative": [
        "...-##-...",
        "...-##-...",
        "...-##-...",
        "-###++###-",
        "-#++++++#-",
        "-###++###-",
        "...-##-...",
        "...-##-...",
        "...-##-...",
        "..........",
    ],
    # Necromancer: what is left.
    "gravemead": [
        "..------..",
        ".-######-.",
        "-##++++##-",
        "-#-####-#-",
        "-#-####-#-",
        "-##++++##-",
        ".-##--##-.",
        "..-#--#-..",
        "..-#--#-..",
        "...----...",
    ],
}


def landmark_colour(humour: int, kind: str) -> tuple[int, int, int]:
    """Humours.colour() for a landmark, worked out the way the game works it out."""
    weights = [0.0, 0.0, 0.0, 0.0]
    aether = 0.0

    if kind == "pure":
        weights[humour] = 8.0
    elif kind == "lean":
        weights[humour] = 6.0
        weights[(humour + 1) % 4] = 6.0
    else:
        weights[humour] = 8.0
        aether = 5.0

    total = sum(weights) + aether
    channels = []
    for shift in (16, 8, 0):
        value = sum(((HUMOUR_RGB[i] >> shift) & 0xFF) * w / total for i, w in enumerate(weights))
        value += ((AETHER_RGB >> shift) & 0xFF) * aether / total
        channels.append(round(value))

    return tuple(channels)


def shade(colour, towards, amount: float):
    return tuple(round(c + (t - c) * amount) for c, t in zip(colour, towards))


def effect_icon(name: str, humour: int, kind: str) -> Image.Image:
    """An eighteen-pixel tile: a dark plate, and the role drawn on it in the brew's own colour."""
    return plated(landmark_colour(humour, kind), GLYPHS[name])


def plated(colour, glyph) -> Image.Image:
    """A dark rounded plate with a glyph on it, in three steps of one colour."""
    black = (0, 0, 0)
    white = (255, 255, 255)

    ramp = {
        "-": shade(colour, black, 0.55) + (255,),
        "+": colour + (255,),
        "#": shade(colour, white, 0.45) + (255,),
    }

    plate = shade(colour, black, 0.86) + (255,)
    rim = shade(colour, black, 0.66) + (255,)

    image = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    drawing = ImageDraw.Draw(image)

    # A rounded plate, so twelve icons read as one set rather than twelve loose symbols.
    drawing.rectangle((1, 1, 16, 16), fill=plate)
    drawing.rectangle((1, 1, 16, 16), outline=rim)
    for x, y in ((1, 1), (16, 1), (1, 16), (16, 16)):
        image.putpixel((x, y), (0, 0, 0, 0))

    for row, line in enumerate(glyph):
        for column, cell in enumerate(line):
            if cell in ramp:
                image.putpixel((4 + column, 4 + row), ramp[cell])

    return image


def effect_icons() -> dict:
    return {name: effect_icon(name, humour, kind) for name, humour, kind in LANDMARKS}


# The four rebounds. Each mirrors the draught it inverts: a snapped blade against Deadman's whole
# one, a cracked shield against Ironroot's, a drained drop against Sapsworn's full one, and an open
# eye against the assassin's knife.
REBOUNDS = [("ashfall", 0), ("brittle", 1), ("bloodless", 2), ("plain_sight", 3)]

REBOUND_GLYPHS = {
    # Choleric spent: the blade snapped.
    "ashfall": [
        "...-##-...",
        "...-##-...",
        "-########-",
        "-#-####-#-",
        "...#++#...",
        "....--....",
        "..........",
        "...-##-...",
        "....##....",
        ".....-....",
    ],
    # Melancholic spent: the wall came apart.
    "brittle": [
        "-########-",
        "-#++-+++#-",
        "-#++-+++#-",
        "-#+-++++#-",
        "-#++-+++#-",
        ".-#+-++#-.",
        "..-#-+#-..",
        "...-##-...",
        "....--....",
        "..........",
    ],
    # Sanguine spent: the drop, emptied.
    "bloodless": [
        "....--....",
        "....##....",
        "...-##-...",
        "...#--#...",
        "..-#--#-..",
        "..#----#..",
        ".-#----#-.",
        ".-#----#-.",
        "..-####-..",
        "...----...",
    ],
    # Phlegmatic spent: somebody is looking straight at you.
    "plain_sight": [
        "..........",
        "..-####-..",
        ".-######-.",
        "-##+--+##-",
        "##+----+##",
        "##+----+##",
        "-##+--+##-",
        ".-######-.",
        "..-####-..",
        "..........",
    ],
}

# Humours.soured(): dragged half-way towards the murk, so a rebound looks like the brew that caused
# it, gone bad. Same constant as ReboundEffect.SOURED.
MURK_RGB = 0x2B2118
SOURED = 0.5


def rebound_colour(humour: int) -> tuple:
    base = HUMOUR_RGB[humour]
    murk = MURK_RGB
    return tuple(
        round((((base >> shift) & 0xFF) * (1 - SOURED)) + (((murk >> shift) & 0xFF) * SOURED))
        for shift in (16, 8, 0))


def rebound_icons() -> dict:
    icons = {}
    for name, humour in REBOUNDS:
        icons[name] = plated(rebound_colour(humour), REBOUND_GLYPHS[name])
    return icons


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
    os.makedirs(EFFECT_DIR, exist_ok=True)
    os.makedirs(DOCS_DIR, exist_ok=True)

    states = {"cinderflask_mote": mote_frames()}
    for tier, swaps in TIER_PALETTES.items():
        states[tier] = recoloured(shell(), swaps)
    for level in range(1, 5):
        states["cinderflask_liquid_" + str(level)] = draw(liquid(level))

    # An empty flask still needs a liquid layer so the mote stays at tint index 2 in every model.
    states["cinderflask_liquid_0"] = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    written = {os.path.join(ITEM_DIR, name + ".png"): art for name, art in states.items()}
    written[os.path.join(ITEM_DIR, "cinderflask_mote.png")] = mote_frames()
    dregs_outline, dregs_fill = outline_and_fill(DREGS_MASK, grainy=True)
    sump_outline, sump_fill = sump_layers()

    # Two layers apiece, the way the flask already works: an untinted shape and a greyscale
    # interior the game colours from the brew it remembers.
    written[os.path.join(ITEM_DIR, "dregs.png")] = draw(dregs_outline)
    written[os.path.join(ITEM_DIR, "dregs_settled.png")] = draw(dregs_fill)
    written[os.path.join(ITEM_DIR, "sump.png")] = draw(sump_outline)
    written[os.path.join(ITEM_DIR, "sump_settled.png")] = draw(sump_fill)
    written[os.path.join(ITEM_DIR, "sinter.png")] = sinter()
    written[os.path.join(ITEM_DIR, "almanac.png")] = almanac()
    written[os.path.join(GUI_DIR, "cinderflask.png")] = gui()
    written[os.path.join(ASSET_DIR, "icon.png")] = icon()
    written[os.path.join(DOCS_DIR, "preview.png")] = preview(states)

    for name, art in effect_icons().items():
        written[os.path.join(EFFECT_DIR, name + ".png")] = art

    for name, art in rebound_icons().items():
        written[os.path.join(EFFECT_DIR, name + ".png")] = art

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
