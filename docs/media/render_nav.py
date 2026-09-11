#!/usr/bin/env python3
"""Simulated Flipper Next Turn catalog art. NOT device captures: the layout
(arrow zone 1..45, text rows at x49, 128x64) mirrors ui/nav_view.c geometry,
but fonts are approximated. Regenerate: python3 docs/media/render_nav.py."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
here = Path(__file__).resolve().parent

# 10x10 monochrome FAP icon: white bg, black right arrow (house style).
icon = Image.new("L", (10, 10), 255)
px = icon.load()
arrow = ["..........", "..#.......", "..##......", "..#####...", "..#######.",
         "..#######.", "..#####...", "..##......", "..#.......", ".........."]
for y, row in enumerate(arrow):
    for x, ch in enumerate(row):
        if ch == "#":
            px[x, y] = 0
icon.save(Path(__file__).resolve().parents[2] / "flipper/nav_turns/assets/app.png")

# 128x64 simulated screen: left-turn state.
base = Image.new("RGB", (128, 64), (15, 15, 15))
d = ImageDraw.Draw(base)
amber = (255, 154, 0)
# Arrow glyph: left arrow across the 1..45 zone (mirrors nav_view.c lines).
d.line([(40, 23), (8, 23)], fill=amber, width=2)
d.line([(8, 23), (18, 13)], fill=amber, width=2)
d.line([(8, 23), (18, 33)], fill=amber, width=2)
font = ImageFont.load_default()
d.text((49, 2), "0.5 mi", font=font, fill=amber)
d.text((49, 17), "Turn left", font=font, fill=amber)
d.text((49, 32), "Main St", font=font, fill=amber)
d.text((2, 55), "Hold Back to exit", font=font, fill=amber)
d.rectangle([124, 2, 127, 5], fill=amber)
base.save(here / "nav-next-turn-native.png")
base.resize((384, 192), Image.NEAREST).save(here / "nav-next-turn.png")

# Catalog icon SVG: orange rounded square, dark right-turn arrow.
(here / "nav-icon.svg").write_text(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"'
    ' viewBox="0 0 48 48" role="img" aria-label="Flipper Next Turn arrow">'
    '<rect width="48" height="48" rx="9" fill="#ffb22d"/>'
    '<path d="M10 24h22m-9-9l9 9-9 9" stroke="#101010" stroke-width="5"'
    ' fill="none" stroke-linecap="square"/></svg>\n')
print("PASS: nav app icon, simulated renders, catalog icon")
