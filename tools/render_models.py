#!/usr/bin/env python3
"""Draws the mod's block models the way the inventory does, as isometric sprites, so a set of props can be
looked over without booting the game.

    python tools/render_models.py                    # every block model, to run/showcase/<name>.png
    python tools/render_models.py crew_seat bunk     # just these
    python tools/render_models.py --sheet            # also a contact sheet, run/showcase/sheet.png

Reads assets/surrogate/models/block/*.json (resolving the vanilla cube parents it uses) and the textures they
name; the first frame of an animated texture is used. Requires Pillow."""
import argparse
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "surrogate")
MODELS = os.path.join(ASSETS, "models", "block")
OUT = os.path.join(ROOT, "run", "showcase")

# The vanilla parents the mod's models lean on, as the faces they add.
PARENTS = {
    "minecraft:block/cube_all": {"all": "#all"},
    "minecraft:block/cube_bottom_top": {"up": "#top", "down": "#bottom", "side": "#side"},
    "minecraft:block/orientable": {"up": "#top", "down": "#top", "north": "#front", "side": "#side"},
    "minecraft:block/cube_column": {"up": "#end", "down": "#end", "side": "#side"},
    "minecraft:block/cube": {"north": "#north", "south": "#south", "east": "#east", "west": "#west", "up": "#up", "down": "#down"},
}
SIDES = ("north", "south", "east", "west")
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}

_texture_cache = {}


def load_texture(ref):
    """A 16x16-ish RGBA texture for `surrogate:block/name`; animated strips give their first frame."""
    if ref in _texture_cache:
        return _texture_cache[ref]
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    file = os.path.join(ASSETS, "textures", path + ".png") if ns == "surrogate" else None
    img = None
    if file and os.path.exists(file):
        img = Image.open(file).convert("RGBA")
        if img.height > img.width:
            img = img.crop((0, 0, img.width, img.width))
    if img is None:
        img = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    _texture_cache[ref] = img
    return img


def load_model(name):
    """The model with its parent chain flattened: textures, elements."""
    with open(os.path.join(MODELS, name + ".json"), encoding="utf-8") as f:
        m = json.load(f)
    textures = dict(m.get("textures", {}))
    elements = list(m.get("elements", []))
    parent = m.get("parent")
    while parent:
        if parent.startswith("surrogate:block/"):
            with open(os.path.join(MODELS, parent.split("/", 1)[1] + ".json"), encoding="utf-8") as f:
                pm = json.load(f)
            for k, v in pm.get("textures", {}).items():
                textures.setdefault(k, v)
            if not elements:
                elements = list(pm.get("elements", []))
            parent = pm.get("parent")
        elif parent in PARENTS and not elements:
            faces = PARENTS[parent]
            face_map = {}
            for side in ("up", "down") + SIDES:
                face_map[side] = {"texture": faces.get(side) or faces.get("side") or faces.get("all")}
            elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": face_map}]
            parent = None
        else:
            parent = None
    return textures, elements


def resolve(textures, ref):
    seen = 0
    while ref and ref.startswith("#") and seen < 10:
        ref = textures.get(ref[1:])
        seen += 1
    return ref


def auto_uv(side, a, b):
    if side == "down":
        return [a[0], 16 - b[2], b[0], 16 - a[2]]
    if side == "up":
        return [a[0], a[2], b[0], b[2]]
    if side == "north":
        return [16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]]
    if side == "south":
        return [a[0], 16 - b[1], b[0], 16 - a[1]]
    if side == "west":
        return [a[2], 16 - b[1], b[2], 16 - a[1]]
    return [16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]]


def corners(side, a, b):
    """Four corners of a face, in texture order: top-left, top-right, bottom-right, bottom-left."""
    x0, y0, z0 = a
    x1, y1, z1 = b
    if side == "north":
        return [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)]
    if side == "south":
        return [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)]
    if side == "west":
        return [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)]
    if side == "east":
        return [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)]
    if side == "up":
        return [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
    return [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]


def rotate_point(p, rotation):
    if not rotation:
        return p
    ox, oy, oz = rotation.get("origin", [8, 8, 8])
    ang = math.radians(rotation.get("angle", 0))
    axis = rotation.get("axis", "y")
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    c, s = math.cos(ang), math.sin(ang)
    if axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    elif axis == "x":
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + ox, y + oy, z + oz)


class Camera:
    def __init__(self, yaw=45.0, pitch=30.0, scale=7.0):
        self.yaw = math.radians(yaw)
        self.pitch = math.radians(pitch)
        self.scale = scale

    def project(self, p):
        x, y, z = p[0] - 8.0, p[1], p[2] - 8.0
        cy, sy = math.cos(self.yaw), math.sin(self.yaw)
        x1, z1 = x * cy - z * sy, x * sy + z * cy
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        y2 = y * cp + z1 * sp
        depth = z1 * cp - y * sp
        return (x1 * self.scale, -y2 * self.scale, depth)


