#!/usr/bin/env python3
"""Models, block states, loot tables, tags and lang for the flashback's furniture.

    py -3.13 tools/gen_house_data.py     # just these, merged into lang/en_us.json
    py -3.13 tools/gen_data.py           # everything, which calls generate() at the end

Every model here is authored facing north — its front is the north face, and anything that hangs on a wall
hangs on the south face — and the block state turns it. Picture faces (a screen, a page, a pizza) map the
whole texture with an explicit uv; material faces (wood, plastic, fabric) let Minecraft crop the texture to
the face the way it does for stairs. The texture region maps are written down in tools/gen_house.py."""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "surrogate")
MOD = "surrogate"
FACINGS = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
SMALL_GUI = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}}
FULL = [0, 0, 16, 16]


def write(rel, obj):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def mid(name):
    return f"{MOD}:{name}"


def tex(name):
    return f"{MOD}:block/{name}"


def model(rel, obj):
    write(f"assets/{MOD}/models/{rel}.json", obj)


def blockstate(name, variants):
    write(f"assets/{MOD}/blockstates/{name}.json", {"variants": variants})


def facing_variants(model_for):
    """{state suffix: model name} -> every facing of each, turned by the block state."""
    out = {}
    for facing, rot in FACINGS:
        for key, model_name in model_for.items():
            v = {"model": f"{MOD}:block/{model_name}"}
            if rot:
                v["y"] = rot
            out[f"facing={facing}" + (("," + key) if key else "")] = v
    return out


def face(texture, uv=None, rotation=None):
    f = {"texture": texture}
    if uv:
        f["uv"] = uv
    if rotation:
        f["rotation"] = rotation
    return f


def box(frm, to, faces, shade=None):
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if shade is not None:
        e["shade"] = shade
    return e


def all_faces(texture, uv=None, **overrides):
    faces = {side: face(texture, uv) for side in ("north", "south", "east", "west", "up", "down")}
    faces.update(overrides)
    return faces


def sides(texture, uv=None):
    return {side: face(texture, uv) for side in ("north", "south", "east", "west")}


def prop_model(name, textures, elements, particle=None):
    model(f"block/{name}", {"parent": "minecraft:block/block",
                            "textures": dict({"particle": particle or next(iter(textures.values()))}, **textures),
                            "elements": elements, "display": SMALL_GUI})


def cross_planes(texture, y0, y1, inset=0):
    return [
        box([8, y0, inset], [8, y1, 16 - inset], {"east": face(texture), "west": face(texture)}, shade=False),
        box([inset, y0, 8], [16 - inset, y1, 8], {"north": face(texture), "south": face(texture)}, shade=False),
    ]


