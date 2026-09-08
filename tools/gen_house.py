#!/usr/bin/env python3
"""Paints the flashback's furniture: everything a house, an office and a bar have that a pod does not.

    py -3.13 tools/gen_house.py         # just these textures, to assets/surrogate/textures
    py -3.13 tools/gen_textures.py      # everything, which calls into here at the end

Kept apart from gen_textures.py so it can be iterated on its own: none of this uses that file's shared
noise stream, so repainting a couch cannot re-roll a hull plate. Every texture is drawn with a handful of
primitives at 16x16 (a few animated strips are 16x64), and the models in gen_data.py map faces onto regions
of them. Where a texture is a region map, the regions are written down next to it."""
import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "surrogate")
TEX = os.path.join(ROOT, "textures")

rnd = random.Random(2189)


# --------------------------------------------------------------------------------------
# Primitives
# --------------------------------------------------------------------------------------
def new(w=16, h=16):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def shade(col, k):
    return (max(0, min(255, int(col[0] * k))), max(0, min(255, int(col[1] * k))), max(0, min(255, int(col[2] * k))), col[3] if len(col) > 3 else 255)


def rgba(col):
    return col if len(col) == 4 else (col[0], col[1], col[2], 255)


def rect(img, x, y, w, h, col):
    col = rgba(col)
    px = img.load()
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= xx < img.width and 0 <= yy < img.height:
                px[xx, yy] = col


def fill(img, col):
    rect(img, 0, 0, img.width, img.height, col)


def hline(img, x, y, w, col):
    rect(img, x, y, w, 1, col)


def vline(img, x, y, h, col):
    rect(img, x, y, 1, h, col)


def dot(img, x, y, col):
    rect(img, x, y, 1, 1, col)


def outline(img, x, y, w, h, col):
    hline(img, x, y, w, col)
    hline(img, x, y + h - 1, w, col)
    vline(img, x, y, h, col)
    vline(img, x + w - 1, y, h, col)


def bevel(img, x, y, w, h, base, light=1.2, dark=0.7):
    """A raised panel: lit top and left edge, shadowed bottom and right."""
    rect(img, x, y, w, h, base)
    hline(img, x, y, w, shade(base, light))
    vline(img, x, y, h, shade(base, light))
    hline(img, x, y + h - 1, w, shade(base, dark))
    vline(img, x + w - 1, y, h, shade(base, dark))


def noise(img, x, y, w, h, amount=0.06, r=None):
    """Per-pixel brightness variance over a region, for anything that should not look painted."""
    r = r or rnd
    px = img.load()
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= xx < img.width and 0 <= yy < img.height:
                c = px[xx, yy]
                if c[3] == 0:
                    continue
                px[xx, yy] = shade(c, 1.0 + r.uniform(-amount, amount))


def grad_v(img, x, y, w, h, top, bottom):
    for i in range(h):
        t = i / max(1, h - 1)
        col = tuple(int(top[k] + (bottom[k] - top[k]) * t) for k in range(3)) + (255,)
        hline(img, x, y + i, w, col)


def disc(img, cx, cy, r, col):
    for yy in range(cy - r, cy + r + 1):
        for xx in range(cx - r, cx + r + 1):
            if (xx - cx + 0.5) ** 2 + (yy - cy + 0.5) ** 2 <= r * r:
                dot(img, xx, yy, col)


def ring(img, cx, cy, r, col, width=1):
    for yy in range(cy - r - 1, cy + r + 2):
        for xx in range(cx - r - 1, cx + r + 2):
            d = ((xx - cx + 0.5) ** 2 + (yy - cy + 0.5) ** 2) ** 0.5
            if r - width < d <= r:
                dot(img, xx, yy, col)


