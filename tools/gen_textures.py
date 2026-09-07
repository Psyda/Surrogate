#!/usr/bin/env python3
"""Generates every texture used by the Surrogate mod.

Run from the mod root:  python3 tools/gen_textures.py
Requires Pillow.  Output goes to src/main/resources/assets/surrogate/textures.
The 16x16 item art is authored as ASCII pixel maps below so it stays editable in a text editor.
The robot skin, block faces and the mod icon are painted procedurally.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "surrogate")
TEX = os.path.join(ROOT, "textures")

PAL = {
    ".": None,
    "k": (31, 35, 40, 255),      # outline
    "x": (17, 24, 39, 255),      # near black
    "d": (59, 63, 70, 255),      # dark steel
    "m": (107, 114, 128, 255),   # mid steel
    "l": (156, 163, 175, 255),   # light steel
    "h": (209, 213, 219, 255),   # highlight
    "w": (249, 250, 251, 255),   # white
    "b": (146, 64, 14, 255),     # copper dark
    "c": (217, 119, 6, 255),     # copper
    "C": (245, 158, 11, 255),    # copper light
    "n": (14, 116, 144, 255),    # cyan dark
    "y": (34, 211, 238, 255),    # cyan
    "Y": (165, 243, 252, 255),   # cyan light
    "r": (220, 38, 38, 255),     # red
    "R": (248, 113, 113, 255),   # red light
    "o": (161, 98, 7, 255),      # gold dark
    "g": (234, 179, 8, 255),     # gold
    "G": (253, 224, 71, 255),    # gold light
    "t": (22, 105, 122, 255),    # teal
    "T": (42, 157, 181, 255),    # teal light
    "p": (34, 197, 94, 255),     # green
    "P": (134, 239, 172, 255),   # green light
    "s": (74, 55, 40, 255),      # scorched
    "S": (124, 74, 36, 255),     # rust
    "F": (37, 99, 235, 255),     # diamond dark
    "D": (96, 165, 250, 255),    # diamond
    "E": (191, 219, 254, 255),   # diamond light
    "q": (75, 85, 99, 255),      # steel shadow
    "u": (55, 48, 163, 255),     # indigo dark (card)
    "U": (99, 102, 241, 255),    # indigo
    "a": (10, 12, 16, 255),      # panel black
}


def from_map(rows):
    h = len(rows)
    w = len(rows[0])
    for r in rows:
        assert len(r) == w, ("bad row width", r, len(r), w)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            col = PAL[ch]
            if col is not None:
                px[x, y] = col
    return img


# Textures that are painted by hand and must not be written over. The call that would have made one is
# still made, and only the save is skipped: this file shares one random stream in file order, so deleting or
# commenting out a painting call re-rolls the noise of every painted texture after it and produces a diff of
# sixty-odd PNGs that have nothing to do with the change.
HAND_PAINTED = {"entity/crew_ferreira.png"}


def save(img, rel):
    if rel.replace("\\", "/") in HAND_PAINTED:
        print("kept  ", rel, "(hand painted)")
        return
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


# --------------------------------------------------------------------------------------
# Item icons (16x16)
# --------------------------------------------------------------------------------------
ITEMS = {}

ITEMS["robot_chassis"] = [
    "................",
    ".......kk.......",
    ".......ky.......",
    "....kkkkkkkk....",
    "...kmlllllllmk..",
    "...klhhhhhhhlk..",
    "..kkkkkkkkkkkkk.",
    "..kmlllllllllmk.",
    "..klhnyyyyyynlk.",
    "..klhnYYYyyynlk.",
    "..klhhkkkkkkhlk.",
    "..kmllllpllllmk.",
    "..kkkkkkkkkkkkk.",
    ".kddkkddddddkddk",
    ".kdmkkmmmmmmkkmk",
    ".kkkk.kkkkkk.kkk",
]

ITEMS["scrap_chassis"] = [
    "................",
    ".......kk.......",
    "......kk........",
    "....kkkkkkk.....",
    "...ksddSdddsk...",
    "...kdssssddsk...",
    "..kkkkkkkkkkkk..",
    "..ksdSSdddssdk..",
    "..kdsxxxxxxsdk..",
    "..kdSxkxxkxsSk..",
    "..kssskkkkkssk..",
    "..kdSsssdSsssk..",
    "..kkkkkkkkkkkkk.",
    ".ksskkSsssskkssk",
    ".kssk.ksssk.kssk",
    ".kkk...kkk...kk.",
]

ITEMS["rocket_kit"] = [
    "................",
    ".......kk.......",
    "......krrk......",
    "......krrk......",
    ".....khwwhk.....",
    ".....khwwhk.....",
    "....kkkkkkkk....",
    "...kmwwwwwwmk...",
    "...kwhwwwwhwk...",
    "...kwwwwwwwwk...",
    "...kmwwwwwwmk...",
    "...kdGGGGGGdk...",
    "...kkkkkkkkkk...",
    "....kdd..ddk....",
    "................",
    "................",
]

ITEMS["repair_kit"] = [
    "................",
    "................",
    ".....kkkkkk.....",
    "....kmmkkmmk....",
    ".kkkkkkkkkkkkkk.",
    ".klhhhhhhhhhhlk.",
    ".klhlllrrlllhlk.",
    ".klhlllrrlllhlk.",
    ".klhlrrrrrrlhlk.",
    ".klhlrrrrrrlhlk.",
    ".klhlllrrlllhlk.",
    ".klhlllrrlllhlk.",
    ".klhhhhhhhhhhlk.",
    ".kmdddddddddddk.",
    ".kkkkkkkkkkkkkk.",
    "................",
]

ITEMS["power_cell"] = [
    "................",
    "......kkkk......",
    ".....kCCCCk.....",
    "....kkccccbk....",
    "....kddddddk....",
    "....kdnyyndk....",
    "....kdyYYydk....",
    "....kdyYYydk....",
    "....kdnyyndk....",
    "....kdddddak....",
    "....kdwwwwak....",
    "....kdddddak....",
    "....kdddddak....",
    "....kbccccbk....",
    ".....kkkkkk.....",
    "................",
]

ITEMS["wrench"] = [
    "................",
    "...........kkk..",
    "..........khhlk.",
    ".........klhkkk.",
    "........klhk.k..",
    ".......klhk.....",
    "......klhk......",
    ".....klhk.......",
    "....klhk........",
    "...klhkk........",
    "..khlmk.........",
    ".kkhlkk.........",
    ".khhlk..........",
    ".klmk...........",
    ".kkk............",
    "................",
]

ITEMS["uplink_card"] = [
    "................",
    "..kkkkkkkkkkkk..",
    ".kuUUUUUUUUUUuk.",
    ".kUxxxxxxxxxxUk.",
    ".kUxggkkggxxxUk.",
    ".kUxgkkkkgxyxUk.",
    ".kUxggkkggxyyUk.",
    ".kUxxxxxxxxyyyk.",
    ".kUxhhhhhhxxxUk.",
    ".kUxxxxxxxxxxUk.",
    ".kUxhhhhhhhhxUk.",
    ".kUxxxxxxxxxxUk.",
    ".kUxhhhhxxxxxUk.",
    ".kuUUUUUUUUUUuk.",
    "..kkkkkkkkkkkk..",
    "................",
]

ITEMS["plating_mk1"] = [
    "................",
    "....kkkkkkkkk...",
    "...kmlllllllmk..",
    "..kmlhhhhhhhlmk.",
    "..klhkhhhhhkhlk.",
    "..klhhhhhhhhhlk.",
    "..klhhhhhhhhhlk.",
    "..klhhhhkhhhhlk.",
    "..klhhhhhhhhhlk.",
    "..klhhhhhhhhhlk.",
    "..klhkhhhhhkhlk.",
    "..kmlhhhhhhhlmk.",
    "...kmlllllllmk..",
    "....kdddddddk...",
    ".....kkkkkkk....",
    "................",
]

ITEMS["plating_mk2"] = [
    "................",
    "....kkkkkkkkk...",
    "...kFDDDDDDDFk..",
    "..kFDEEEEEEEDFk.",
    "..kDEkEEEEEkEDk.",
    "..kDEEEEEEEEEDk.",
    "..kDEEEDDDEEEDk.",
    "..kDEEEDkDEEEDk.",
    "..kDEEEDDDEEEDk.",
    "..kDEEEEEEEEEDk.",
    "..kDEkEEEEEkEDk.",
    "..kFDEEEEEEEDFk.",
    "...kFDDDDDDDFk..",
    "....kFFFFFFFk...",
    ".....kkkkkkk....",
    "................",
]

ITEMS["battery_upgrade"] = [
    "................",
    "..kkkk....kkkk..",
    ".kCCCCk..kCCCCk.",
    ".kddddk..kddddk.",
    ".kdyydk..kdyydk.",
    ".kdYYdk..kdYYdk.",
    ".kdyydk..kdyydk.",
    ".kddddkkkkddddk.",
    ".kddddkGGkddddk.",
    ".kdwwdkGGkdwwdk.",
    ".kddddkkkkddddk.",
    ".kddddk..kddddk.",
    ".kbccbk..kbccbk.",
    ".kkkkkk..kkkkkk.",
    "................",
    "................",
]

ITEMS["robot_core"] = [
    "................",
    "....kkkkkkkk....",
    "...kgGGGGGGgk...",
    "..kgGkkkkkkGgk..",
    "..kGknnyynnkGk..",
    "..kGknyYYynkGk..",
    "..kGkyYwwYykGk..",
    "..kGkyYwwYykGk..",
    "..kGknyYYynkGk..",
    "..kGknnyynnkGk..",
    "..kgGkkkkkkGgk..",
    "...kogggggggk...",
    "....kkkkkkkk....",
    "................",
    "................",
    "................",
]

ITEMS["servo_motor"] = [
    "................",
    "................",
    "....kkkkkkk.....",
    "...kmllllllk....",
    "..kkkkkkkkkkk...",
    "..kbccccccccbk..",
    "..kcCCCCCCCCck..",
    "..kbccccccccbk..",
    "..kcCCCCCCCCck..",
    "..kbccccccccbk..",
    "..kkkkkkkkkkkkkk",
    "...kdmmmmmmmkhlk",
    "...kdddddddkkkk.",
    "....kkkkkkk.....",
    "................",
    "................",
]


ITEMS["sulfur"] = [
    "................",
    "................",
    "......kkk.......",
    ".....kGGgk......",
    "....kGGGggk.....",
    "...kGGgggggk....",
    "...kGgggoogk....",
    "..kGGgggooook...",
    "..kGggggoooook..",
    "..kgggooooookk..",
    "...koooooookk...",
    "...kkoooookk....",
    "....kkkkkkk.....",
    "................",
    "................",
    "................",
]

ITEMS["mining_drill"] = [
    "................",
    "..........kkkk..",
    ".........kmlllk.",
    "........kmlhhlk.",
    ".......kmlhhlk..",
    "......kkllllk...",
    ".....kmkkkkk....",
    "....kdmhk.......",
    "...kdmhkk.......",
    "..kdmhkkk.......",
    ".kdmhkkk........",
    ".kmhkkk.........",
    ".kkkkkyk........",
    "...kkkyk........",
    "....kyyk........",
    "....kkkk........",
]

ITEMS["arc_cutter"] = [
    "..............k.",
    ".............kYk",
    "............kYyk",
    "...........kYyk.",
    "..........kYyk..",
    ".........kYyk...",
    "........kYyk....",
    ".......kYyk.....",
    "......kYyk......",
    ".....kyyk.......",
    "..kkkkyk........",
    ".kddkkk.........",
    ".kdmdkk.........",
    ".kdddmk.........",
    "..kddk..........",
    "...kk...........",
]

ITEMS["atmo_scanner"] = [
    "................",
    "....kkkkkkkk....",
    "...kmllllllmk...",
    "...klaaaaaalk...",
    "...klapppaalk...",
    "...klapPpaalk...",
    "...klaaaaaalk...",
    "...klaaaaaalk...",
    "...kmllllllmk...",
    "...kdmmmmmmdk...",
    "...kdddrdddk....",
    "...kkkkkkkkk....",
    ".....kdmdk......",
    ".....kdmdk......",
    ".....kkkkk......",
    "................",
]

ITEMS["airlock_door"] = [
    "....kkkkkkkk....",
    "....kmllllmk....",
    "....klhhhhlk....",
    "....klhyyhlk....",
    "....klhyyhlk....",
    "....klhhhhlk....",
    "....kmllllmk....",
    "....kkkkkkkk....",
    "....kmllllmk....",
    "....klhhhhlk....",
    "....klhhhhlk....",
    "....klggh.lk....",
    "....klhhhhlk....",
    "....klhhhhlk....",
    "....kmllllmk....",
    "....kkkkkkkk....",
]


ITEMS["cargo_bay"] = [
    "................",
    ".kkkkkkkkkkkkkk.",
    ".kmllllllllllmk.",
    ".klhhhhhhhhhhlk.",
    ".klhkkkkkkkkhlk.",
    ".klhkddddddkhlk.",
    ".klhkdSSSSdkhlk.",
    ".klhkdSyySdkhlk.",
    ".klhkdSyySdkhlk.",
    ".klhkdSSSSdkhlk.",
    ".klhkddddddkhlk.",
    ".klhkkkkkkkkhlk.",
    ".klhhhhhhhhhhlk.",
    ".kmddddddddddmk.",
    ".kkkkkkkkkkkkkk.",
    "................",
]

ITEMS["fabricator"] = [
    "................",
    "....kkkkkkkk....",
    "...kmllllllmk...",
    "..kmlkkkkkklmk..",
    "..klkhhkhhkhlk..",
    "..klkhhkhhkhlk..",
    "..klkkkkkkkklk..",
    "..klkhhkyykhlk..",
    "..klkhhkYykhlk..",
    "..klkkkkkkkklk..",
    "..klkhhkhhkhlk..",
    "..klkhhkhhkhlk..",
    "..kmlkkkkkklmk..",
    "...kmddddddmk...",
    "....kkkkkkkk....",
    "................",
]

ITEMS["locked_bay"] = [
    "................",
    "................",
    "....kkkkkkkk....",
    "...kqddddddqk...",
    "...kdqqqqqqdk...",
    "...kdqkkkkqdk...",
    "...kdqkddkqdk...",
    "...kdqkkkkqdk...",
    "...kdqqkkqqdk...",
    "...kdqqkkqqdk...",
    "...kdqqqqqqdk...",
    "...kqddddddqk...",
    "....kkkkkkkk....",
    "................",
    "................",
    "................",
]

ITEMS["field_radio"] = [
    "..........k.....",
    "..........kl....",
    "..........kl....",
    "..kkkkkkkkkkkk..",
    ".kmllllllllllmk.",
    ".klaaaaaakkkklk.",
    ".klapppaakyyklk.",
    ".klaaaaaakkkklk.",
    ".klhhhhhhhhhhlk.",
    ".kldkdkdkdkdklk.",
    ".klkdkdkdkdkdlk.",
    ".kldkdkdkdkdklk.",
    ".klhhhhhhhhhhlk.",
    ".kmllllllcllmk..",
    ".kkkkkkkkkkkkk..",
    "................",
]


ITEMS["tomato_seeds"] = [
    "................",
    "....kkkkkkkk....",
    "...khhhhhhhhk...",
    "...khwwwwwwhk...",
    "...khwwppwwhk...",
    "...khwrRrpwhk...",
    "...khwrRRrwhk...",
    "...khwrrrrwhk...",
    "...khwwrrwwhk...",
    "...khwwwwwwhk...",
    "...khlkkkklhk...",
    "...khwwwwwwhk...",
    "...khwwwwwwhk...",
    "...kkkkkkkkkk...",
    "................",
    "................",
]


ITEMS["crawler_blueprint"] = [
    "................",
    "..kkkkkkkkkkkk..",
    "..kFFFFFFFFFFk..",
    "..kFEEEEEEEEFk..",
    "..kFFFFFFFFFFk..",
    "..kFFEEEEEEFFk..",
    "..kFEFFFFFFEFk..",
    "..kFEFEEEEFEFk..",
    "..kFEFFFFFFEFk..",
    "..kFFEEEEEEFFk..",
    "..kFEFFFFFFEFk..",
    "..kFEEEEEEEEFk..",
    "..kFFFFFFFFFFk..",
    "..kFEEEFFFEEFk..",
    "..kkkkkkkkkkkk..",
    "................",
]

ITEMS["crawler_kit"] = [
    "................",
    "................",
    "....kkkkkkkkk...",
    "...kmllllllllk..",
    "..kmyyyllllllkk.",
    ".kmnnnllllllllmk",
    ".klllllllllllllk",
    ".klggkggkggkgglk",
    ".kdddddddddddddk",
    ".kkkkkkkkkkkkkkk",
    ".kxkxkxkxkxkxkxk",
    ".kcxxxcxxxcxxxck",
    ".kxkxkxkxkxkxkxk",
    "..kkkkkkkkkkkkk.",
    "................",
    "................",
]

for name, rows in ITEMS.items():
    save(from_map(rows), os.path.join("item", name + ".png"))

# Mod icon: chassis icon upscaled with a dark rounded backdrop.
icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
bg = Image.new("RGBA", (128, 128), (17, 24, 39, 255))
mask = Image.new("L", (128, 128), 0)
mpx = mask.load()
for y in range(128):
    for x in range(128):
        cx = min(max(x, 12), 115)
        cy = min(max(y, 12), 115)
        if (x - cx) ** 2 + (y - cy) ** 2 <= 144:
            mpx[x, y] = 255
icon.paste(bg, (0, 0), mask)
chassis = from_map(ITEMS["robot_chassis"]).resize((112, 112), Image.NEAREST)
icon.alpha_composite(chassis, (8, 8))
icon.save(os.path.join(ROOT, "icon.png"))
print("wrote icon.png")

# --------------------------------------------------------------------------------------
# Procedural helpers
# --------------------------------------------------------------------------------------
rng = random.Random(1337)


def rect(img, x, y, w, h, col):
    px = img.load()
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= xx < img.width and 0 <= yy < img.height:
                px[xx, yy] = col


def shade(col, k):
    return (max(0, min(255, int(col[0] * k))), max(0, min(255, int(col[1] * k))), max(0, min(255, int(col[2] * k))), col[3])


def panel(img, x, y, w, h, base, noise=0.08, rivets=True, outline=True):
    """Steel panel with light noise, a bevel and optional rivets."""
    px = img.load()
    for yy in range(h):
        for xx in range(w):
            k = 1.0 + rng.uniform(-noise, noise)
            px[x + xx, y + yy] = shade(base, k)
    if outline:
        rect(img, x, y, w, 1, shade(base, 1.25))
        rect(img, x, y, 1, h, shade(base, 1.15))
        rect(img, x, y + h - 1, w, 1, shade(base, 0.6))
        rect(img, x + w - 1, y, 1, h, shade(base, 0.7))
    if rivets and w >= 5 and h >= 5:
        for (rx, ry) in [(x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)]:
            px[rx, ry] = shade(base, 0.55)


def box_faces(u, v, w, h, d):
    """Rects of the faces of a Minecraft cuboid unwrap placed at (u,v)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