def self_drop(name):
    return {"type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": mid(name)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": mid(f"blocks/{name}")}


def shifted(elements, dy):
    """The same elements, moved down for the suitcase lying on a bed."""
    out = []
    for e in elements:
        e2 = dict(e)
        e2["from"] = [e["from"][0], e["from"][1] + dy, e["from"][2]]
        e2["to"] = [e["to"][0], e["to"][1] + dy, e["to"][2]]
        out.append(e2)
    return out


LANG = {}
BLOCKS = []


def block(name, label, blockstate_variants, item_model=None, drop=True):
    """One block: its state file, its item model, its loot table and its name."""
    blockstate(name, blockstate_variants)
    model(f"item/{name}", {"parent": f"{MOD}:block/{item_model or name}"})
    if drop:
        write(f"data/{MOD}/loot_table/blocks/{name}.json", self_drop(name))
    LANG[f"block.{MOD}.{name}"] = label
    BLOCKS.append(name)


def single(name):
    return {"": {"model": f"{MOD}:block/{name}"}}


# ======================================================================================
def generate():
    house()
    office()
    bar()
    small_things()
    tags()
    words()
    return LANG


# --------------------------------------------------------------------------------------
def house():
    for paper in ("wallpaper_stripe", "wallpaper_floral"):
        model(f"block/{paper}", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(paper)}})
        block(paper, "Striped Wallpaper" if paper == "wallpaper_stripe" else "Floral Wallpaper", single(paper))

    # The couch, in the four ways a block of it can be.
    fabric, cushion, walnut = tex("couch_fabric"), tex("couch_cushion"), tex("walnut")
    for part, west_arm, east_arm in (("single", True, True), ("left", False, True), ("right", True, False), ("middle", False, False)):
        elements = []
        for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
            elements.append(box([x, 0, z], [x + 2, 2, z + 2], all_faces("#walnut")))
        elements.append(box([0, 2, 1], [16, 7, 16], all_faces("#fabric")))
        xa = 3 if west_arm else 0
        xb = 13 if east_arm else 16
        elements.append(box([xa, 7, 1], [xb, 10, 12], all_faces("#fabric", up=face("#cushion", FULL))))
        elements.append(box([0, 7, 12], [16, 15, 16], all_faces("#fabric", north=face("#cushion", FULL))))
        if west_arm:
            elements.append(box([0, 7, 1], [3, 13, 16], all_faces("#fabric")))
        if east_arm:
            elements.append(box([13, 7, 1], [16, 13, 16], all_faces("#fabric")))
        prop_model(f"couch_{part}", {"fabric": fabric, "cushion": cushion, "walnut": walnut}, elements)
    block("couch", "Couch", facing_variants({f"part={p}": f"couch_{p}" for p in ("single", "left", "middle", "right")}), "couch_single")

    # Tables, and the chair that goes with the kitchen one.
    prop_model("coffee_table", {"top": tex("coffee_table_top"), "side": tex("oak_side")}, [
        box([0, 13, 0], [16, 16, 16], all_faces("#side", up=face("#top", FULL))),
        box([1, 11, 1], [15, 13, 15], all_faces("#side")),
        box([1, 0, 1], [3, 11, 3], all_faces("#side")), box([13, 0, 1], [15, 11, 3], all_faces("#side")),
        box([1, 0, 13], [3, 11, 15], all_faces("#side")), box([13, 0, 13], [15, 11, 15], all_faces("#side"))])
    block("coffee_table", "Coffee Table", single("coffee_table"))
    prop_model("dining_table", {"top": tex("dining_table_top"), "side": tex("walnut")}, [
        box([0, 14, 0], [16, 16, 16], all_faces("#side", up=face("#top", FULL))),
        box([1, 12, 1], [15, 14, 15], all_faces("#side")),
        box([1, 0, 1], [3, 12, 3], all_faces("#side")), box([13, 0, 1], [15, 12, 3], all_faces("#side")),
        box([1, 0, 13], [3, 12, 15], all_faces("#side")), box([13, 0, 13], [15, 12, 15], all_faces("#side"))])
    block("dining_table", "Kitchen Table", single("dining_table"))
    chair = tex("chair")
    legs = [box([x, 0, z], [x + 2, 8, z + 2], all_faces("#chair", [0, 0, 2, 8])) for (x, z) in ((2, 2), (12, 2), (2, 12), (12, 12))]
    prop_model("dining_chair", {"chair": chair}, legs + [
        box([2, 8, 2], [14, 10, 14], all_faces("#chair", [0, 8, 12, 10], up=face("#chair", [2, 8, 14, 16]), down=face("#chair", [0, 0, 12, 8]))),
        box([2, 10, 12], [4, 16, 14], all_faces("#chair", [0, 0, 2, 6])), box([12, 10, 12], [14, 16, 14], all_faces("#chair", [0, 0, 2, 6])),
        box([4, 11, 12], [12, 12, 14], all_faces("#chair", [0, 0, 8, 1])), box([4, 13, 12], [12, 15, 14], all_faces("#chair", [0, 0, 8, 2]))])
    block("dining_chair", "Kitchen Chair", facing_variants({"": "dining_chair"}))

    # The pizza box, at five levels of eaten; the block state rounds the count to the nearest picture.
    for n in (8, 6, 3, 1, 0):
        prop_model(f"pizza_box_{n}", {"side": tex("pizza_box_side"), "top": tex("pizza_box_top"), "inside": tex("pizza_box_inside"), "pie": tex(f"pizza_{n}")}, [
            box([1, 0, 2], [15, 2, 14], all_faces("#side", up=face("#pie", FULL))),
            box([1, 2, 13], [15, 14, 15], all_faces("#side", north=face("#inside", FULL), south=face("#top", FULL)))],
            particle=tex("pizza_box_side"))
    nearest = {8: 8, 7: 6, 6: 6, 5: 3, 4: 3, 3: 3, 2: 1, 1: 1, 0: 0}
    block("pizza_box", "Pizza Box", facing_variants({f"slices={s}": f"pizza_box_{nearest[s]}" for s in range(9)}), "pizza_box_8")

    # The set: a flat panel on a foot, its screen one of four pictures.
    for channel, screen in (("off", "tv_screen_off"), ("news", "tv_screen_news"), ("credits", "tv_screen_credits"), ("static", "tv_screen_static")):
        prop_model(f"television_{channel}", {"frame": tex("tv_frame"), "back": tex("tv_back"), "screen": tex(screen), "stand": tex("tv_stand")}, [
            box([1, 2, 8], [15, 13, 10], all_faces("#frame", north=face("#screen", FULL), south=face("#back", FULL)), shade=channel == "off" or None),
            box([7, 0, 8], [9, 2, 10], all_faces("#stand")),
            box([4, 0, 7], [12, 1, 11], all_faces("#stand"))], particle=tex("tv_frame"))
    block("television", "Television", facing_variants({f"channel={c}": f"television_{c}" for c in ("off", "news", "credits", "static")}), "television_off")

    # Lights: the pendant, the office panel, and the switch on the wall.
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        lamp = tex("pendant_lamp" + suffix)
        prop_model("pendant_lamp" + suffix, {"lamp": lamp}, [
            box([7, 9, 7], [9, 16, 9], all_faces("#lamp", [7, 8, 9, 16])),
            box([5, 7, 5], [11, 9, 11], all_faces("#lamp", [0, 0, 16, 8])),
            box([3, 3, 3], [13, 7, 13], all_faces("#lamp", [0, 0, 16, 8], down=face("#lamp", [10, 8, 14, 14])), shade=(False if lit else None)),
            box([6, 1, 6], [10, 3, 10], all_faces("#lamp", [10, 8, 14, 14]), shade=(False if lit else None))])
        panel = tex("panel_light" + suffix)
        prop_model("panel_light" + suffix, {"panel": panel, "frame": tex("panel_light")}, [
            box([0, 14, 0], [16, 16, 16], all_faces("#frame", down=face("#panel", FULL)), shade=(False if lit else None))])
        on = "_on" if lit else ""
        plate = tex("light_switch" + on)
        prop_model("light_switch" + on, {"plate": plate}, [
            box([6, 5, 15], [10, 11, 16], all_faces("#plate", [6, 5, 7, 6], north=face("#plate", [6, 5, 10, 11]))),
            box([7, 6 if lit else 8, 14], [9, 8 if lit else 10, 15], all_faces("#plate", [7, 8, 9, 10]))])
    block("pendant_lamp", "Pendant Lamp", {"lit=false": {"model": tex("pendant_lamp")}, "lit=true": {"model": tex("pendant_lamp_lit")}}, "pendant_lamp_lit")
    block("panel_light", "Ceiling Panel", {"lit=false": {"model": tex("panel_light")}, "lit=true": {"model": tex("panel_light_lit")}}, "panel_light_lit")
    block("light_switch", "Light Switch", facing_variants({"powered=false": "light_switch", "powered=true": "light_switch_on"}), "light_switch")

    # Things to read.
    prop_model("bills", {"paper": tex("bills")}, [box([2, 0, 3], [14, 1, 13], all_faces("#paper", [1, 5, 13, 6], up=face("#paper", FULL), down=face("#paper", FULL)))])
    block("bills", "Unopened Post", single("bills"))
    prop_model("calendar", {"paper": tex("calendar")}, [box([4, 2, 15], [12, 14, 16], all_faces("#paper", [4, 2, 5, 3], north=face("#paper", [4, 2, 12, 14])))])
    block("calendar", "Wall Calendar", facing_variants({"": "calendar"}))
    prop_model("printout", {"paper": tex("printout")}, [box([3, 0, 3], [13, 1, 13], all_faces("#paper", [3, 2, 13, 3], up=face("#paper", FULL), down=face("#paper", FULL)))])
    block("printout", "Signed Contract", single("printout"))

    # The dog's things.
    for occupied in (False, True):
        suffix = "_dog" if occupied else ""
        prop_model("dog_carrier" + suffix, {"side": tex("dog_carrier_side"), "front": tex("dog_carrier_front" + suffix), "top": tex("dog_carrier_top")}, [
            box([2, 0, 2], [14, 11, 14], all_faces("#side", FULL, north=face("#front", FULL), up=face("#top", FULL))),
            box([6, 11, 6], [10, 13, 10], all_faces("#side", [3, 3, 7, 5]))], particle=tex("dog_carrier_side"))
    blockstate("dog_carrier", facing_variants({"occupied=false": "dog_carrier", "occupied=true": "dog_carrier_dog"}))
    model("item/dog_carrier", {"parent": f"{MOD}:block/dog_carrier"})
    write(f"data/{MOD}/loot_table/blocks/dog_carrier.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": mid("dog_carrier"), "functions": [
            {"function": "minecraft:copy_components", "source": "block_entity", "include": [mid("pet_data")]}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": mid("blocks/dog_carrier")})
    LANG[f"block.{MOD}.dog_carrier"] = "Dog Carrier"
    BLOCKS.append("dog_carrier")
    prop_model("dog_bowl", {"bowl": tex("dog_bowl"), "steel": tex("steel")}, [
        box([4, 0, 4], [12, 3, 12], all_faces("#steel", [0, 0, 8, 3], up=face("#bowl", [4, 4, 12, 12])))])
    block("dog_bowl", "Dog Bowl", single("dog_bowl"))

    # The kitchen.
    prop_model("fridge", {"front": tex("fridge_front"), "side": tex("fridge_side")}, [
        box([1, 0, 1], [15, 16, 15], all_faces("#side", north=face("#front", FULL)))])
    block("fridge", "Fridge", facing_variants({"": "fridge"}))
    for name, label, top, front in (("kitchen_counter", "Kitchen Counter", "counter_top", "counter_front"),
                                    ("kitchen_sink", "Kitchen Sink", "sink_top", "counter_front"),
                                    ("stove", "Cooker", "stove_top", "stove_front")):
        elements = [box([0, 0, 0], [16, 16, 16], all_faces("#side", north=face("#front"), up=face("#top")))]
        if name == "kitchen_sink":
            elements += [box([7, 16, 12], [9, 20, 14], all_faces("#chrome")), box([7, 19, 8], [9, 20, 14], all_faces("#chrome"))]
        prop_model(name, {"side": tex("counter_side"), "front": tex(front), "top": tex(top), "chrome": tex("chrome")}, elements, particle=tex("counter_side"))
        block(name, label, facing_variants({"": name}))

    # Upstairs.
    prop_model("wardrobe", {"front": tex("wardrobe_front"), "side": tex("walnut")}, [
        box([0, 0, 2], [16, 16, 16], all_faces("#side", north=face("#front", FULL)))])
    block("wardrobe", "Wardrobe", facing_variants({"": "wardrobe"}))
    prop_model("nightstand", {"front": tex("nightstand_front"), "side": tex("walnut")}, [
        box([1, 0, 1], [15, 16, 15], all_faces("#side", north=face("#front", FULL)))])
    block("nightstand", "Bedside Cabinet", facing_variants({"": "nightstand"}))
    porcelain, seat, chrome = tex("porcelain"), tex("toilet_seat"), tex("chrome")
    prop_model("toilet", {"p": porcelain, "seat": seat, "chrome": chrome}, [
        box([5, 0, 4], [11, 5, 10], all_faces("#p")),
        box([3, 5, 3], [13, 10, 12], all_faces("#p")),
        box([3, 10, 3], [13, 11, 12], all_faces("#p", up=face("#seat", [3, 3, 13, 12]))),
        box([3, 6, 12], [13, 16, 15], all_faces("#p")),
        box([7, 16, 13], [9, 17, 14], all_faces("#chrome"))], particle=porcelain)
    block("toilet", "Toilet", facing_variants({"": "toilet"}))
    prop_model("washbasin", {"p": porcelain, "top": tex("basin_top"), "chrome": chrome}, [
        box([5, 0, 8], [11, 10, 14], all_faces("#p")),
        box([2, 10, 5], [14, 15, 16], all_faces("#p", up=face("#top", FULL))),
        box([7, 15, 13], [9, 19, 15], all_faces("#chrome")),
        box([7, 18, 9], [9, 19, 15], all_faces("#chrome"))], particle=porcelain)
    block("washbasin", "Washbasin", facing_variants({"": "washbasin"}))
    for side in ("left", "right"):
        prop_model(f"bathtub_{side}", {"p": porcelain, "top": tex(f"bath_top_{side}")}, [
            box([0, 0, 1], [16, 9, 15], all_faces("#p", up=face("#top", FULL)))], particle=porcelain)
    block("bathtub", "Bath", facing_variants({"part=single": "bathtub_left", "part=left": "bathtub_left", "part=middle": "bathtub_left", "part=right": "bathtub_right"}), "bathtub_left")
    prop_model("mirror", {"m": tex("mirror")}, [box([3, 2, 15], [13, 14, 16], all_faces("#m", [3, 2, 4, 3], north=face("#m", [3, 2, 13, 14])))])
    block("mirror", "Mirror", facing_variants({"": "mirror"}))

    # The case, open, and the same case lying on a bed.
    case = [box([2, 0, 3], [14, 6, 13], all_faces("#side", FULL, up=face("#inside", FULL))),
            box([2, 6, 11], [14, 16, 13], all_faces("#side", FULL, north=face("#lining", FULL), south=face("#top", FULL)))]
    textures = {"side": tex("suitcase_side"), "top": tex("suitcase_top"), "inside": tex("suitcase_inside"), "lining": tex("suitcase_lining")}
    prop_model("suitcase", textures, case, particle=tex("suitcase_side"))
    prop_model("suitcase_on_bed", textures, shifted(case, -7), particle=tex("suitcase_side"))
    block("suitcase", "Suitcase", facing_variants({"on_bed=false": "suitcase", "on_bed=true": "suitcase_on_bed"}), "suitcase")
    prop_model("cardboard_box", {"side": tex("cardboard_side"), "top": tex("cardboard_top")}, [
        box([2, 0, 2], [14, 10, 14], all_faces("#side", FULL, up=face("#top", FULL)))])
    block("cardboard_box", "Cardboard Box", facing_variants({"": "cardboard_box"}))


# --------------------------------------------------------------------------------------
def office():
    prop_model("office_desk", {"top": tex("desk_top"), "panel": tex("desk_panel"), "leg": tex("desk_leg"), "keys": tex("keyboard")}, [
        box([0, 14, 0], [16, 16, 16], all_faces("#top")),
        box([0, 2, 14], [16, 14, 16], all_faces("#panel")),
        box([0, 0, 0], [2, 14, 2], all_faces("#leg")), box([14, 0, 0], [16, 14, 2], all_faces("#leg")),
        box([0, 0, 14], [2, 14, 16], all_faces("#leg")), box([14, 0, 14], [16, 14, 16], all_faces("#leg")),
        box([4, 16, 3], [12, 17, 7], all_faces("#keys", [0, 0, 16, 2], up=face("#keys", [0, 0, 16, 8])))], particle=tex("desk_top"))
    block("office_desk", "Office Desk", facing_variants({"": "office_desk"}))
    prop_model("office_chair", {"mesh": tex("office_chair"), "metal": tex("chair_metal")}, [
        box([2, 0, 7], [14, 1, 9], all_faces("#metal")), box([7, 0, 2], [9, 1, 14], all_faces("#metal")),
        box([7, 1, 7], [9, 7, 9], all_faces("#metal")),
        box([3, 7, 3], [13, 9, 13], all_faces("#mesh")),
        box([3, 9, 11], [13, 16, 13], all_faces("#mesh"))], particle=tex("office_chair"))
    block("office_chair", "Office Chair", facing_variants({"": "office_chair"}))
    for screen in ("off", "contract", "saver"):
        prop_model(f"monitor_{screen}", {"frame": tex("monitor_frame"), "screen": tex(f"monitor_{screen}"), "metal": tex("chair_metal")}, [
            box([2, 3, 7], [14, 12, 9], all_faces("#frame", north=face("#screen", FULL)), shade=(None if screen == "off" else False)),
            box([7, 1, 8], [9, 3, 10], all_faces("#metal")),
            box([5, 0, 7], [11, 1, 11], all_faces("#metal"))], particle=tex("monitor_frame"))
    block("monitor", "Monitor", facing_variants({f"screen={s}": f"monitor_{s}" for s in ("off", "contract", "saver")}), "monitor_off")
    prop_model("filing_cabinet", {"front": tex("filing_front"), "side": tex("filing_side")}, [
        box([2, 0, 2], [14, 16, 14], all_faces("#side", north=face("#front", FULL)))])
    block("filing_cabinet", "Filing Cabinet", facing_variants({"": "filing_cabinet"}))
    prop_model("water_cooler", {"c": tex("water_cooler")}, [
        box([4, 0, 4], [12, 9, 12], all_faces("#c", [0, 8, 16, 16])),
        box([5, 9, 5], [11, 16, 11], all_faces("#c", [0, 0, 16, 8]))])
    block("water_cooler", "Water Cooler", single("water_cooler"))
    prop_model("whiteboard", {"w": tex("whiteboard")}, [box([0, 1, 15], [16, 15, 16], all_faces("#w", [0, 0, 1, 1], north=face("#w", [0, 1, 16, 15])))])
    block("whiteboard", "Whiteboard", facing_variants({"": "whiteboard"}))
    prop_model("printer", {"p": tex("printer"), "sheet": tex("printout")}, [
        box([2, 0, 3], [14, 6, 13], all_faces("#p", [0, 0, 16, 6], up=face("#p", FULL))),
        box([4, 6, 4], [12, 7, 12], all_faces("#sheet", [3, 2, 13, 3], up=face("#sheet", [3, 2, 13, 15])))], particle=tex("printer"))
    block("printer", "Printer", facing_variants({"": "printer"}))


# --------------------------------------------------------------------------------------
def bar():
    prop_model("bar_counter", {"top": tex("bar_top"), "front": tex("bar_front"), "side": tex("bar_side")}, [
        box([0, 13, 0], [16, 16, 16], all_faces("#side", up=face("#top", FULL))),
        box([0, 0, 2], [16, 13, 16], all_faces("#side", north=face("#front", FULL))),
        box([0, 2, 0], [16, 3, 1], all_faces("#front", [0, 14, 16, 15]))], particle=tex("bar_side"))
    block("bar_counter", "Bar Counter", facing_variants({f"part={p}": "bar_counter" for p in ("single", "left", "middle", "right")}), "bar_counter")
    prop_model("bar_stool", {"seat": tex("stool_seat"), "metal": tex("chair_metal")}, [
        box([5, 0, 5], [11, 1, 11], all_faces("#metal")),
        box([7, 1, 7], [9, 8, 9], all_faces("#metal")),
        box([5, 4, 5], [11, 5, 6], all_faces("#metal")), box([5, 4, 10], [11, 5, 11], all_faces("#metal")),
        box([5, 4, 6], [6, 5, 10], all_faces("#metal")), box([10, 4, 6], [11, 5, 10], all_faces("#metal")),
        box([4, 8, 4], [12, 10, 12], all_faces("#seat", [3, 3, 13, 5], up=face("#seat", [3, 3, 13, 13])))], particle=tex("stool_seat"))
    block("bar_stool", "Bar Stool", facing_variants({"": "bar_stool"}))
    for full in ("full", "empty"):
        prop_model(f"pint_{full}", {"g": tex(f"pint_{full}")}, [
            box([6, 0, 6], [10, 7, 10], all_faces("#g", [6, 9, 10, 16], up=face("#g", [6, 9, 10, 13]), down=face("#g", [6, 12, 10, 16])))])
    block("pint_glass", "Pint", {"full=true": {"model": tex("pint_full")}, "full=false": {"model": tex("pint_empty")}}, "pint_full")
    # The board, with up to three darts in it.
    spots = ((6, 8), (9, 6), (8, 10))
    for darts in range(4):
        elements = [box([2, 2, 15], [14, 14, 16], all_faces("#board", [0, 7, 1, 8], north=face("#board", FULL)))]
        for (x, y) in spots[:darts]:
            elements += [box([x, y, 14], [x + 1, y + 1, 15], all_faces("#dart", [0, 7, 4, 8])),
                         box([x, y, 12.5], [x + 1, y + 1, 14], all_faces("#dart", [5, 6, 10, 9])),
                         box([x - 0.5, y - 0.5, 11], [x + 1.5, y + 1.5, 12.5], all_faces("#dart", [13, 5, 16, 10]))]
        prop_model(f"dartboard_{darts}", {"board": tex("dartboard"), "dart": tex("dart")}, elements, particle=tex("dartboard"))
    block("dartboard", "Dartboard", facing_variants({f"darts={d}": f"dartboard_{d}" for d in range(4)}), "dartboard_0")
    bottles = [box([1 + 3 * i, 1, 12], [3 + 3 * i, 8, 14], all_faces("#bottles", [3 * i, 0, 3 * i + 3, 8], up=face("#bottles", [3 * i, 0, 3 * i + 3, 1]))) for i in range(5)]
    prop_model("back_bar", {"mirror": tex("back_bar"), "shelf": tex("walnut"), "bottles": tex("bottles")}, [
        box([0, 0, 15], [16, 16, 16], all_faces("#shelf", north=face("#mirror", FULL))),
        box([0, 0, 10], [16, 1, 16], all_faces("#shelf"))] + bottles, particle=tex("walnut"))
    block("back_bar", "Back Bar", facing_variants({"": "back_bar"}))
    prop_model("beer_tap", {"chrome": tex("chrome"), "handle": tex("tap_handle")}, [
        box([6, 0, 6], [10, 9, 10], all_faces("#chrome")),
        box([7, 5, 3], [9, 6, 6], all_faces("#chrome")),
        box([7, 9, 7], [9, 13, 9], all_faces("#handle", [6, 0, 10, 16]))], particle=tex("chrome"))
    block("beer_tap", "Beer Tap", facing_variants({"": "beer_tap"}))
    prop_model("neon_sign", {"n": tex("neon_sign")}, [
        box([1, 4, 15], [15, 12, 16], all_faces("#n", [1, 4, 2, 5])),
        box([1, 4, 14], [15, 12, 15], all_faces("#n", [1, 4, 2, 5], north=face("#n", [1, 4, 15, 12])), shade=False)])
    block("neon_sign", "Neon Sign", facing_variants({"": "neon_sign"}))


# --------------------------------------------------------------------------------------
def small_things():
    """The props that were already here, redrawn: the radio, the lamp, the mug, the clock and the rest."""
    for lit in (False, True):
        name = "radio_set_on" if lit else "radio_set"
        prop_model(name, {"front": tex("radio_front_lit" if lit else "radio_front"), "side": tex("radio_side"), "top": tex("radio_top")}, [
            box([2, 0, 7], [14, 10, 16], all_faces("#side", north=face("#front", FULL), up=face("#top", FULL), down=face("#top", FULL)))], particle=tex("radio_side"))
    block("radio_set", "Radio Set", facing_variants({"lit=false": "radio_set", "lit=true": "radio_set_on"}), "radio_set")
    prop_model("desk_lamp", {"lamp": tex("desk_lamp")}, [
        box([4, 0, 4], [12, 1, 12], all_faces("#lamp", [0, 14, 16, 16])),
        box([7, 1, 7], [9, 10, 9], all_faces("#lamp", [7, 8, 9, 14])),
        box([3, 9, 3], [13, 13, 13], all_faces("#lamp", [0, 0, 16, 6], down=face("#lamp", [0, 6, 16, 8])), shade=False),
        box([5, 13, 5], [11, 15, 11], all_faces("#lamp", [0, 0, 16, 6]))])
    block("desk_lamp", "Desk Lamp", facing_variants({"": "desk_lamp"}))
    prop_model("photo_frame", {"f": tex("photo_frame")}, [box([3, 3, 15], [13, 13, 16], all_faces("#f", [3, 3, 4, 4], north=face("#f", [3, 3, 13, 13])))])
    block("photo_frame", "Photograph", facing_variants({"": "photo_frame"}))
    for late in (False, True):
        suffix = "_late" if late else ""
        prop_model("wall_clock" + suffix, {"c": tex("wall_clock" + suffix)}, [
            box([3, 3, 15], [13, 13, 16], all_faces("#c", [2, 8, 3, 9], north=face("#c", [2, 2, 14, 14])))])
    block("wall_clock", "Wall Clock", facing_variants({"late=false": "wall_clock", "late=true": "wall_clock_late"}), "wall_clock")
    prop_model("snow_globe", {"g": tex("snow_globe")}, [
        box([4, 0, 4], [12, 2, 12], all_faces("#g", [4, 10, 12, 14])),
        box([5, 2, 5], [11, 8, 11], all_faces("#g", [3, 0, 13, 10]))])
    block("snow_globe", "Snow Globe", single("snow_globe"))
    prop_model("mug", {"m": tex("mug"), "top": tex("mug_top")}, [
        box([5, 0, 5], [11, 6, 11], all_faces("#m", [0, 2, 6, 8], up=face("#top", [2, 2, 14, 14]), down=face("#m", [0, 0, 6, 6]))),
        box([12, 2, 7], [13, 5, 9], all_faces("#m", [12, 0, 16, 4])),
        box([11, 1, 7], [13, 2, 9], all_faces("#m", [12, 0, 16, 4])),
        box([11, 5, 7], [13, 6, 9], all_faces("#m", [12, 0, 16, 4]))], particle=tex("mug"))
    block("mug", "Mug", facing_variants({"": "mug"}))
    prop_model("houseplant", {"pot": tex("houseplant_pot"), "leaves": tex("houseplant_leaves")}, [
        box([5, 0, 5], [11, 6, 11], all_faces("#pot", [4, 9, 12, 15], up=face("#pot", [5, 8, 11, 9])))] + cross_planes("#leaves", 5, 15, 3),
        particle=tex("houseplant_pot"))
    block("houseplant", "Houseplant", single("houseplant"))
    prop_model("telephone", {"t": tex("telephone"), "h": tex("telephone_handset")}, [
        box([3, 0, 5], [13, 4, 12], all_faces("#t", [0, 0, 10, 4], up=face("#t", [3, 4, 13, 11]))),
        box([4, 4, 6], [12, 6, 11], all_faces("#h", [0, 5, 16, 11]))], particle=tex("telephone"))
    block("telephone", "Telephone", facing_variants({"": "telephone"}))
    # The two items with icons of their own.
    for item, label in (("pizza_slice", "Slice of Pizza"), ("dart", "Dart")):
        model(f"item/{item}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{item}"}})
        LANG[f"item.{MOD}.{item}"] = label