def grain(img, x, y, w, h, base, streak, count=6, vertical=False, r=None):
    """Wood: a base, and a few darker streaks along the grain."""
    r = r or rnd
    rect(img, x, y, w, h, base)
    for _ in range(count):
        if vertical:
            sx = x + r.randrange(w)
            sy = y + r.randrange(h)
            vline(img, sx, sy, r.randrange(2, max(3, h // 2)), streak)
        else:
            sx = x + r.randrange(w)
            sy = y + r.randrange(h)
            hline(img, sx, sy, r.randrange(2, max(3, w // 2)), streak)
    noise(img, x, y, w, h, 0.04, r)


# A tiny 3x5 font for the few words a room needs printed on it.
FONT = {
    "A": ["010", "101", "111", "101", "101"],
    "B": ["110", "101", "110", "101", "110"],
    "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"],
    "H": ["101", "101", "111", "101", "101"],
    "K": ["101", "101", "110", "101", "101"],
    "N": ["101", "111", "111", "111", "101"],
    "O": ["111", "101", "101", "101", "111"],
    "R": ["110", "101", "110", "101", "101"],
    "S": ["111", "100", "111", "001", "111"],
    "T": ["111", "010", "010", "010", "010"],
    "W": ["101", "101", "101", "111", "101"],
    "Q": ["111", "101", "101", "111", "001"],
    "3": ["111", "001", "111", "001", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "2": ["111", "001", "111", "100", "111"],
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "-": ["000", "000", "111", "000", "000"],
    " ": ["000", "000", "000", "000", "000"],
}


def text(img, x, y, s, col):
    for ch in s:
        glyph = FONT.get(ch)
        if glyph:
            for row, bits in enumerate(glyph):
                for c, bit in enumerate(bits):
                    if bit == "1":
                        dot(img, x + c, y + row, col)
        x += 4


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


def mcmeta(rel, frametime, interpolate=False):
    path = os.path.join(TEX, rel + ".mcmeta")
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"animation": anim}, f)
    print("wrote", os.path.relpath(path, ROOT))


# --------------------------------------------------------------------------------------
# Palette. Earth, 2189: nothing on Sallow is any of these colours.
# --------------------------------------------------------------------------------------
CREAM = (232, 220, 196)
CREAM_D = (206, 190, 160)
ROSE = (186, 120, 128)
ROSE_D = (150, 86, 96)
LEAF = (118, 140, 88)
WALNUT_D = (74, 50, 33)
WALNUT = (120, 82, 52)
WALNUT_L = (166, 122, 82)
OAK = (178, 138, 88)
OAK_D = (140, 104, 62)
TEAL = (72, 112, 98)
TEAL_D = (52, 84, 72)
TEAL_L = (96, 138, 122)
PLASTIC = (44, 44, 50)
PLASTIC_L = (70, 70, 78)
CHROME = (184, 188, 196)
CHROME_D = (120, 124, 132)
PORCELAIN = (238, 238, 236)
PORCELAIN_S = (206, 208, 212)
GLASS = (206, 226, 232, 140)
WATER = (168, 208, 220)
CARDBOARD = (188, 148, 98)
CARDBOARD_D = (150, 112, 70)
TAPE = (216, 196, 150)
PAPER = (242, 238, 226)
PAPER_S = (214, 208, 190)
INK = (58, 58, 66)
INK_L = (120, 120, 130)
STAMP = (190, 44, 44)
BRASS = (198, 158, 84)
BRASS_D = (140, 106, 50)
SCREEN_OFF = (28, 30, 34)
SCREEN_GLOW = (176, 214, 210)
GREY = (150, 152, 158)
GREY_D = (96, 98, 104)
GREY_L = (196, 198, 204)
WHITE = (246, 246, 244)
LAMINATE = (212, 210, 200)
BLACK = (24, 24, 28)
RED_LEATHER = (150, 40, 44)
RED_LEATHER_D = (104, 26, 30)
AMBER = (222, 154, 46)
AMBER_D = (176, 112, 26)
FOAM = (246, 240, 220)
NEON = (255, 84, 120)
NEON_D = (150, 40, 70)
CORK = (226, 208, 166)
BOARD_K = (30, 30, 32)
BOARD_R = (184, 40, 44)
BOARD_G = (34, 112, 66)
WIRE = (208, 208, 210)
SKIN = (222, 178, 142)
FUR = (150, 104, 62)
FUR_D = (104, 70, 40)
PIZZA_CRUST = (206, 152, 80)
PIZZA_CHEESE = (238, 200, 92)
PIZZA_SAUCE = (178, 58, 40)
PEPPERONI = (146, 36, 40)


# --------------------------------------------------------------------------------------
# The house
# --------------------------------------------------------------------------------------
def wallpapers():
    img = new()
    fill(img, CREAM)
    noise(img, 0, 0, 16, 16, 0.02)
    # A rose every eight pixels, offset on alternate rows, with a leaf beside it.
    for (cx, cy) in ((3, 3), (11, 11)):
        dot(img, cx, cy, ROSE_D)
        for (dx, dy) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            dot(img, cx + dx, cy + dy, ROSE)
        for (dx, dy) in ((-1, -1), (1, 1), (1, -1), (-1, 1)):
            dot(img, cx + dx, cy + dy, shade(ROSE, 1.1))
        dot(img, cx + 2, cy + 1, LEAF)
        dot(img, cx + 2, cy + 2, LEAF)
        dot(img, cx - 2, cy - 1, LEAF)
    for (cx, cy) in ((11, 3), (3, 11)):
        dot(img, cx, cy, shade(CREAM_D, 0.95))
        dot(img, cx, cy + 1, CREAM_D)
    save(img, "block/wallpaper_floral.png")

    img = new()
    fill(img, CREAM)
    noise(img, 0, 0, 16, 16, 0.02)
    for x in (2, 3, 10, 11):
        vline(img, x, 0, 16, CREAM_D)
    for x in (6, 14):
        vline(img, x, 0, 16, shade(CREAM_D, 1.06))
    save(img, "block/wallpaper_stripe.png")


def couch():
    # Fabric: a woven teal, used for everything that is not the cushion top.
    img = new()
    fill(img, TEAL)
    for y in range(0, 16, 2):
        for x in range(0, 16, 2):
            dot(img, x + (y // 2) % 2, y, TEAL_D)
    noise(img, 0, 0, 16, 16, 0.05)
    save(img, "block/couch_fabric.png")
    # Cushion: the same fabric with piping round the edge and a button in the middle.
    img = new()
    fill(img, TEAL)
    for y in range(0, 16, 2):
        for x in range(0, 16, 2):
            dot(img, x + (y // 2) % 2, y, TEAL_D)
    noise(img, 0, 0, 16, 16, 0.05)
    outline(img, 0, 0, 16, 16, TEAL_L)
    outline(img, 1, 1, 14, 14, shade(TEAL, 0.9))
    dot(img, 7, 7, TEAL_D)
    dot(img, 8, 7, TEAL_D)
    dot(img, 7, 8, TEAL_D)
    dot(img, 8, 8, TEAL_D)
    save(img, "block/couch_cushion.png")


def tables():
    # Coffee table: oak, a ring where a mug stood, a knot.
    img = new()
    grain(img, 0, 0, 16, 16, OAK, OAK_D, count=7)
    ring(img, 10, 6, 3, shade(OAK_D, 0.8))
    dot(img, 4, 11, shade(OAK_D, 0.7))
    dot(img, 5, 11, shade(OAK_D, 0.8))
    outline(img, 0, 0, 16, 16, shade(OAK, 1.1))
    save(img, "block/coffee_table_top.png")
    img = new()
    grain(img, 0, 0, 16, 16, OAK_D, shade(OAK_D, 0.8), count=5, vertical=True)
    save(img, "block/oak_side.png")
    # Dining table: darker walnut, a placemat woven into the top.
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=8)
    for y in range(4, 12):
        for x in range(3, 13):
            if (x + y) % 2 == 0:
                dot(img, x, y, shade(WALNUT_L, 0.95))
            else:
                dot(img, x, y, shade(WALNUT_L, 0.85))
    outline(img, 0, 0, 16, 16, shade(WALNUT, 1.15))
    save(img, "block/dining_table_top.png")
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=5, vertical=True)
    save(img, "block/walnut.png")
    # A chair: walnut frame, a dark red seat pad.
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=5, vertical=True)
    rect(img, 0, 8, 16, 8, RED_LEATHER)
    noise(img, 0, 8, 16, 8, 0.05)
    outline(img, 0, 8, 16, 8, RED_LEATHER_D)
    save(img, "block/chair.png")


def pizza():
    """The box and the pizza in it, at five levels of eaten. Region maps: the pizza is the whole texture."""
    img = new()
    fill(img, CARDBOARD)
    noise(img, 0, 0, 16, 16, 0.05)
    hline(img, 0, 6, 16, STAMP)
    hline(img, 0, 9, 16, STAMP)
    text(img, 3, 1, "HOT", STAMP)
    save(img, "block/pizza_box_side.png")
    img = new()
    fill(img, CARDBOARD)
    noise(img, 0, 0, 16, 16, 0.05)
    disc(img, 8, 8, 5, shade(CARDBOARD, 1.08))
    text(img, 2, 6, "HOT", STAMP)
    save(img, "block/pizza_box_top.png")
    img = new()
    fill(img, shade(CARDBOARD, 0.92))
    noise(img, 0, 0, 16, 16, 0.05)
    for (cx, cy, r) in ((4, 5, 2), (11, 9, 3), (7, 12, 1)):
        disc(img, cx, cy, r, shade(CARDBOARD, 0.8))
    save(img, "block/pizza_box_inside.png")

    def pie(slices):
        img = new()
        fill(img, shade(CARDBOARD, 0.92))
        for (cx, cy, r) in ((4, 12, 2), (12, 4, 2)):
            disc(img, cx, cy, r, shade(CARDBOARD, 0.8))
        if slices <= 0:
            for (x, y) in ((6, 7), (9, 8), (7, 10)):
                dot(img, x, y, PIZZA_CRUST)
            return img
        # Eight wedges round the middle; the ones still there are drawn, the rest is box.
        import math
        px = img.load()
        for yy in range(16):
            for xx in range(16):
                dx, dy = xx - 7.5, yy - 7.5
                d = math.hypot(dx, dy)
                if d > 7.2:
                    continue
                angle = (math.atan2(dy, dx) + math.pi) / (2 * math.pi)
                wedge = int(angle * 8) % 8
                if wedge >= slices:
                    continue
                if d > 6.0:
                    col = PIZZA_CRUST
                elif d > 5.3:
                    col = shade(PIZZA_CRUST, 1.1)
                else:
                    col = PIZZA_CHEESE if (xx * 7 + yy * 3) % 5 else PIZZA_SAUCE
                px[xx, yy] = rgba(col)
        # Pepperoni, on whichever wedges survive.
        for (x, y) in ((5, 4), (10, 5), (4, 9), (9, 10), (7, 7), (12, 9), (6, 12)):
            dx, dy = x - 7.5, y - 7.5
            angle = (math.atan2(dy, dx) + math.pi) / (2 * math.pi)
            if int(angle * 8) % 8 < slices and math.hypot(dx, dy) < 5.5:
                dot(img, x, y, PEPPERONI)
                dot(img, x + 1, y, PEPPERONI)
                dot(img, x, y + 1, PEPPERONI)
                dot(img, x + 1, y + 1, shade(PEPPERONI, 0.85))
        return img

    for n in (8, 6, 3, 1, 0):
        save(pie(n), "block/pizza_%d.png" % n)
    # The slice, as an item.
    img = new()
    px = img.load()
    for yy in range(16):
        for xx in range(16):
            # A wedge pointing down-left: inside if below the line from (2,2) to (14,2) narrowing to (2,14).
            if 2 <= xx <= 14 and 2 <= yy <= 14 and (xx - 2) >= (yy - 2) * 0.9 - 0.5:
                col = PIZZA_CHEESE if (xx * 5 + yy * 3) % 4 else PIZZA_SAUCE
                if xx >= 13:
                    col = PIZZA_CRUST
                px[xx, yy] = rgba(col)
    for (x, y) in ((6, 4), (10, 5), (9, 9), (12, 8)):
        dot(img, x, y, PEPPERONI)
        dot(img, x + 1, y, PEPPERONI)
        dot(img, x, y + 1, PEPPERONI)
        dot(img, x + 1, y + 1, shade(PEPPERONI, 0.85))
    save(img, "item/pizza_slice.png")


def television():
    """A flat set. Frame and back are plastic; the screen has four faces: off, news, credits, snow."""
    img = new()
    fill(img, PLASTIC)
    noise(img, 0, 0, 16, 16, 0.04)
    outline(img, 0, 0, 16, 16, shade(PLASTIC, 1.3))
    save(img, "block/tv_frame.png")
    img = new()
    fill(img, PLASTIC)
    noise(img, 0, 0, 16, 16, 0.04)
    for y in range(3, 13, 2):
        hline(img, 3, y, 10, shade(PLASTIC, 0.7))
    rect(img, 6, 13, 4, 2, shade(PLASTIC, 0.6))
    save(img, "block/tv_back.png")
    img = new()
    fill(img, PLASTIC)
    outline(img, 0, 0, 16, 16, shade(PLASTIC, 1.3))
    rect(img, 1, 1, 14, 12, SCREEN_OFF)
    for i in range(6):
        dot(img, 3 + i, 3 + i, shade(SCREEN_OFF, 1.5))
    dot(img, 13, 13, (60, 200, 90))
    save(img, "block/tv_screen_off.png")

    def frame_base():
        f = new()
        fill(f, PLASTIC)
        outline(f, 0, 0, 16, 16, shade(PLASTIC, 1.3))
        return f

    # The news: an anchor at a desk, a box over their shoulder, a ticker underneath. Four frames: the anchor
    # nods, the ticker moves.
    strip = new(16, 64)
    for i in range(4):
        f = frame_base()
        rect(f, 1, 1, 14, 12, (36, 52, 84))
        grad_v(f, 1, 1, 14, 6, (52, 74, 118), (36, 52, 84))
        # The picture box: a planet, which is the only thing on the news tonight.
        rect(f, 9, 2, 5, 4, (20, 26, 40))
        disc(f, 11, 4, 1, (196, 150, 90))
        dot(f, 12, 3, (230, 190, 120))
        # Desk, and the anchor: shoulders, a head that moves a pixel.
        rect(f, 1, 9, 14, 4, (90, 70, 50))
        rect(f, 3, 7, 5, 3, (40, 40, 56))
        head_y = 3 + (i % 2)
        rect(f, 4, head_y, 3, 3, SKIN)
        rect(f, 4, head_y, 3, 1, (70, 50, 40))
        dot(f, 6, head_y + 1, shade(SKIN, 0.8))
        # The ticker.
        rect(f, 1, 11, 14, 2, (170, 30, 40))
        for x in range(1, 15):
            if (x + i * 2) % 4 < 2:
                dot(f, x, 12, (250, 240, 230))
        strip.paste(f, (0, i * 16))
    save(strip, "block/tv_screen_news.png")
    mcmeta("block/tv_screen_news.png", 8)

    # Credits: white lines rolling up a black screen.
    strip = new(16, 64)
    for i in range(4):
        f = frame_base()
        rect(f, 1, 1, 14, 12, (14, 14, 18))
        for row in range(6):
            y = 1 + ((row * 4 - i * 2) % 14)
            if 1 <= y <= 12:
                w = 4 + (row * 3) % 6
                hline(f, 8 - w // 2, y, w, (200, 200, 205))
                if row % 2 == 0:
                    hline(f, 8 - w // 2 - 1, y, 1, (120, 120, 125))
        strip.paste(f, (0, i * 16))
    save(strip, "block/tv_screen_credits.png")
    mcmeta("block/tv_screen_credits.png", 6)

    # Snow.
    strip = new(16, 64)
    r = random.Random(12)
    for i in range(4):
        f = frame_base()
        for yy in range(1, 13):
            for xx in range(1, 15):
                v = r.randrange(40, 230)
                dot(f, xx, yy, (v, v, v))
        strip.paste(f, (0, i * 16))
    save(strip, "block/tv_screen_static.png")
    mcmeta("block/tv_screen_static.png", 2)

    img = new()
    fill(img, CHROME_D)
    noise(img, 0, 0, 16, 16, 0.04)
    hline(img, 0, 0, 16, CHROME)
    save(img, "block/tv_stand.png")


def lights():
    # The pendant: shade fabric in the top half, cord and bulb regions below. Regions: shade 0..16 x 0..8,
    # cord 7..9 x 8..16, bulb 10..14 x 8..14.
    for lit in (False, True):
        img = new()
        base = shade(CREAM, 1.05) if lit else CREAM_D
        rect(img, 0, 0, 16, 8, base)
        noise(img, 0, 0, 16, 8, 0.05)
        hline(img, 0, 0, 16, shade(base, 1.1))
        hline(img, 0, 7, 16, shade(base, 0.8))
        rect(img, 7, 8, 2, 8, BLACK)
        rect(img, 10, 8, 4, 6, (255, 240, 190) if lit else (200, 200, 196))
        if lit:
            outline(img, 10, 8, 4, 6, (255, 220, 150))
        rect(img, 0, 8, 6, 8, shade(base, 0.9))
        save(img, "block/pendant_lamp%s.png" % ("_lit" if lit else ""))
    # The office panel: a diffuser grid, lit or grey.
    for lit in (False, True):
        img = new()
        fill(img, (250, 250, 240) if lit else GREY_L)
        for y in range(0, 16, 4):
            hline(img, 0, y, 16, (236, 236, 224) if lit else GREY)
        for x in range(0, 16, 4):
            vline(img, x, 0, 16, (236, 236, 224) if lit else GREY)
        outline(img, 0, 0, 16, 16, GREY_D)
        save(img, "block/panel_light%s.png" % ("_lit" if lit else ""))
    # The switch plate, with the toggle drawn once for each position. Region: plate 6..10 x 5..11.
    for on in (False, True):
        img = new()
        rect(img, 6, 5, 4, 6, WHITE)
        outline(img, 6, 5, 4, 6, GREY_L)
        rect(img, 7, 8 if not on else 6, 2, 2, GREY)
        dot(img, 7, 8 if not on else 6, GREY_L)
        save(img, "block/light_switch%s.png" % ("_on" if on else ""))


def paperwork():
    # The post on the table: three letters, the top one with a red band across it.
    img = new()
    fill(img, (0, 0, 0, 0))
    rect(img, 3, 2, 11, 8, PAPER_S)
    rect(img, 1, 5, 12, 8, PAPER)
    outline(img, 1, 5, 12, 8, PAPER_S)
    rect(img, 2, 7, 6, 1, STAMP)
    for y in (9, 10, 11):
        hline(img, 2, y, 8 - (y - 9) * 2, INK_L)
    rect(img, 6, 9, 9, 6, PAPER)
    outline(img, 6, 9, 9, 6, PAPER_S)
    rect(img, 7, 10, 4, 2, (220, 230, 240))
    hline(img, 7, 13, 6, INK_L)
    save(img, "block/bills.png")
    # The calendar: a picture at the top, a grid, a date circled twice.
    img = new()
    rect(img, 4, 2, 8, 12, PAPER)
    outline(img, 4, 2, 8, 12, PAPER_S)
    grad_v(img, 5, 3, 6, 3, (120, 170, 220), (200, 220, 240))
    hline(img, 5, 5, 6, (90, 140, 80))
    for y in range(7, 13, 2):
        for x in range(5, 11, 2):
            dot(img, x, y, INK_L)
    ring(img, 9, 10, 1, STAMP)
    dot(img, 9, 10, INK)
    save(img, "block/calendar.png")
    # One sheet out of a printer: a header, lines, a signature.
    img = new()
    rect(img, 3, 2, 10, 13, PAPER)
    outline(img, 3, 2, 10, 13, PAPER_S)
    rect(img, 4, 3, 8, 1, INK)
    for y in range(5, 12):
        hline(img, 4, y, 6 + (y % 3), INK_L)
    hline(img, 4, 13, 7, INK)
    dot(img, 9, 12, INK)
    dot(img, 10, 12, INK)
    save(img, "block/printout.png")


def dog():
    # The carrier: beige plastic, vent slots down the sides, a wire door on the front, a handle on top.
    beige = (214, 198, 168)
    img = new()
    fill(img, beige)
    noise(img, 0, 0, 16, 16, 0.04)
    for y in range(3, 11, 2):
        hline(img, 3, y, 10, shade(beige, 0.65))
    outline(img, 0, 0, 16, 16, shade(beige, 0.85))
    save(img, "block/dog_carrier_side.png")
    img = new()
    fill(img, beige)
    noise(img, 0, 0, 16, 16, 0.04)
    rect(img, 4, 6, 8, 3, shade(beige, 0.75))
    rect(img, 5, 7, 6, 1, shade(beige, 0.6))
    outline(img, 0, 0, 16, 16, shade(beige, 0.85))
    save(img, "block/dog_carrier_top.png")
    for occupied in (False, True):
        img = new()
        fill(img, beige)
        noise(img, 0, 0, 16, 16, 0.04)
        rect(img, 2, 2, 12, 12, (40, 36, 34))
        if occupied:
            rect(img, 4, 5, 8, 8, FUR)
            rect(img, 5, 4, 2, 2, FUR_D)
            rect(img, 9, 4, 2, 2, FUR_D)
            dot(img, 6, 7, BLACK)
            dot(img, 9, 7, BLACK)
            rect(img, 7, 9, 2, 2, (70, 44, 30))
            dot(img, 7, 11, (200, 120, 120))
        for x in range(2, 14, 3):
            vline(img, x, 2, 12, WIRE)
        for y in range(2, 14, 3):
            hline(img, 2, y, 12, WIRE)
        hline(img, 2, 13, 12, WIRE)
        vline(img, 13, 2, 12, WIRE)
        outline(img, 0, 0, 16, 16, shade(beige, 0.85))
        save(img, "block/dog_carrier_front%s.png" % ("_dog" if occupied else ""))
    # The bowl, from above: a steel rim and what is left of dinner.
    img = new()
    disc(img, 8, 8, 4, CHROME)
    disc(img, 8, 8, 3, (120, 90, 60))
    for (x, y) in ((6, 7), (8, 6), (9, 9), (7, 9)):
        dot(img, x, y, (170, 130, 80))
    save(img, "block/dog_bowl.png")
    img = new()
    fill(img, CHROME)
    noise(img, 0, 0, 16, 16, 0.04)
    hline(img, 0, 0, 16, shade(CHROME, 1.15))
    save(img, "block/steel.png")


def kitchen():
    # The fridge: cream, a freezer door on top, long handles.
    for name, (w, h) in (("fridge_front", (16, 16)),):
        img = new()
        fill(img, (236, 234, 226))
        noise(img, 0, 0, 16, 16, 0.03)
        hline(img, 0, 5, 16, GREY)
        vline(img, 2, 1, 3, GREY_D)
        vline(img, 2, 7, 8, GREY_D)
        outline(img, 0, 0, 16, 16, GREY_L)
        # A note stuck to the door, and a magnet.
        rect(img, 8, 8, 4, 3, PAPER)
        dot(img, 9, 9, INK_L)
        dot(img, 10, 9, INK_L)
        dot(img, 11, 3, STAMP)
        save(img, "block/%s.png" % name)
    img = new()
    fill(img, (236, 234, 226))
    noise(img, 0, 0, 16, 16, 0.03)
    outline(img, 0, 0, 16, 16, GREY_L)
    save(img, "block/fridge_side.png")
    # Counters: cupboard doors below a laminate top.
    img = new()
    fill(img, CREAM_D)
    noise(img, 0, 0, 16, 16, 0.04)
    bevel(img, 1, 1, 6, 14, shade(CREAM_D, 1.02), 1.12, 0.8)
    bevel(img, 9, 1, 6, 14, shade(CREAM_D, 1.02), 1.12, 0.8)
    rect(img, 5, 7, 1, 3, GREY_D)
    rect(img, 10, 7, 1, 3, GREY_D)
    save(img, "block/counter_front.png")
    img = new()
    fill(img, CREAM_D)
    noise(img, 0, 0, 16, 16, 0.04)
    save(img, "block/counter_side.png")
    img = new()
    fill(img, LAMINATE)
    for _ in range(30):
        dot(img, rnd.randrange(16), rnd.randrange(16), shade(LAMINATE, 0.9))
    for _ in range(12):
        dot(img, rnd.randrange(16), rnd.randrange(16), shade(LAMINATE, 1.05))
    outline(img, 0, 0, 16, 16, shade(LAMINATE, 0.85))
    save(img, "block/counter_top.png")
    # The sink top: a steel basin sunk into the laminate.
    img = new()
    fill(img, LAMINATE)
    for _ in range(20):
        dot(img, rnd.randrange(16), rnd.randrange(16), shade(LAMINATE, 0.9))
    rect(img, 2, 3, 12, 10, CHROME)
    rect(img, 3, 4, 10, 8, CHROME_D)
    disc(img, 8, 8, 1, BLACK)
    outline(img, 0, 0, 16, 16, shade(LAMINATE, 0.85))
    save(img, "block/sink_top.png")
    # The hob: four rings on black enamel.
    img = new()
    fill(img, (40, 40, 42))
    noise(img, 0, 0, 16, 16, 0.04)
    for (cx, cy) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        ring(img, cx, cy, 2, GREY)
        dot(img, cx, cy, GREY_D)
    outline(img, 0, 0, 16, 16, GREY_D)
    save(img, "block/stove_top.png")
    img = new()
    fill(img, (236, 234, 226))
    noise(img, 0, 0, 16, 16, 0.03)
    rect(img, 2, 3, 12, 9, (50, 50, 54))
    rect(img, 3, 4, 10, 7, (36, 36, 40))
    hline(img, 2, 13, 12, GREY_D)
    for x in (4, 7, 10):
        dot(img, x, 1, GREY_D)
    save(img, "block/stove_front.png")
    # The radio, redone: a walnut box with a brass dial and a cloth grille.
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=4)
    rect(img, 2, 2, 7, 9, (200, 184, 140))
    for y in range(3, 11):
        for x in range(3, 9):
            if (x + y) % 2:
                dot(img, x, y, (160, 146, 110))
    rect(img, 10, 3, 4, 4, BRASS)
    dot(img, 11, 4, BRASS_D)
    dot(img, 12, 5, BRASS_D)
    rect(img, 10, 8, 4, 2, PAPER_S)
    hline(img, 10, 9, 4, STAMP)
    hline(img, 2, 13, 12, BRASS_D)
    save(img, "block/radio_front.png")
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=4)
    rect(img, 2, 2, 7, 9, (200, 184, 140))
    for y in range(3, 11):
        for x in range(3, 9):
            if (x + y) % 2:
                dot(img, x, y, (160, 146, 110))
    rect(img, 10, 3, 4, 4, BRASS)
    dot(img, 11, 4, BRASS_D)
    dot(img, 12, 5, BRASS_D)
    rect(img, 10, 8, 4, 2, (255, 214, 120))
    hline(img, 10, 9, 4, STAMP)
    hline(img, 2, 13, 12, BRASS_D)
    save(img, "block/radio_front_lit.png")
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=5, vertical=True)
    save(img, "block/radio_side.png")
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=5)
    rect(img, 6, 6, 4, 4, BRASS_D)
    save(img, "block/radio_top.png")
    # The mug: white with a blue band, a handle region on the right.
    img = new()
    rect(img, 0, 0, 12, 16, WHITE)
    noise(img, 0, 0, 12, 16, 0.03)
    rect(img, 0, 4, 12, 3, (70, 110, 170))
    hline(img, 0, 0, 12, GREY_L)
    rect(img, 12, 0, 4, 16, WHITE)
    outline(img, 12, 0, 4, 16, GREY_L)
    # The inside, seen from above: a ring of coffee.
    save(img, "block/mug.png")
    img = new()
    disc(img, 8, 8, 6, WHITE)
    disc(img, 8, 8, 4, (86, 56, 34))
    save(img, "block/mug_top.png")
    # The telephone: a cream handset on a base with a keypad.
    img = new()
    fill(img, (226, 220, 200))
    noise(img, 0, 0, 16, 16, 0.04)
    for y in range(4, 13, 3):
        for x in range(3, 12, 3):
            rect(img, x, y, 2, 2, GREY_D)
    outline(img, 0, 0, 16, 16, shade((226, 220, 200), 0.8))
    save(img, "block/telephone.png")
    img = new()
    fill(img, (226, 220, 200))
    noise(img, 0, 0, 16, 16, 0.04)
    rect(img, 1, 6, 3, 4, (60, 60, 64))
    rect(img, 12, 6, 3, 4, (60, 60, 64))
    outline(img, 0, 0, 16, 16, shade((226, 220, 200), 0.8))
    save(img, "block/telephone_handset.png")


def new_from(painter):
    img = new()
    painter(img)
    return img


def bedroom():
    # The wardrobe: two tall walnut doors with brass handles.
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=6, vertical=True)
    vline(img, 8, 0, 16, WALNUT_D)
    outline(img, 0, 0, 16, 16, WALNUT_D)
    bevel(img, 1, 1, 6, 14, WALNUT, 1.12, 0.85)
    bevel(img, 9, 1, 6, 14, WALNUT, 1.12, 0.85)
    rect(img, 6, 7, 1, 2, BRASS)
    rect(img, 9, 7, 1, 2, BRASS)
    save(img, "block/wardrobe_front.png")
    # The bedside cabinet: a drawer over a door.
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT, WALNUT_D, count=5)
    bevel(img, 1, 1, 14, 5, WALNUT, 1.12, 0.85)
    bevel(img, 1, 7, 14, 8, WALNUT, 1.12, 0.85)
    rect(img, 7, 3, 2, 1, BRASS)
    rect(img, 7, 10, 2, 1, BRASS)
    save(img, "block/nightstand_front.png")
    # The desk lamp, redone: a conical green shade, a brass arm, a heavy base. Regions: shade 0..16 x 0..6
    # (outside), 0..16 x 6..8 (inside, lit), arm 7..9 x 8..14, base 0..16 x 14..16.
    img = new()
    rect(img, 0, 0, 16, 6, (40, 96, 70))
    noise(img, 0, 0, 16, 6, 0.05)
    hline(img, 0, 0, 16, (60, 130, 96))
    rect(img, 0, 6, 16, 2, (255, 236, 180))
    rect(img, 7, 8, 2, 6, BRASS)
    rect(img, 0, 14, 16, 2, BRASS_D)
    hline(img, 0, 14, 16, BRASS)
    save(img, "block/desk_lamp.png")
    # The photograph: two people and a dog on a beach, in a walnut frame.
    img = new()
    rect(img, 3, 3, 10, 10, WALNUT_D)
    outline(img, 3, 3, 10, 10, WALNUT_L)
    grad_v(img, 4, 4, 8, 4, (120, 170, 220), (200, 220, 240))
    rect(img, 4, 8, 8, 4, (222, 200, 150))
    rect(img, 6, 6, 1, 5, (60, 60, 80))
    dot(img, 6, 5, SKIN)
    rect(img, 9, 6, 1, 5, (160, 60, 60))
    dot(img, 9, 5, SKIN)
    rect(img, 7, 10, 2, 1, FUR)
    save(img, "block/photo_frame.png")
    # The clock, twice: quarter past nine, and twenty to two.
    for late in (False, True):
        img = new()
        disc(img, 8, 8, 6, BLACK)
        disc(img, 8, 8, 5, WHITE)
        for (x, y) in ((8, 4), (12, 8), (8, 12), (4, 8)):
            dot(img, x, y, INK)
        if late:
            rect(img, 8, 6, 1, 3, INK)
            rect(img, 8, 8, 1, 1, INK)
            rect(img, 5, 9, 3, 1, INK)
            dot(img, 4, 10, INK)
        else:
            rect(img, 8, 8, 4, 1, INK)
            rect(img, 6, 6, 1, 3, INK)
            dot(img, 7, 7, INK)
        dot(img, 8, 8, STAMP)
        save(img, "block/wall_clock%s.png" % ("_late" if late else ""))
    # The snow globe: a glass ball on a walnut base, something small and white inside.
    img = new()
    disc(img, 8, 5, 5, GLASS)
    disc(img, 8, 5, 4, (196, 226, 236, 160))
    rect(img, 7, 4, 2, 3, WHITE)
    dot(img, 6, 7, WHITE)
    dot(img, 9, 7, WHITE)
    for (x, y) in ((5, 3), (10, 2), (11, 6), (4, 6)):
        dot(img, x, y, WHITE)
    rect(img, 4, 10, 8, 4, WALNUT)
    hline(img, 4, 10, 8, WALNUT_L)
    hline(img, 4, 13, 8, WALNUT_D)
    save(img, "block/snow_globe.png")
    # The plant: a terracotta pot and a fern.
    img = new()
    rect(img, 4, 9, 8, 6, (176, 96, 66))
    hline(img, 4, 9, 8, (200, 120, 90))
    hline(img, 4, 14, 8, (130, 70, 48))
    rect(img, 5, 8, 6, 1, (70, 50, 36))
    save(img, "block/houseplant_pot.png")
    img = new()
    for (x0, y0, dx) in ((7, 8, -1), (8, 8, 1), (7, 7, -2), (8, 7, 2), (7, 5, -1), (8, 5, 1)):
        x, y = x0, y0
        for i in range(5):
            dot(img, x, y, LEAF if i % 2 else shade(LEAF, 0.85))
            x += dx
            y -= 1
            if x < 0 or x > 15 or y < 0:
                break
    rect(img, 7, 3, 2, 8, shade(LEAF, 0.7))
    dot(img, 7, 2, LEAF)
    dot(img, 8, 1, LEAF)
    save(img, "block/houseplant_leaves.png")


def bathroom():
    img = new()
    fill(img, PORCELAIN)
    noise(img, 0, 0, 16, 16, 0.02)
    hline(img, 0, 15, 16, PORCELAIN_S)
    vline(img, 15, 0, 16, PORCELAIN_S)
    save(img, "block/porcelain.png")
    img = new()
    fill(img, PORCELAIN)
    outline(img, 0, 0, 16, 16, PORCELAIN_S)
    disc(img, 8, 8, 5, PORCELAIN_S)
    disc(img, 8, 8, 3, WATER)
    save(img, "block/toilet_seat.png")
    # The bath, from above, in two halves: the rim runs round the outside and stops at the shared edge. The
    # left half (as seen from the front) has its neighbour to the west, at u=0, so the water runs off that
    # edge and the rim is at u=15; the right half is the mirror of it.
    for side in ("left", "right"):
        img = new()
        fill(img, PORCELAIN)
        rect(img, 1, 2, 14, 12, WATER)
        noise(img, 1, 2, 14, 12, 0.04)
        hline(img, 3, 5, 6, shade(WATER, 1.15))
        if side == "left":
            rect(img, 0, 2, 1, 12, WATER)
            vline(img, 15, 0, 16, PORCELAIN_S)
        else:
            rect(img, 15, 2, 1, 12, WATER)
            vline(img, 0, 0, 16, PORCELAIN_S)
        hline(img, 0, 0, 16, shade(PORCELAIN, 1.02))
        hline(img, 0, 15, 16, PORCELAIN_S)
        save(img, "block/bath_top_%s.png" % side)
    # The basin from above, and the mirror.
    img = new()
    fill(img, PORCELAIN)
    disc(img, 8, 9, 5, PORCELAIN_S)
    disc(img, 8, 9, 4, shade(PORCELAIN_S, 0.9))
    dot(img, 8, 9, GREY_D)
    rect(img, 7, 1, 2, 3, CHROME)
    outline(img, 0, 0, 16, 16, PORCELAIN_S)
    save(img, "block/basin_top.png")
    img = new()
    rect(img, 3, 2, 10, 12, CHROME_D)
    grad_v(img, 4, 3, 8, 10, (200, 214, 222), (150, 166, 178))
    for i in range(6):
        dot(img, 5 + i, 3 + i, (236, 244, 248))
    dot(img, 11, 11, (236, 244, 248))
    save(img, "block/mirror.png")
    img = new()
    fill(img, CHROME)
    noise(img, 0, 0, 16, 16, 0.05)
    hline(img, 0, 0, 16, shade(CHROME, 1.2))
    vline(img, 15, 0, 16, CHROME_D)
    save(img, "block/chrome.png")


def suitcase():
    # Leather, stitched round the edge, brass corners. Region map: the whole face.
    leather = (112, 70, 44)
    img = new()
    fill(img, leather)
    noise(img, 0, 0, 16, 16, 0.06)
    for x in range(1, 15, 2):
        dot(img, x, 1, shade(leather, 1.4))
        dot(img, x, 14, shade(leather, 1.4))
    for y in range(1, 15, 2):
        dot(img, 1, y, shade(leather, 1.4))
        dot(img, 14, y, shade(leather, 1.4))
    for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rect(img, x, y, 2, 2, BRASS)
    save(img, "block/suitcase_side.png")
    # The lid outside: two straps and a paper tag on a string.
    img = new()
    fill(img, leather)
    noise(img, 0, 0, 16, 16, 0.06)
    for x in (4, 11):
        vline(img, x, 0, 16, shade(leather, 0.7))
        dot(img, x, 7, BRASS)
    rect(img, 6, 9, 4, 3, PAPER)
    hline(img, 7, 10, 2, INK_L)
    dot(img, 6, 8, PAPER_S)
    for (x, y) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rect(img, x, y, 2, 2, BRASS)
    save(img, "block/suitcase_top.png")
    # Inside the base: folded clothes.
    img = new()
    fill(img, (196, 176, 140))
    rect(img, 1, 1, 7, 6, (90, 110, 160))
    hline(img, 1, 4, 7, (70, 90, 140))
    rect(img, 8, 1, 7, 7, (220, 220, 214))
    hline(img, 8, 4, 7, (190, 190, 186))
    rect(img, 1, 8, 9, 7, (150, 60, 60))
    hline(img, 1, 11, 9, (120, 44, 44))
    rect(img, 10, 9, 5, 5, (60, 60, 66))
    outline(img, 0, 0, 16, 16, shade(leather, 0.9))
    save(img, "block/suitcase_inside.png")
    # Inside the lid: the lining and its elastic pocket.
    img = new()
    fill(img, (196, 176, 140))
    noise(img, 0, 0, 16, 16, 0.03)
    rect(img, 2, 8, 12, 6, (176, 156, 120))
    hline(img, 2, 8, 12, (130, 110, 80))
    outline(img, 0, 0, 16, 16, shade(leather, 0.9))
    save(img, "block/suitcase_lining.png")
    # The cardboard box: tape across the side, a word in marker, the flaps open on top.
    img = new()
    fill(img, CARDBOARD)
    noise(img, 0, 0, 16, 16, 0.05)
    rect(img, 0, 3, 16, 3, TAPE)
    text(img, 2, 9, "DESK", INK)
    outline(img, 0, 0, 16, 16, CARDBOARD_D)
    save(img, "block/cardboard_side.png")
    img = new()
    fill(img, CARDBOARD_D)
    rect(img, 0, 0, 16, 5, CARDBOARD)
    rect(img, 0, 11, 16, 5, CARDBOARD)
    rect(img, 0, 0, 4, 16, shade(CARDBOARD, 0.95))
    rect(img, 12, 0, 4, 16, shade(CARDBOARD, 0.95))
    noise(img, 0, 0, 16, 16, 0.05)
    rect(img, 5, 6, 6, 4, (60, 60, 66))
    rect(img, 6, 7, 3, 2, PAPER)
    save(img, "block/cardboard_top.png")


# --------------------------------------------------------------------------------------
# The office
# --------------------------------------------------------------------------------------
def office():
    img = new()
    fill(img, LAMINATE)
    noise(img, 0, 0, 16, 16, 0.03)
    outline(img, 0, 0, 16, 16, shade(LAMINATE, 0.85))
    save(img, "block/desk_top.png")
    img = new()
    fill(img, GREY)
    noise(img, 0, 0, 16, 16, 0.04)
    rect(img, 6, 6, 4, 1, GREY_D)
    outline(img, 0, 0, 16, 16, GREY_D)
    save(img, "block/desk_panel.png")
    img = new()
    fill(img, GREY_D)
    noise(img, 0, 0, 16, 16, 0.04)
    vline(img, 0, 0, 16, GREY)
    save(img, "block/desk_leg.png")
    # A keyboard, from above.
    img = new()
    fill(img, (50, 50, 56))
    for y in range(1, 15, 3):
        for x in range(1, 15, 2):
            dot(img, x, y, (90, 90, 98))
            dot(img, x, y + 1, (74, 74, 82))
    rect(img, 4, 13, 8, 2, (90, 90, 98))
    save(img, "block/keyboard.png")
    # The chair: black mesh, a grey frame.
    img = new()
    fill(img, (40, 40, 44))
    for y in range(0, 16, 2):
        for x in range(0, 16, 2):
            dot(img, x + (y // 2) % 2, y, (58, 58, 64))
    outline(img, 0, 0, 16, 16, (30, 30, 34))
    save(img, "block/office_chair.png")
    img = new()
    fill(img, GREY_D)
    noise(img, 0, 0, 16, 16, 0.04)
    hline(img, 0, 0, 16, GREY)
    save(img, "block/chair_metal.png")
    # The monitor: frame, back, and three screens (the saver moves).
    img = new()
    fill(img, PLASTIC)
    noise(img, 0, 0, 16, 16, 0.04)
    outline(img, 0, 0, 16, 16, shade(PLASTIC, 1.3))
    dot(img, 13, 13, (60, 200, 90))
    save(img, "block/monitor_frame.png")
    img = new()
    fill(img, PLASTIC)
    noise(img, 0, 0, 16, 16, 0.04)
    outline(img, 0, 0, 16, 16, shade(PLASTIC, 1.3))
    rect(img, 1, 1, 14, 12, SCREEN_OFF)
    for i in range(5):
        dot(img, 3 + i, 3 + i, shade(SCREEN_OFF, 1.5))
    save(img, "block/monitor_off.png")
    img = new()
    fill(img, PLASTIC)
    outline(img, 0, 0, 16, 16, shade(PLASTIC, 1.3))
    rect(img, 1, 1, 14, 12, (232, 232, 228))
    rect(img, 1, 1, 14, 2, (44, 52, 80))
    for y in range(4, 11):
        hline(img, 2, y, 6 + (y * 5) % 7, INK_L)
    hline(img, 2, 11, 5, INK)
    rect(img, 9, 10, 5, 2, (200, 60, 60))
    dot(img, 13, 6, (44, 52, 80))
    save(img, "block/monitor_contract.png")
    strip = new(16, 64)
    for i in range(4):
        f = new()
        fill(f, PLASTIC)
        outline(f, 0, 0, 16, 16, shade(PLASTIC, 1.3))
        rect(f, 1, 1, 14, 12, (10, 12, 18))
        cx = 3 + (i * 3) % 9
        cy = 3 + (i * 2) % 7
        rect(f, cx, cy, 3, 3, (40, 200, 220))
        dot(f, cx + 1, cy + 1, (10, 12, 18))
        strip.paste(f, (0, i * 16))
    save(strip, "block/monitor_saver.png")
    mcmeta("block/monitor_saver.png", 10)
    # The filing cabinet: three drawers with label slots.
    img = new()
    fill(img, GREY)
    noise(img, 0, 0, 16, 16, 0.04)
    for y in (1, 6, 11):
        bevel(img, 1, y, 14, 4, GREY, 1.15, 0.75)
        rect(img, 4, y + 1, 5, 1, WHITE)
        rect(img, 10, y + 1, 3, 2, GREY_D)
    save(img, "block/filing_front.png")
    img = new()
    fill(img, GREY)
    noise(img, 0, 0, 16, 16, 0.04)
    outline(img, 0, 0, 16, 16, GREY_D)
    save(img, "block/filing_side.png")
    # The water cooler: regions, bottle 0..16 x 0..8 (blue, a bubble), base 0..16 x 8..16 (white, a tap).
    img = new()
    rect(img, 0, 0, 16, 8, (140, 190, 224))
    noise(img, 0, 0, 16, 8, 0.05)
    hline(img, 0, 2, 16, (190, 226, 244))
    dot(img, 5, 5, (220, 240, 250))
    dot(img, 10, 3, (220, 240, 250))
    rect(img, 0, 8, 16, 8, WHITE)
    noise(img, 0, 8, 16, 8, 0.02)
    rect(img, 6, 10, 4, 2, (60, 120, 200))
    rect(img, 10, 10, 2, 2, (200, 60, 60))
    outline(img, 0, 8, 16, 8, GREY_L)
    save(img, "block/water_cooler.png")
    # The whiteboard: a frame, a chart going up, a word, a number circled.
    img = new()
    fill(img, GREY_L)
    rect(img, 1, 1, 14, 14, WHITE)
    text(img, 2, 2, "Q3", (40, 60, 160))
    for i in range(8):
        dot(img, 2 + i, 12 - i // 2 - (i % 3 == 0), (200, 40, 40))
    hline(img, 2, 13, 9, INK_L)
    vline(img, 2, 6, 8, INK_L)
    ring(img, 12, 6, 2, (40, 120, 60))
    text(img, 11, 4, "7", INK)
    save(img, "block/whiteboard.png")
    # The printer: grey, a tray slot, one green light, a sheet on top.
    img = new()
    fill(img, GREY_L)
    noise(img, 0, 0, 16, 16, 0.03)
    rect(img, 3, 6, 10, 2, (60, 60, 66))
    dot(img, 13, 2, (60, 200, 90))
    outline(img, 0, 0, 16, 16, GREY)
    save(img, "block/printer.png")


# --------------------------------------------------------------------------------------
# The bar
# --------------------------------------------------------------------------------------
def bar():
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT_D, shade(WALNUT_D, 0.7), count=6)
    hline(img, 0, 3, 16, shade(WALNUT_L, 0.9))
    hline(img, 0, 4, 16, shade(WALNUT_L, 0.7))
    save(img, "block/bar_top.png")
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT_D, shade(WALNUT_D, 0.7), count=6, vertical=True)
    bevel(img, 1, 2, 6, 11, WALNUT_D, 1.2, 0.75)
    bevel(img, 9, 2, 6, 11, WALNUT_D, 1.2, 0.75)
    hline(img, 0, 14, 16, BRASS)
    save(img, "block/bar_front.png")
    img = new()
    grain(img, 0, 0, 16, 16, WALNUT_D, shade(WALNUT_D, 0.7), count=6, vertical=True)
    save(img, "block/bar_side.png")
    # The stool: red leather from above, a stitched ring.
    img = new()
    disc(img, 8, 8, 5, RED_LEATHER)
    noise(img, 0, 0, 16, 16, 0.05)
    ring(img, 8, 8, 5, RED_LEATHER_D)
    ring(img, 8, 8, 3, shade(RED_LEATHER, 1.15))
    save(img, "block/stool_seat.png")
    # The pint, full and empty. Regions: glass 6..10 x 9..16.
    for full in (True, False):
        img = new()
        if full:
            rect(img, 6, 9, 4, 7, AMBER)
            noise(img, 6, 9, 4, 7, 0.06)
            rect(img, 6, 9, 4, 2, FOAM)
            vline(img, 6, 9, 7, shade(AMBER, 1.25))
            vline(img, 9, 9, 7, AMBER_D)
            hline(img, 6, 15, 4, AMBER_D)
        else:
            rect(img, 6, 9, 4, 7, (206, 226, 232, 120))
            vline(img, 6, 9, 7, (236, 246, 250, 200))
            hline(img, 6, 15, 4, (206, 226, 232, 200))
            hline(img, 7, 14, 2, (220, 200, 140, 200))
        save(img, "block/pint_%s.png" % ("full" if full else "empty"))
    # The board: twenty segments, alternating, with the doubles and trebles in red and green.
    import math
    img = new()
    px = img.load()
    for yy in range(16):
        for xx in range(16):
            dx, dy = xx - 7.5, yy - 7.5
            d = math.hypot(dx, dy)
            if d > 7.5:
                continue
            seg = int(((math.atan2(dy, dx) + math.pi) / (2 * math.pi)) * 20) % 2
            if d > 6.6:
                col = BOARD_R if seg else BOARD_G
            elif d > 4.6:
                col = BOARD_K if seg else CORK
            elif d > 3.9:
                col = BOARD_R if seg else BOARD_G
            elif d > 1.3:
                col = BOARD_K if seg else CORK
            elif d > 0.8:
                col = BOARD_G
            else:
                col = BOARD_R
            px[xx, yy] = rgba(col)
    for k in range(20):
        a = k * math.pi / 10
        for r_ in range(2, 8):
            x = int(7.5 + math.cos(a) * r_)
            y = int(7.5 + math.sin(a) * r_)
            if k % 5 == 0 and 0 <= x < 16 and 0 <= y < 16:
                dot(img, x, y, WIRE)
    save(img, "block/dartboard.png")
    # A dart, side on, for the three in the board: needle, barrel, shaft, flight.
    img = new()
    rect(img, 0, 7, 5, 1, CHROME)
    rect(img, 5, 6, 5, 3, BRASS)
    rect(img, 5, 7, 5, 1, BRASS_D)
    rect(img, 10, 7, 3, 1, (40, 40, 44))
    rect(img, 13, 5, 3, 5, STAMP)
    rect(img, 13, 7, 3, 1, shade(STAMP, 0.7))
    save(img, "block/dart.png")
    # And as an item, on the diagonal.
    img = new()
    for i in range(4):
        dot(img, 1 + i, 14 - i, CHROME)
    for i in range(5):
        rect(img, 5 + i, 10 - i, 1, 2, BRASS)
        dot(img, 5 + i, 9 - i, BRASS_D)
    for i in range(3):
        dot(img, 10 + i, 5 - i, (40, 40, 44))
    for i in range(3):
        rect(img, 12 + i, 3 - i, 2, 2, STAMP)
        dot(img, 12 + i, 1 - i + 1, shade(STAMP, 1.2))
    save(img, "item/dart.png")
    # The back bar: a mirror, a shelf, bottles of five colours. Regions: mirror is the whole face; the
    # bottles use 0..16 x 0..8 as their sides.
    img = new()
    grad_v(img, 0, 0, 16, 16, (150, 166, 178), (60, 70, 80))
    for i in range(5):
        dot(img, 3 + i * 2, 3 + i, (236, 244, 248))
    hline(img, 0, 15, 16, WALNUT_D)
    save(img, "block/back_bar.png")
    img = new()
    bottles = ((60, 120, 80), (150, 40, 40), (200, 150, 60), (60, 60, 120), (200, 200, 190))
    for i, col in enumerate(bottles):
        x = i * 3
        rect(img, x, 0, 3, 8, col)
        vline(img, x, 0, 8, shade(col, 1.3))
        rect(img, x + 1, 0, 1, 2, shade(col, 0.6))
        rect(img, x, 4, 3, 2, PAPER)
    rect(img, 0, 8, 16, 8, WALNUT)
    noise(img, 0, 8, 16, 8, 0.04)
    save(img, "block/bottles.png")
    # The tap handle: black with a brass ferrule.
    img = new()
    rect(img, 6, 0, 4, 10, (36, 36, 40))
    rect(img, 6, 10, 4, 2, BRASS)
    rect(img, 6, 12, 4, 4, CHROME)
    vline(img, 6, 0, 16, shade((36, 36, 40), 1.6))
    save(img, "block/tap_handle.png")
    # The neon: tube letters on a dark backing, lit. Region map: the whole face; the backing is drawn in.
    img = new()
    rect(img, 1, 4, 14, 8, (20, 16, 22))
    text(img, 2, 5, "BAR", NEON)
    for (x, y) in ((2, 10), (6, 10), (10, 10)):
        hline(img, x, y, 3, NEON_D)
    save(img, "block/neon_sign.png")


# --------------------------------------------------------------------------------------
# The people: no faces, three sets of clothes.
# --------------------------------------------------------------------------------------
def box_faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def person(name, skin, shirt, trousers, shoes, detail=None):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    parts = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "right_arm": (40, 16, 4, 12, 4),
             "left_arm": (32, 48, 4, 12, 4), "right_leg": (0, 16, 4, 12, 4), "left_leg": (16, 48, 4, 12, 4)}
    for part, (u, v, w, h, d) in parts.items():
        base = skin if part == "head" else shirt if part in ("body", "right_arm", "left_arm") else trousers
        for face_name, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
            col = shade(base, 0.75) if face_name in ("top", "bottom") else shade(base, 0.9 if face_name in ("left", "right") else 1.0)
            rect(img, fx, fy, fw, fh, col)
            noise(img, fx, fy, fw, fh, 0.03)
        if part in ("right_leg", "left_leg"):
            fx, fy, fw, fh = box_faces(u, v, w, h, d)["front"]
            rect(img, fx, fy + fh - 2, fw, 2, shoes)
            fx, fy, fw, fh = box_faces(u, v, w, h, d)["bottom"]
            rect(img, fx, fy, fw, fh, shoes)
        if part in ("right_arm", "left_arm"):
            fx, fy, fw, fh = box_faces(u, v, w, h, d)["front"]
            rect(img, fx, fy + fh - 3, fw, 3, skin)
    # The one feature: the front of the head is a shade darker, which reads as a face turned away.
    fx, fy, fw, fh = box_faces(0, 0, 8, 8, 8)["front"]
    rect(img, fx + 1, fy + 2, 6, 4, shade(skin, 0.8))
    if detail:
        detail(img)
    save(img, "entity/crew_%s.png" % name)


def people():
    # The figure at home: a shadow, in something soft, barefoot. Almost no colour anywhere.
    person("figure", (52, 52, 60), (70, 70, 80), (58, 58, 68), (48, 48, 56))
    # The coworker: a pale shirt, a tie, dark trousers.
    def tie(img):
        fx, fy, fw, fh = box_faces(16, 16, 8, 12, 4)["front"]
        rect(img, fx + 3, fy, 2, 7, (120, 40, 50))
        rect(img, fx + 2, fy, 4, 1, (90, 30, 40))
    person("coworker", (96, 96, 104), (214, 216, 220), (54, 56, 64), (30, 30, 34), tie)
    # The barman: a black shirt under an apron, sleeves rolled.
    def apron(img):
        fx, fy, fw, fh = box_faces(16, 16, 8, 12, 4)["front"]
        rect(img, fx + 1, fy + 3, 6, 9, (94, 70, 50))
        rect(img, fx + 1, fy + 3, 6, 1, (120, 92, 66))
    person("barman", (110, 104, 100), (36, 36, 40), (40, 40, 46), (30, 30, 34), apron)


def paint_all():
    wallpapers()
    couch()
    tables()
    pizza()
    television()
    lights()
    paperwork()
    dog()
    kitchen()
    bedroom()
    bathroom()
    suitcase()
    office()
    bar()
    people()
    print("the house, the office and the bar painted")


if __name__ == "__main__":
    paint_all()
    sys.exit(0)