def lerp(p, q, t):
    return tuple(p[i] + (q[i] - p[i]) * t for i in range(3))


def render(name, camera=None, pad=10):
    camera = camera or Camera()
    textures, elements = load_model(name)
    faces = []
    for el in elements:
        a, b = el["from"], el["to"]
        rotation = el.get("rotation")
        shade = el.get("shade", True)
        for side, fdef in el.get("faces", {}).items():
            ref = resolve(textures, fdef.get("texture"))
            if not ref:
                continue
            tex = load_texture(ref)
            uv = fdef.get("uv") or auto_uv(side, a, b)
            quad = [rotate_point(c, rotation) for c in corners(side, a, b)]
            projected = [camera.project(c) for c in quad]
            # Back faces: the projected quad winds the wrong way.
            area = 0.0
            for i in range(4):
                x0, y0, _ = projected[i]
                x1, y1, _ = projected[(i + 1) % 4]
                area += x0 * y1 - x1 * y0
            if area >= 0:
                continue
            depth = sum(p[2] for p in projected) / 4.0
            faces.append((depth, side, quad, uv, tex, SHADE[side] if shade else 1.0, fdef.get("rotation", 0)))
    faces.sort(key=lambda f: -f[0])
    # Canvas size from the projected extent of everything.
    xs, ys = [], []
    for _, _, quad, _, _, _, _ in faces:
        for c in quad:
            px, py, _ = camera.project(c)
            xs.append(px)
            ys.append(py)
    if not xs:
        return Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    w, h = int(maxx - minx) + pad * 2, int(maxy - miny) + pad * 2
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def to_screen(c):
        px, py, _ = camera.project(c)
        return (px - minx + pad, py - miny + pad)

    for _, side, quad, uv, tex, shade, rot in faces:
        u0, v0, u1, v1 = uv
        cols = max(1, int(round(abs(u1 - u0))))
        rows = max(1, int(round(abs(v1 - v0))))
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        tw, th = tex.size
        for j in range(rows):
            for i in range(cols):
                tu = u0 + (u1 - u0) * (i + 0.5) / cols
                tv = v0 + (v1 - v0) * (j + 0.5) / rows
                tx = min(tw - 1, max(0, int(tu * tw / 16.0)))
                ty = min(th - 1, max(0, int(tv * th / 16.0)))
                col = tex.getpixel((tx, ty))
                if col[3] == 0:
                    continue
                fu0, fu1 = i / cols, (i + 1) / cols
                fv0, fv1 = j / rows, (j + 1) / rows
                top0, top1 = lerp(quad[0], quad[1], fu0), lerp(quad[0], quad[1], fu1)
                bot0, bot1 = lerp(quad[3], quad[2], fu0), lerp(quad[3], quad[2], fu1)
                poly = [to_screen(lerp(top0, bot0, fv0)), to_screen(lerp(top1, bot1, fv0)),
                        to_screen(lerp(top1, bot1, fv1)), to_screen(lerp(top0, bot0, fv1))]
                shaded = (int(col[0] * shade), int(col[1] * shade), int(col[2] * shade), col[3])
                draw.polygon(poly, fill=shaded, outline=shaded)
        canvas.alpha_composite(layer)
    return canvas


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("names", nargs="*", help="block model names; all of them when empty")
    parser.add_argument("--sheet", action="store_true", help="also write a contact sheet")
    parser.add_argument("--scale", type=float, default=7.0)
    args = parser.parse_args()
    os.makedirs(OUT, exist_ok=True)
    names = args.names or sorted(os.path.splitext(f)[0] for f in os.listdir(MODELS) if f.endswith(".json"))
    sprites = []
    for name in names:
        img = render(name, Camera(scale=args.scale))
        img.save(os.path.join(OUT, name + ".png"))
        sprites.append((name, img))
        print("rendered", name, img.size)
    if args.sheet and sprites:
        cell = max(max(img.size) for _, img in sprites) + 8
        cols = 6
        rows = (len(sprites) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * (cell + 14)), (34, 36, 40, 255))
        d = ImageDraw.Draw(sheet)
        for i, (name, img) in enumerate(sprites):
            x = (i % cols) * cell + (cell - img.width) // 2
            y = (i // cols) * (cell + 14) + (cell - img.height)
            sheet.alpha_composite(img, (x, y))
            d.text(((i % cols) * cell + 4, (i // cols) * (cell + 14) + cell + 1), name[:22], fill=(220, 220, 224, 255))
        sheet.save(os.path.join(OUT, "sheet.png"))
        print("wrote", os.path.join(OUT, "sheet.png"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
