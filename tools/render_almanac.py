#!/usr/bin/env python3
"""Draws the Almanac's node layout, so the map can be looked at without opening the game.

The layout itself comes from the game: `AlmanacTest.theLayoutIsWrittenOutToBeLookedAt` writes
`build/gametest/almanac.json` when the gametests run. This only draws it, which means the picture
cannot disagree with the thing it is a picture of.

    ./gradlew runGametest
    python tools/render_almanac.py
"""

from __future__ import annotations

import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAYOUT = os.path.join(ROOT, "build", "gametest", "almanac.json")
DOCS_DIR = os.path.join(ROOT, "docs")

MARGIN = 60
NODE = 20

PAPER = (26, 24, 28, 255)
EDGE = (70, 66, 78, 255)
OPEN_FILL = (224, 138, 42, 255)
OPEN_EDGE = (255, 206, 128, 255)
GATED_FILL = (58, 56, 66, 255)
GATED_EDGE = (96, 92, 108, 255)
LABEL = (196, 190, 200, 255)
GATED_LABEL = (120, 116, 128, 255)


def main() -> None:
    with open(LAYOUT, encoding="utf-8") as handle:
        layout = json.load(handle)

    nodes = {n["id"]: n for n in layout["nodes"]}

    xs = [n["x"] for n in nodes.values()]
    ys = [n["y"] for n in nodes.values()]
    left, right = min(xs) - MARGIN, max(xs) + MARGIN
    top, bottom = min(ys) - MARGIN, max(ys) + MARGIN

    image = Image.new("RGBA", (right - left, bottom - top), PAPER)
    drawing = ImageDraw.Draw(image)

    def place(node):
        return node["x"] - left, node["y"] - top

    for edge in layout["edges"]:
        a, b = nodes.get(edge["from"]), nodes.get(edge["to"])
        if a and b:
            drawing.line([place(a), place(b)], fill=EDGE, width=2)

    for node in nodes.values():
        x, y = place(node)
        gated = node["gated"]
        drawing.rectangle(
            [x - NODE // 2, y - NODE // 2, x + NODE // 2, y + NODE // 2],
            fill=GATED_FILL if gated else OPEN_FILL,
            outline=GATED_EDGE if gated else OPEN_EDGE)
        drawing.text((x - NODE // 2, y + NODE // 2 + 2), node["id"],
                     fill=GATED_LABEL if gated else LABEL)

    os.makedirs(DOCS_DIR, exist_ok=True)
    out = os.path.join(DOCS_DIR, "almanac.png")
    image.save(out, optimize=True)
    print("wrote", os.path.relpath(out, ROOT), f"({len(nodes)} nodes, {len(layout['edges'])} edges)")


if __name__ == "__main__":
    main()