STEEL = (112, 118, 130, 255)
STEEL_DARK = (66, 70, 78, 255)
BURNT = (52, 46, 44, 255)
CYAN = PAL["y"]
CYAN_DARK = PAL["n"]
CYAN_LIGHT = PAL["Y"]
COPPER = PAL["c"]
HAZARD_Y = (234, 179, 8, 255)
HAZARD_K = (31, 35, 40, 255)


def paint_robot(variant, tint=None, hull=None):
    """variant: online | offline | wreck.  Layout must match RobotEntityModel.java.

    tint replaces the cyan trim and the status light, and hull shifts the steel, so an NPC's chassis reads as
    somebody else's from across a pad rather than only up close.
    """
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    base = BURNT if variant == "wreck" else (hull or STEEL)
    dark = shade(BURNT, 0.8) if variant == "wreck" else (shade(hull, 0.6) if hull else STEEL_DARK)
    trim = tint or CYAN
    trim_dark = shade(trim, 0.45)
    trim_light = shade(trim, 1.5)

    # body 12x9x12 @ (0,0)
    for name, (fx, fy, fw, fh) in box_faces(0, 0, 12, 9, 12).items():
        panel(img, fx, fy, fw, fh, base, rivets=name in ("front", "back", "left", "right"))
    # hazard stripe along the bottom of the front face, status light top-left
    fx, fy, fw, fh = box_faces(0, 0, 12, 9, 12)["front"]
    for i in range(fw):
        rect(img, fx + i, fy + fh - 2, 1, 1, HAZARD_Y if (i // 2) % 2 == 0 else HAZARD_K)
    light = {"online": tint or PAL["p"], "offline": (90, 30, 30, 255), "wreck": (40, 20, 20, 255)}[variant]
    rect(img, fx + 1, fy + 1, 2, 1, light)
    # vent slits on the back
    bx, by, bw, bh = box_faces(0, 0, 12, 9, 12)["back"]
    for i in range(2, bh - 2, 2):
        rect(img, bx + 2, by + i, bw - 4, 1, shade(base, 0.55))

    # arms 2x6x2 @ (48,0) and (56,0)
    for u in (48, 56):
        for name, (fx, fy, fw, fh) in box_faces(u, 0, 2, 6, 2).items():
            panel(img, fx, fy, fw, fh, dark, rivets=False, outline=False)

    # head 8x3x8 @ (0,21)
    for name, (fx, fy, fw, fh) in box_faces(0, 21, 8, 3, 8).items():
        panel(img, fx, fy, fw, fh, shade(base, 0.9), rivets=False)
    # lens 4x2x1 @ (32,21)
    lens = {"online": trim, "offline": (40, 60, 70, 255), "wreck": (30, 30, 34, 255)}[variant]
    lens_hi = {"online": trim_light, "offline": (70, 90, 100, 255), "wreck": (60, 55, 55, 255)}[variant]
    for name, (fx, fy, fw, fh) in box_faces(32, 21, 4, 2, 1).items():
        rect(img, fx, fy, fw, fh, trim_dark if variant == "online" else (25, 30, 36, 255))
    lf = box_faces(32, 21, 4, 2, 1)["front"]
    rect(img, lf[0], lf[1], lf[2], lf[3], lens)
    rect(img, lf[0] + 1, lf[1], 1, 1, lens_hi)
    # antenna 1x3x1 @ (44,21)
    for name, (fx, fy, fw, fh) in box_faces(44, 21, 1, 3, 1).items():
        rect(img, fx, fy, fw, fh, dark)
    rect(img, 45, 22, 1, 1, PAL["r"] if variant == "online" else dark)

    # treads 4x4x12 @ (0,32) and (0,48)
    for v in (32, 48):
        faces = box_faces(0, v, 4, 4, 12)
        for name, (fx, fy, fw, fh) in faces.items():
            panel(img, fx, fy, fw, fh, shade(dark, 0.9), rivets=False, outline=False)
        # tread lugs on the long faces
        for name in ("right", "left", "top", "bottom"):
            fx, fy, fw, fh = faces[name]
            for i in range(0, fh if name in ("right", "left") else fw, 2):
                if name in ("right", "left"):
                    rect(img, fx, fy + i, fw, 1, shade(dark, 0.55))
                else:
                    rect(img, fx + i, fy, 1, fh, shade(dark, 0.55))
        # copper wheel hubs on the sides
        fx, fy, fw, fh = faces["front"]
        rect(img, fx + 1, fy + 1, 2, 2, COPPER)
        fx, fy, fw, fh = faces["back"]
        rect(img, fx + 1, fy + 1, 2, 2, COPPER)

    if variant == "wreck":
        # scorch marks and cracks
        px = img.load()
        for _ in range(140):
            x = rng.randrange(0, 64)
            y = rng.randrange(0, 64)
            if px[x, y][3] > 0:
                px[x, y] = shade(px[x, y], rng.uniform(0.3, 0.7))
        for _ in range(12):
            x = rng.randrange(0, 60)
            y = rng.randrange(0, 60)
            for i in range(rng.randrange(2, 5)):
                if px[x + i, y][3] > 0:
                    px[x + i, y] = (20, 18, 18, 255)
    return img


def paint_rocket():
    """The sample vehicle. UV layout must match RocketEntityModel.java (128x64)."""
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    hull = (222, 224, 230, 255)
    hull_dark = (150, 154, 164, 255)

    # skirt 10x6x10 @ (0,0): scorched steel at the business end
    for name, (fx, fy, fw, fh) in box_faces(0, 0, 10, 6, 10).items():
        panel(img, fx, fy, fw, fh, STEEL_DARK, noise=0.09, rivets=False)
    # the bell, seen from below
    bx, by, bw, bh = box_faces(0, 0, 10, 6, 10)["bottom"]
    rect(img, bx + 2, by + 2, bw - 4, bh - 4, BURNT)
    rect(img, bx + 3, by + 3, bw - 6, bh - 6, (30, 26, 24, 255))

    # body 8x24x8 @ (0,20): white hull, hazard band at the base, company stripe up the side
    for name, (fx, fy, fw, fh) in box_faces(0, 20, 8, 24, 8).items():
        panel(img, fx, fy, fw, fh, hull, noise=0.04, rivets=False)
        if name in ("front", "back", "left", "right"):
            for i in range(fw):
                rect(img, fx + i, fy + fh - 3, 1, 2, HAZARD_Y if (i // 2) % 2 == 0 else HAZARD_K)
            rect(img, fx + 1, fy + 4, 1, fh - 9, PAL["r"])
            rect(img, fx + fw - 2, fy + 4, 1, fh - 9, PAL["r"])
            rect(img, fx + 2, fy + 6, fw - 4, 1, hull_dark)
            rect(img, fx + 2, fy + 9, fw - 4, 1, hull_dark)

    # capsule 6x8x6 @ (56,20): the payload section, glassed and lit
    for name, (fx, fy, fw, fh) in box_faces(56, 20, 6, 8, 6).items():
        panel(img, fx, fy, fw, fh, STEEL, noise=0.05, rivets=False)
        if name in ("front", "back", "left", "right"):
            rect(img, fx + 1, fy + 2, fw - 2, 3, CYAN_DARK)
            rect(img, fx + 1, fy + 3, fw - 2, 1, CYAN)

    # nose 4x6x4 @ (56,0)
    for name, (fx, fy, fw, fh) in box_faces(56, 0, 4, 6, 4).items():
        panel(img, fx, fy, fw, fh, PAL["r"] if name != "top" else hull, noise=0.05, rivets=False)

    # fins 2x10x4 @ (48,0), one patch reused by all four
    for name, (fx, fy, fw, fh) in box_faces(48, 0, 2, 10, 4).items():
        panel(img, fx, fy, fw, fh, hull_dark, noise=0.05, rivets=False)
        if name in ("front", "back"):
            rect(img, fx, fy + fh - 3, fw, 1, PAL["r"])
    return img


def paint_company_ship():
    """The company ship. UV layout must match CompanyShipModel.java (256x256)."""
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    hull = (86, 92, 104, 255)
    hull_dark = (54, 58, 68, 255)
    lit = (255, 214, 130, 255)

    def plate(u, v, w, h, d, base, windows=False, stripe=False):
        for name, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
            panel(img, fx, fy, fw, fh, base if name != "bottom" else shade(base, 0.7), noise=0.05, rivets=False)
            # Panel seams, so a hull this big does not read as one flat slab.
            for i in range(4, fw, 9):
                rect(img, fx + i, fy, 1, fh, shade(base, 0.82))
            for i in range(4, fh, 7):
                rect(img, fx, fy + i, fw, 1, shade(base, 0.88))
            if stripe and name in ("left", "right"):
                rect(img, fx, fy + fh // 2, fw, 2, PAL["g"])
            if windows and name in ("left", "right"):
                for i in range(3, fw - 2, 4):
                    rect(img, fx + i, fy + 3, 2, 2, lit)

    plate(0, 0, 12, 6, 36, hull, stripe=True)       # spine
    plate(0, 100, 6, 4, 6, hull, stripe=True)       # prow
    plate(0, 60, 16, 5, 20, hull_dark)              # belly
    plate(0, 140, 18, 9, 6, hull_dark)              # engines
    plate(0, 180, 8, 2, 14, hull)                   # fins
    plate(0, 215, 6, 6, 12, hull, windows=True)     # tower

    # The engine bells, on the stern face of the engine block.
    ex, ey, ew, eh = box_faces(0, 140, 18, 9, 6)["back"]
    for i in range(4):
        bx = ex + 1 + i * 4
        rect(img, bx, ey + 2, 3, 5, (30, 26, 24, 255))
        rect(img, bx + 1, ey + 3, 1, 3, PAL["c"])
    return img


save(paint_company_ship(), "entity/company_ship.png")
save(paint_rocket(), "entity/rocket.png")
save(paint_robot("online"), "entity/robot.png")
save(paint_robot("offline"), "entity/robot_offline.png")
save(paint_robot("wreck"), "entity/robot_wreck.png")
# The NPCs' chassis. Order and names must match RobotPaint.java.
save(paint_robot("online", tint=PAL["g"], hull=(120, 104, 78, 255)), "entity/robot_halloran.png")
save(paint_robot("online", tint=PAL["p"], hull=(92, 112, 96, 255)), "entity/robot_sorensen.png")
save(paint_robot("online", tint=PAL["U"], hull=(104, 100, 124, 255)), "entity/robot_okafor.png")
save(paint_robot("online", tint=PAL["Y"], hull=(74, 84, 110, 255)), "entity/robot_marsh.png")


def paint_crawler():
    """The crawler hull. Layout must match CrawlerEntityModel.java (half scale: one texel is an eighth of a
    block, so the panels are coarse and the details big)."""
    img = Image.new("RGBA", (256, 192), (0, 0, 0, 0))
    hull = shade(STEEL, 0.95)
    dark = STEEL_DARK

    def hull_box(u, v, w, h, d, windows=False, ring=False):
        faces = box_faces(u, v, w, h, d)
        for name, (fx, fy, fw, fh) in faces.items():
            panel(img, fx, fy, fw, fh, hull, rivets=fw >= 8 and fh >= 8)
        # a hazard stripe low on every side face
        for name in ("front", "back", "left", "right"):
            fx, fy, fw, fh = faces[name]
            if fh >= 6:
                for i in range(fw):
                    rect(img, fx + i, fy + fh - 2, 1, 1, HAZARD_Y if (i // 2) % 2 == 0 else HAZARD_K)
        if windows:
            # the cab: a band of glass across the front and down the sides, lit from inside
            fx, fy, fw, fh = faces["front"]
            rect(img, fx + 2, fy + 2, fw - 4, 4, CYAN_DARK)
            rect(img, fx + 3, fy + 3, fw - 6, 2, CYAN)
            for side in ("left", "right"):
                sx, sy, sw, sh = faces[side]
                rect(img, sx + 2, sy + 2, sw - 4, 4, CYAN_DARK)
                rect(img, sx + 3, sy + 3, sw - 6, 2, CYAN)
            # two headlamps
            rect(img, fx + 2, fy + fh - 5, 3, 2, PAL["G"])
            rect(img, fx + fw - 5, fy + fh - 5, 3, 2, PAL["G"])
        if ring:
            fx, fy, fw, fh = faces["back"]
            rect(img, fx, fy, fw, fh, dark)
            rect(img, fx + 2, fy + 2, fw - 4, fh - 4, shade(hull, 0.7))
            rect(img, fx + 4, fy + 4, fw - 8, fh - 8, HAZARD_K)
            for i in range(0, fw, 3):
                rect(img, fx + i, fy, 1, 1, COPPER)
                rect(img, fx + i, fy + fh - 1, 1, 1, COPPER)

    def tread_box(u, v, w, h, d):
        faces = box_faces(u, v, w, h, d)
        for name, (fx, fy, fw, fh) in faces.items():
            panel(img, fx, fy, fw, fh, shade(dark, 0.9), rivets=False, outline=False)
        for name in ("left", "right", "top", "bottom"):
            fx, fy, fw, fh = faces[name]
            if name in ("left", "right"):
                for i in range(0, fh, 2):
                    rect(img, fx, fy + i, fw, 1, shade(dark, 0.55))
            else:
                for i in range(0, fw, 2):
                    rect(img, fx + i, fy, 1, fh, shade(dark, 0.55))
        # road wheels along the outer face
        fx, fy, fw, fh = faces["left"] if u < 128 else faces["right"]
        for i in range(2, fw - 3, 6):
            rect(img, fx + i, fy + fh // 2 - 2, 4, 4, shade(hull, 0.8))
            rect(img, fx + i + 1, fy + fh // 2 - 1, 2, 2, COPPER)

    hull_box(0, 0, 40, 12, 24, windows=True)      # cab
    hull_box(128, 0, 40, 12, 16)                  # deck fore
    hull_box(128, 28, 40, 12, 16)                 # deck aft
    hull_box(0, 40, 24, 12, 28)                   # body fore
    hull_box(104, 64, 24, 12, 28)                 # body aft
    tread_box(0, 104, 8, 12, 32)                  # tread left fore
    tread_box(80, 104, 8, 12, 32)                 # tread left aft
    tread_box(160, 104, 8, 12, 32)                # tread right fore
    tread_box(0, 148, 8, 12, 32)                  # tread right aft
    hull_box(208, 64, 20, 18, 4, ring=True)       # docking ring
    # solar panels on the aft deck top
    for (u, v) in ((128, 0), (128, 28)):
        tx, ty, tw, th = box_faces(u, v, 40, 12, 16)["top"]
        for yy in range(2, th - 2, 3):
            for xx in range(2, tw - 2, 6):
                rect(img, tx + xx, ty + yy, 5, 2, PAL["F"])
                rect(img, tx + xx + 1, ty + yy, 1, 1, PAL["D"])
    return img


save(paint_crawler(), "entity/crawler.png")

# --------------------------------------------------------------------------------------
# Block textures (16x16 each; models pick faces from these)
# --------------------------------------------------------------------------------------


def tex16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


# Generic machine plating
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
rect(img, 7, 0, 1, 16, shade(STEEL, 0.7))
rect(img, 0, 7, 16, 1, shade(STEEL, 0.7))
save(img, "block/machine_plating.png")

# Dark frame metal
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.07)
save(img, "block/frame_metal.png")

# Chair cushion (padded, teal)
img = tex16()
TEAL = PAL["t"]
panel(img, 0, 0, 16, 16, TEAL, noise=0.05, rivets=False)
for y in range(0, 16, 4):
    rect(img, 0, y, 16, 1, shade(TEAL, 0.7))
for x in range(0, 16, 8):
    rect(img, x, 0, 1, 16, shade(TEAL, 0.75))
for y in range(1, 16, 4):
    for x in range(1, 16, 8):
        rect(img, x, y, 6, 1, shade(TEAL, 1.25))
save(img, "block/chair_cushion.png")

# Visor glass (cyan glow with reflection)
img = tex16()
panel(img, 0, 0, 16, 16, CYAN_DARK, noise=0.04, rivets=False)
rect(img, 2, 2, 12, 12, CYAN)
rect(img, 3, 3, 4, 1, CYAN_LIGHT)
rect(img, 3, 4, 1, 3, CYAN_LIGHT)
rect(img, 10, 10, 3, 1, shade(CYAN, 0.8))
save(img, "block/visor.png")

# Console screen (green readout)
img = tex16()
rect(img, 0, 0, 16, 16, PAL["a"])
rect(img, 0, 0, 16, 1, STEEL_DARK)
rect(img, 0, 15, 16, 1, STEEL_DARK)
rect(img, 0, 0, 1, 16, STEEL_DARK)
rect(img, 15, 0, 1, 16, STEEL_DARK)
for i, w in enumerate([8, 11, 6, 9, 12, 5]):
    rect(img, 2, 2 + i * 2, w, 1, PAL["p"] if i % 3 else PAL["P"])
save(img, "block/console_screen.png")

# Dock pad top: guide rails + hazard chevrons
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
for x in range(0, 16):
    rect(img, x, 0, 1, 2, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
    rect(img, x, 14, 1, 2, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
rect(img, 3, 3, 2, 10, shade(STEEL, 1.1))
rect(img, 11, 3, 2, 10, shade(STEEL, 1.1))
rect(img, 7, 6, 2, 4, CYAN_DARK)
rect(img, 7, 7, 2, 2, CYAN)
save(img, "block/dock_pad_top.png")

# Dock pillar front: charging column with lights and a coupler
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
rect(img, 2, 2, 12, 5, PAL["a"])
for i in range(4):
    rect(img, 3 + i * 3, 3, 2, 3, CYAN if i < 3 else PAL["p"])
rect(img, 4, 9, 8, 5, STEEL_DARK)
rect(img, 5, 10, 6, 3, COPPER)
rect(img, 6, 11, 4, 1, PAL["C"])
save(img, "block/dock_pillar_front.png")

# Dock pillar side/back: vents
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
for y in range(3, 14, 2):
    rect(img, 3, y, 10, 1, shade(STEEL, 0.55))
save(img, "block/dock_pillar_side.png")

# --------------------------------------------------------------------------------------
# Toxic Wastes update: terrain and base blocks
# --------------------------------------------------------------------------------------
SULFUR = (222, 196, 44, 255)
SULFUR_DARK = (150, 128, 20, 255)
ASH_COL = (92, 90, 86, 255)
CAUSTIC = (183, 170, 96, 255)
BASALT = (44, 42, 46, 255)


def grain(img, base, noise=0.08, speckle=None, count=0):
    panel(img, 0, 0, 16, 16, base, noise=noise, rivets=False, outline=False)
    px = img.load()
    for _ in range(count):
        px[rng.randrange(16), rng.randrange(16)] = speckle


img = tex16()
grain(img, CAUSTIC, noise=0.10, speckle=shade(CAUSTIC, 0.7), count=10)
grain_px = img.load()
for _ in range(6):
    grain_px[rng.randrange(16), rng.randrange(16)] = (206, 190, 70, 255)
save(img, "block/caustic_sand.png")

img = tex16()
grain(img, shade(CAUSTIC, 0.85), noise=0.05)
for y in (3, 8, 12):
    rect(img, 0, y, 16, 1, shade(CAUSTIC, 0.65))
rect(img, 5, 4, 1, 4, shade(CAUSTIC, 0.7))
rect(img, 11, 9, 1, 3, shade(CAUSTIC, 0.7))
save(img, "block/caustic_sandstone.png")

img = tex16()
grain(img, shade(CAUSTIC, 0.9), noise=0.06, speckle=shade(CAUSTIC, 0.7), count=8)
save(img, "block/caustic_sandstone_top.png")

img = tex16()
grain(img, ASH_COL, noise=0.12, speckle=(60, 58, 56, 255), count=14)
ash_px = img.load()
for _ in range(5):
    ash_px[rng.randrange(16), rng.randrange(16)] = (130, 128, 122, 255)
save(img, "block/ash.png")

img = tex16()
grain(img, shade(CAUSTIC, 0.8), noise=0.06)
for _ in range(9):
    x = rng.randrange(1, 14)
    y = rng.randrange(1, 14)
    rect(img, x, y, 2, 2, SULFUR)
    rect(img, x, y, 1, 1, (245, 224, 90, 255))
    rect(img, x + 1, y + 1, 1, 1, SULFUR_DARK)
save(img, "block/sulfur_crust.png")

img = tex16()
grain(img, (74, 58, 44, 255), noise=0.12, speckle=(48, 38, 30, 255), count=12)
for _ in range(7):
    x = rng.randrange(0, 13)
    y = rng.randrange(0, 14)
    w = rng.randrange(2, 4)
    rect(img, x, y, w, 1, shade(STEEL, rng.uniform(0.6, 1.1)))
    if rng.random() < 0.4:
        rect(img, x, y + 1, 1, 1, PAL["S"])
rect(img, 3, 9, 2, 2, COPPER)
save(img, "block/scrap_heap.png")

img = tex16()
grain(img, BASALT, noise=0.10, speckle=(30, 28, 32, 255), count=10)
save(img, "block/vent_side.png")

img = tex16()
grain(img, BASALT, noise=0.10, speckle=(30, 28, 32, 255), count=6)
rect(img, 4, 4, 8, 8, (22, 20, 22, 255))
rect(img, 5, 5, 6, 6, (12, 10, 12, 255))
rect(img, 6, 6, 4, 4, (120, 50, 10, 255))
rect(img, 7, 7, 2, 2, (220, 110, 20, 255))
for (x, y) in [(3, 7), (12, 8), (7, 3), (8, 12)]:
    rect(img, x, y, 1, 1, SULFUR_DARK)
save(img, "block/vent_top.png")

# Geyser: a wet throat instead of a dry one, sulfur crusting the rim, and the same again lit for the half of
# the cycle where something is on its way up it.
img = tex16()
grain(img, BASALT, noise=0.10, speckle=(30, 28, 32, 255), count=6)
for (x, y) in [(2, 5), (13, 6), (6, 2), (9, 13), (4, 12), (11, 3)]:
    rect(img, x, y, 1, 1, SULFUR_DARK)
rect(img, 3, 3, 10, 10, (26, 26, 24, 255))
rect(img, 4, 4, 8, 8, (16, 18, 18, 255))
rect(img, 5, 5, 6, 6, (30, 44, 40, 255))
rect(img, 6, 6, 4, 4, (46, 66, 56, 255))
rect(img, 7, 7, 2, 2, (20, 28, 26, 255))
for (x, y) in [(3, 4), (12, 11), (4, 11), (11, 4)]:
    rect(img, x, y, 1, 1, SULFUR)
save(img, "block/geyser_top.png")

img = tex16()
grain(img, shade(BASALT, 1.2), noise=0.10, speckle=(44, 36, 30, 255), count=6)
for (x, y) in [(2, 5), (13, 6), (6, 2), (9, 13)]:
    rect(img, x, y, 1, 1, SULFUR)
rect(img, 3, 3, 10, 10, (70, 34, 12, 255))
rect(img, 4, 4, 8, 8, (150, 66, 16, 255))
rect(img, 5, 5, 6, 6, (222, 120, 26, 255))
rect(img, 6, 6, 4, 4, (250, 196, 92, 255))
rect(img, 7, 7, 2, 2, (255, 246, 214, 255))
save(img, "block/geyser_top_hot.png")

# Geothermal tap: a bolted collar with a grille in it, the same on every face because the block is all cap.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
rect(img, 0, 0, 16, 1, shade(STEEL, 1.2))
rect(img, 0, 15, 16, 1, shade(STEEL, 0.6))
rect(img, 2, 2, 12, 12, shade(STEEL, 0.75))
rect(img, 3, 3, 10, 10, STEEL_DARK)
for y in (4, 6, 8, 10):
    rect(img, 4, y, 8, 1, shade(STEEL, 0.5))
    rect(img, 4, y + 1, 8, 1, (168, 92, 24, 255))
rect(img, 4, 12, 8, 1, shade(STEEL, 0.5))
for (x, y) in [(1, 2), (14, 2), (1, 13), (14, 13)]:
    rect(img, x, y, 1, 1, shade(STEEL, 0.45))
    rect(img, x, y - 1, 1, 1, shade(STEEL, 1.3))
save(img, "block/geothermal_tap.png")

# Hull plating: heavy panel with a seam and eight rivets
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
rect(img, 0, 0, 16, 1, shade(STEEL, 1.2))
rect(img, 0, 15, 16, 1, shade(STEEL, 0.6))
rect(img, 0, 0, 1, 16, shade(STEEL, 1.1))
rect(img, 15, 0, 1, 16, shade(STEEL, 0.7))
rect(img, 0, 8, 16, 1, shade(STEEL, 0.6))
rect(img, 0, 7, 16, 1, shade(STEEL, 1.15))
for (x, y) in [(2, 2), (13, 2), (2, 5), (13, 5), (2, 10), (13, 10), (2, 13), (13, 13)]:
    rect(img, x, y, 1, 1, shade(STEEL, 0.5))
    rect(img, x, y - 1, 1, 1, shade(STEEL, 1.3))
save(img, "block/hull_plating.png")

# Reinforced glass: transparent pane in a steel frame
img = tex16()
px = img.load()
for y in range(16):
    for x in range(16):
        px[x, y] = (170, 210, 220, 40)
rect(img, 0, 0, 16, 1, STEEL_DARK)
rect(img, 0, 15, 16, 1, STEEL_DARK)
rect(img, 0, 0, 1, 16, STEEL_DARK)
rect(img, 15, 0, 1, 16, STEEL_DARK)
rect(img, 2, 2, 3, 1, (230, 245, 250, 120))
rect(img, 2, 3, 1, 2, (230, 245, 250, 120))
for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
    rect(img, x, y, 1, 1, shade(STEEL, 0.8))
save(img, "block/reinforced_glass.png")

# Airlock door halves: 16x16 each, the bottom has a kick plate, the top a porthole
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14), (1, 7), (14, 7)]:
    rect(img, x, y, 1, 1, shade(STEEL, 0.5))
rect(img, 2, 9, 12, 6, STEEL_DARK)
for i in range(12):
    rect(img, 2 + i, 9, 1, 1, HAZARD_Y if (i // 2) % 2 == 0 else HAZARD_K)
rect(img, 12, 3, 2, 3, shade(STEEL, 0.55))
save(img, "block/airlock_door_bottom.png")

img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14), (1, 7), (14, 7)]:
    rect(img, x, y, 1, 1, shade(STEEL, 0.5))
rect(img, 4, 3, 8, 8, STEEL_DARK)
rect(img, 5, 4, 6, 6, CYAN_DARK)
rect(img, 6, 5, 4, 4, shade(CYAN, 0.6))
rect(img, 6, 5, 2, 1, CYAN_LIGHT)
rect(img, 3, 13, 10, 1, shade(STEEL, 0.6))
save(img, "block/airlock_door_top.png")

# Life support unit: vents on the sides, a fan grille and status lamp on the front
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
for y in range(3, 14, 2):
    rect(img, 3, y, 10, 1, shade(STEEL, 0.55))
save(img, "block/life_support_side.png")

img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
rect(img, 3, 3, 10, 10, STEEL_DARK)
rect(img, 4, 4, 8, 8, shade(STEEL, 0.8))
rect(img, 5, 5, 6, 6, STEEL_DARK)
rect(img, 7, 7, 2, 2, COPPER)
save(img, "block/life_support_top.png")

for status, lamp in [("off", (60, 30, 30, 255)), ("ok", PAL["p"]), ("leak", PAL["r"])]:
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
    rect(img, 2, 5, 12, 9, STEEL_DARK)
    for y in range(6, 14, 2):
        rect(img, 3, y, 10, 1, shade(STEEL_DARK, 0.6))
    for x in range(3, 13, 3):
        rect(img, x, 6, 1, 7, shade(STEEL, 0.9))
    rect(img, 2, 1, 12, 3, PAL["a"])
    rect(img, 3, 2, 2, 1, lamp)
    rect(img, 6, 2, 7, 1, CYAN_DARK if status == "off" else CYAN)
    if status == "leak":
        rect(img, 6, 2, 7, 1, PAL["R"])
    save(img, "block/life_support_front_%s.png" % status)

# Solar collector top: a grid of cells, brighter when generating
for lit in (False, True):
    img = tex16()
    base = (60, 90, 170, 255) if lit else (40, 60, 120, 255)
    panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.03, rivets=False)
    for cy in range(0, 3):
        for cx in range(0, 3):
            x = 1 + cx * 5
            y = 1 + cy * 5
            rect(img, x, y, 4, 4, base)
            rect(img, x, y, 4, 1, shade(base, 1.4))
            rect(img, x, y, 1, 4, shade(base, 1.25))
            if lit:
                rect(img, x + 1, y + 1, 1, 1, (200, 220, 255, 255))
    save(img, "block/solar_top%s.png" % ("_lit" if lit else ""))


# Power conduit: dark frame with a copper bus crossing each face
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
rect(img, 6, 0, 4, 16, shade(COPPER, 0.75))
rect(img, 0, 6, 16, 4, shade(COPPER, 0.75))
rect(img, 7, 0, 2, 16, COPPER)
rect(img, 0, 7, 16, 2, COPPER)
rect(img, 7, 7, 2, 2, PAL["C"])
save(img, "block/power_conduit.png")


# Decon shower: pipes on the sides, a nozzle plate underneath that glows while spraying
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
rect(img, 0, 6, 16, 4, shade(STEEL_DARK, 1.1))
rect(img, 0, 7, 16, 2, COPPER)
rect(img, 4, 2, 2, 12, shade(STEEL, 0.7))
rect(img, 10, 2, 2, 12, shade(STEEL, 0.7))
save(img, "block/decon_shower_side.png")

for lit in (False, True):
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
    rect(img, 3, 3, 10, 10, shade(STEEL, 0.9))
    for yy in range(4, 12, 2):
        for xx in range(4, 12, 2):
            rect(img, xx, yy, 1, 1, CYAN if lit else (30, 40, 48, 255))
    rect(img, 7, 7, 2, 2, CYAN_LIGHT if lit else (40, 50, 58, 255))
    save(img, "block/decon_shower_nozzle%s.png" % ("_lit" if lit else ""))


def paint_survivor(name, suit, trim, prefix="survivor"):
    """A hazmat suit on the vanilla 64x64 player layout. Overlay layers stay empty."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    dark = shade(suit, 0.75)
    # head 8x8x8 at (0,0): a helmet with a visor on the front
    for face, (fx, fy, fw, fh) in box_faces(0, 0, 8, 8, 8).items():
        panel(img, fx, fy, fw, fh, dark if face in ("top", "bottom") else suit, noise=0.05, rivets=False, outline=False)
    fx, fy, fw, fh = box_faces(0, 0, 8, 8, 8)["front"]
    rect(img, fx + 1, fy + 2, 6, 4, (24, 30, 40, 255))
    rect(img, fx + 2, fy + 3, 2, 1, (120, 160, 190, 255))
    rect(img, fx + 1, fy + 6, 6, 1, trim)
    # body 8x12x4 at (16,16)
    for face, (fx, fy, fw, fh) in box_faces(16, 16, 8, 12, 4).items():
        panel(img, fx, fy, fw, fh, suit, noise=0.05, rivets=False, outline=False)
    fx, fy, fw, fh = box_faces(16, 16, 8, 12, 4)["front"]
    rect(img, fx, fy, fw, 1, trim)
    rect(img, fx + 3, fy + 1, 2, 10, dark)
    rect(img, fx + 1, fy + 3, 2, 2, trim)
    bx, by, bw, bh = box_faces(16, 16, 8, 12, 4)["back"]
    rect(img, bx + 1, by + 2, 6, 7, dark)
    rect(img, bx + 2, by + 3, 4, 5, STEEL_DARK)
    # arms and legs 4x12x4 at (40,16) (32,48) (0,16) (16,48)
    for (u, v) in [(40, 16), (32, 48), (0, 16), (16, 48)]:
        for face, (fx, fy, fw, fh) in box_faces(u, v, 4, 12, 4).items():
            panel(img, fx, fy, fw, fh, suit, noise=0.05, rivets=False, outline=False)
            if face in ("front", "back", "left", "right"):
                rect(img, fx, fy + 9, fw, 1, trim)
                rect(img, fx, fy + 10, fw, 2, dark)
    save(img, "entity/%s_%s.png" % (prefix, name))


paint_survivor("okafor", (222, 122, 47, 255), (250, 210, 90, 255))
paint_survivor("sorensen", (52, 96, 170, 255), (200, 210, 230, 255))
paint_survivor("tanaka", (58, 140, 90, 255), (230, 220, 120, 255))
paint_survivor("brandt", (120, 100, 70, 255), (190, 160, 110, 255))

# The Habitat Seven crew: the station lead in charcoal with red trim, the tech in pale grey with orange.
paint_survivor("halloran", (62, 70, 82, 255), (205, 62, 52, 255), prefix="crew")
paint_survivor("marsh", (152, 156, 162, 255), (232, 140, 40, 255), prefix="crew")




# --------------------------------------------------------------------------------------
# The Provender: the crew, the rebreather, the planet outside, and the prints on the walls
# --------------------------------------------------------------------------------------
from PIL import ImageDraw, ImageFont

ART = os.path.join(os.path.dirname(os.path.abspath(__file__)), "art")


def paint_person(name, suit, trim, skin, hair, prefix="crew"):
    """A flight suit and a bare head on the vanilla 64x64 layout: the crew of a ship do not wear helmets indoors."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    dark = shade(suit, 0.75)
    faces = box_faces(0, 0, 8, 8, 8)
    for face, (fx, fy, fw, fh) in faces.items():
        panel(img, fx, fy, fw, fh, skin, noise=0.03, rivets=False, outline=False)
    fx, fy, fw, fh = faces["top"]
    rect(img, fx, fy, fw, fh, hair)
    fx, fy, fw, fh = faces["back"]
    rect(img, fx, fy, fw, 5, hair)
    for face in ("left", "right"):
        fx, fy, fw, fh = faces[face]
        rect(img, fx, fy, fw, 2, hair)
        rect(img, fx, fy + 2, 1, 2, hair)
    fx, fy, fw, fh = faces["front"]
    rect(img, fx, fy, fw, 2, hair)
    white = (245, 245, 245, 255)
    iris = (40, 60, 90, 255)
    rect(img, fx + 1, fy + 4, 2, 1, white)
    rect(img, fx + 5, fy + 4, 2, 1, white)
    rect(img, fx + 2, fy + 4, 1, 1, iris)
    rect(img, fx + 5, fy + 4, 1, 1, iris)
    rect(img, fx + 1, fy + 3, 2, 1, shade(hair, 0.8))
    rect(img, fx + 5, fy + 3, 2, 1, shade(hair, 0.8))
    rect(img, fx + 3, fy + 6, 2, 1, shade(skin, 0.7))
    # body 8x12x4 at (16,16): collar, zip, badge
    for face, (fx, fy, fw, fh) in box_faces(16, 16, 8, 12, 4).items():
        panel(img, fx, fy, fw, fh, suit, noise=0.05, rivets=False, outline=False)
    fx, fy, fw, fh = box_faces(16, 16, 8, 12, 4)["front"]
    rect(img, fx, fy, fw, 1, trim)
    rect(img, fx + 3, fy + 1, 2, 11, dark)
    rect(img, fx + 4, fy + 1, 1, 11, shade(suit, 1.2))
    rect(img, fx + 1, fy + 2, 2, 2, trim)
    rect(img, fx, fy + 11, fw, 1, dark)
    bx, by, bw, bh = box_faces(16, 16, 8, 12, 4)["back"]
    rect(img, bx, by, bw, 1, trim)
    rect(img, bx + 2, by + 3, 4, 1, trim)
    # Limbs 4x12x4: right arm (40,16), left arm (32,48), right leg (0,16), left leg (16,48).
    # Arms end in bare hands; legs end in boots.
    for (u, v, arm) in [(40, 16, True), (32, 48, True), (0, 16, False), (16, 48, False)]:
        for face, (fx, fy, fw, fh) in box_faces(u, v, 4, 12, 4).items():
            panel(img, fx, fy, fw, fh, suit, noise=0.05, rivets=False, outline=False)
            if face in ("front", "back", "left", "right"):
                if arm:
                    rect(img, fx, fy + 9, fw, 3, skin)
                else:
                    rect(img, fx, fy + 10, fw, 2, dark)
            elif face == "bottom":
                rect(img, fx, fy, fw, fh, skin if arm else dark)
    save(img, "entity/%s_%s.png" % (prefix, name))


paint_person("castellanos", (36, 48, 78, 255), (220, 200, 150, 255), (166, 118, 86, 255), (30, 26, 24, 255))
# Ferreira's is hand painted; the call stays so the random stream does not move, and HAND_PAINTED above
# stops it being written over.
paint_person("ferreira", (222, 226, 230, 255), (60, 160, 170, 255), (120, 80, 60, 255), (20, 16, 14, 255))
paint_person("teague", (205, 110, 40, 255), (40, 40, 44, 255), (228, 194, 164, 255), (150, 90, 40, 255))
paint_person("sleeper", (170, 190, 178, 255), (90, 110, 100, 255), (200, 172, 150, 255), (70, 60, 50, 255))

# The rebreather item.
REBREATHER = [
    "................",
    "......kkkk......",
    ".....kmllmk.....",
    "....kmlhhlmk....",
    "....klhhhhlk....",
    "...kkkkkkkkkk...",
    "...kdnyyyyndk...",
    "...kdyYYYYydk...",
    "...kdnyyyyndk...",
    "...kkkkkkkkkk...",
    "....kmkkkkmk....",
    "...kmk....kmk...",
    "...kk......kk...",
    "..kmk......kmk..",
    "..kkk......kkk..",
    "................",
]
save(from_map(REBREATHER), "item/rebreather.png")
effect_icon = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
effect_icon.paste(from_map(REBREATHER), (1, 1))
save(effect_icon, "mob_effect/rebreather.png")


# --------------------------------------------------------------------------------------
# Sallow from orbit: a disc of ochre and sulfur with dust bands, transparent outside the limb.
# A painted planet in tools/art/sallow.png is used instead when one exists.
# --------------------------------------------------------------------------------------
def value_noise(size, freq, seed):
    prng = random.Random(seed)
    grid = Image.new("L", (freq, freq))
    gp = grid.load()
    for y in range(freq):
        for x in range(freq):
            gp[x, y] = prng.randint(0, 255)
    return grid.resize((size, size), Image.BICUBIC).load()


def paint_planet(size=256):
    art = os.path.join(ART, "sallow.png")
    if os.path.exists(art):
        src = Image.open(art).convert("RGBA")
        src.thumbnail((size, size), Image.LANCZOS)
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        img.paste(src, ((size - src.width) // 2, (size - src.height) // 2), src)
        return img
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    layers = [(value_noise(size, 5, 11), 0.45), (value_noise(size, 11, 12), 0.28), (value_noise(size, 23, 13), 0.17), (value_noise(size, 47, 14), 0.10)]
    ochre = (196, 148, 66)
    brown = (118, 78, 40)
    sulfur = (226, 196, 96)
    grey = (108, 104, 96)
    acid = (128, 146, 62)
    salt = (214, 208, 178)
    c = size / 2.0
    r = c - 1.5
    import math
    for y in range(size):
        for x in range(size):
            ux = (x + 0.5 - c) / r
            uy = (y + 0.5 - c) / r
            d = math.sqrt(ux * ux + uy * uy)
            if d > 1.0 + 1.5 / r:
                continue
            n = sum(layer[x, y] / 255.0 * w for layer, w in layers)
            band = 0.5 + 0.5 * math.sin(uy * 9.0 + n * 3.5)
            t = n * 0.6 + band * 0.4
            if t < 0.3:
                col = brown
            elif t < 0.5:
                col = ochre
            elif t < 0.66:
                col = sulfur
            elif t < 0.8:
                col = grey
            elif t < 0.9:
                col = acid
            else:
                col = salt
            k = 0.85 + 0.3 * n
            col = (min(255, int(col[0] * k)), min(255, int(col[1] * k)), min(255, int(col[2] * k)))
            edge = max(0.0, min(1.0, (1.0 + 1.5 / r - d) * r / 1.5))
            px[x, y] = (col[0], col[1], col[2], int(255 * edge))
    return img


save(paint_planet(512), "sky/sallow.png")


# --------------------------------------------------------------------------------------
# Prints for the walls: 64x64 tiles, two of them stacked for the tall ones. Painted files in
# tools/art (poster_body.png, poster_sallow.png, poster_provender.png, poster_manifest.png) are used instead.
# --------------------------------------------------------------------------------------
def font(size):
    for candidate in ("C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(candidate):
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def centered(draw, y, text, size, fill, width=64):
    f = font(size)
    box = draw.textbbox((0, 0), text, font=f)
    w = box[2] - box[0]
    draw.text(((width - w) / 2 - box[0], y), text, font=f, fill=fill)


def tall_from_art(name):
    art = os.path.join(ART, "poster_%s.png" % name)
    if not os.path.exists(art):
        return None
    return Image.open(art).convert("RGBA").resize((64, 128), Image.LANCZOS)


def square_from_art(name):
    art = os.path.join(ART, "poster_%s.png" % name)
    if not os.path.exists(art):
        return None
    return Image.open(art).convert("RGBA").resize((64, 64), Image.LANCZOS)


NAVY = (18, 26, 44, 255)
SULFUR_Y = (232, 196, 88, 255)
PALE = (210, 214, 220, 255)

body = tall_from_art("body")
if body is None:
    body = Image.new("RGBA", (64, 128), NAVY)
    d = ImageDraw.Draw(body)
    d.rectangle((2, 2, 61, 125), outline=(60, 74, 104, 255))
    centered(d, 8, "YOUR", 16, SULFUR_Y)
    centered(d, 24, "BODY", 16, SULFUR_Y)
    centered(d, 44, "STAYS", 14, PALE)
    centered(d, 58, "HOME", 14, PALE)
    # a figure in a chair, and a small chassis walking off the plate
    d.rectangle((14, 84, 30, 100), fill=(70, 90, 120, 255))
    d.ellipse((17, 76, 27, 86), fill=(200, 170, 150, 255))
    d.rectangle((10, 100, 34, 104), fill=(90, 100, 120, 255))
    d.rectangle((40, 92, 52, 102), fill=(150, 156, 168, 255))
    d.rectangle((42, 94, 50, 97), fill=(34, 211, 238, 255))
    d.rectangle((41, 102, 44, 106), fill=(90, 96, 108, 255))
    d.rectangle((48, 102, 51, 106), fill=(90, 96, 108, 255))
    centered(d, 112, "SURROGATE SYSTEMS", 6, (140, 150, 170, 255))
save(body.crop((0, 0, 64, 64)), "block/poster_body_top.png")
save(body.crop((0, 64, 64, 128)), "block/poster_body_bottom.png")

sallow = tall_from_art("sallow")
if sallow is None:
    sallow = Image.new("RGBA", (64, 128), (12, 14, 20, 255))
    d = ImageDraw.Draw(sallow)
    d.rectangle((2, 2, 61, 125), outline=(70, 70, 80, 255))
    disc = paint_planet(52)
    sallow.paste(disc, (6, 10), disc)
    for yy in range(14, 62, 8):
        d.line((4, yy, 60, yy), fill=(40, 44, 56, 120))
    centered(d, 66, "SALLOW", 15, SULFUR_Y)
    centered(d, 84, "SITE SURVEY", 8, PALE)
    centered(d, 94, "HABITATS 1-9", 8, PALE)
    d.line((8, 108, 56, 108), fill=(90, 94, 110, 255))
    centered(d, 112, "ATMOSPHERE: LETHAL", 6, (232, 100, 90, 255))
save(sallow.crop((0, 0, 64, 64)), "block/poster_sallow_top.png")
save(sallow.crop((0, 64, 64, 128)), "block/poster_sallow_bottom.png")

provender = square_from_art("provender")
if provender is None:
    provender = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    panel(provender, 0, 0, 64, 64, STEEL, noise=0.04, rivets=True)
    d = ImageDraw.Draw(provender)
    d.rectangle((6, 6, 57, 57), fill=(52, 56, 64, 255), outline=(150, 156, 168, 255))
    centered(d, 10, "S.S.V.", 9, PALE)
    centered(d, 20, "PROVENDER", 10, SULFUR_Y)
    d.rectangle((14, 36, 50, 42), fill=(150, 156, 168, 255))
    d.rectangle((10, 38, 14, 40), fill=(150, 156, 168, 255))
    d.rectangle((50, 34, 56, 44), fill=(110, 116, 128, 255))
    d.rectangle((24, 32, 40, 36), fill=(120, 126, 140, 255))
    centered(d, 46, "SUPPLY RUN 41", 6, PALE)
save(provender, "block/poster_provender.png")

for alert in (False, True):
    screen = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    panel(screen, 0, 0, 64, 64, STEEL_DARK, noise=0.05, rivets=True)
    d = ImageDraw.Draw(screen)
    bg = (60, 12, 12, 255) if alert else (8, 22, 28, 255)
    fg = (240, 70, 60, 255) if alert else (34, 211, 238, 255)
    dim = (140, 40, 40, 255) if alert else (14, 116, 144, 255)
    d.rectangle((6, 6, 57, 57), fill=bg, outline=dim)
    rows = [30, 22, 38, 18, 44, 26, 34, 20]
    for i, w in enumerate(rows):
        y = 10 + i * 6
        d.rectangle((10, y, 10 + w, y + 2), fill=fg if i % 3 else dim)
    if alert:
        d.rectangle((30, 10, 54, 30), fill=(120, 20, 20, 255), outline=fg)
        centered(d, 13, "!", 16, fg, width=84)
    else:
        d.rectangle((44, 10, 54, 20), outline=fg)
        d.rectangle((46, 24, 54, 30), outline=dim)
    save(screen, "block/poster_console%s.png" % ("_alert" if alert else ""))

manifest = square_from_art("manifest")
if manifest is None:
    manifest = Image.new("RGBA", (64, 64), (20, 22, 26, 255))
    d = ImageDraw.Draw(manifest)
    d.rectangle((2, 2, 61, 61), outline=(80, 84, 92, 255))
    centered(d, 4, "MANIFEST 41", 8, PALE)
    prng = random.Random(41)
    for i in range(12):
        y = 16 + i * 3
        w = 14 + prng.randint(0, 34)
        col = (232, 100, 90, 255) if i == 6 else (120, 180, 130, 255)
        d.rectangle((8, y, 8 + w, y + 1), fill=col)
        d.rectangle((54, y, 56, y + 1), fill=(90, 94, 110, 255))
    centered(d, 54, "TRANSIT: BILLABLE", 5, (140, 150, 170, 255))
save(manifest, "block/poster_manifest.png")
print("transit art done")


# ---- The galley unit: a door with a window on the front, a control strip beside it, vents on the sides.
for lit in (False, True):
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
    rect(img, 1, 7, 14, 1, shade(STEEL, 1.2))
    glass = (255, 208, 110, 255) if lit else (26, 30, 34, 255)
    rect(img, 2, 8, 8, 7, shade(glass, 0.75))
    rect(img, 3, 9, 6, 5, glass)
    if lit:
        rect(img, 4, 10, 4, 3, (255, 236, 170, 255))
        rect(img, 5, 11, 2, 1, (255, 250, 220, 255))
    else:
        for yy in range(9, 14, 2):
            for xx in range(3, 9, 2):
                rect(img, xx, yy, 1, 1, shade(glass, 1.6))
    rect(img, 10, 9, 1, 5, shade(STEEL, 1.5))
    rect(img, 11, 8, 4, 7, STEEL_DARK)
    rect(img, 12, 9, 2, 1, (245, 158, 11, 255) if lit else (86, 240, 120, 255))
    rect(img, 12, 11, 2, 1, shade(STEEL, 1.3))
    rect(img, 12, 13, 2, 1, shade(STEEL, 1.3))
    save(img, "block/microwave_front%s.png" % ("_lit" if lit else ""))
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
rect(img, 1, 7, 14, 1, shade(STEEL, 1.2))
for yy in range(9, 14, 2):
    rect(img, 3, yy, 10, 1, shade(STEEL, 0.6))
save(img, "block/microwave_side.png")
img = tex16()
panel(img, 1, 1, 14, 14, STEEL, noise=0.05, rivets=True)
save(img, "block/microwave_top.png")

# ---- Breached plating: the hull plate with a ragged hole burnt through it.
plate = Image.open(os.path.join(TEX, "block", "hull_plating.png")).convert("RGBA")
px = plate.load()
import math
rng_hole = random.Random(12)
radii = [3.0 + rng_hole.uniform(0.0, 2.2) for _ in range(16)]
for yy in range(16):
    for xx in range(16):
        dx = xx + 0.5 - 8.0
        dy = yy + 0.5 - 8.0
        angle = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 16
        r = radii[int(angle) % 16]
        d = math.hypot(dx, dy)
        if d < r:
            px[xx, yy] = (0, 0, 0, 0)
        elif d < r + 1.6:
            px[xx, yy] = BURNT
        elif d < r + 2.6:
            c = px[xx, yy]
            px[xx, yy] = (c[0] * 2 // 3, c[1] * 2 // 3, c[2] * 2 // 3, 255)
save(plate, "block/breached_plating.png")

# ---- The wall terminal: a dark screen with green text and a keyboard strip under it.
term = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
panel(term, 0, 0, 64, 64, STEEL_DARK, noise=0.05, rivets=True)
d = ImageDraw.Draw(term)
d.rectangle((4, 4, 59, 47), fill=(5, 18, 10, 255), outline=(20, 70, 36, 255))
d.rectangle((5, 5, 58, 11), fill=(16, 52, 28, 255))
centered(d, 5, "SHIPNET", 6, (90, 230, 130, 255))
prng = random.Random(7)
for i in range(6):
    yy = 15 + i * 5
    w = 10 + prng.randint(0, 32)
    col = (76, 224, 122, 255) if i % 3 else (31, 122, 60, 255)
    d.rectangle((8, yy, 8 + w, yy + 1), fill=col)
d.rectangle((8, 44, 12, 46), fill=(76, 224, 122, 255))
d.rectangle((4, 50, 59, 59), fill=(40, 44, 50, 255), outline=(80, 86, 96, 255))
for kx in range(7, 57, 4):
    for ky in (52, 56):
        d.rectangle((kx, ky, kx + 2, ky + 1), fill=(90, 96, 108, 255))
save(term, "block/terminal.png")
print("galley unit, breached plating and terminal done")

# ======================================================================================
# Props, ores and the vehicle fabricator (2026-09-05)
# ======================================================================================
import zipfile

NAVY_C = (24, 32, 52, 255)
NAVY_L = (40, 52, 80, 255)
SULFUR_Y = (232, 196, 88, 255)
PALE_W = (226, 228, 232, 255)
RED_C = (196, 40, 36, 255)
RUBBER = (22, 24, 28, 255)
GLASS_C = (120, 170, 190, 140)
AMBER = (255, 176, 40, 255)
AMBER_L = (255, 226, 150, 255)
GREEN_L = (86, 200, 110, 255)
SOIL = (54, 42, 34, 255)
LEAF = (66, 150, 70, 255)
LEAF_D = (40, 104, 48, 255)
TOMATO = (214, 60, 44, 255)

prop_rng = random.Random(2026)


def hline(img, x, y, w, col):
    rect(img, x, y, w, 1, col)


def vline(img, x, y, h, col):
    rect(img, x, y, 1, h, col)


def speck(img, count, col, x0=0, y0=0, w=16, h=16, rnd=prop_rng):
    px = img.load()
    for _ in range(count):
        px[x0 + rnd.randrange(w), y0 + rnd.randrange(h)] = col


def vanilla_texture(name):
    """A block texture out of the cached client jar, so ores sit on the real stone. None when it is not there."""
    jar = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom", "1.21.1", "minecraft-client.jar")
    if not os.path.exists(jar):
        return None
    try:
        with zipfile.ZipFile(jar) as z:
            with z.open("assets/minecraft/textures/block/%s.png" % name) as f:
                return Image.open(f).convert("RGBA").copy()
    except (KeyError, OSError):
        return None


def stone_like(base, streaks=False):
    img = tex16()
    panel(img, 0, 0, 16, 16, base, noise=0.09, rivets=False, outline=False)
    if streaks:
        for x in range(0, 16, 3):
            rect(img, x, prop_rng.randrange(0, 6), 1, prop_rng.randrange(6, 12), shade(base, 0.8))
    speck(img, 14, shade(base, 0.72))
    speck(img, 10, shade(base, 1.18))
    return img


# ---- Company crate: navy with a sulfur band and a stencil square.
img = tex16()
panel(img, 0, 0, 16, 16, NAVY_C, noise=0.05, rivets=True)
rect(img, 0, 6, 16, 4, SULFUR_Y)
hline(img, 0, 6, 16, shade(SULFUR_Y, 0.8))
hline(img, 0, 9, 16, shade(SULFUR_Y, 0.8))
rect(img, 4, 11, 8, 4, shade(NAVY_C, 1.35))
rect(img, 5, 12, 3, 1, PALE_W)
rect(img, 9, 12, 2, 1, PALE_W)
rect(img, 5, 14, 5, 1, PALE_W)
save(img, "block/supply_crate_side.png")
img = tex16()
panel(img, 0, 0, 16, 16, NAVY_C, noise=0.05, rivets=True)
rect(img, 6, 0, 4, 16, SULFUR_Y)
vline(img, 6, 0, 16, shade(SULFUR_Y, 0.8))
vline(img, 9, 0, 16, shade(SULFUR_Y, 0.8))
hline(img, 1, 7, 14, shade(NAVY_C, 0.6))
rect(img, 2, 2, 3, 3, shade(NAVY_C, 1.4))
save(img, "block/supply_crate_top.png")

# ---- Locker: two tall doors with vents and handles.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
for x0 in (1, 9):
    panel(img, x0, 1, 6, 14, STEEL, noise=0.05, rivets=False)
    for yy in (3, 5):
        hline(img, x0 + 1, yy, 4, shade(STEEL, 0.55))
    rect(img, x0 + 4, 8, 1, 3, shade(STEEL, 1.5))
    rect(img, x0 + 1, 12, 4, 2, shade(STEEL, 0.8))
vline(img, 8, 0, 16, shade(STEEL_DARK, 0.7))
save(img, "block/locker_front.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
hline(img, 1, 8, 14, shade(STEEL_DARK, 0.7))
save(img, "block/locker_side.png")

# ---- Data rack: four frames of blinking drive lights, one 16x16 frame per row.
frames = Image.new("RGBA", (16, 64), (0, 0, 0, 0))
drive_rng = random.Random(9)
for fi in range(4):
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
    rect(img, 2, 2, 12, 12, PAL["a"])
    for row in range(5):
        yy = 3 + row * 2
        rect(img, 3, yy, 7, 1, shade(STEEL_DARK, 0.9 + 0.1 * (row % 2)))
        for i in range(3):
            on = drive_rng.random() < 0.65
            col = (PAL["p"] if (row + i + fi) % 4 else CYAN) if on else (18, 40, 30, 255)
            rect(img, 11 + i, yy, 1, 1, col)
    rect(img, 3, 13, 10, 1, shade(STEEL, 0.9))
    rect(img, 12, 13, 1, 1, PAL["r"] if fi % 2 else PAL["o"])
    frames.paste(img, (0, fi * 16))
save(frames, "block/data_rack_front.png")
with open(os.path.join(TEX, "block", "data_rack_front.png.mcmeta"), "w", encoding="utf-8") as f:
    f.write('{"animation": {"frametime": 6}}\n')
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
for yy in range(3, 14, 2):
    hline(img, 3, yy, 10, shade(STEEL_DARK, 0.6))
save(img, "block/data_rack_side.png")

# ---- Ceiling lamp: a frosted panel in a steel frame, lit from within.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.04, rivets=False)
rect(img, 2, 2, 12, 12, (236, 240, 244, 255))
rect(img, 3, 3, 10, 10, (255, 252, 240, 255))
rect(img, 4, 4, 4, 1, (255, 255, 255, 255))
save(img, "block/ceiling_lamp_bottom.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
rect(img, 6, 6, 4, 4, shade(STEEL_DARK, 0.7))
save(img, "block/ceiling_lamp_top.png")

# ---- Deck grating: a steel grid with the floor showing through.
img = tex16()
for yy in range(16):
    for xx in range(16):
        if xx % 4 == 0 or yy % 4 == 0:
            col = shade(STEEL_DARK, 1.0 + prop_rng.uniform(-0.08, 0.08))
            if xx % 4 == 0 and yy % 4 != 0:
                col = shade(col, 0.85)
            img.load()[xx, yy] = col
hline(img, 0, 0, 16, shade(STEEL_DARK, 1.3))
save(img, "block/deck_grating.png")

# ---- Pipe: a vertical run, flanges at the ends; the model slices the width it needs.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.04, rivets=False, outline=False)
vline(img, 0, 0, 16, shade(STEEL, 0.6))
vline(img, 1, 0, 16, shade(STEEL, 0.85))
vline(img, 14, 0, 16, shade(STEEL, 0.85))
vline(img, 15, 0, 16, shade(STEEL, 0.6))
vline(img, 5, 0, 16, shade(STEEL, 1.25))
for yy in (0, 1, 14, 15):
    hline(img, 0, yy, 16, shade(STEEL_DARK, 1.1 if yy in (1, 14) else 0.9))
rect(img, 3, 7, 10, 2, COPPER)
hline(img, 3, 7, 10, PAL["C"])
save(img, "block/pipe_side.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
rect(img, 3, 3, 10, 10, shade(STEEL, 0.9))
rect(img, 5, 5, 6, 6, PAL["a"])
save(img, "block/pipe_end.png")

# ---- Handrail: a rail on two posts, the flat texture wraps every part.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.04, rivets=False, outline=False)
hline(img, 0, 0, 16, shade(STEEL, 1.35))
hline(img, 0, 15, 16, shade(STEEL, 0.6))
vline(img, 0, 0, 16, shade(STEEL, 1.15))
vline(img, 15, 0, 16, shade(STEEL, 0.7))
save(img, "block/rail.png")

# ---- Mess table: a brushed top with a rolled edge.
img = tex16()
panel(img, 0, 0, 16, 16, shade(STEEL, 1.15), noise=0.03, rivets=False)
for yy in range(2, 14, 3):
    hline(img, 1, yy, 14, shade(STEEL, 1.05))
save(img, "block/table_top.png")

# ---- Hydroponic tray: a white tray, dark substrate, sprouts on a cross, a cyan feed line along the rim.
img = tex16()
panel(img, 0, 0, 16, 16, PALE_W, noise=0.03, rivets=False)
hline(img, 0, 0, 16, (250, 250, 252, 255))
rect(img, 0, 2, 16, 1, CYAN)
rect(img, 1, 5, 14, 8, shade(PALE_W, 0.88))
rect(img, 3, 7, 4, 1, shade(PALE_W, 0.7))
rect(img, 3, 9, 6, 1, shade(PALE_W, 0.7))
save(img, "block/tray_side.png")
img = tex16()
panel(img, 0, 0, 16, 16, SOIL, noise=0.10, rivets=False, outline=False)
speck(img, 18, (92, 74, 58, 255))
speck(img, 8, (150, 150, 160, 255))
speck(img, 6, (30, 20, 16, 255))
save(img, "block/tray_soil.png")
img = tex16()
px = img.load()
for (sx, top) in ((3, 5), (8, 3), (12, 6)):
    for yy in range(top, 16):
        px[sx, yy] = LEAF_D if yy % 3 else LEAF
    for (lx, ly) in ((sx - 1, top + 2), (sx + 1, top + 1), (sx - 2, top + 2), (sx + 2, top + 4), (sx - 1, top + 6), (sx + 1, top + 7)):
        if 0 <= lx < 16 and 0 <= ly < 16:
            px[lx, ly] = LEAF
    px[sx, top] = (110, 200, 110, 255)
px[9, 8] = TOMATO
px[2, 11] = TOMATO
px[13, 12] = (220, 120, 50, 255)
save(img, "block/sprouts.png")

# ---- Med cabinet: a white box with a red cross and a handle.
img = tex16()
panel(img, 0, 0, 16, 16, PALE_W, noise=0.03, rivets=False)
rect(img, 6, 3, 4, 10, RED_C)
rect(img, 3, 6, 10, 4, RED_C)
rect(img, 7, 4, 1, 8, shade(RED_C, 1.2))
rect(img, 4, 7, 8, 1, shade(RED_C, 1.2))
rect(img, 13, 6, 1, 4, shade(STEEL, 0.9))
save(img, "block/med_cabinet_front.png")
img = tex16()
panel(img, 0, 0, 16, 16, PALE_W, noise=0.03, rivets=False)
save(img, "block/med_cabinet_side.png")

# ---- Fire extinguisher: a red cylinder with a label, and its wall bracket.
img = tex16()
panel(img, 0, 0, 16, 16, RED_C, noise=0.05, rivets=False, outline=False)
vline(img, 0, 0, 16, shade(RED_C, 0.6))
vline(img, 3, 0, 16, shade(RED_C, 1.3))
vline(img, 15, 0, 16, shade(RED_C, 0.65))
rect(img, 2, 6, 12, 5, PALE_W)
rect(img, 4, 7, 8, 1, RED_C)
rect(img, 4, 9, 5, 1, PAL["k"])
rect(img, 0, 0, 16, 2, STEEL_DARK)
rect(img, 5, 0, 6, 1, PAL["k"])
rect(img, 0, 14, 16, 2, shade(RED_C, 0.7))
save(img, "block/extinguisher.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
save(img, "block/bracket.png")

# ---- Wall vent: louvres in a frame.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=True)
rect(img, 2, 2, 12, 12, PAL["a"])
for yy in range(3, 14, 2):
    hline(img, 3, yy, 10, shade(STEEL, 0.95))
    hline(img, 3, yy + 1, 10, shade(STEEL, 0.5))
save(img, "block/wall_vent.png")

# ---- Coolant tank: steel bands with a sight glass full of cyan coolant.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
rect(img, 0, 4, 16, 9, shade(CYAN_DARK, 0.7))
rect(img, 1, 5, 14, 7, CYAN_DARK)
rect(img, 1, 8, 14, 4, CYAN)
for (bx, by) in ((3, 9), (7, 10), (11, 8), (5, 11), (13, 10)):
    rect(img, bx, by, 1, 1, CYAN_LIGHT)
vline(img, 2, 5, 7, (200, 240, 250, 120))
hline(img, 0, 3, 16, shade(STEEL, 1.3))
hline(img, 0, 13, 16, shade(STEEL, 0.6))
for (rx, ry) in ((2, 1), (13, 1), (2, 14), (13, 14)):
    rect(img, rx, ry, 1, 1, shade(STEEL, 0.55))
save(img, "block/coolant_tank_side.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=True)
rect(img, 4, 4, 8, 8, STEEL_DARK)
rect(img, 6, 6, 4, 4, COPPER)
rect(img, 7, 3, 2, 10, shade(COPPER, 1.1))
rect(img, 3, 7, 10, 2, shade(COPPER, 1.1))
save(img, "block/coolant_tank_top.png")

# ---- Bunk: a thin mattress with a pillow at the head and a company blanket.
img = tex16()
panel(img, 0, 0, 16, 16, (150, 158, 172, 255), noise=0.04, rivets=False)
rect(img, 1, 1, 14, 4, PALE_W)
hline(img, 2, 2, 12, (255, 255, 255, 255))
rect(img, 0, 7, 16, 9, NAVY_L)
rect(img, 0, 7, 16, 1, SULFUR_Y)
for yy in range(9, 16, 2):
    hline(img, 0, yy, 16, shade(NAVY_L, 0.85))
save(img, "block/bunk_top.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
rect(img, 0, 0, 16, 5, (150, 158, 172, 255))
hline(img, 0, 0, 16, shade((150, 158, 172, 255), 1.2))
rect(img, 0, 2, 16, 3, NAVY_L)
save(img, "block/bunk_side.png")

# ---- Floors and walls: hazard band, tread plate, structural frame.
img = Image.open(os.path.join(TEX, "block", "hull_plating.png")).convert("RGBA")
for x in range(16):
    for yy in range(5, 11):
        img.load()[x, yy] = HAZARD_Y if ((x + yy) // 3) % 2 == 0 else HAZARD_K
hline(img, 0, 5, 16, shade(HAZARD_K, 1.3))
hline(img, 0, 10, 16, shade(HAZARD_K, 1.3))
save(img, "block/hazard_plating.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.06, rivets=False)
for yy in range(1, 16, 4):
    for xx in range(1 + (yy // 4 % 2) * 2, 15, 4):
        rect(img, xx, yy, 2, 1, shade(STEEL_DARK, 1.35))
        rect(img, xx, yy + 1, 2, 1, shade(STEEL_DARK, 0.7))
save(img, "block/deck_plating.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.06, rivets=True)
rect(img, 0, 0, 16, 2, shade(STEEL_DARK, 1.25))
rect(img, 0, 14, 16, 2, shade(STEEL_DARK, 0.75))
rect(img, 0, 0, 2, 16, shade(STEEL_DARK, 1.15))
rect(img, 14, 0, 2, 16, shade(STEEL_DARK, 0.75))
for i in range(2, 14):
    img.load()[i, i] = shade(STEEL_DARK, 1.2)
    img.load()[15 - i, i] = shade(STEEL_DARK, 1.2)
save(img, "block/hull_frame.png")

# ---- Survey marker: a stake with a flag, on a cross of planes.
img = tex16()
px = img.load()
for yy in range(2, 16):
    px[7, yy] = shade(STEEL, 0.9) if yy % 2 else shade(STEEL, 1.15)
    px[8, yy] = shade(STEEL, 0.7)
for yy in range(2, 8):
    for xx in range(9, 9 + (6 if yy < 5 else 7 - yy)):
        px[xx, yy] = (255, 120, 30, 255) if (xx + yy) % 5 else (255, 160, 60, 255)
rect(img, 6, 14, 4, 2, STEEL_DARK)
rect(img, 7, 1, 2, 1, PAL["r"])
save(img, "block/survey_marker.png")

# ---- Pad light: a squat lamp with an amber lens.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
rect(img, 0, 0, 16, 6, AMBER)
rect(img, 0, 2, 16, 2, AMBER_L)
hline(img, 0, 6, 16, shade(STEEL_DARK, 1.3))
save(img, "block/pad_light_side.png")
img = tex16()
panel(img, 0, 0, 16, 16, AMBER, noise=0.04, rivets=False, outline=False)
rect(img, 3, 3, 10, 10, AMBER_L)
rect(img, 5, 5, 6, 6, (255, 245, 210, 255))
rect(img, 0, 0, 16, 1, shade(AMBER, 0.7))
rect(img, 0, 15, 16, 1, shade(AMBER, 0.7))
rect(img, 0, 0, 1, 16, shade(AMBER, 0.7))
rect(img, 15, 0, 1, 16, shade(AMBER, 0.7))
save(img, "block/pad_light_top.png")

# ---- Antenna mast: a lattice column with a beacon on the head.
img = tex16()
px = img.load()
for yy in range(16):
    for xx in range(16):
        if xx in (0, 15) or (xx + yy) % 5 == 0 or (xx - yy) % 5 == 0:
            px[xx, yy] = shade(STEEL, 0.95 if xx in (0, 15) else 0.8)
save(img, "block/mast.png")
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
rect(img, 5, 5, 6, 6, PAL["r"])
rect(img, 6, 6, 2, 2, PAL["R"])
save(img, "block/mast_top.png")

# ---- Chem drum: a steel drum with rolling hoops, a hazard band and a stencil.
img = tex16()
panel(img, 0, 0, 16, 16, shade(STEEL, 0.95), noise=0.05, rivets=False, outline=False)
vline(img, 0, 0, 16, shade(STEEL, 0.6))
vline(img, 2, 0, 16, shade(STEEL, 1.2))
vline(img, 15, 0, 16, shade(STEEL, 0.65))
for yy in (3, 12):
    hline(img, 0, yy, 16, shade(STEEL, 1.3))
    hline(img, 0, yy + 1, 16, shade(STEEL, 0.6))
for x in range(16):
    rect(img, x, 6, 1, 4, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
rect(img, 6, 7, 4, 2, HAZARD_K)
rect(img, 7, 7, 2, 1, HAZARD_Y)
speck(img, 6, (124, 74, 36, 255), 0, 12, 16, 4)
save(img, "block/drum_side.png")
img = tex16()
panel(img, 0, 0, 16, 16, shade(STEEL, 0.95), noise=0.05, rivets=False)
rect(img, 2, 2, 12, 12, shade(STEEL, 0.8))
rect(img, 4, 4, 3, 3, STEEL_DARK)
rect(img, 10, 9, 3, 3, STEEL_DARK)
rect(img, 5, 5, 1, 1, shade(STEEL, 1.3))
save(img, "block/drum_top.png")

# ---- The vehicle fabricator: a pad with a cyan ring, two pylons and a beam full of emitters.
for lit in (False, True):
    suffix = "_lit" if lit else ""
    ring = CYAN if lit else shade(STEEL, 0.75)
    ring_l = CYAN_LIGHT if lit else shade(STEEL, 0.9)
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
    for x in range(16):
        rect(img, x, 0, 1, 1, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
        rect(img, x, 15, 1, 1, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
        rect(img, 0, x, 1, 1, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
        rect(img, 15, x, 1, 1, HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K)
    rect(img, 3, 3, 10, 10, ring)
    rect(img, 4, 4, 8, 8, shade(STEEL_DARK, 0.9))
    rect(img, 7, 2, 2, 12, ring)
    rect(img, 2, 7, 12, 2, ring)
    rect(img, 7, 7, 2, 2, ring_l)
    save(img, "block/fabricator_top%s.png" % suffix)
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
    rect(img, 2, 12, 12, 2, PAL["a"])
    for yy in range(0, 16, 2):
        pass
    rect(img, 3, 12, 10, 1, ring if lit else shade(STEEL_DARK, 0.7))
    save(img, "block/fabricator_side%s.png" % suffix)
    # Pylon: a column with a light strip up the inside face.
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL, noise=0.05, rivets=False)
    vline(img, 0, 0, 16, shade(STEEL, 1.3))
    vline(img, 15, 0, 16, shade(STEEL, 0.6))
    rect(img, 6, 0, 4, 16, PAL["a"])
    for yy in range(1, 16, 3):
        rect(img, 7, yy, 2, 2, ring if lit else (28, 44, 50, 255))
    save(img, "block/fabricator_pylon%s.png" % suffix)
    # Beam: the crossbar; its underside carries the emitters.
    img = tex16()
    panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=True)
    for xx in range(2, 15, 4):
        rect(img, xx, 6, 2, 4, ring if lit else (28, 44, 50, 255))
        if lit:
            rect(img, xx, 7, 2, 2, ring_l)
    save(img, "block/fabricator_beam%s.png" % suffix)

# ---- Ores: crystals and flecks on the real stone where the jar is around, a painted stone otherwise.
STONE_BASE = vanilla_texture("stone") or stone_like((125, 125, 125, 255))
DEEPSLATE_BASE = vanilla_texture("deepslate") or stone_like((80, 80, 86, 255), streaks=True)
TUFF_BASE = vanilla_texture("tuff") or stone_like((108, 109, 102, 255))
SANDSTONE_BASE = Image.open(os.path.join(TEX, "block", "caustic_sandstone.png")).convert("RGBA")


def ore_on(base, blobs, dark, mid, light, seed, glow=None):
    img = base.copy()
    px = img.load()
    r = random.Random(seed)
    for _ in range(blobs):
        cx, cy = r.randrange(1, 15), r.randrange(1, 15)
        cells = [(cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + r.choice((-1, 1)), cy + r.choice((-1, 1)))]
        if r.random() < 0.5:
            cells.append((cx + 1, cy + 1))
        for (x, y) in cells:
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = mid
        px[cx, cy] = light
        ex, ey = cells[-1]
        if 0 <= ex + 1 < 16 and 0 <= ey < 16:
            px[ex + 1, ey] = dark
        if 0 <= cx - 1 < 16 and 0 <= cy + 1 < 16:
            px[cx - 1, cy + 1] = dark
        if glow and 0 <= cx < 16 and 0 <= cy - 1 < 16:
            px[cx, cy - 1] = glow
    return img


save(ore_on(TUFF_BASE, 6, (110, 20, 18, 255), (190, 44, 36, 255), (245, 120, 100, 255), 11), "block/cinnabar_ore.png")
save(ore_on(SANDSTONE_BASE, 6, (190, 170, 160, 255), (240, 232, 226, 255), (255, 255, 255, 255), 12), "block/halite_ore.png")
save(ore_on(STONE_BASE, 6, (24, 44, 110, 255), (46, 92, 200, 255), (140, 180, 255, 255), 13), "block/cobalt_ore.png")
save(ore_on(DEEPSLATE_BASE, 6, (24, 44, 110, 255), (46, 92, 200, 255), (140, 180, 255, 255), 14), "block/deepslate_cobalt_ore.png")
save(ore_on(DEEPSLATE_BASE, 5, (90, 96, 112, 255), (196, 204, 220, 255), (255, 255, 255, 255), 15, glow=(170, 220, 240, 255)), "block/tellurium_ore.png")

# ---- Their drops, and the metal.
ITEMS_LATE = {}
ITEMS_LATE["cinnabar"] = [
    "................",
    "................",
    "......kk........",
    ".....krRk.......",
    "....krRRrk......",
    "...krrRrrrk.....",
    "..kkrrrrrrkk....",
    ".krrkrrrkrrrk...",
    ".krrrkrkrrrrk...",
    ".kRrrrkrrrrrk...",
    "..krrrrrrrrk....",
    "...krrrkrrk.....",
    "....kkkkkk......",
    "................",
    "................",
    "................",
]
ITEMS_LATE["salt"] = [
    "................",
    "................",
    "......kk..kk....",
    ".....khwkkhwk...",
    "....khwwhhwwhk..",
    "...khwwwwhwwwhk.",
    "...kwwhwwwwhwwk.",
    "..kkwwwwhhwwwwk.",
    ".khwhwwwwwwhwwk.",
    ".kwwwwhwwwhwwwk.",
    ".khwwwwwhwwwwk..",
    "..kwwhwwwwwhwk..",
    "...kkwwwkkwwk...",
    ".....kkkk.kk....",
    "................",
    "................",
]
ITEMS_LATE["raw_cobalt"] = [
    "................",
    "................",
    ".....kkkk.......",
    "....kmDFmk......",
    "...kmFDDFmk.....",
    "..kmDDEDDFmk....",
    "..kFDEEDDDFk....",
    ".kmDDDDFDDDmk...",
    ".kFDDFDDDEDFk...",
    ".kmFDDDDDDFmk...",
    "..kFDDFDDFmk....",
    "..kmFDDDFmk.....",
    "...kmFFFmk......",
    "....kkkkk.......",
    "................",
    "................",
]
ITEMS_LATE["cobalt_ingot"] = [
    "................",
    "................",
    "................",
    "................",
    ".........kkkkk..",
    "......kkkDEEDFk.",
    "...kkkDDDDDDDFk.",
    "..kDEEDDDDDDFk..",
    ".kDEDDDDDDDFFk..",
    ".kDDDDDDDFFkk...",
    ".kFDDDDFFkk.....",
    ".kFFFFFkk.......",
    "..kkkkk.........",
    "................",
    "................",
    "................",
]
ITEMS_LATE["tellurium_crystal"] = [
    "................",
    "........kk......",
    ".......kYwk.....",
    "......kwhwhk....",
    "......khwwlk....",
    ".....kwhwhlk....",
    ".....khwwllk....",
    "....kwhwhllk....",
    "....khwwlllk....",
    "...kwhwhlllk....",
    "...khwwllllk....",
    "...kwwlllllk....",
    "...kkllllkk.....",
    "....kkkkk.......",
    "................",
    "................",
]
for name, rows in ITEMS_LATE.items():
    save(from_map(rows), "item/%s.png" % name)
print("props, ores and fabricator done")

# --------------------------------------------------------------------------------------
# Modules and countermeasures (2026-09-06): the four things bolted to a chassis, the one bolted
# to the crawler, and the three things planted on the ground. docs/DESIGN-hazards.md.
# --------------------------------------------------------------------------------------
ITEMS_MODULES = {}
ITEMS_MODULES["relay_module"] = [
    "................",
    "......kkk.......",
    ".....kyYyk......",
    "....kyYwYyk.....",
    "....kyYYYyk.....",
    ".....kyyyk......",
    "......kmk.......",
    "......kmk.......",
    "...kkkkmkkkk....",
    "..kmllllllllmk..",
    "..klhhhhhhhhlk..",
    "..klhkcccckhlk..",
    "..klhhhhhhhhlk..",
    "..kmddddddddmk..",
    "...kkkkkkkkkk...",
    "................",
]
ITEMS_MODULES["resonance_damper"] = [
    "................",
    "................",
    "....kkkkkkkk....",
    "...kmllllllmk...",
    "...klFFFFFFlk...",
    "...klFDDDDFlk...",
    "...klFDEEDFlk...",
    "...klFDEEDFlk...",
    "...klFDDDDFlk...",
    "...klFFFFFFlk...",
    "...kmllllllmk...",
    "...kdmmmmmmdk...",
    "....kkkkkkkk....",
    "......kddk......",
    "......kkkk......",
    "................",
]
ITEMS_MODULES["acid_coating"] = [
    "................",
    "....kkkkkkk.....",
    "...kpPPPPPpk....",
    "...kpPwwwPpk....",
    "..kkkkkkkkkkk...",
    "..kmlllllllmk...",
    "..klhpPPPphlk...",
    "..klhpPwPphlk...",
    "..klhpPPPphlk...",
    "..klhhhhhhhlk...",
    "..klmmmmmmmlk...",
    "..kmdddddddmk...",
    "..kkkkkkkkkkk...",
    "................",
    "................",
    "................",
]
ITEMS_MODULES["shielded_uplink"] = [
    "................",
    "...kkkkkkkkkk...",
    "..kuUUUUUUUUuk..",
    "..kUhhhhhhhhUk..",
    "..kUhkkkkkkhUk..",
    "..kUhkyyyykhUk..",
    "..kUhkyYYykhUk..",
    "..kUhkyYYykhUk..",
    "..kUhkyyyykhUk..",
    "..kUhkkyykkhUk..",
    "..kUhkkyykkhUk..",
    "..kUhhhkkhhhUk..",
    "..kuUUUUUUUUuk..",
    "...kkkkkkkkkk...",
    "................",
    "................",
]
ITEMS_MODULES["ceramic_cladding"] = [
    "................",
    "................",
    "..kkkkkkkkkkkk..",
    "..kwwwwwwwwwwk..",
    "..khhhhhhhhhhk..",
    "..kkkkkkkkkkkk..",
    "..kwwwwwwwwwwk..",
    "..khhhhhhhhhhk..",
    "..kkkkkkkkkkkk..",
    "..kwwwwwwwwwwk..",
    "..khhhhhhhhhhk..",
    "..kkkkkkkkkkkk..",
    "...kmllllllmk...",
    "...kkkkkkkkkk...",
    "................",
    "................",
]
for name, rows in ITEMS_MODULES.items():
    save(from_map(rows), "item/%s.png" % name)

# ---- Relay mast: a plated column with the dish on its face and a copper feed along the foot.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06)
rect(img, 3, 2, 10, 10, shade(STEEL, 0.8))
rect(img, 4, 3, 8, 8, CYAN_DARK)
rect(img, 5, 4, 6, 6, CYAN)
rect(img, 7, 6, 2, 2, CYAN_LIGHT)
hline(img, 0, 13, 16, shade(STEEL, 0.6))
for x in range(2, 14, 3):
    rect(img, x, 14, 2, 2, COPPER)
save(img, "block/relay_mast.png")

# ---- Damper beacon: the tone drawn the way a seismograph draws it, rings out from the middle.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL_DARK, noise=0.05, rivets=False)
for r, col in ((6, PAL["u"]), (4, PAL["U"]), (2, PAL["y"])):
    rect(img, 8 - r, 8 - r, r * 2, r * 2, col)
rect(img, 7, 7, 2, 2, PAL["Y"])
for (cx, cy) in ((1, 1), (14, 1), (1, 14), (14, 14)):
    rect(img, cx, cy, 1, 1, shade(STEEL, 1.2))
save(img, "block/damper_beacon.png")

# ---- Span anchor: hazard stripes top and bottom and the bracket a deck would bolt to.
img = tex16()
panel(img, 0, 0, 16, 16, STEEL, noise=0.06, rivets=False)
for x in range(16):
    col = HAZARD_Y if (x // 2) % 2 == 0 else HAZARD_K
    rect(img, x, 0, 1, 3, col)
    rect(img, x, 13, 1, 3, col)
rect(img, 3, 4, 10, 8, shade(STEEL, 0.75))
rect(img, 5, 6, 6, 4, STEEL_DARK)
rect(img, 6, 7, 4, 2, AMBER)
for (cx, cy) in ((1, 4), (14, 4), (1, 11), (14, 11)):
    rect(img, cx, cy, 1, 1, shade(STEEL, 0.5))
save(img, "block/span_anchor.png")
print("modules and countermeasures done")

# ======================================================================================
# The belt's rain, the borers, and the two things act one leaves lying about (2026-09-06)
# ======================================================================================
CORRODE = (86, 104, 62, 255)
CORRODE_D = (52, 64, 38, 255)
CORRODE_L = (128, 146, 88, 255)

# ---- Corroded machine: what is left when the rain has been through it. Steel gone soft and green, holed.
img = tex16()
panel(img, 0, 0, 16, 16, shade(STEEL, 0.7), noise=0.10, rivets=False)
for (x, y, w, h) in ((1, 3, 5, 4), (9, 2, 6, 3), (2, 9, 4, 5), (8, 10, 7, 4), (6, 6, 4, 3)):
    rect(img, x, y, w, h, CORRODE)
for (x, y) in ((3, 4), (11, 3), (4, 11), (10, 12), (7, 7), (13, 8), (2, 7)):
    rect(img, x, y, 2, 2, CORRODE_D)
for (x, y) in ((5, 5), (12, 6), (3, 13), (9, 4)):
    rect(img, x, y, 1, 1, CORRODE_L)
# Two holes eaten right through, which is why the block is not airtight.
rect(img, 6, 8, 3, 3, (14, 16, 14, 255))
rect(img, 11, 11, 2, 2, (14, 16, 14, 255))
save(img, "block/corroded_machine.png")

img = tex16()
panel(img, 0, 0, 16, 16, shade(STEEL, 0.55), noise=0.12, rivets=False)
for (x, y, w, h) in ((2, 2, 6, 5), (9, 8, 5, 6), (3, 10, 4, 4)):
    rect(img, x, y, w, h, CORRODE)
for (x, y) in ((4, 3), (11, 10), (5, 11), (9, 4)):
    rect(img, x, y, 2, 2, CORRODE_D)
rect(img, 6, 6, 4, 4, (14, 16, 14, 255))
save(img, "block/corroded_machine_top.png")

# ---- Survey stake: a ranging rod with a company tag knotted to it, on a cross of planes.
img = tex16()
px = img.load()
for yy in range(1, 16):
    band = (yy // 2) % 2 == 0
    px[7, yy] = HAZARD_Y if band else (236, 238, 240, 255)
    px[8, yy] = shade(HAZARD_Y, 0.7) if band else (170, 174, 180, 255)
rect(img, 6, 14, 4, 2, STEEL_DARK)
rect(img, 9, 3, 5, 4, (216, 220, 224, 255))
rect(img, 9, 4, 4, 1, (60, 64, 70, 255))
rect(img, 9, 6, 3, 1, (60, 64, 70, 255))
rect(img, 7, 0, 2, 1, PAL["r"])
save(img, "block/survey_stake.png")

ITEMS_ACT_ONE = {}
ITEMS_ACT_ONE["sealed_sample"] = [
    "................",
    ".....kkkkkk.....",
    "....kddddddk....",
    "....kmllllmk....",
    "...kkkkkkkkkk...",
    "...kmllllllmk...",
    "...kltppppTlk...",
    "...kltppppTlk...",
    "...kltppppTlk...",
    "...klttppTTlk...",
    "...kmllllllmk...",
    "...kdmmmmmmdk...",
    "...kdyyyyyydk...",
    "...kkkkkkkkkk...",
    "....kkkkkkkk....",
    "................",
]
for name, rows in ITEMS_ACT_ONE.items():
    save(from_map(rows), "item/%s.png" % name)

# ---- The borer: 128x128, a head of grinding plates and one body segment, banded and dusty.
CHITIN = (74, 62, 52, 255)
CHITIN_D = (44, 36, 30, 255)
CHITIN_L = (112, 96, 78, 255)
MAW = (128, 40, 34, 255)
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
# Head, uv 0,0: a 16x16x24 cuboid, so the unwrap is 112 across and 48 down.
panel(img, 0, 0, 112, 48, CHITIN, noise=0.16, rivets=False, outline=False)
for y in range(0, 48, 3):
    rect(img, 0, y, 112, 1, shade(CHITIN, 0.78))
for y in range(1, 48, 6):
    rect(img, 0, y, 112, 1, shade(CHITIN_L, 1.05))
# The face: the front cap of the head cuboid, a ring of plates around a dark throat.
rect(img, 16, 0, 16, 16, CHITIN_D)
rect(img, 18, 2, 12, 12, MAW)
rect(img, 20, 4, 8, 8, (26, 14, 12, 255))
for (x, y) in ((17, 1), (30, 1), (17, 14), (30, 14), (23, 0), (23, 15)):
    rect(img, x, y, 1, 1, CHITIN_L)
# Segment, uv 0,48, 14x14x12.
panel(img, 0, 48, 100, 40, shade(CHITIN, 0.9), noise=0.16, rivets=False, outline=False)
for y in range(48, 88, 4):
    rect(img, 0, y, 100, 1, shade(CHITIN, 0.72))
for y in range(50, 88, 8):
    rect(img, 0, y, 100, 1, shade(CHITIN_L, 1.02))
save(img, "entity/borer.png")
print("acid, borer and act one textures done")

# ======================================================================================
# The four animals (2026-09-06). Layouts must match the models in client/render/fauna/.
# ======================================================================================
FIBRE = (108, 104, 92, 255)
FIBRE_D = (68, 66, 58, 255)
FIBRE_L = (146, 142, 126, 255)
BASALT = (58, 56, 60, 255)
BASALT_D = (36, 35, 38, 255)
BASALT_L = (86, 84, 88, 255)
SHELL = (92, 104, 112, 255)
GLOW = (150, 240, 210, 255)
GLOW_D = (60, 150, 130, 255)
FLESH = (128, 116, 132, 255)
FLESH_D = (88, 78, 92, 255)


def fur(img, x, y, w, h, base, strands=40):
    """A patch of matted fibre: noise, then short vertical strands of a lighter and a darker tone."""
    panel(img, x, y, w, h, base, noise=0.14, rivets=False, outline=False)
    for _ in range(strands):
        sx = x + rng.randrange(w)
        sy = y + rng.randrange(max(1, h - 3))
        col = shade(base, rng.choice((0.72, 0.8, 1.18, 1.3)))
        rect(img, sx, sy, 1, rng.randint(2, 3), col)


# ---- Trundle: 64x32. A 10-cube of grey fibre with a 4x2x4 tuft on top.
img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
for (name, (fx, fy, fw, fh)) in box_faces(0, 0, 10, 10, 10).items():
    fur(img, fx, fy, fw, fh, FIBRE if name != "bottom" else FIBRE_D, strands=22)
# The underside is packed flat and pale from being sat on all day.
for (fx, fy, fw, fh) in [box_faces(0, 0, 10, 10, 10)["bottom"]]:
    panel(img, fx, fy, fw, fh, shade(FIBRE_L, 0.85), noise=0.05, rivets=False, outline=False)
# A pair of dark eye-spots on the front face, which is decoration: it has no eyes on that side or any other.
front = box_faces(0, 0, 10, 10, 10)["front"]
rect(img, front[0] + 2, front[1] + 4, 2, 2, FIBRE_D)
rect(img, front[0] + 6, front[1] + 4, 2, 2, FIBRE_D)
for (fx, fy, fw, fh) in box_faces(40, 0, 4, 2, 4).values():
    fur(img, fx, fy, fw, fh, FIBRE_L, strands=6)
save(img, "entity/trundle.png")

# ---- Slagback: 64x32. A 14x5x12 shell, four 6x1x6 plates, four 2x4x2 legs.
img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
for (name, (fx, fy, fw, fh)) in box_faces(0, 0, 14, 5, 12).items():
    base = BASALT_D if name == "bottom" else BASALT
    panel(img, fx, fy, fw, fh, base, noise=0.20, rivets=False, outline=False)
    # Crazed basalt: short pale cracks, dense enough to read as stone at two blocks and no further.
    for _ in range(fw * fh // 6):
        cx = fx + rng.randrange(fw)
        cy = fy + rng.randrange(fh)
        rect(img, cx, cy, rng.randint(1, 2), 1, shade(base, rng.choice((0.62, 1.35))))
# The top of the shell is where the plates sit, so it is a shade lighter and a little sun-bleached.
top = box_faces(0, 0, 14, 5, 12)["top"]
panel(img, top[0], top[1], top[2], top[3], BASALT_L, noise=0.16, rivets=False, outline=False)
for (fx, fy, fw, fh) in box_faces(0, 20, 6, 1, 6).values():
    panel(img, fx, fy, fw, fh, BASALT_L, noise=0.18, rivets=False, outline=False)
# Plate undersides are the one part that is obviously not rock: hot shell, seen only when it is up.
under = box_faces(0, 20, 6, 1, 6)["bottom"]
panel(img, under[0], under[1], under[2], under[3], SHELL, noise=0.10, rivets=False, outline=False)
for (fx, fy, fw, fh) in box_faces(28, 20, 2, 4, 2).values():
    panel(img, fx, fy, fw, fh, shade(SHELL, 0.75), noise=0.12, rivets=False, outline=False)
save(img, "entity/slagback.png")

# ---- Tocker: 32x32. A 5-cube body, a 4x4x1 lens, three 1x4x1 legs.
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
for (name, (fx, fy, fw, fh)) in box_faces(0, 0, 5, 5, 5).items():
    panel(img, fx, fy, fw, fh, FLESH if name != "bottom" else FLESH_D, noise=0.12, rivets=False, outline=False)
# Speckle: pale flecks over the back and sides, thickest on top.
for _ in range(26):
    rect(img, rng.randrange(20), rng.randrange(10), 1, 1, shade(FLESH, 1.4))
for (fx, fy, fw, fh) in box_faces(0, 12, 4, 4, 1).values():
    panel(img, fx, fy, fw, fh, GLOW_D, noise=0.06, rivets=False, outline=False)
# The lens itself, on the front face of the eye cuboid.
lens = box_faces(0, 12, 4, 4, 1)["front"]
rect(img, lens[0], lens[1], 4, 4, GLOW)
rect(img, lens[0] + 1, lens[1] + 1, 2, 2, (250, 255, 250, 255))
for i in range(3):
    for (fx, fy, fw, fh) in box_faces(20 + i * 4, 0, 1, 4, 1).values():
        panel(img, fx, fy, fw, fh, FLESH_D, noise=0.10, rivets=False, outline=False)
save(img, "entity/tocker.png")

# The eyes layer: the same sheet with everything but the lens rubbed out. Vanilla's EyesFeatureRenderer
# draws the whole model a second time with this at full brightness, so anything left opaque here glows.
img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
rect(img, lens[0], lens[1], 4, 4, GLOW)
rect(img, lens[0] + 1, lens[1] + 1, 2, 2, (255, 255, 255, 255))
save(img, "entity/tocker_eye.png")

# ---- Lantern slug: 32x16. A 6x2x8 body and a 5x1x2 head with four lights on its front.
img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
for (name, (fx, fy, fw, fh)) in box_faces(0, 0, 6, 2, 8).items():
    base = FLESH_D if name == "top" else FLESH
    panel(img, fx, fy, fw, fh, base, noise=0.14, rivets=False, outline=False)
# A wet sheen down the back, which is the face pressed to the ceiling.
back_top = box_faces(0, 0, 6, 2, 8)["top"]
for i in range(back_top[3]):
    rect(img, back_top[0] + 2, back_top[1] + i, 2, 1, shade(FLESH_D, 1.3))
for (fx, fy, fw, fh) in box_faces(0, 10, 5, 1, 2).values():
    panel(img, fx, fy, fw, fh, FLESH, noise=0.10, rivets=False, outline=False)
head_front = box_faces(0, 10, 5, 1, 2)["front"]
EYES = [(head_front[0] + x, head_front[1]) for x in (0, 1, 3, 4)]
for (ex, ey) in EYES:
    rect(img, ex, ey, 1, 1, GLOW)
save(img, "entity/lantern_slug.png")

img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
for (ex, ey) in EYES:
    rect(img, ex, ey, 1, 1, GLOW)
save(img, "entity/lantern_slug_eyes.png")
print("fauna textures done")

# ======================================================================================
# The survey tier: a table, a beacon in two colours, and a pillar (2026-09-06)
# ======================================================================================
SURVEY_GLASS = (36, 92, 76, 255)
SURVEY_LINE = (124, 232, 192, 255)

# The table's sides: plated, with a seam and a vent grille where the projector lives.
img = tex16()
grain(img, STEEL_DARK, noise=0.07)
rect(img, 0, 3, 16, 1, shade(STEEL_DARK, 1.35))
rect(img, 0, 11, 16, 1, shade(STEEL_DARK, 0.65))
for x in range(4, 12, 2):
    rect(img, x, 5, 1, 5, shade(STEEL_DARK, 0.55))
rect(img, 1, 1, 1, 1, shade(STEEL_DARK, 0.5))
rect(img, 14, 1, 1, 1, shade(STEEL_DARK, 0.5))
save(img, "block/survey_station_side.png")

# The glass top: a dark pane with a contour lattice etched into it, which is the picture at rest.
img = tex16()
grain(img, SURVEY_GLASS, noise=0.05)
for i in range(0, 16, 4):
    rect(img, 0, i, 16, 1, shade(SURVEY_GLASS, 1.5))
    rect(img, i, 0, 1, 16, shade(SURVEY_GLASS, 1.35))
# A brighter run across the middle: the ridge the table is centred on.
for (x, y) in ((3, 6), (4, 5), (5, 5), (6, 4), (7, 4), (8, 5), (9, 6), (10, 6), (11, 7), (12, 8)):
    rect(img, x, y, 1, 1, SURVEY_LINE)
rect(img, 7, 7, 2, 2, (255, 255, 255, 255))
save(img, "block/survey_station_top.png")

# The beacon pole: thin, banded, scuffed from being carried in a bag.
img = tex16()
grain(img, shade(STEEL, 0.8), noise=0.10)
for y in (2, 6, 10, 14):
    rect(img, 0, y, 16, 1, shade(STEEL, 0.55))
save(img, "block/survey_beacon.png")

# The lamp, twice. Same glass, different bulb, and the difference is the entire interface.
for (name, core, halo) in (("", PAL["r"], PAL["R"]), ("_linked", PAL["p"], PAL["P"])):
    img = tex16()
    grain(img, shade(STEEL_DARK, 0.9), noise=0.06)
    rect(img, 1, 0, 5, 3, halo)
    rect(img, 2, 0, 3, 2, core)
    rect(img, 0, 3, 6, 1, shade(STEEL_DARK, 0.6))
    save(img, "block/survey_beacon_lamp%s.png" % name)

# The pillar: a tall column of bolted plate with a stack of dish slots up one face.
for (suffix, glow) in (("", None), ("_lit", CYAN)):
    img = tex16()
    grain(img, STEEL, noise=0.09)
    rect(img, 0, 0, 16, 1, shade(STEEL, 1.3))
    rect(img, 0, 15, 16, 1, shade(STEEL, 0.6))
    rect(img, 2, 0, 1, 16, shade(STEEL, 0.7))
    rect(img, 13, 0, 1, 16, shade(STEEL, 0.7))
    for y in range(2, 15, 4):
        rect(img, 4, y, 8, 2, shade(STEEL_DARK, 0.9))
        if glow:
            rect(img, 5, y, 6, 1, glow)
            rect(img, 5, y + 1, 6, 1, shade(glow, 0.6))
    save(img, "block/long_range_scanner%s.png" % suffix)

img = tex16()
grain(img, shade(STEEL, 0.85), noise=0.07)
for r in range(2, 8, 2):
    rect(img, 8 - r, 8 - r, r * 2, 1, shade(STEEL, 1.25))
    rect(img, 8 - r, 8 + r - 1, r * 2, 1, shade(STEEL, 0.65))
rect(img, 7, 7, 2, 2, CYAN)
save(img, "block/long_range_scanner_top.png")

# ---- The three loose items.
ITEMS_SURVEY = {}
ITEMS_SURVEY["bio_sampler"] = [
    "................",
    "......kkk.......",
    ".....kYYYk......",
    ".....kYwYk......",
    ".....kYYYk......",
    "......kmk.......",
    "......kmk.......",
    ".....kmmmk......",
    "....kmlllmk.....",
    "....kmlnlmk.....",
    "....kmlllmk.....",
    "....kmmmmmk.....",
    ".....kmkmk......",
    ".....kk.kk......",
    "................",
    "................",
]
ITEMS_SURVEY["specimen_bag"] = [
    "................",
    "....kkkkkkk.....",
    "...khhhhhhhk....",
    "...khwwwwwhk....",
    "..kkhwwwwwhkk...",
    "..kmkhhhhhkmk...",
    "..kmmmmmmmmmk...",
    "..kmlllllllmk...",
    "..kmlPPPPPlmk...",
    "..kmlPpppPlmk...",
    "..kmlPPPPPlmk...",
    "..kmlllllllmk...",
    "..kmmmmmmmmmk...",
    "...kkkkkkkkk....",
    "................",
    "................",
]
ITEMS_SURVEY["analysis_disk"] = [
    "................",
    "..kkkkkkkkkkk...",
    "..kuuuuuuuuuk...",
    "..kuUUUUUUUuk...",
    "..kuUwwwwwUuk...",
    "..kuUwkkkwUuk...",
    "..kuUwkakwUuk...",
    "..kuUwkkkwUuk...",
    "..kuUwwwwwUuk...",
    "..kuUUUUUUUuk...",
    "..kuuuuuuuuuk...",
    "..kullllllluk...",
    "..kulhhhhhluk...",
    "..kulllllllUk...",
    "..kkkkkkkkkkk...",
    "................",
]
for name, rows in ITEMS_SURVEY.items():
    save(from_map(rows), "item/%s.png" % name)
print("survey textures done")

# ======================================================================================
# Acts four and five: the span kit, and the two faces the game never had (2026-09-06)
# ======================================================================================
# The icon is an ASCII map and touches no RNG, so it could go anywhere. The two suits are painted with
# panel() and therefore consume the shared rng stream, which is why this whole section sits at the very end
# of the file: inserting a painted texture anywhere earlier re-rolls the noise of every painted texture
# after it and produces a large and entirely spurious diff across tracked PNGs.

ITEMS_SPAN = {}
# A deck section strapped into a crate: the straps are the only part of it that is not steel, because the
# only thing anybody remembers about a flat-pack is the strapping.
ITEMS_SPAN["span_kit"] = [
    "................",
    "................",
    "..kkkkkkkkkkkk..",
    "..kmmmmmmmmmmk..",
    "..kmllllllllmk..",
    "..kmlgggggglmk..",
    "..kmldhhhhhdmk..",
    "..kmldxxxxxdmk..",
    "..kmldhhhhhdmk..",
    "..kmldxxxxxdmk..",
    "..kmldhhhhhdmk..",
    "..kmlgggggglmk..",
    "..kmllllllllmk..",
    "..kmmmmmmmmmmk..",
    "..kkkkkkkkkkkk..",
    "................",
]
for name, rows in ITEMS_SPAN.items():
    save(from_map(rows), "item/%s.png" % name)

# Reyes and Novak have been in the roster, on the radio and on the terminals since the campaign was laid
# out, and neither of them has ever had a skin: their renderer builds the path from the character key at run
# time, so nothing static ever noticed, and both of them have been rendering as the missing texture. Act
# five is the act they are in and act four puts their faces on a screen, so they get suits.
#
# Reyes is medical: the pale kit, and the only red trim on the planet that means something. Novak is survey
# contract 39, the same programme as Brandt, in a suit that has been under a crawler for eleven days.
paint_survivor("reyes", (208, 210, 214, 255), (198, 64, 64, 255))
paint_survivor("novak", (74, 92, 98, 255), (232, 172, 60, 255))
print("span kit and the last two suits done")

print("done")