# --------------------------------------------------------------------------------------
def tags():
    # All of it comes off with a hand (house() sets no requiresTool), but an axe should still be quicker on
    # the wooden things. In the minecraft namespace: a mineable tag under surrogate: is a tag nothing reads.
    wooden = ["radio_set", "television", "photo_frame", "wall_clock", "telephone", "wallpaper_stripe", "wallpaper_floral",
              "couch", "coffee_table", "dining_table", "dining_chair", "wardrobe", "nightstand", "office_desk", "bar_counter",
              "bar_stool", "back_bar", "cardboard_box", "suitcase", "dog_carrier", "kitchen_counter", "kitchen_sink"]
    write("data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [mid(n) for n in wooden]})
    # The shell of every room in the dream: the floor, the walls, the stairs, the doors, the lamps set into
    # the ceiling. None of it can be broken from inside the dream, because behind every wall in there is a
    # flat generator's worth of nothing.
    fixed = ["minecraft:black_concrete", "minecraft:polished_deepslate", "minecraft:polished_blackstone_bricks",
             "minecraft:oak_planks", "minecraft:dark_oak_planks", "minecraft:white_wool", "minecraft:light_gray_concrete",
             "minecraft:gray_concrete", "minecraft:white_concrete", "minecraft:glass_pane", "minecraft:red_stained_glass",
             "minecraft:oak_stairs", "minecraft:oak_fence", "minecraft:oak_door", "minecraft:spruce_door",
             "minecraft:dark_oak_door", "minecraft:iron_door", "minecraft:oak_wall_sign", "minecraft:soul_lantern",
             "minecraft:lantern", "minecraft:sea_lantern", "minecraft:shroomlight", "minecraft:ochre_froglight",
             mid("wallpaper_stripe"), mid("wallpaper_floral")]
    write(f"data/{MOD}/tags/block/dream_fixed.json", {"replace": False, "values": fixed})


# --------------------------------------------------------------------------------------
def words():
    K = "cinematic.surrogate.flashback."
    LANG.update({
        # The corridor.
        K + "open_1": "This is not the pod. There is carpet under your feet, and a smell of somebody else's cooking, and a corridor with three doors at the end of it.",
        K + "open_2": "It is the last night before you shipped. You were not paying much attention at the time, so some of this is going to have to be decided now.",
        K + "again_1": "Asleep, and here again. The doors you have already been through are bricked up, which is either the mind being tidy or the mind being honest.",
        K + "again_2": "There is more of it than one night can hold. There always was.",
        K + "where": "Where was I?",
        K + "objective.where": "Walk through a door",
        K + "hint.where": "Three doors, and the names are over them. One of them is where you were. It becomes true when you go through it.",
        K + "done": "That is all of it. Three rooms, three answers, and nothing left in the dark to walk towards.",
        K + "woke": "You wake up in your own bed, four years and eleven light hours away, with nothing you did not already have.",
        K + "woke_packed": "You wake up in your own bed. There is a case on the floor beside it that was not there when you lay down, and the manifest has never heard of any of it.",
        K + "car": "That is the car.",
        K + "car_work": "That is the lift, and it is not going to wait.",
        # Home.
        K + "home.arrive": "The living room. Of course it was the living room. The set is on because it is always on, and there is a pizza box on the table because nobody was going to cook tonight.",
        K + "objective.eat": "Eat something",
        K + "hint.eat": "There is a box on the table. You have not eaten since the office.",
        K + "home_figure": "Somebody is in the kitchen doorway. You cannot get their face to hold still, and you have stopped trying, because you know perfectly well who it is.",
        K + "home_q1": "How are we going to get through this?",
        K + "ask.home.0": "How are we going to get through this?",
        K + "ask.home.0.0": "I always find a way.",
        K + "ask.home.0.1": "Four years. Then it is paid, all of it, and we start again.",
        K + "ask.home.0.2": "I do not know. I signed anyway.",
        K + "home_q2": "And after? When the four years are done?",
        K + "ask.home.1": "And after? When the four years are done?",
        K + "ask.home.1.0": "I come back, and we start again.",
        K + "ask.home.1.1": "I come back with the money and nothing else.",
        K + "ask.home.1.2": "I might not come back. You know that.",
        K + "home.reason.return": "To come back. You said it twice, which is once more than a thing that is true needs saying.",
        K + "home.reason.money": "For the money. You did not dress it up, and they did not ask you to.",
        K + "home.reason.gone": "To go. That was the honest answer, and it was the one you gave, and neither of you said anything for a while after.",
        K + "home_up": "I am going up. Do not stay up all night with that thing on.",
        K + "objective.watch": "Watch the news",
        K + "hint.watch": "It is about you. It does not know it is about you.",
        K + "tv_1": "...departing Ceres orbit on Thursday with twelve surrogate pilots aboard, bound for the assay at Sallow. The company describes the four-year contracts as a fresh start for qualifying households.",
        K + "tv_2": "Applicants with outstanding balances were prioritised, a spokesperson confirmed. Those balances are discharged on arrival. The return passage is, quote, scheduled.",
        K + "tv_3": "Local news after this. First, a word from our sponsor.",
        K + "ad_1": "Behind on payments? Four years on the surrogate programme clears every qualifying debt. Full board. Your body stays home. Only your attention travels.",
        K + "ad_2": "Final boarding for the Ceres shuttle is six a.m. Thursday. Places are limited. Terms apply. Sleep well.",
        K + "home.later": "Later. The credits are going up on something you did not watch, and the house has gone quiet the way houses do when they know.",
        K + "home.quiet": "Nobody said what time the car was coming. Somebody wrote it on the calendar in the kitchen, and underlined it, and you have not looked.",
        K + "objective.upstairs": "Go up",
        K + "hint.upstairs": "It is late. The stairs are in the hall.",
        K + "home.bedroom": "The case is on the bed, open, half done. The half that is done is the half you did not need to think about.",
        K + "home.pack": "Whatever is in the case when you walk out of the front door is what you brought. Nothing in this house is real enough to miss what you take.",
        K + "objective.pack": "Pack, then go",
        K + "hint.pack": "Whatever is in the case when you leave is what you brought.",
        K + "hint.pack_home": "Whatever is in the case when you leave the house is what you brought. The front door is at the bottom of the stairs.",
        # The office.
        K + "work.arrive": "The office, at whatever hour this is. Every screen on the floor is dark except yours, and yours has the contract on it, page eleven of fourteen.",
        K + "objective.read": "Read the contract",
        K + "hint.read": "It is on your screen. Page eleven of fourteen, and the part they want you to read is on page twelve.",
        K + "work_figure": "Somebody has come over from two desks along, the way people do at this hour, when there is nobody left to see them do it.",
        K + "work_q1": "How long have you been on this floor, anyway?",
        K + "ask.work.0": "How long have you been on this floor, anyway?",
        K + "ask.work.0.0": "Eleven years, and they still spell my name wrong.",
        K + "ask.work.0.1": "Long enough to know exactly what I am signing.",
        K + "ask.work.0.2": "Not long. I was already halfway out of the door.",
        K + "work_q2": "So why sign it? Honestly.",
        K + "ask.work.1": "So why sign it? Honestly.",
        K + "ask.work.1.0": "The money. I am not going to dress it up.",
        K + "ask.work.1.1": "There is nothing down here worth staying for.",
        K + "ask.work.1.2": "I want to see it. Somebody has to want to.",
        K + "work.reason.money": "The money. Eleven years of it would not have cleared what four years of this will, and you had done the sums more than once.",
        K + "work.reason.nothing": "Nothing worth staying for. You said it lightly, and they laughed, and you both let it stand.",
        K + "work.reason.see": "To see it. That was the true one, and it embarrassed you to hear it out loud in an office.",
        K + "work_go": "I would say see you in four years, but I will not be here either. Good luck. Do not leave your mug.",
        K + "work.later": "Later. The floor has gone dark a bank at a time, and the printer has finished with you.",
        K + "work.quiet": "The lift is behind you. There is a box by your chair with eleven years in it and room to spare, which is a thing you would rather not have been told.",
        K + "work.pack": "Whatever is in the box when the lift doors close is what you took with you. Nobody is going to check.",
        K + "hint.pack_work": "Clear the desk into the box. Whatever is in it when you get into the lift is what you brought.",
        # The bar.
        K + "bar.arrive": "The bar on the corner, at the end of the evening. Your case is by the door because you came straight from the port office, and the barman has not asked.",
        K + "objective.dart": "Throw a dart, or drink up",
        K + "hint.dart": "There are three darts in your hand and a board on the wall, and a pint on the bar with your name on it.",
        K + "bar_figure": "The barman has come down the counter with a cloth, which is what a barman does when they have something to say.",
        K + "bar_q1": "Who is drinking with you tonight, then?",
        K + "ask.bar.0": "Who is drinking with you tonight, then?",
        K + "ask.bar.0.0": "Nobody. That was rather the arrangement.",
        K + "ask.bar.0.1": "People from work. They are being decent about it.",
        K + "ask.bar.0.2": "One person, and we are not saying much.",
        K + "bar_q2": "Last orders. Anything you want to say before you go?",
        K + "ask.bar.1": "Last orders. Anything you want to say before you go?",
        K + "ask.bar.1.0": "Something funny. It will get a laugh.",
        K + "ask.bar.1.1": "Something true, which will not.",
        K + "ask.bar.1.2": "Nothing. That is rather the point.",
        K + "bar.reason.laugh": "Something funny. It got a laugh, and you took the laugh with you, and it has lasted longer than you expected.",
        K + "bar.reason.truth": "Something true. Nobody laughed, and the barman found something to do at the far end.",
        K + "bar.reason.nothing": "Nothing. You finished the drink and put the glass down carefully, the way you do when you have decided not to say a thing.",
        K + "bar_go": "Right. I am putting the stools up. Take your time, and mind the case on the way out.",
        K + "bar.later": "Later. The stools are up on the far end of the counter and the set is on the credits of something, and the only light left on is the one over the door.",
        K + "bar.quiet": "The coach to the port is at six. Your case is where you left it. So is the rest of the bar.",
        K + "bar.pack": "Whatever is in the case when you go out of that door is what you took with you, and this is not a place that counts its glasses.",
        K + "hint.pack_bar": "Your case is by the door. Whatever is in it when you step out is what you brought.",
        # Who and what.
        "flashback.surrogate.television": "Television",
        "flashback.surrogate.dog": "Biscuit",
        "flashback.surrogate.suitcase": "Suitcase",
        "flashback.surrogate.sign.home": "HOME",
        "flashback.surrogate.sign.work": "THE OFFICE",
        "flashback.surrogate.sign.bar": "THE BAR",
        "flashback.surrogate.place.home": "Home",
        "flashback.surrogate.place.work": "The office",
        "flashback.surrogate.place.bar": "The bar",
        "flashback.surrogate.reason.home.return": "To come back",
        "flashback.surrogate.reason.home.money": "For the money",
        "flashback.surrogate.reason.home.gone": "To go",
        "flashback.surrogate.reason.work.money": "The money",
        "flashback.surrogate.reason.work.nothing": "Nothing here",
        "flashback.surrogate.reason.work.see": "To see it",
        "flashback.surrogate.reason.bar.laugh": "Something funny",
        "flashback.surrogate.reason.bar.truth": "Something true",
        "flashback.surrogate.reason.bar.nothing": "Nothing at all",
        "crew.surrogate.figure.name": "Someone",
        "crew.surrogate.figure.busy": "They are not going to answer that one.",
        "crew.surrogate.figure.epitaph": "Nobody. There was never anybody there.",
        "crew.surrogate.coworker.name": "Someone from work",
        "crew.surrogate.coworker.busy": "They have gone back to their desk.",
        "crew.surrogate.coworker.epitaph": "Nobody. There was never anybody there.",
        "crew.surrogate.barman.name": "The barman",
        "crew.surrogate.barman.busy": "He is wiping the same glass again.",
        "crew.surrogate.barman.epitaph": "Nobody. There was never anybody there.",
        # Things to read.
        "document.surrogate.bills.title": "FINAL NOTICE",
        "document.surrogate.bills.body": "HALVERSON & DEAL, LENDERS\nAccount 40-1187-C. Arrears: 14,200. Fourth notice. Recovery proceedings begin on the 30th unless the balance is cleared in full.\n\nMUNICIPAL POWER\nSupply restricted from the 12th. Reconnection is subject to a fee and a visit.\n\nST. AGNES CLINICAL\nOutstanding: 61,940. We are happy to discuss a payment plan. This is the last letter we will send before the account is passed on.\n\nOn the back of the last one, in pencil, in your writing: 4 yrs = all of it.",
        "document.surrogate.calendar.title": "The calendar",
        "document.surrogate.calendar.body": "THURSDAY 22nd, circled twice.\n\n05:40 - coach to the port. Written underneath, in somebody else's hand: DO NOT MISS IT.\n\nWritten under that, in yours: I know.",
        "document.surrogate.contract.title": "SURROGATE PILOT SERVICES AGREEMENT - page 11 of 14",
        "document.surrogate.contract.body": "7. TERM. Four (4) years from arrival at Habitat Seven, Sallow.\n\n8. REMUNERATION. All qualifying household liabilities (Schedule B) are discharged on arrival. The balance is paid on completion of the term.\n\n9. NON-RETURN EVENTS. Where the pilot does not complete the term for any reason listed in Schedule C, Schedule B remains discharged. The company considers this generous.\n\n10. RETURN PASSAGE. Per Schedule D. Schedule D is scheduled.\n\n[ ] I have read clause 9.\n\nYour name is already printed on the signature line. It is spelled wrong.",
        "document.surrogate.contract_copy.title": "Printed 23:14",
        "document.surrogate.contract_copy.body": "SURROGATE PILOT SERVICES AGREEMENT - signed copy.\n\nFour years. Schedule B discharged on arrival. Return passage per Schedule D.\n\nSigned, in your writing, over a name that is not quite yours. Somebody has already stapled it.",
        # Screens and messages.
        "gui.surrogate.document.close": "Put it down",
        "gui.surrogate.choice.how": "Click an answer, or press its number",
        "message.surrogate.carrier.empty": "There is no dog near enough to call.",
        "message.surrogate.carrier.in": "%s is in the carrier.",
        "tooltip.surrogate.dog_carrier.empty": "Empty. Somebody was meant to be in it.",
        "tooltip.surrogate.dog_carrier.someone": "Somebody is in here, and he is not complaining.",
        # Subtitles.
        "subtitles.surrogate.flashback_radio": "A radio, somewhere",
        "subtitles.surrogate.flashback_television": "A television, murmuring",
        "subtitles.surrogate.flashback_car_pass": "A car goes past",
        "subtitles.surrogate.flashback_car_horn": "A car horn, outside",
        "subtitles.surrogate.flashback_creak": "Springs creak",
        "subtitles.surrogate.flashback_footsteps_upstairs": "Footsteps upstairs",
        "subtitles.surrogate.flashback_door_upstairs": "A door closes upstairs",
        "subtitles.surrogate.flashback_front_door": "The front door",
        "subtitles.surrogate.flashback_tv_switch": "The channel changes",
        "subtitles.surrogate.flashback_tv_credits": "Credits music",
        "subtitles.surrogate.flashback_news_sting": "The news comes on",
        "subtitles.surrogate.flashback_dart_hit": "A dart hits the board",
        "subtitles.surrogate.flashback_glass_clink": "Glasses clink",
        "subtitles.surrogate.flashback_pub_murmur": "The bar, murmuring",
        "subtitles.surrogate.flashback_fluorescent_hum": "Lights humming",
        "subtitles.surrogate.flashback_lift_ding": "The lift arrives",
        "subtitles.surrogate.flashback_printer": "The printer",
        "subtitles.surrogate.flashback_pizza_box": "The pizza box opens",
        "subtitles.surrogate.flashback_light_switch": "A light switch",
    })


def merge_lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(path, encoding="utf-8") as f:
        lang = json.load(f)
    lang.update(LANG)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("updated lang/en_us.json with", len(LANG), "keys")


if __name__ == "__main__":
    generate()
    merge_lang()
    print(len(BLOCKS), "blocks of furniture written")
    sys.exit(0)
