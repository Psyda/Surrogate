#!/usr/bin/env python3
"""Generates the data-driven half of the Toxic Wastes update: world preset, noise settings, biomes, features,
tags, loot tables, recipes, block states, models, the damage type, and the lang entries for all of it.

Run from the mod root:  python3 tools/gen_data.py
Hand-written files are left alone; everything this script owns is regenerated from scratch.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
DATA = os.path.join(ROOT, "data")
ASSETS = os.path.join(ROOT, "assets", "surrogate")
TOOLS = os.path.dirname(os.path.abspath(__file__))
MOD = "surrogate"


def write(rel, obj):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")
    print("wrote", rel)


def mid(name):
    return f"{MOD}:{name}"


# ======================================================================================
# Surface rules
# ======================================================================================
def cond(if_true, then_run):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}


def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}


def block(name, props=None):
    state = {"Name": name}
    if props:
        state["Properties"] = props
    return {"type": "minecraft:block", "result_state": state}


def biome_is(*ids):
    return {"type": "minecraft:biome", "biome_is": list(ids)}


def stone_depth(surface_type, add_surface_depth=False, offset=0, secondary=0):
    return {"type": "minecraft:stone_depth", "surface_type": surface_type, "add_surface_depth": add_surface_depth,
            "offset": offset, "secondary_depth_range": secondary}


def noise(name, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": name, "min_threshold": lo, "max_threshold": hi}


BIOMES = ["toxic_desert", "ash_dunes", "acid_flats", "salt_pans", "dead_grove", "caustic_mire", "rift"]

with open(os.path.join(TOOLS, "vanilla_overworld_noise_settings.json"), encoding="utf-8") as f:
    vanilla_noise = json.load(f)
vanilla_rules = vanilla_noise["surface_rule"]["sequence"]
bedrock_rule = vanilla_rules[0]
deepslate_rule = vanilla_rules[2]


def y_above(y, wobble=0):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": wobble, "add_stone_depth": False}


# The mesa faces: flat beds of sallow rock from the valley floor to the tables, highest first so the first
# true condition wins. Ash dunes sit on the tables, so their columns are what the cliffs are cut from.
STRATA = [(96, "minecraft:tuff"), (93, "minecraft:yellow_terracotta"), (90, "minecraft:tuff"), (87, "minecraft:terracotta"),
          (85, "minecraft:calcite"), (84, "minecraft:brown_terracotta"), (80, "minecraft:tuff"), (77, "minecraft:yellow_terracotta"),
          (74, "minecraft:terracotta"), (72, "minecraft:tuff"), (69, "minecraft:brown_terracotta"), (66, "minecraft:tuff")]
strata = seq(*[cond(y_above(y), block(name)) for y, name in STRATA], block("minecraft:tuff"))

surface = seq(
    bedrock_rule,
    cond({"type": "minecraft:above_preliminary_surface"}, seq(
        cond(biome_is(mid("toxic_desert")), seq(
            cond(stone_depth("ceiling"), block(mid("caustic_sandstone"))),
            cond(stone_depth("floor", True, 0, 0), block(mid("caustic_sand"))),
            cond(stone_depth("floor", True, 0, 6), block(mid("caustic_sandstone"))))),
        cond(biome_is(mid("ash_dunes")), seq(
            cond(stone_depth("ceiling"), block("minecraft:tuff")),
            cond(stone_depth("floor", True, 0, 0), block(mid("ash"))),
            cond(stone_depth("floor", True, 0, 2), block("minecraft:tuff")),
            strata)),
        cond(biome_is(mid("salt_pans")), seq(
            cond(stone_depth("floor", True, 0, 0), seq(
                cond(noise("minecraft:surface", 0.45, 10.0), block(mid("caustic_sand"))),
                block("minecraft:calcite"))),
            cond(stone_depth("floor", True, 0, 4), block("minecraft:tuff")))),
        cond(biome_is(mid("acid_flats")), seq(
            cond(stone_depth("floor", True, 0, 0), seq(
                cond(noise("minecraft:surface", 0.2, 10.0), block("minecraft:clay")),
                block("minecraft:mud"))),
            cond(stone_depth("floor", True, 0, 4), block("minecraft:gravel")))),
        cond(biome_is(mid("dead_grove")), seq(
            cond(stone_depth("floor", True, 0, 0), seq(
                cond(noise("minecraft:surface", 0.35, 10.0), block("minecraft:podzol", {"snowy": "false"})),
                block("minecraft:coarse_dirt"))),
            cond(stone_depth("floor", True, 0, 4), block("minecraft:dirt")))),
        # The belt: the same floors as the corridors, but everything on them has been rained on for a century.
        cond(biome_is(mid("caustic_mire")), seq(
            cond(stone_depth("floor", True, 0, 0), seq(
                cond(noise("minecraft:surface", 0.3, 10.0), block("minecraft:mud")),
                block(mid("caustic_sand")))),
            cond(stone_depth("floor", True, 0, 3), block("minecraft:clay")),
            cond(stone_depth("floor", True, 0, 6), block("minecraft:tuff")))),
        # The Rift: raw section through everything the valleys are made of, wet at the bottom.
        cond(biome_is(mid("rift")), seq(
            cond(stone_depth("ceiling"), block("minecraft:tuff")),
            cond(stone_depth("floor", True, 0, 0), seq(
                cond(y_above(40), block("minecraft:tuff")),
                block("minecraft:mud"))),
            cond(stone_depth("floor", True, 0, 3), block("minecraft:tuff")),
            strata)),
    )),
    deepslate_rule,
)

# ======================================================================================
# Terrain: flat valley floors between mesa tables (docs/DESIGN-crawler.md)
# ======================================================================================
# The vanilla router's spline terrain is replaced by a height field: valley floors just above the acid,
# rolling by a block or two over a hundred blocks, and tables that rise from them across a few blocks. The
# floors are the corridors along the zero contour of one long noise plus the basins where a second one dips;
# seas fill the deepest basins and rivers run in the lowest channels of the others. Caves keep their vanilla
# shapes but never open in the top six blocks, so a floor stays a floor. The router's `continents` is the
# mesa mask (floor about -0.8, table +0.8, sea below -1.05) and `depth` is the river mask, which is how the
# biomes and the site picker (world.Valleys) read the terrain without loading a chunk.
FLOOR_Y = 64.0          # valley floors sit just above sea level (63)
FLOOR_ROLL = 2.5        # blocks the floors rise and fall over ~128 blocks
TABLE_RISE = 26.0       # how far the tables stand above the floors
TABLE_ROLL = 8.0        # blocks the table tops rise and fall over ~256 blocks
CORRIDOR = 0.2          # half width of a corridor, in noise units (about 60 blocks)
BASIN_EDGE = 0.42       # a basin opens where the basin noise drops below this
CLIFF_GAIN = 50.0       # how sharply a floor becomes a table
SEA_DEPTH = 10.0
RIVER_DEPTH = 5.0
SLOPE = 0.25            # density per block below the surface
CAVE_ROOF = 3.5         # density (14 blocks) the ground must reach before a cave may be cut: the interpolation
                        # works in 8-block cells, so a shallower window let cave mouths pull the floor down
# The Rift (docs/DESIGN-campaign.md, act five): the zero contour of one very long noise, cut down through
# every valley floor it crosses. It is continuous, so together with the tables it partitions the drivable
# floor, and the far side of it cannot be reached until somebody builds a bridge. The mask goes out as the
# router's `temperature`, which nothing else here uses, so world.Valleys reads it without loading a chunk.
RIFT_HALF = 0.009       # half width of the chasm, in noise units: about 16 blocks at the narrows, 40 at the
                        # wide places. It cannot go much under this and still cut: the density interpolation
                        # works in four-block columns, so a narrower slot comes out as a shallow V.
RIFT_GAIN = 320.0       # how sharply a floor becomes a Rift wall
RIFT_DEPTH = 34.0       # blocks the Rift floor sits below the valley floor
# The acid belt: a region, not a weather state. A second very long noise, out as `weirdness`.
BELT_EDGE = 0.16        # the belt opens where the belt noise rises above this
BELT_FEATHER = 0.10     # noise units the belt edge is smeared over

TERRAIN_NOISES = {
    "mesa": (-10, [1.0, 1.0, 0.5]),
    "basin": (-10, [1.0, 0.5]),
    "channel": (-9, [1.0, 1.0]),
    "floor": (-7, [1.0, 0.5]),
    "table": (-8, [1.0, 1.0]),
    "rift": (-11, [1.0, 0.3]),
    "belt": (-12, [1.0]),
}
for name, (octave, amplitudes) in TERRAIN_NOISES.items():
    write(f"data/{MOD}/worldgen/noise/{name}.json", {"firstOctave": octave, "amplitudes": amplitudes})


def df(name):
    return mid("mesa/" + name)


def df_write(name, obj):
    write(f"data/{MOD}/worldgen/density_function/mesa/{name}.json", obj)
    return df(name)


def dnoise(name, xz=1.0, y=0.0):
    return {"type": "minecraft:noise", "noise": mid(name), "xz_scale": xz, "y_scale": y}


def add(a, b):
    return {"type": "minecraft:add", "argument1": a, "argument2": b}


def mul(a, b):
    return {"type": "minecraft:mul", "argument1": a, "argument2": b}


def dmin(a, b):
    return {"type": "minecraft:min", "argument1": a, "argument2": b}


def dmax(a, b):
    return {"type": "minecraft:max", "argument1": a, "argument2": b}


def dabs(a):
    return {"type": "minecraft:abs", "argument": a}


def neg(a):
    return mul(-1.0, a)


def clamp(a, lo, hi):
    return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}


def flat_cache(a):
    return {"type": "minecraft:flat_cache", "argument": a}


def range_choice(inp, lo, hi, when_in, when_out):
    return {"type": "minecraft:range_choice", "input": inp, "min_inclusive": lo, "max_exclusive": hi,
            "when_in_range": when_in, "when_out_of_range": when_out}


def ramp(a, lo, hi):
    """0 where a <= lo, 1 where a >= hi, linear between."""
    return clamp(mul(add(a, -lo), 1.0 / (hi - lo)), 0.0, 1.0)


def y_gradient(from_y, to_y, from_value, to_value):
    return {"type": "minecraft:y_clamped_gradient", "from_y": from_y, "to_y": to_y, "from_value": from_value, "to_value": to_value}


def shape(core):
    """Vanilla's bottom and top fades around a density."""
    return add(0.1171875, mul(y_gradient(-64, -40, 0.0, 1.0),
                              add(-0.1171875, add(-0.078125, mul(y_gradient(240, 256, 1.0, 0.0), add(0.078125, core))))))


mesa_n, basin_n, channel_n, floor_n, table_n, rift_n, belt_n = (
    dnoise(n) for n in ("mesa", "basin", "channel", "floor", "table", "rift", "belt"))
valley = df_write("valley", flat_cache(dmax(add(CORRIDOR, neg(dabs(mesa_n))), add(-BASIN_EDGE, neg(basin_n)))))
floor_mask = df_write("floor_mask", flat_cache(clamp(mul(valley, CLIFF_GAIN), 0.0, 1.0)))
sea = df_write("sea", flat_cache(ramp(neg(basin_n), 0.55, 0.72)))
river = df_write("river", flat_cache(mul(mul(ramp(add(0.075, neg(dabs(channel_n))), 0.0, 0.06),
                                             ramp(neg(basin_n), 0.15, 0.35)), floor_mask)))
# Only where there is floor to cut: the Rift tapers out as it climbs a cliff and never scars a table top.
rift = df_write("rift", flat_cache(mul(clamp(mul(add(RIFT_HALF, neg(dabs(rift_n))), RIFT_GAIN), 0.0, 1.0), floor_mask)))
belt = df_write("belt", flat_cache(ramp(belt_n, BELT_EDGE, BELT_EDGE + BELT_FEATHER)))
floor_height = df_write("floor_height", flat_cache(add(add(FLOOR_Y, mul(FLOOR_ROLL, floor_n)),
                                                       add(add(mul(-SEA_DEPTH, sea), mul(-RIVER_DEPTH, river)),
                                                           mul(-RIFT_DEPTH, rift)))))
height = df_write("height", flat_cache(add(floor_height, mul(add(1.0, neg(floor_mask)), add(TABLE_RISE, mul(TABLE_ROLL, table_n))))))
base = df_write("base", mul(SLOPE, add(height, neg("minecraft:y"))))

# Vanilla caves with the height field in place of sloped_cheese, and no entrances: nothing opens at the surface.
cheese = add(mul(4.0, {"type": "minecraft:square", "argument": {"type": "minecraft:noise", "noise": "minecraft:cave_layer", "xz_scale": 1.0, "y_scale": 8.0}}),
             add(clamp(add(0.27, {"type": "minecraft:noise", "noise": "minecraft:cave_cheese", "xz_scale": 1.0, "y_scale": 0.6666666666666666}), -1.0, 1.0),
                 clamp(add(1.5, mul(-0.64, base)), 0.0, 0.5)))
spaghetti = add("minecraft:overworld/caves/spaghetti_2d", "minecraft:overworld/caves/spaghetti_roughness_function")
pillars = range_choice("minecraft:overworld/caves/pillars", -1000000.0, 0.03, -1000000.0, "minecraft:overworld/caves/pillars")
caves = dmax(dmin(dmin(cheese, "minecraft:overworld/caves/entrances"), spaghetti), pillars)
core = range_choice(base, -1000000.0, CAVE_ROOF, base, caves)
noodles = range_choice(base, -1000000.0, CAVE_ROOF, 64.0, "minecraft:overworld/caves/noodle")
final_density = dmin({"type": "minecraft:squeeze", "argument": mul(0.64, {"type": "minecraft:interpolated", "argument": {"type": "minecraft:blend_density", "argument": shape(core)}})},
                     noodles)

router = dict(vanilla_noise["noise_router"])
router["continents"] = df_write("continents", add(add(0.8, mul(-1.6, floor_mask)), mul(-0.6, sea)))
router["erosion"] = df_write("erosion", flat_cache(basin_n))
router["depth"] = river
# Two axes vanilla uses for climate carry the two regions instead: temperature is the Rift, weirdness the
# belt. Nothing else on Sallow reads either, and it means both are one noise sample away everywhere.
router["temperature"] = df_write("rift_mask", flat_cache(add(-1.0, mul(2.0, rift))))
router["ridges"] = df_write("belt_mask", flat_cache(add(-1.0, mul(2.0, belt))))
router["initial_density_without_jaggedness"] = shape(base)
router["final_density"] = final_density

noise_settings = dict(vanilla_noise)
noise_settings["surface_rule"] = surface
noise_settings["noise_router"] = router
# Spawn on a valley floor, away from the acid and the rivers, out of the Rift and out of the belt.
noise_settings["spawn_target"] = [{"temperature": [-1.0, -0.5], "humidity": [-1.0, 1.0], "continentalness": [-0.95, -0.65],
                                   "erosion": [-1.5, 1.5], "depth": 0.0, "weirdness": [-1.0, -0.5], "offset": 0.0}]
write(f"data/{MOD}/worldgen/noise_settings/toxic_wastes.json", noise_settings)

# ======================================================================================
# World preset and biome layout
# ======================================================================================
FULL = [-1.0, 1.0]


def params(temperature=FULL, humidity=FULL, continentalness=FULL, erosion=FULL, weirdness=FULL, depth=(0.0, 1.0)):
    return {"temperature": list(temperature), "humidity": list(humidity), "continentalness": list(continentalness),
            "erosion": list(erosion), "weirdness": list(weirdness), "depth": list(depth), "offset": 0.0}


# Continentalness is the mesa mask (sea below -1.05, floors near -0.8, tables above -0.4), erosion is the basin
# noise (open basins below -0.35), and depth is the river mask; see the terrain section above.
SEA_C = [-1.6, -1.05]
FLOOR_C = [-1.05, -0.4]
TABLE_C = [-0.4, 1.0]
LAND = [0.0, 0.7]
# Temperature is the Rift mask and weirdness the belt mask (see the terrain section): -1 outside, +1 inside.
NEAR_T = [-1.0, 0.4]
RIFT_T = [0.6, 1.0]
NEAR_W = [-1.0, 0.1]
BELT_W = [0.3, 1.0]
biome_layout = [
    # The chasm reads as itself wherever it cuts, so it wins on temperature before anything else is asked.
    {"biome": mid("rift"), "parameters": params(temperature=RIFT_T)},
    {"biome": mid("acid_flats"), "parameters": params(temperature=NEAR_T, continentalness=SEA_C)},
    {"biome": mid("acid_flats"), "parameters": params(temperature=NEAR_T, continentalness=[-1.05, 1.0], depth=[0.7, 1.0])},
    {"biome": mid("ash_dunes"), "parameters": params(temperature=NEAR_T, continentalness=TABLE_C, depth=LAND)},
    {"biome": mid("salt_pans"), "parameters": params(temperature=NEAR_T, continentalness=FLOOR_C, erosion=[-1.5, -0.35], weirdness=NEAR_W, depth=LAND)},
    {"biome": mid("dead_grove"), "parameters": params(temperature=NEAR_T, humidity=[0.1, 1.0], continentalness=FLOOR_C, erosion=[-0.35, 1.5], weirdness=NEAR_W, depth=LAND)},
    {"biome": mid("toxic_desert"), "parameters": params(temperature=NEAR_T, humidity=[-1.0, 0.1], continentalness=FLOOR_C, erosion=[-0.35, 1.5], weirdness=NEAR_W, depth=LAND)},
    # Downwind of the vent field: the same floors, and it rains on them.
    {"biome": mid("caustic_mire"), "parameters": params(temperature=NEAR_T, continentalness=FLOOR_C, weirdness=BELT_W, depth=LAND)},
]

write(f"data/{MOD}/worldgen/world_preset/toxic_wastes.json", {
    "dimensions": {
        "minecraft:overworld": {
            "type": "minecraft:overworld",
            "generator": {
                "type": "minecraft:noise",
                "biome_source": {"type": "minecraft:multi_noise", "biomes": biome_layout},
                "settings": mid("toxic_wastes"),
            },
        },
        "minecraft:the_nether": {
            "type": "minecraft:the_nether",
            "generator": {
                "type": "minecraft:noise",
                "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"},
                "settings": "minecraft:nether",
            },
        },
        "minecraft:the_end": {
            "type": "minecraft:the_end",
            "generator": {"type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"}, "settings": "minecraft:end"},
        },
    }
})
write("data/minecraft/tags/worldgen/world_preset/normal.json", {"replace": False, "values": [mid("toxic_wastes")]})
write(f"data/{MOD}/tags/worldgen/biome/toxic.json", {"replace": False, "values": [mid(b) for b in BIOMES]})

# ======================================================================================
# Features
# ======================================================================================
def simple_state(name, props=None):
    state = {"Name": name}
    if props:
        state["Properties"] = props
    return {"type": "minecraft:simple_state_provider", "state": state}


def surface_patch(block_name, tries, spread):
    """A random_patch that only puts blocks in air sitting on something solid."""
    return {
        "type": "minecraft:random_patch",
        "config": {
            "tries": tries, "xz_spread": spread, "y_spread": 1,
            "feature": {
                "feature": {"type": "minecraft:simple_block", "config": {"to_place": simple_state(block_name)}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:all_of", "predicates": [
                        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                        {"type": "minecraft:solid", "offset": [0, -1, 0]},
                    ]}}],
            },
        },
    }


def placed(feature, placement):
    return {"feature": feature, "placement": placement}


def rarity(chance):
    return {"type": "minecraft:rarity_filter", "chance": chance}


IN_SQUARE = {"type": "minecraft:in_square"}
BIOME_FILTER = {"type": "minecraft:biome"}
SURFACE = {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}

write(f"data/{MOD}/worldgen/configured_feature/sulfur_patch.json", {
    "type": "minecraft:disk",
    "config": {
        "half_height": 1,
        "radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4},
        "state_provider": {"fallback": simple_state(mid("sulfur_crust")), "rules": []},
        "target": {"type": "minecraft:matching_blocks", "blocks": [
            mid("caustic_sand"), mid("caustic_sandstone"), mid("ash"), "minecraft:calcite", "minecraft:tuff"]},
    },
})
write(f"data/{MOD}/worldgen/placed_feature/sulfur_patch.json",
      placed(mid("sulfur_patch"), [rarity(6), IN_SQUARE, SURFACE, BIOME_FILTER]))

write(f"data/{MOD}/worldgen/configured_feature/scrap_heap.json", surface_patch(mid("scrap_heap"), 5, 3))
write(f"data/{MOD}/worldgen/placed_feature/scrap_heap.json",
      placed(mid("scrap_heap"), [rarity(9), IN_SQUARE, SURFACE, BIOME_FILTER]))

write(f"data/{MOD}/worldgen/configured_feature/vent.json", surface_patch(mid("vent"), 1, 2))
write(f"data/{MOD}/worldgen/placed_feature/vent.json",
      placed(mid("vent"), [rarity(12), IN_SQUARE, SURFACE, BIOME_FILTER]))

# The ones that still work. Rare, one to a few chunks, and each is on a ninety second clock of its own.
write(f"data/{MOD}/worldgen/configured_feature/geyser.json", surface_patch(mid("geyser"), 1, 1))
write(f"data/{MOD}/worldgen/placed_feature/geyser.json",
      placed(mid("geyser"), [rarity(26), IN_SQUARE, SURFACE, BIOME_FILTER]))
# The same vent, thinned out for the tables and the flats. The field is centred on the mire and reaches onto
# them (docs/DESIGN-hazards.md): about one working vent in seventy chunks out there against one in
# twenty-six down in the belt, so a live one on the dunes is a thing you find rather than a thing you expect.
write(f"data/{MOD}/worldgen/placed_feature/geyser_sparse.json",
      placed(mid("geyser"), [rarity(70), IN_SQUARE, SURFACE, BIOME_FILTER]))

write(f"data/{MOD}/worldgen/configured_feature/petrified_tree.json", {
    "type": "minecraft:tree",
    "config": {
        "decorators": [],
        "dirt_provider": simple_state("minecraft:coarse_dirt"),
        "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 0, "offset": 0, "height": 0},
        "foliage_provider": simple_state("minecraft:air"),
        "force_dirt": False,
        "ignore_vines": True,
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
        "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 4, "height_rand_a": 3, "height_rand_b": 0},
        "trunk_provider": simple_state("minecraft:stripped_dark_oak_log", {"axis": "y"}),
    },
})
write(f"data/{MOD}/worldgen/placed_feature/petrified_tree.json", placed(mid("petrified_tree"), [
    {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
        {"data": 1, "weight": 5}, {"data": 2, "weight": 3}, {"data": 4, "weight": 1}]}},
    IN_SQUARE,
    {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
    {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
    BIOME_FILTER,
]))

def ore_target(tag_or_blocks, state):
    """Ore targets: one rule test per block (there is no list test), or one tag test for a "#tag"."""
    if isinstance(tag_or_blocks, str):
        return [{"target": {"predicate_type": "minecraft:tag_match", "tag": tag_or_blocks[1:]}, "state": state}]
    return [{"target": {"predicate_type": "minecraft:block_match", "block": b}, "state": state} for b in tag_or_blocks]


def ore(name, targets, size, count, height, discard_on_air=0.0):
    write(f"data/{MOD}/worldgen/configured_feature/{name}.json", {"type": "minecraft:ore", "config": {
        "size": size, "discard_chance_on_air_exposure": discard_on_air, "targets": [t for group in targets for t in group]}})
    write(f"data/{MOD}/worldgen/placed_feature/{name}.json", placed(mid(name), [
        {"type": "minecraft:count", "count": count}, IN_SQUARE, height, BIOME_FILTER]))


def uniform_height(lo, hi):
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


def trapezoid_height(lo, hi):
    return {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}


MESA_BEDS = ["minecraft:tuff", "minecraft:terracotta", "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:calcite"]
# Cinnabar: red veins through the mesa beds and the stone under them.
ore("ore_cinnabar", [ore_target("#minecraft:stone_ore_replaceables", {"Name": mid("cinnabar_ore")}),
                     ore_target(MESA_BEDS, {"Name": mid("cinnabar_ore")})], 7, 9, uniform_height(30, 120))
# Halite: rock salt just under the pans.
ore("ore_halite", [ore_target([mid("caustic_sandstone"), "minecraft:calcite", "minecraft:stone", "minecraft:tuff"], {"Name": mid("halite_ore")})],
    6, 7, uniform_height(48, 128))
# Cobalt: in the stone below the valleys, deepslate lower down.
ore("ore_cobalt", [ore_target("#minecraft:stone_ore_replaceables", {"Name": mid("cobalt_ore")}),
                   ore_target("#minecraft:deepslate_ore_replaceables", {"Name": mid("deepslate_cobalt_ore")})], 7, 8, trapezoid_height(-24, 56))
# Tellurium: the company's deep resource. Small pockets in the deepslate, half of them lost where a cave cuts through.
ore("ore_tellurium", [ore_target("#minecraft:deepslate_ore_replaceables", {"Name": mid("tellurium_ore")})], 4, 3, uniform_height(-60, -8), 0.5)

# ======================================================================================
# Biomes
# ======================================================================================
UNDERGROUND = [
    [], ["minecraft:lake_lava_underground", "minecraft:lake_lava_surface"], ["minecraft:amethyst_geode"],
    ["minecraft:fossil_upper", "minecraft:fossil_lower"],
    [], [],
    ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
     "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower",
     "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper",
     "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_gold_lower",
     "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond", "minecraft:ore_diamond_medium",
     "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried", "minecraft:ore_lapis", "minecraft:ore_lapis_buried",
     "minecraft:ore_copper", "minecraft:underwater_magma", "minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel"],
    [], ["minecraft:spring_water", "minecraft:spring_lava"],
]
# Everything custom lives in the vegetal step, always in this order, so feature ordering never cycles between biomes.
CUSTOM_ORDER = ["sulfur_patch", "scrap_heap", "vent", "geyser", "geyser_sparse", "petrified_tree"]
# Ores of Sallow, always in this order in the underground_ores step. Halite only where the ground is salt.
ORE_ORDER = ["ore_cinnabar", "ore_halite", "ore_cobalt", "ore_tellurium"]
COMMON_ORES = ["ore_cinnabar", "ore_cobalt", "ore_tellurium"]
SALT_ORES = COMMON_ORES + ["ore_halite"]

# Nothing lives on Sallow. Every spawner list is empty and the dungeons are out of the underground steps, so
# the only things that move out there are the crawler, six people in six sealed rooms, and what is under the
# rock (docs/DESIGN-hazards.md). Surrogate.vanillaMonsters puts them back for anyone who wants them; the
# server sweeps up any hostile that arrives by some other route.
NO_SPAWNERS = {"monster": [], "creature": [], "ambient": [], "axolotls": [], "misc": [],
               "underground_water_creature": [], "water_ambient": [], "water_creature": []}


def spawn(name, weight, lo, hi):
    return {"type": mid(name), "weight": weight, "minCount": lo, "maxCount": hi}


# Nothing lives on Sallow except what grew here (docs/DESIGN-fauna.md). Four animals, none of them a threat
# in the way an empty monster list is a promise that there are none. Only two of the four come off these
# lists: a slagback wants to be beside a geyser and a lantern slug wants a cave ceiling, and the vanilla
# spawner can aim at neither, so Fauna.java places those two itself.
#
# The weights are low and the group sizes small on purpose. A trundle should be a thing you notice, not a
# herd you walk through.
TRUNDLES = [spawn("trundle", 8, 1, 2)]
TOCKERS = [spawn("tocker", 6, 1, 3)]


def wildlife(*groups):
    """A spawner block with a creature list in it and every other list still empty."""
    out = dict(NO_SPAWNERS)
    creatures = []
    for g in groups:
        creatures += g
    out["creature"] = creatures
    return out


def biome(name, *, temperature, downfall, precipitation, fog, sky, water, water_fog, grass, foliage,
          vegetal, custom, music="minecraft:music.overworld.desert", particle=None, ores=COMMON_ORES,
          spawners=None):
    effects = {
        "fog_color": fog, "sky_color": sky, "water_color": water, "water_fog_color": water_fog,
        "grass_color": grass, "foliage_color": foliage,
        "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
        "music": {"sound": music, "min_delay": 12000, "max_delay": 24000, "replace_current_music": False},
    }
    if particle:
        effects["particle"] = {"options": {"type": particle[0]}, "probability": particle[1]}
    features = [list(step) for step in UNDERGROUND]
    features[6] += [mid(o) for o in ORE_ORDER if o in ores]
    features.append(list(vegetal) + [mid(c) for c in CUSTOM_ORDER if c in custom])
    features.append(["minecraft:freeze_top_layer"])
    return {
        "temperature": temperature,
        "downfall": downfall,
        "has_precipitation": precipitation,
        "effects": effects,
        # No canyons. minecraft:canyon fires on one chunk in a hundred between y 10 and 67, so it cut real
        # chasms across valley floors that the reachability fill could not see, and a drive the terrain scan
        # called drivable could be cut in half on the ground. The Rift is the only chasm on Sallow now, it is
        # in the noise, and Valleys knows exactly where it is.
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground"]},
        "features": features,
        "spawners": dict(spawners) if spawners else dict(NO_SPAWNERS),
        "spawn_costs": {},
    }


SCRUB = ["minecraft:glow_lichen", "minecraft:patch_dead_bush_2", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"]

write(f"data/{MOD}/worldgen/biome/toxic_desert.json", biome(
    "toxic_desert", temperature=2.0, downfall=0.0, precipitation=False,
    fog=0xC9B85E, sky=0x9FA050, water=0x6E8B2B, water_fog=0x2F4A0F, grass=0x8A8A3A, foliage=0x7A7A2A,
    vegetal=SCRUB, custom=["sulfur_patch", "scrap_heap"], ores=SALT_ORES,
    spawners=wildlife(TRUNDLES, TOCKERS)))
write(f"data/{MOD}/worldgen/biome/ash_dunes.json", biome(
    "ash_dunes", temperature=1.6, downfall=0.0, precipitation=False,
    fog=0x77746A, sky=0x6E6B5F, water=0x4A5A3A, water_fog=0x1F2A14, grass=0x5C5C40, foliage=0x4E4E36,
    vegetal=["minecraft:glow_lichen"], custom=["sulfur_patch", "scrap_heap", "vent", "geyser_sparse"],
    particle=("minecraft:white_ash", 0.02),
    spawners=wildlife(TRUNDLES)))
write(f"data/{MOD}/worldgen/biome/acid_flats.json", biome(
    "acid_flats", temperature=1.0, downfall=0.4, precipitation=True,
    fog=0x9CB35A, sky=0x8E9E4E, water=0x7FBF2A, water_fog=0x3E6A10, grass=0x7D8F3A, foliage=0x6C7D2E,
    vegetal=["minecraft:glow_lichen", "minecraft:patch_dead_bush_2"], custom=["scrap_heap"], ores=SALT_ORES,
    spawners=wildlife(TOCKERS)))
write(f"data/{MOD}/worldgen/biome/salt_pans.json", biome(
    "salt_pans", temperature=1.9, downfall=0.0, precipitation=False,
    fog=0xD9D7B0, sky=0xA9AA6E, water=0x86A648, water_fog=0x3B4F1A, grass=0xA0A070, foliage=0x8C8C5E,
    vegetal=["minecraft:glow_lichen", "minecraft:patch_dead_bush_2"], custom=["sulfur_patch", "scrap_heap", "geyser_sparse"], ores=SALT_ORES,
    spawners=wildlife(TRUNDLES, TOCKERS)))
write(f"data/{MOD}/worldgen/biome/dead_grove.json", biome(
    "dead_grove", temperature=1.2, downfall=0.3, precipitation=True,
    fog=0xA8A66A, sky=0x8E9450, water=0x6E8B2B, water_fog=0x2F4A0F, grass=0x6B6B2F, foliage=0x5B5B25,
    vegetal=SCRUB, custom=["scrap_heap", "petrified_tree"], music="minecraft:music.overworld.forest",
    spawners=wildlife(TRUNDLES, TOCKERS)))
# The belt, downwind of the vent field: wet floors that nothing has been able to dry out. It rains here, and
# what falls is not water (docs/DESIGN-hazards.md). The rain itself is the mod's, on the belt's own clock.
write(f"data/{MOD}/worldgen/biome/caustic_mire.json", biome(
    "caustic_mire", temperature=1.1, downfall=0.9, precipitation=True,
    fog=0x5E6B3A, sky=0x556031, water=0x5C7A22, water_fog=0x25400C, grass=0x4E5A28, foliage=0x424D20,
    vegetal=["minecraft:glow_lichen", "minecraft:patch_dead_bush_2"], custom=["vent", "geyser", "scrap_heap"],
    particle=("minecraft:white_ash", 0.008), music="minecraft:music.overworld.swamp"))
# The Rift: thirty-four blocks of section through everything the valleys are made of, with mist at the bottom.
write(f"data/{MOD}/worldgen/biome/rift.json", biome(
    "rift", temperature=1.0, downfall=0.5, precipitation=False,
    fog=0x2A3320, sky=0x3C4630, water=0x6E8B2B, water_fog=0x1A2A08, grass=0x4A4A28, foliage=0x3E3E20,
    vegetal=["minecraft:glow_lichen"], custom=["vent"], music="minecraft:music.overworld.dripstone_caves",
    particle=("minecraft:white_ash", 0.03)))

# ======================================================================================
# Damage type
# ======================================================================================
write(f"data/{MOD}/damage_type/toxin.json", {"exhaustion": 0.0, "message_id": "surrogate.toxin", "scaling": "never"})
write("data/minecraft/tags/damage_type/bypasses_armor.json", {"replace": False, "values": [mid("toxin")]})

# ======================================================================================
# Block tags
# ======================================================================================
# Everything a pickaxe breaks; sections below extend it and the props section writes it out once, last.
PICKAXE = [mid("dive_chair"), mid("charging_dock"), mid("life_support"), mid("solar_collector"), mid("power_conduit"), mid("decon_shower"), mid("hull_plating"),
           mid("reinforced_glass"), mid("airlock_door"), mid("caustic_sandstone"), mid("sulfur_crust"), mid("vent"),
           mid("geyser"), mid("geothermal_tap"), mid("damper_beacon"), mid("relay_mast"), mid("span_anchor"),
           mid("corroded_machine"), mid("survey_stake")]
write("data/minecraft/tags/block/mineable/shovel.json", {"replace": False, "values": [mid("caustic_sand"), mid("ash"), mid("scrap_heap")]})
write("data/minecraft/tags/block/sand.json", {"replace": False, "values": [mid("caustic_sand"), mid("ash")]})
write("data/minecraft/tags/block/doors.json", {"replace": False, "values": [mid("airlock_door"), mid("dock_door")]})
write(f"data/{MOD}/tags/block/mineable/drill.json", {"replace": False, "values": ["#minecraft:mineable/pickaxe", "#minecraft:mineable/shovel"]})
# Caustic: fouls the air of a sealed room when carried, dropped or built into its walls.

# ======================================================================================
# Loot tables
# ======================================================================================
def self_drop(name):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": mid(name)}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": mid(f"blocks/{name}"),
    }


SILK_TOUCH = {"condition": "minecraft:match_tool", "predicate": {"predicates": {
    "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}

for name in ["life_support", "solar_collector", "power_conduit", "decon_shower", "hull_plating", "caustic_sand", "caustic_sandstone", "ash", "scrap_heap", "vent",
             "geyser", "geothermal_tap", "damper_beacon", "relay_mast", "span_anchor"]:
    write(f"data/{MOD}/loot_table/blocks/{name}.json", self_drop(name))

write(f"data/{MOD}/loot_table/blocks/reinforced_glass.json", {
    "type": "minecraft:block",
    "pools": [{"rolls": 1, "bonus_rolls": 0, "conditions": [SILK_TOUCH],
               "entries": [{"type": "minecraft:item", "name": mid("reinforced_glass")}]}],
    "random_sequence": mid("blocks/reinforced_glass"),
})
write(f"data/{MOD}/loot_table/blocks/sulfur_crust.json", {
    "type": "minecraft:block",
    "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": mid("sulfur_crust"), "conditions": [SILK_TOUCH]},
        {"type": "minecraft:item", "name": mid("sulfur"), "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2.0, "max": 4.0}, "add": False},
            {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count", "parameters": {"bonusMultiplier": 1}},
            {"function": "minecraft:explosion_decay"}]},
    ]}]}],
    "random_sequence": mid("blocks/sulfur_crust"),
})
write(f"data/{MOD}/loot_table/blocks/scrap_heap.json", {
    "type": "minecraft:block",
    "pools": [
        {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "minecraft:iron_nugget", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3.0, "max": 6.0}, "add": False},
            {"function": "minecraft:explosion_decay"}]}]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.3}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:copper_ingot"}]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.12}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:redstone", "functions": [
             {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1.0, "max": 3.0}, "add": False}]}]},
        {"rolls": 1, "bonus_rolls": 0, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.04}],
         "entries": [{"type": "minecraft:item", "name": mid("servo_motor")}]},
    ],
    "random_sequence": mid("blocks/scrap_heap"),
})
write(f"data/{MOD}/loot_table/blocks/airlock_door.json", {
    "type": "minecraft:block",
    "pools": [{"rolls": 1, "bonus_rolls": 0,
               "entries": [{"type": "minecraft:item", "name": mid("airlock_door")}],
               "conditions": [
                   {"condition": "minecraft:block_state_property", "block": mid("airlock_door"), "properties": {"half": "lower"}},
                   {"condition": "minecraft:survives_explosion"}]}],
    "random_sequence": mid("blocks/airlock_door"),
})

# ======================================================================================
# Recipes
# ======================================================================================
def shaped(name, pattern, key, result, count=1, category="misc"):
    write(f"data/{MOD}/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": result, "count": count},
    })


def shapeless(name, ingredients, result, count=1, category="misc"):
    write(f"data/{MOD}/recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": [({"tag": i[1:]} if i.startswith("#") else {"item": i}) for i in ingredients],
        "result": {"id": result, "count": count},
    })


IRON = "minecraft:iron_ingot"
NUGGET = "minecraft:iron_nugget"
COPPER = "minecraft:copper_ingot"
REDSTONE = "minecraft:redstone"
GLASS = "minecraft:glass"

shaped("hull_plating", ["SSS", "SIS", "SSS"], {"S": "#minecraft:stone_crafting_materials", "I": IRON}, mid("hull_plating"), 8, "building")
shaped("reinforced_glass", ["GNG", "NIN", "GNG"], {"G": GLASS, "N": NUGGET, "I": IRON}, mid("reinforced_glass"), 4, "building")
shaped("airlock_door", ["II", "IR", "II"], {"I": IRON, "R": REDSTONE}, mid("airlock_door"), 1, "redstone")
shaped("life_support", ["ISI", "MBM", "ISI"], {"I": IRON, "S": mid("sulfur"), "M": mid("servo_motor"), "B": "minecraft:redstone_block"}, mid("life_support"))
shaped("solar_collector", ["GGG", "LLL", "IRI"], {"G": GLASS, "L": "minecraft:lapis_lazuli", "I": IRON, "R": REDSTONE}, mid("solar_collector"))
shaped("power_conduit", ["PCP", "CRC", "PCP"], {"P": mid("hull_plating"), "C": COPPER, "R": REDSTONE}, mid("power_conduit"), 6, "redstone")
shaped("decon_shower", ["PPP", "IBI", "CRC"], {"P": mid("hull_plating"), "I": IRON, "B": "minecraft:water_bucket", "C": COPPER, "R": REDSTONE}, mid("decon_shower"))
shaped("cargo_bay", ["III", "ICI", "III"], {"I": IRON, "C": "minecraft:chest"}, mid("cargo_bay"), 1, "equipment")
shaped("fabricator", ["IMI", "STS", "IRI"], {"I": IRON, "M": mid("servo_motor"), "S": mid("sulfur"), "T": "minecraft:crafting_table", "R": "minecraft:redstone_block"}, mid("fabricator"), 1, "equipment")
shaped("field_radio", ["I I", "CRC", "III"], {"I": IRON, "C": COPPER, "R": REDSTONE}, mid("field_radio"), 1, "equipment")
shaped("mining_drill", ["III", "IMI", " C "], {"I": IRON, "M": mid("servo_motor"), "C": COPPER}, mid("mining_drill"), 1, "equipment")
shaped("arc_cutter", ["I", "C", "M"], {"I": IRON, "C": COPPER, "M": mid("servo_motor")}, mid("arc_cutter"), 1, "equipment")
shaped("atmo_scanner", [" G ", "RCR", " I "], {"G": "minecraft:glass_pane", "R": REDSTONE, "C": COPPER, "I": IRON}, mid("atmo_scanner"), 1, "equipment")
shaped("caustic_sandstone", ["SS", "SS"], {"S": mid("caustic_sand")}, mid("caustic_sandstone"), 1, "building")
shapeless("gunpowder_from_sulfur", [mid("sulfur"), "minecraft:charcoal", "minecraft:bone_meal"], "minecraft:gunpowder", 2)

# ======================================================================================
# Block states and models
# ======================================================================================
def blockstate(name, variants):
    write(f"assets/{MOD}/blockstates/{name}.json", {"variants": variants})


def model(rel, obj):
    write(f"assets/{MOD}/models/{rel}.json", obj)


def cube_all(name, texture=None):
    blockstate(name, {"": {"model": f"{MOD}:block/{name}"}})
    model(f"block/{name}", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/{texture or name}"}})
    model(f"item/{name}", {"parent": f"{MOD}:block/{name}"})


for name in ["caustic_sand", "ash", "sulfur_crust", "scrap_heap", "hull_plating", "reinforced_glass", "power_conduit",
             "geothermal_tap", "damper_beacon", "relay_mast", "span_anchor"]:
    cube_all(name)

blockstate("caustic_sandstone", {"": {"model": f"{MOD}:block/caustic_sandstone"}})
model("block/caustic_sandstone", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{MOD}:block/caustic_sandstone_top", "bottom": f"{MOD}:block/caustic_sandstone_top", "side": f"{MOD}:block/caustic_sandstone"}})
model("item/caustic_sandstone", {"parent": f"{MOD}:block/caustic_sandstone"})

blockstate("vent", {"": {"model": f"{MOD}:block/vent"}})
model("block/vent", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{MOD}:block/vent_top", "bottom": f"{MOD}:block/vent_side", "side": f"{MOD}:block/vent_side"}})
model("item/vent", {"parent": f"{MOD}:block/vent"})

# Geyser: the same throat as a fumarole with something still coming up it. The four stages of the cycle are
# two models; the block entity does the rest (docs/DESIGN-hazards.md).
for suffix, top in (("", "geyser_top"), ("_hot", "geyser_top_hot")):
    model(f"block/geyser{suffix}", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{MOD}:block/{top}", "bottom": f"{MOD}:block/vent_side", "side": f"{MOD}:block/vent_side"}})
blockstate("geyser", {f"stage={stage}": {"model": f"{MOD}:block/geyser" + ("" if stage in ("quiet", "steam") else "_hot")}
                      for stage in ("quiet", "steam", "rumble", "erupting")})
model("item/geyser", {"parent": f"{MOD}:block/geyser"})

# Solar collector: a 3px panel.
for lit in (False, True):
    suffix = "_lit" if lit else ""
    model(f"block/solar_collector{suffix}", {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{MOD}:block/frame_metal", "frame": f"{MOD}:block/frame_metal", "panel": f"{MOD}:block/solar_top{suffix}"},
        "elements": [{"from": [0, 0, 0], "to": [16, 3, 16], "faces": {
            "down": {"texture": "#frame", "cullface": "down"}, "up": {"texture": "#panel"},
            "north": {"texture": "#frame"}, "south": {"texture": "#frame"}, "west": {"texture": "#frame"}, "east": {"texture": "#frame"}}}],
    })
blockstate("solar_collector", {"lit=false": {"model": f"{MOD}:block/solar_collector"}, "lit=true": {"model": f"{MOD}:block/solar_collector_lit"}})
model("item/solar_collector", {"parent": f"{MOD}:block/solar_collector"})

# Life support: an orientable machine whose front panel reports its state.
for status in ["off", "ok", "leak"]:
    model(f"block/life_support_{status}", {"parent": "minecraft:block/orientable", "textures": {
        "top": f"{MOD}:block/life_support_top", "front": f"{MOD}:block/life_support_front_{status}", "side": f"{MOD}:block/life_support_side"}})
variants = {}
for facing, rot in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    for active in ("false", "true"):
        for sealed in ("false", "true"):
            status = "off" if active == "false" else ("ok" if sealed == "true" else "leak")
            entry = {"model": f"{MOD}:block/life_support_{status}"}
            if rot:
                entry["y"] = rot
            variants[f"active={active},facing={facing},sealed={sealed}"] = entry
blockstate("life_support", variants)
model("item/life_support", {"parent": f"{MOD}:block/life_support_off"})

# Airlock door: vanilla door geometry with our textures.
with open(os.path.join(TOOLS, "vanilla_iron_door_blockstate.json"), encoding="utf-8") as f:
    door_variants = json.load(f)["variants"]
for key, entry in door_variants.items():
    entry["model"] = entry["model"].replace("minecraft:block/iron_door", f"{MOD}:block/airlock_door")
blockstate("airlock_door", door_variants)
for half in ("bottom", "top"):
    for hinge in ("left", "right"):
        for state in ("", "_open"):
            model(f"block/airlock_door_{half}_{hinge}{state}", {
                "parent": f"minecraft:block/door_{half}_{hinge}{state}",
                "textures": {"bottom": f"{MOD}:block/airlock_door_bottom", "top": f"{MOD}:block/airlock_door_top"}})
model("item/airlock_door", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/airlock_door"}})
# The collar door in a base's docking station wears the airlock door's skin; it only differs in who can open it.
blockstate("dock_door", door_variants)

for name in ["mining_drill", "arc_cutter"]:
    model(f"item/{name}", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{MOD}:item/{name}"}})
for name in ["atmo_scanner", "sulfur", "cargo_bay", "fabricator", "locked_bay", "field_radio"]:
    model(f"item/{name}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})

# Decon shower: a ceiling block whose underside is the nozzle, lit while spraying or alarming.
for lit in (False, True):
    suffix = "_lit" if lit else ""
    model(f"block/decon_shower{suffix}", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{MOD}:block/hull_plating", "side": f"{MOD}:block/decon_shower_side", "bottom": f"{MOD}:block/decon_shower_nozzle{suffix}"}})
blockstate("decon_shower", {"lit=false": {"model": f"{MOD}:block/decon_shower"}, "lit=true": {"model": f"{MOD}:block/decon_shower_lit"}})
model("item/decon_shower", {"parent": f"{MOD}:block/decon_shower"})

# ======================================================================================
# Lang
# ======================================================================================
LANG = {
    "block.surrogate.life_support": "Life Support Unit",
    "block.surrogate.solar_collector": "Solar Collector",
    "block.surrogate.power_conduit": "Power Conduit",
    "block.surrogate.hull_plating": "Hull Plating",
    "block.surrogate.reinforced_glass": "Reinforced Glass",
    "block.surrogate.airlock_door": "Airlock Door",
    "block.surrogate.caustic_sand": "Caustic Sand",
    "block.surrogate.caustic_sandstone": "Caustic Sandstone",
    "block.surrogate.ash": "Ash",
    "block.surrogate.sulfur_crust": "Sulfur Crust",
    "block.surrogate.scrap_heap": "Scrap Heap",
    "block.surrogate.vent": "Fumarole",
    "block.surrogate.geyser": "Geyser",
    "block.surrogate.geothermal_tap": "Geothermal Tap",
    "block.surrogate.damper_beacon": "Damper Beacon",
    "block.surrogate.relay_mast": "Relay Mast",
    "block.surrogate.span_anchor": "Span Anchor",
    "item.surrogate.sulfur": "Sulfur",
    "item.surrogate.mining_drill": "Mining Drill",
    "item.surrogate.arc_cutter": "Arc Cutter",
    "item.surrogate.atmo_scanner": "Atmosphere Scanner",

    "biome.surrogate.toxic_desert": "Toxic Desert",
    "biome.surrogate.ash_dunes": "Ash Dunes",
    "biome.surrogate.acid_flats": "Acid Flats",
    "biome.surrogate.salt_pans": "Salt Pans",
    "biome.surrogate.dead_grove": "Dead Grove",
    "biome.surrogate.caustic_mire": "Caustic Mire",
    "biome.surrogate.rift": "The Rift",
    "generator.surrogate.toxic_wastes": "Toxic Wastes",
    "death.attack.surrogate.toxin": "%1$s choked on the outside air",

    "direction.surrogate.north": "north",
    "direction.surrogate.south": "south",
    "direction.surrogate.east": "east",
    "direction.surrogate.west": "west",
    "direction.surrogate.up": "above",
    "direction.surrogate.down": "below",

    "hud.surrogate.air": "ATMOSPHERE",
    "hud.surrogate.air.safe": "CLEAN",
    "hud.surrogate.air.sealed": "SEALED",
    "hud.surrogate.air.leak": "BREACH",
    "hud.surrogate.air.exposed": "EXPOSED",
    "hud.surrogate.air_quality": "AIR %s%%",
    "hud.surrogate.toxin": "TOXIN %s%%",
    "hud.surrogate.body.safe": "BODY clean air",
    "hud.surrogate.body.sealed": "BODY sealed, air %s%%, toxin %s%%",
    "hud.surrogate.body.leak": "BODY BREACH, air %s%%, toxin %s%%",
    "hud.surrogate.body.exposed": "BODY EXPOSED, toxin %s%%",
    "hud.surrogate.body_exposed": "BODY EXPOSED",

    "title.surrogate.exposed": "Toxic atmosphere",
    "title.surrogate.exposed.sub": "Get inside a sealed room",
    "title.surrogate.body_exposed": "Your body is breathing bad air",
    "title.surrogate.body_exposed.sub": "The chair room is not sealed",
    "title.surrogate.breach": "ATMOSPHERE BREACH",
    "title.surrogate.breach.sub": "Leak near %s",

    "message.surrogate.breach": "Life support: enclosure breached near %s. Seal it or the air will be gone in seconds.",
    "message.surrogate.welcome": "You wake in a habitat pod on a world you cannot breathe. The chest has everything you need.",
    "message.surrogate.welcome.hint": "Read the field manual, deploy the chassis, and never open both airlock doors at once.",
    "message.surrogate.tool_needs_chassis": "This tool only works coupled to a running chassis.",
    "message.surrogate.tool_no_power": "The chassis does not have enough power for that.",
    "message.surrogate.life_support.info": "Life support: air %s%%, buffer %s%%, %s. %s",
    "message.surrogate.life_support.powered": "running",
    "message.surrogate.life_support.unpowered": "NO POWER",
    "message.surrogate.life_support.blocked": "The vent is blocked; face it into the room.",
    "message.surrogate.life_support.sealed": "Enclosure sealed (%s blocks).",
    "message.surrogate.life_support.leak": "ENCLOSURE BREACHED near %s.",
    "message.surrogate.solar_info": "Solar collector: %s E/t, buffer %s%%.",
    "message.surrogate.scanner.open_toxic": "Scanner: open atmosphere. TOXIC. No enclosure here.",
    "message.surrogate.scanner.open_clean": "Scanner: open atmosphere. Breathable.",
    "message.surrogate.scanner.sealed": "Scanner: enclosure sealed, %s blocks, air %s%%. Life support at %s (%s).",
    "message.surrogate.scanner.leak": "Scanner: BREACH near %s, %s blocks %s. Air %s%%.",
    "message.surrogate.scanner.leak_unknown": "Scanner: enclosure is not airtight, leak location unknown.",
    "message.surrogate.scanner.airtight": "%s is airtight.",
    "message.surrogate.scanner.porous": "%s is NOT airtight.",

    "tooltip.surrogate.drill": "Pickaxe and shovel. Costs the chassis %s energy per block.",
    "tooltip.surrogate.cutter": "Costs the chassis %s energy per strike.",
    "tooltip.surrogate.chassis_only": "Only works while piloting a chassis",
    "tooltip.surrogate.scanner.use": "Use to read the air, and the belt, where you stand",
    "tooltip.surrogate.scanner.block": "Sneak-use on a block: airtight or not, and how far the rain has got with a machine",

    "book.surrogate.guide.title": "Habitat Field Manual",
    "book.surrogate.guide.author": "Surrogate Systems",
    "book.surrogate.guide.page1": "HABITAT FIELD MANUAL\n\nThe air outside will kill you in about a minute. This pod is sealed. Keep it that way.\n\nThe Life Support Unit on the north wall scrubs the air. Its light is green when the room is airtight, red when it is not, dark when it has no power.",
    "book.surrogate.guide.page2": "THE AIRLOCK\n\nTwo doors, one chamber. Never open both. Doors close on their own after a few seconds.\n\nOpening a single door to the outside vents the pod: the air goes bad in about ten seconds and takes half a minute to recover.",
    "book.surrogate.guide.page3": "YOUR CHASSIS\n\nDeploy the Robot Chassis inside. Key the Uplink Card on it, then on the Dive Chair. Sit in the chair to dive.\n\nThe chassis does not breathe. It goes outside. You do not.\n\nThe Charging Dock parks and charges it. The Solar Collectors on the roof feed the dock and life support.",
    "book.surrogate.guide.page4": "TOOLS\n\nThe Mining Drill and Arc Cutter only work coupled to a chassis, and they draw its power.\n\nThe Atmosphere Scanner tells you if a room is sealed, and points at the leak if it is not. Sneak-use it on a block to ask if that block is airtight.",
    "book.surrogate.guide.page5": "BUILDING\n\nAny full block is airtight: stone, planks, glass, hull plating. Ordinary doors, slabs, stairs and fences are not.\n\nTo expand: build the new room against the pod, seal it, then knock through the shared wall. Do the outside work with the chassis.",
    "book.surrogate.guide.page6": "THE WASTES\n\nSulfur crust makes life support units and gunpowder. Scrap heaps hold iron. Dead groves have petrified wood.\n\nWatch your fatigue. Sleep resets it. Watch your body readout while diving: if the pod breaches, come home.",
    "book.surrogate.guide.page7": "DECONTAMINATION\n\nThe chassis picks up grime outside. The shower in the airlock ceiling hoses it down and holds the inner door until it is clean.\n\nCaustic sand, ash and sulfur set off the alarm. Leave them outside, or in a chest, never loose in the base.",
    "book.surrogate.guide.page8": "THE RADIO\n\nYou are not alone. Keep the field radio in your pack, or listen through the chassis. Use it to get a bearing on each signal.\n\nThey each need something. Bring it, and they will make it worth the trip.",

    # Round two
    "block.surrogate.decon_shower": "Decon Shower",
    "item.surrogate.cargo_bay": "Cargo Bay",
    "item.surrogate.fabricator": "Fabricator Module",
    "item.surrogate.locked_bay": "Sealed Bay",
    "item.surrogate.field_radio": "Field Radio",
    "entity.surrogate.survivor": "Survivor",

    "screen.surrogate.cargo": "Hold of %s",
    "screen.surrogate.fabricator": "Fabricator",
    "screen.surrogate.pilot_menu.fabricator": "Open fabricator",
    "screen.surrogate.pilot_menu.no_fabricator": "No fabricator fitted",

    "hud.surrogate.contamination": "GRIME %s%%",

    "message.surrogate.status": "%s [%s] Hull %s/%s, Power %s%%, Plating Mk%s, Battery Mk%s, Cargo %s slots, %s, grime %s%%",
    "message.surrogate.status.fabricator": "fabricator fitted",
    "message.surrogate.status.no_fabricator": "no fabricator",
    "message.surrogate.cargo_max": "There is no room for another cargo bay.",
    "message.surrogate.fabricator_fitted": "This chassis already has a fabricator.",
    "message.surrogate.no_fabricator": "This chassis has no fabricator. Fit a Fabricator Module while it is powered down.",
    "message.surrogate.contamination": "Contamination: %s dirty chassis, %s caustic items, %s caustic blocks in the walls.",
    "message.surrogate.decon.alarm": "DECON ALARM: caustic material detected (%s). Leave it outside.",
    "message.surrogate.decon.start": "Decontamination cycle started. Hold still.",
    "message.surrogate.decon.done": "Decontamination complete.",
    "message.surrogate.decon.open": "Decon shower: the space below is not enclosed. Put it in the ceiling of a closed airlock chamber.",
    "message.surrogate.decon.info": "Decon shower: chamber of %s blocks, %s.",
    "message.surrogate.decon.state.idle": "idle",
    "message.surrogate.decon.state.washing": "washing",
    "message.surrogate.decon.state.alarm": "ALARM",
    "message.surrogate.airlock.washing": "Airlock interlocked: decontamination in progress.",
    "message.surrogate.airlock.contaminated": "Airlock interlocked: caustic material detected (%s).",
    "message.surrogate.survivor.reward": "%s gives you: %s",
    "message.surrogate.radio.silent": "The radio hisses. Nothing but static.",
    "message.surrogate.radio.header": "[RADIO] Signals:",
    "message.surrogate.radio.signal": "  %s: %s signal, about %s m %s. %s",
    "message.surrogate.radio.strength.very_strong": "very strong",
    "message.surrogate.radio.strength.strong": "strong",
    "message.surrogate.radio.strength.weak": "weak",
    "message.surrogate.radio.strength.faint": "faint",
    "message.surrogate.radio.bearing.north": "to the north",
    "message.surrogate.radio.bearing.north_east": "to the north-east",
    "message.surrogate.radio.bearing.east": "to the east",
    "message.surrogate.radio.bearing.south_east": "to the south-east",
    "message.surrogate.radio.bearing.south": "to the south",
    "message.surrogate.radio.bearing.south_west": "to the south-west",
    "message.surrogate.radio.bearing.west": "to the west",
    "message.surrogate.radio.bearing.north_west": "to the north-west",
    "message.surrogate.radio.safe": "Safe.",
    "message.surrogate.radio.distress": "Still calling for help.",

    "tooltip.surrogate.cargo_bay": "Adds %s cargo slots. Fits up to %s times.",
    "tooltip.surrogate.fabricator": "Fits a 3x3 crafting bench, opened from the pilot menu",
    "tooltip.surrogate.locked_bay": "No cargo bay fitted here",
    "tooltip.surrogate.cargo": "Cargo: %s slots",
    "tooltip.surrogate.fabricator_fitted": "Fabricator fitted",
    "tooltip.surrogate.radio": "Hears the survivors. Use for a bearing on every signal.",
    "tooltip.surrogate.radio.use": "A chassis always hears the radio",

    "survivor.surrogate.okafor.name": "Dr. Ada Okafor",
    "survivor.surrogate.okafor.radio.1": "This is Okafor at Greenhouse Station. Scrubber is down to its last cell. If anyone is out there, I am not going anywhere.",
    "survivor.surrogate.okafor.radio.2": "Okafor again. The tomatoes are fine. I am less fine. Two power cells would keep this room breathing another month.",
    "survivor.surrogate.okafor.radio.3": "Greenhouse Station, still transmitting. I can hear a chassis on the dunes some days. I keep waving at the window.",
    "survivor.surrogate.okafor.greet": "Over here! Do not open that door with dirt on you, I only have the one room.",
    "survivor.surrogate.okafor.plea": "My scrubber needs power. %s %s would do it. I can pay in parts: I have a fabricator module I will never use.",
    "survivor.surrogate.okafor.thanks": "You beautiful machine. Take the fabricator module. Fit it to your chassis and it will craft anything a bench can.",
    "survivor.surrogate.okafor.idle.1": "The air in here has not been this clean in months. Come by any time.",
    "survivor.surrogate.okafor.idle.2": "I am drying seeds for you. Next time.",
    "survivor.surrogate.okafor.rescued_radio": "Greenhouse Station to all stations: scrubber is back up. Somebody out there has a good heart and a better robot.",
    "survivor.surrogate.okafor.safe": "Okafor here. Sky is the colour of old brass again this morning. Take care of that chassis.",

    "survivor.surrogate.sorensen.name": "Mikkel Sorensen",
    "survivor.surrogate.sorensen.radio.1": "Mayday, mayday. Survey rover Two is down, I am holed up in the pod. The rover is scrap unless I get repair kits.",
    "survivor.surrogate.sorensen.radio.2": "Sorensen, Survey Two. Still here. The pod is holding. The rover is not. Two repair kits and I could get her hull back.",
    "survivor.surrogate.sorensen.radio.3": "If anyone copies, I have a spare cargo bay module in the pod. It is yours for a couple of repair kits.",
    "survivor.surrogate.sorensen.greet": "You made it! Mind the doorway, I have not swept.",
    "survivor.surrogate.sorensen.plea": "The rover is in pieces. Bring me %s %s and the cargo bay module on that shelf is yours.",
    "survivor.surrogate.sorensen.thanks": "That is more than enough. The cargo bay bolts on to any chassis. Nine more slots. Do not fill them with sand.",
    "survivor.surrogate.sorensen.idle.1": "Rover Two will drive again. Give me a week.",
    "survivor.surrogate.sorensen.idle.2": "Salt pans to the west are flat as a table. Good driving, if you like flat.",
    "survivor.surrogate.sorensen.rescued_radio": "Survey Two to all stations: repairs under way. Whoever you are, I owe you a drink and a drive.",
    "survivor.surrogate.sorensen.safe": "Sorensen here. Rover Two is running. Badly, but running.",

    "survivor.surrogate.tanaka.name": "Yuki Tanaka",
    "survivor.surrogate.tanaka.radio.1": "Sulfur Works calling. The scrubber cartridges are spent and the vents outside are the only sulfur I cannot reach.",
    "survivor.surrogate.tanaka.radio.2": "Tanaka, Sulfur Works. Eight sulfur and I can rebuild the cartridges. I would go myself if the door did not kill me.",
    "survivor.surrogate.tanaka.radio.3": "Sulfur Works, still on the air. There is a battery expansion here nobody will ever use. Trade?",
    "survivor.surrogate.tanaka.greet": "A chassis! Tell me you have sulfur on you. Actually, tell me at the door, not in here.",
    "survivor.surrogate.tanaka.plea": "I need %s %s to rebuild the scrubber cartridges. The battery expansion is yours for it.",
    "survivor.surrogate.tanaka.thanks": "Perfect. Take the battery expansion. Twice the charge, twice the range. Now get that sulfur out of my airlock.",
    "survivor.surrogate.tanaka.idle.1": "Cartridges are curing. Another day and this room will smell like nothing at all.",
    "survivor.surrogate.tanaka.idle.2": "Do not build with sulfur crust. I learned that the hard way.",
    "survivor.surrogate.tanaka.rescued_radio": "Sulfur Works to all stations: cartridges rebuilt. To the chassis that brought the sulfur: thank you.",
    "survivor.surrogate.tanaka.safe": "Tanaka here. The vents are loud tonight. Sleep well, everyone.",

    "survivor.surrogate.brandt.name": "Old Brandt",
    "survivor.surrogate.brandt.radio.1": "This is Brandt. I am not asking for rescue. I am asking for bread. Eight loaves and I will stop transmitting.",
    "survivor.surrogate.brandt.radio.2": "Brandt again. Been out here longer than your station has existed. Potatoes gave out. Bread, if you have it.",
    "survivor.surrogate.brandt.radio.3": "Brandt. Still hungry. I have reinforced plating I pulled off a dead rover. Bread for plating. Fair trade.",
    "survivor.surrogate.brandt.greet": "Huh. A tin can. Well, come in then, if you are clean.",
    "survivor.surrogate.brandt.plea": "%s %s. That is the deal. The plating is by the door.",
    "survivor.surrogate.brandt.thanks": "That will do. Reinforced plating, as promised. Bolt it on before you go anywhere stupid.",
    "survivor.surrogate.brandt.idle.1": "Bread was good. You can go now.",
    "survivor.surrogate.brandt.idle.2": "The grove east of here has the only real wood for a hundred blocks. Do not tell anyone.",
    "survivor.surrogate.brandt.rescued_radio": "Brandt. Fed. Signing off. If you find my rover, it is yours.",
    "survivor.surrogate.brandt.safe": "Brandt here. Nothing to report. Which is how I like it.",
}

# ======================================================================================
# Transit: the void the Provender crosses, and what is bolted to its walls
# ======================================================================================
write(f"data/{MOD}/dimension_type/transit.json", {
    "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": False, "has_ceiling": False,
    "ambient_light": 0.0, "fixed_time": 18000, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
    "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
    "logical_height": 256, "min_y": 0, "height": 256, "infiniburn": "#minecraft:infiniburn_overworld",
    "effects": mid("transit")})
write(f"data/{MOD}/dimension/transit.json", {
    "type": mid("transit"),
    "generator": {"type": "minecraft:flat", "settings": {
        "biome": mid("vacuum"), "layers": [{"block": "minecraft:air", "height": 1}], "features": False, "lakes": False}}})
# Outside the hull is as deadly as the wastes: the vacuum biome is tagged toxic so the scrubbers matter aboard.
write(f"data/{MOD}/worldgen/biome/vacuum.json", {
    "temperature": 0.5, "downfall": 0.5, "has_precipitation": False,
    "effects": {"fog_color": 0, "sky_color": 0, "water_color": 4159204, "water_fog_color": 329011},
    "carvers": {}, "features": [], "spawners": {}, "spawn_costs": {}})
write(f"data/{MOD}/tags/worldgen/biome/toxic.json", {"replace": False, "values": [mid(b) for b in BIOMES] + [mid("vacuum"), mid("crawler_cabin")]})
PICKAXE.append(mid("poster"))
write(f"data/{MOD}/loot_table/blocks/poster.json", self_drop("poster"))

# Posters: a hull plate with a print on the room-facing side. One model per print, turned by the block state.
PRINTS = ["body_top", "body_bottom", "sallow_top", "sallow_bottom", "provender", "console", "console_alert", "manifest"]
poster_variants = {}
for facing, rot in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
    for p in PRINTS:
        v = {"model": f"{MOD}:block/poster_{p}"}
        if rot:
            v["y"] = rot
        poster_variants[f"facing={facing},print={p}"] = v
blockstate("poster", poster_variants)
for p in PRINTS:
    model(f"block/poster_{p}", {"parent": "minecraft:block/orientable", "textures": {
        "top": f"{MOD}:block/hull_plating", "side": f"{MOD}:block/hull_plating", "front": f"{MOD}:block/poster_{p}"}})
model("item/poster", {"parent": f"{MOD}:block/poster_body_top"})

# The rebreather: a minute of your own air.
model("item/rebreather", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/rebreather"}})
shaped("rebreather", ["IGI", "SCS", " I "], {"I": IRON, "G": GLASS, "S": mid("sulfur"), "C": COPPER}, mid("rebreather"), 2, "equipment")
print("wrote the transit data")


# ======================================================================================
# Galley unit, wall terminal, breached plating
# ======================================================================================
FACINGS = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]


def face(tex, uv=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    return f


def facing_variants(model_for):
    out = {}
    for facing, rot in FACINGS:
        for key, model_name in model_for.items():
            v = {"model": f"{MOD}:block/{model_name}"}
            if rot:
                v["y"] = rot
            out[f"facing={facing}" + (("," + key) if key else "")] = v
    return out


# The galley unit: a box on the counter, its door on the front, lit while it runs.
for lit in (False, True):
    suffix = "_lit" if lit else ""
    model(f"block/microwave{suffix}", {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{MOD}:block/microwave_side", "front": f"{MOD}:block/microwave_front{suffix}",
                     "side": f"{MOD}:block/microwave_side", "top": f"{MOD}:block/microwave_top"},
        "elements": [{"from": [1, 0, 1], "to": [15, 9, 15], "faces": {
            "north": face("#front", [1, 7, 15, 16]), "south": face("#side", [1, 7, 15, 16]),
            "east": face("#side", [1, 7, 15, 16]), "west": face("#side", [1, 7, 15, 16]),
            "up": face("#top", [1, 1, 15, 15]), "down": face("#top", [1, 1, 15, 15])}}]})
blockstate("microwave", facing_variants({"lit=false": "microwave", "lit=true": "microwave_lit"}))
model("item/microwave", {"parent": f"{MOD}:block/microwave"})
write(f"data/{MOD}/loot_table/blocks/microwave.json", self_drop("microwave"))
shaped("microwave", ["III", "GRC", "III"], {"I": IRON, "G": GLASS, "R": REDSTONE, "C": COPPER}, mid("microwave"), 1, "misc")

# The wall terminal: a hull plate with a screen on the room side.
model("block/terminal", {"parent": "minecraft:block/orientable", "textures": {
    "top": f"{MOD}:block/hull_plating", "side": f"{MOD}:block/hull_plating", "front": f"{MOD}:block/terminal"}})
blockstate("terminal", facing_variants({"": "terminal"}))
model("item/terminal", {"parent": f"{MOD}:block/terminal"})
write(f"data/{MOD}/loot_table/blocks/terminal.json", self_drop("terminal"))
shaped("terminal", ["IGI", "RCR", "III"], {"I": IRON, "G": GLASS, "R": REDSTONE, "C": COPPER}, mid("terminal"), 1, "misc")

# Breached plating: a torn plate with a hole through it. Drops nothing; a hull plate used on it mends it.
model("block/breached_plating", {
    "parent": "minecraft:block/block",
    "textures": {"particle": f"{MOD}:block/hull_plating", "plate": f"{MOD}:block/breached_plating", "edge": f"{MOD}:block/hull_plating"},
    "elements": [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": {
        "north": face("#plate"), "south": face("#plate"),
        "east": face("#edge", [6, 0, 10, 16]), "west": face("#edge", [6, 0, 10, 16]),
        "up": face("#edge", [0, 6, 16, 10]), "down": face("#edge", [0, 6, 16, 10])}}]})
blockstate("breached_plating", facing_variants({"": "breached_plating"}))
write(f"data/{MOD}/loot_table/blocks/breached_plating.json", {"type": "minecraft:block", "pools": []})

PICKAXE.extend([mid("microwave"), mid("terminal"), mid("breached_plating")])

LANG.update({
    "block.surrogate.microwave": "Galley Unit",
    "block.surrogate.terminal": "Wall Terminal",
    "block.surrogate.breached_plating": "Breached Plating",
    "subtitles.surrogate.microwave_hum": "Galley unit hums",
    "subtitles.surrogate.microwave_ding": "Galley unit dings",
    "message.surrogate.breach_needs_plate": "The plate is gone. Use a hull plate on the frame to seal it.",

    "key.surrogate.log": "Mission Log",
    "screen.surrogate.log": "MISSION LOG",
    "screen.surrogate.log.key": "[%s] closes",
    "screen.surrogate.log.no_objective": "NO CURRENT OBJECTIVE",
    "screen.surrogate.log.transcript": "TRANSCRIPT",
    "screen.surrogate.log.empty": "Nothing has been said yet.",
    "cinematic.surrogate.objective.log": "[%s] log",

    "title.surrogate.name": "SURROGATE",
    "title.surrogate.tagline": "YOUR BODY STAYS HOME",

    "screen.surrogate.terminal": "Terminal",
    "terminal.surrogate.header": "SHIPNET // %s",
    "terminal.surrogate.keys": "UP/DOWN or click: files.  ESC: log off.",
    "terminal.surrogate.status": "STATUS",
    "terminal.surrogate.status.ship": "S.S.V. PROVENDER, supply run 41",
    "terminal.surrogate.status.day": "Transit day %s of 7",
    "terminal.surrogate.status.breach": "HULL BREACH: cargo hold, starboard. Seal it.",
    "terminal.surrogate.status.engine_off": "Main engine: OFF (turnover)",
    "terminal.surrogate.status.nominal": "All systems nominal.",
    "terminal.surrogate.status.site": "SALLOW, SITE 04, habitat pod",
    "terminal.surrogate.status.fatigue": "PILOT FATIGUE %s%%",
    "terminal.surrogate.status.footer": "Session logged. Surrogate Systems accepts no liability for anything read on this unit.",

    "terminal.surrogate.bridge.name": "BRIDGE",
    "terminal.surrogate.bridge.1.title": "COURSE",
    "terminal.surrogate.bridge.1.body": "# ROUTE 41: TRANSFER STATION TO SALLOW\nSeven days under thrust. Turnover on day four, main engine off for the flip, back on for the braking burn.\n\nArrival window: day seven, 0600 ship time. Drop pod release once the hold is depressurised and the passengers are logged.\n\n# RETURN\nThe Provender does not land. She drops the pod, takes on the empty chairs from run 40, and burns for home.",
    "terminal.surrogate.bridge.2.title": "CAPTAIN LOG 41-03",
    "terminal.surrogate.bridge.2.body": "Twelve chairs in the hold, twelve pilots in them, all reading green except seven. Seven reads green too, just not in a way I like. Ferreira says the port is fine and the pilot is dreaming. I have asked her to watch it.\n\nTeague wants the coolant bypass looked at before turnover. He is right and I have told him so, which he enjoyed.\n\nThe new one thawed early. Company error, again. We will feed them and put them to work.",
    "terminal.surrogate.bridge.3.title": "COMMS SCHEDULE",
    "terminal.surrogate.bridge.3.body": "# RELAY WINDOWS\nDay 1: 0900 company relay, 2100 site 04 beacon (ping only).\nDay 4: turnover. No comms while the engine is off; the dish is on the engine mount.\nDay 6: 1200 company relay, contract confirmation for the passengers.\nDay 7: continuous with site 04 from 0400 until the drop.\n\n! Site 04 has not acknowledged a voice call since run 39. Beacon pings are on schedule.",

    "terminal.surrogate.medbay.name": "MED BAY",
    "terminal.surrogate.medbay.1.title": "THAW PROTOCOL",
    "terminal.surrogate.medbay.1.body": "# COLD SLEEP REVIVAL, STANDARD\n1. Warm to 34 degrees over ninety minutes. Do not rush this.\n2. Monitor for arrhythmia. Sedate if the patient thrashes.\n3. Fluids, then solid food within six hours. The galley is port side, aft of the bunks.\n4. Port calibration in the link chair no sooner than day two.\n\n! Early thaw (company scheduling error, form CS-12) skips step 1. Expect headache, confusion and a bad temper. Not the patient at fault.",
    "terminal.surrogate.medbay.2.title": "PATIENT 07",
    "terminal.surrogate.medbay.2.body": "# VASQUEZ, R. ROTATION 3. DAY 611 IN CHAIR.\nPort telemetry nominal. Neural load steady. Dream state continuous since day 590, which is unusual and which the company manual calls acceptable.\n\nI do not call it acceptable. A pilot that long in the link is not resting; they are somewhere else. I have flagged it twice. Both flags were closed with the note SEE MANUAL.\n\n! If the chair alarms, do not pull the port. Sedate first, then pull. Pulling first is how you get a flatline.",
    "terminal.surrogate.medbay.3.title": "FERREIRA, NOTES",
    "terminal.surrogate.medbay.3.body": "The new pilot is fine. Angry, hungry, fine. I like them better than the manual does.\n\nBallast has been sleeping on the cot again. I have stopped moving her.\n\nCastellanos asked me what happens to a body that never comes back for its pilot. I said the body sleeps. He asked for how long. I did not have an answer he wanted.",

    "terminal.surrogate.hold.name": "CARGO HOLD",
    "terminal.surrogate.hold.1.title": "MANIFEST 41",
    "terminal.surrogate.hold.1.body": "# PASSENGERS: 12 (billable)\nChairs 01 to 12, port rack odd, starboard rack even. All rotation contracts current.\n07: VASQUEZ, R. Rotation 3. Flagged by medical, flag closed.\n\n# FREIGHT\nHull plating, 40 units. Reinforced glass, 12. Power cells, 30. Rebreathers, 6. Life support spares, 2.\nOne cat (not billable, not cargo, do not list the cat).\n\n# DROP POD\nOne seat. One pilot. The chairs stay aboard; the pilots do not.",
    "terminal.surrogate.hold.2.title": "ENVIRONMENT LOG",
    "terminal.surrogate.hold.2.body": "Hold scrubber, starboard forward. Sealed volume nominal. Pressure holding.\n\n# NOTES\nThe frame at rib 12 starboard was patched at the transfer station and signed off. The weld looks like it was done in a hurry, because it was.\n\n! If the pressure drops, close the coolant bypass on the aft rack FIRST, then find the hole. The bypass dumps heat into the rack, and the rack is where the passengers are.",
    "terminal.surrogate.hold.3.title": "CHAIR FIRMWARE",
    "terminal.surrogate.hold.3.body": "# SURROGATE SYSTEMS DIVE CHAIR, RACK MODEL\nFirmware 4.1.2. Link timeout raised to indefinite for transit rotations.\n\nKnown issues: the visor boom on early units sits low. Raise it before the pilot goes under, not after.\n\nDo not update the firmware in transit. Do not update the firmware on a chair with a pilot in it. The company has asked us to stop saying this in the notes. Noted.",

    "terminal.surrogate.engineering.name": "ENGINEERING",
    "terminal.surrogate.engineering.1.title": "FAULT LOG",
    "terminal.surrogate.engineering.1.body": "# OPEN\nCoolant bypass, aft rack: valve sticks. Manual lever fitted. Close it before turnover. (Teague)\nGalley unit: door interlock removed by a previous crew. It will run with the door open. It will run with anything. (Teague)\nRib 12 starboard weld: watch it under the relight. (Teague)\n\n# CLOSED\nForward scrubber grate, cleaned.\nDay lamps in the corridor, rewired. One per day now, so people stop asking what day it is.",
    "terminal.surrogate.engineering.2.title": "COOLANT BYPASS",
    "terminal.surrogate.engineering.2.body": "The bypass takes reactor heat past the radiators and into the hold rack when the fins are stowed for the turn. It is meant to be shut once the fins are out again. On this ship it is shut when somebody walks aft and shuts it.\n\nThe lever is on the aft bulkhead of the hold, port side, next to the sign that says COOLANT. It is that lever. There is no other lever.",
    "terminal.surrogate.engineering.3.title": "HULL SURVEY",
    "terminal.surrogate.engineering.3.body": "# STARBOARD, RIBS 10 TO 14\nPlating sound except rib 12, which was replaced at the transfer station. The new plate is thinner than the old one and the weld is cold along the upper edge.\n\n! Under a hard relight this is where the hull goes. The patch locker by the hold door has four plates and a rebreather for exactly that.\n\nTo patch: rebreather on, plate in hand, use the plate on the torn section. It will take a plate from anyone. It will not take an argument.",

    "terminal.surrogate.pod.name": "DROP BAY",
    "terminal.surrogate.pod.1.title": "DESCENT CHECKLIST",
    "terminal.surrogate.pod.1.body": "# BEFORE RELEASE\n1. Pilot seated, restraints locked, visor down.\n2. Hatch sealed. Bay scrubber to standby.\n3. Sedation offered. Sedation is a good idea.\n4. Cat secured. (Amended, run 41.)\n\n# AFTER RELEASE\nThe pod flies itself. Nine minutes of noise, then a bump, then the door. The habitat pod is the door you can see. Go in. Do not open the annex.",
    "terminal.surrogate.pod.2.title": "LANDING SITE",
    "terminal.surrogate.pod.2.body": "# SALLOW, SITE 04\nOne habitat pod, sealed, powered by roof collectors. One crew annex, attached. Landing pad south of the airlock.\n\nAtmosphere: lethal. Sulfur, chlorine, ash. A rebreather buys one minute. A hull breach buys less.\n\nPrevious occupant: HALLORAN, contract pilot, rotation 2. Status: on site.\nSecond occupant: MARSH, company representative. Status: on site.\n\n! Site 04 has not answered a voice call since run 39.",

    "terminal.surrogate.habitat.name": "SITE 04",
    "terminal.surrogate.habitat.1.title": "SITE SURVEY",
    "terminal.surrogate.habitat.1.body": "# SALLOW, SITE 04\nCaustic desert with ash dunes to the north and acid flats to the west. Salt pans south. A dead grove east, which was alive when the survey was made.\n\nSulfur crust and scrap from earlier runs within two hundred metres. Fumaroles vent to the north; the drill chews through their crust.\n\n! Nothing out there breathes this. You do not either. The chassis does not need to.",
    "terminal.surrogate.habitat.2.title": "OCCUPANT LOG",
    "terminal.surrogate.habitat.2.body": "# HALLORAN, ROTATION 2\nDay 1: Pod sealed. Scrubber green. The annex chair is mine, the pod chair is for the next one.\nDay 18: Marsh arrived on the supply run with a clipboard and no chassis. Says the company wants the numbers.\nDay 40: Found the scrap heap south of the pad. Somebody walked out here before us and did not walk back.\nDay 55: The annex scrubber coughs at night. Marsh says it is fine. Marsh says everything is fine.\nDay 61: Sleeping in the chair. Easier than getting up.",
    "terminal.surrogate.habitat.3.title": "COMPANY NOTICE",
    "terminal.surrogate.habitat.3.body": "# SURROGATE SYSTEMS, CONTRACT PILOTS, SALLOW\nYour body stays home. Your chassis goes out. Your contract covers the chassis.\n\nRotations are 90 days. Extensions are voluntary and cannot be refused. Questions about this sentence should be directed to your company representative.\n\nDo not open both airlock doors at once. Do not open the annex door if the annex is not answering. Do not feed the cat from the galley.",

    "terminal.surrogate.personal.name": "TERMINAL",
    "terminal.surrogate.personal.1.title": "PILOT MANUAL",
    "terminal.surrogate.personal.1.body": "# THE SHORT VERSION\nDeploy a chassis from the chest. Key the uplink card on it, then on a dive chair. Sit. You are the chassis now.\n\nThe chassis runs on power cells and a dock. It does not heal; repair kits do that. If it is wrecked, the wreck keeps your cargo. Come back for it.\n\nYour body is still in the chair. It gets tired, hungry and poisoned like anyone. Disconnect (pilot menu) to eat and sleep. Keep its room sealed.\n\nThe mission log key shows what you are meant to be doing and what everyone has said.",
    "terminal.surrogate.personal.2.title": "ABOUT THIS UNIT",
    "terminal.surrogate.personal.2.body": "Surrogate Systems wall terminal, field model. Wired to nothing in particular.\n\nPlace one aboard a ship or in a company habitat and it joins that network. Out here it shows you the air, your body, and this page.",
})

# ======================================================================================
# Docking station and crawler (docs/DESIGN-crawler.md)
# ======================================================================================
# The pocket the cabins live in: a void under Sallow's sky, sharing the overworld's clock, so the ground
# painted in front of a porthole is lit like the ground outside. Its biome is tagged toxic (above).
write(f"data/{MOD}/dimension_type/crawler_cabin.json", {
    "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
    "ambient_light": 0.0, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
    "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
    "logical_height": 256, "min_y": 0, "height": 256, "infiniburn": "#minecraft:infiniburn_overworld",
    "effects": "minecraft:overworld"})
write(f"data/{MOD}/dimension/crawler_cabin.json", {
    "type": mid("crawler_cabin"),
    "generator": {"type": "minecraft:flat", "settings": {
        "biome": mid("crawler_cabin"), "layers": [{"block": "minecraft:air", "height": 1}], "features": False, "lakes": False}}})
write(f"data/{MOD}/worldgen/biome/crawler_cabin.json", {
    "temperature": 2.0, "downfall": 0.0, "has_precipitation": False,
    "effects": {"fog_color": 13219934, "sky_color": 10461264, "water_color": 7244587, "water_fog_color": 3099151,
                "grass_color": 9079354, "foliage_color": 8026666},
    "carvers": {}, "features": [], "spawners": {}, "spawn_costs": {}})

# The cabin's fittings wear the wall terminal's face; the hatch is a door panel set flush in the wall.
for name in ("crawler_helm", "crawler_dock_console"):
    blockstate(name, facing_variants({"": "terminal"}))
    model(f"item/{name}", {"parent": f"{MOD}:block/terminal"})
model("block/crawler_bay", {"parent": "minecraft:block/orientable", "textures": {
    "top": f"{MOD}:block/hull_plating", "side": f"{MOD}:block/hull_plating", "front": f"{MOD}:block/poster_console"}})
blockstate("crawler_bay", facing_variants({"": "crawler_bay"}))
model("item/crawler_bay", {"parent": f"{MOD}:block/crawler_bay"})
model("block/crawler_hatch", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/airlock_door_bottom"}})
blockstate("crawler_hatch", {"": {"model": f"{MOD}:block/crawler_hatch"}})
model("item/crawler_hatch", {"parent": f"{MOD}:block/crawler_hatch"})
model("item/crawler_kit", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/crawler_kit"}})
# The kit needs the researcher's blueprint on the bench; the bench hands it back (CrawlerBlueprintItem).
model("item/crawler_blueprint", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/crawler_blueprint"}})
# The sample vehicle kit: built at a gantry, never by hand, so its recipe is the expensive one.
model("item/rocket_kit", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/rocket_kit"}})
shaped("rocket_kit", ["PGP", "SBS", "PCP"],
       {"P": mid("hull_plating"), "G": mid("reinforced_glass"), "S": mid("servo_motor"),
        "B": "minecraft:iron_block", "C": mid("cobalt_ingot")},
       mid("rocket_kit"), 1, "transportation")
shaped("crawler_kit", ["PPP", "SBS", "PLP"], {"P": mid("hull_plating"), "S": mid("servo_motor"), "B": "minecraft:iron_block", "L": mid("crawler_blueprint")},
       mid("crawler_kit"), 1, "transportation")
# A shelter's chassis port wears the terminal's face too.
blockstate("chassis_port", facing_variants({"": "terminal"}))
model("item/chassis_port", {"parent": f"{MOD}:block/terminal"})
LANG.update({
    "block.surrogate.chassis_port": "Chassis Port",
    "item.surrogate.crawler_blueprint": "Crawler Blueprint",
    "tooltip.surrogate.crawler_blueprint": "The plans for a crawler hull, from Greenhouse Station",
    "tooltip.surrogate.crawler_blueprint.use": "Put it on the bench with the kit parts; it comes back",
    "message.surrogate.port.needs_chassis": "The port takes a chassis, not a person.",
    "message.surrogate.port.dead": "The port is dead.",
    "message.surrogate.survivor.aboard": "%s is aboard.",
    "message.surrogate.survivor.home": "%s is home.",
    "message.surrogate.crawler.lock_refused": "Not on the collar. Line the ring up first.",
    "message.surrogate.crawler.slipped": "Slipped off the collar.",
    "subtitles.surrogate.dock_beep": "Docking console beeps",
    "subtitles.surrogate.dock_lock": "Ring locks home",
    "subtitles.surrogate.dock_error": "Ring clunks off the collar",
    "terminal.surrogate.shelter_okafor.name": "GREENHOUSE STATION",
    "terminal.surrogate.shelter_okafor.1.title": "PORT LOG",
    "terminal.surrogate.shelter_okafor.1.body": "# GREENHOUSE STATION, CHASSIS PORT\nOne room, one scrubber, one researcher. Dr. Ada Okafor, soil and crop programme, contract 41.\n\nA chassis on the port. That is the first thing on this line since the company stopped answering it.\n\nDo not open the airlock. The room is clean and I would like it to stay that way. Talk here.",
    "terminal.surrogate.shelter_okafor.2.title": "CRAWLER BLUEPRINT",
    "terminal.surrogate.shelter_okafor.2.body": "# SURROGATE SYSTEMS TRACKED HULL, MARK 2\nMy chassis is in three pieces in the porch and I have nothing to mend it with. So I cannot build this, and you can.\n\nThe file is on your chassis now. Hull plating round the outside, two servo motors, a block of iron in the middle, the blueprint on the bench with them. The bench gives the blueprint back.\n\nBuild it. Back it onto my collar, the door in the west wall. I will walk aboard. Then take me somewhere with a bed and something to eat, because this room is done.",
    "terminal.surrogate.shelter_sorensen.name": "SURVEY TWO",
    "terminal.surrogate.shelter_sorensen.1.title": "PORT LOG",
    "terminal.surrogate.shelter_sorensen.1.body": "# SURVEY TWO, CHASSIS PORT\nMikkel Sorensen. Rover down, pod holding.\n\nIf you are reading this on a chassis, you are the first traffic on this port. The collar is on the west wall. Bring something with a ring on the back and I will get in it.",
    "terminal.surrogate.shelter_tanaka.name": "SULFUR WORKS",
    "terminal.surrogate.shelter_tanaka.1.title": "PORT LOG",
    "terminal.surrogate.shelter_tanaka.1.body": "# SULFUR WORKS, CHASSIS PORT\nYuki Tanaka. Cartridges spent, door lethal, mood fair.\n\nThe port is for talking. The collar on the west wall is for leaving. I would very much like to use the second one.",
    "terminal.surrogate.shelter_brandt.name": "BRANDT",
    "terminal.surrogate.shelter_brandt.1.title": "PORT LOG",
    "terminal.surrogate.shelter_brandt.1.body": "# CHASSIS PORT\nBrandt. Nobody asked for a port on this wall and here it is anyway.\n\nBread. And if you have one of those crawlers, the collar is on the west side. I am not walking anywhere.",
    "survivor.surrogate.okafor.port.greet": "A chassis on my port. Hello, tin can. Do not touch the airlock; talk here.",
    "survivor.surrogate.okafor.port.blueprint": "Listen. I have the plans for a crawler, a proper hull with a room in it, and no chassis to build it with. Mine is scrap. I am sending you the file. Build it, dock it here, and get me out.",
    "survivor.surrogate.okafor.port.again": "The blueprint is on your chassis. Hull plating, servos, an iron block, the plans on the bench. Then my collar, west wall.",
    "survivor.surrogate.okafor.port.empty": "Nobody home. She left on a crawler.",
    "survivor.surrogate.okafor.aboard": "I am in. I am in. Drive carefully, I have not been in a vehicle in two hundred days.",
    "survivor.surrogate.okafor.home": "A pod. With air in it. Show me the bed and I will tell you everything I know about tomatoes.",
    "survivor.surrogate.okafor.home_radio": "Greenhouse Station is closed. Okafor is at the pod. Somebody built a crawler, and it works.",
    "survivor.surrogate.sorensen.port.greet": "A chassis at the port. Copy. Mind the porch, it is where the rover died.",
    "survivor.surrogate.sorensen.port.again": "Still here. Still would like a lift.",
    "survivor.surrogate.sorensen.port.empty": "Nobody home. He left on a crawler.",
    "survivor.surrogate.sorensen.aboard": "Aboard. Nice hull. Who welded it?",
    "survivor.surrogate.sorensen.home": "Solid ground and a roof. I will earn my keep, I promise.",
    "survivor.surrogate.sorensen.home_radio": "Survey Two to all stations: Sorensen is at the pod. Somebody has a crawler out here.",
    "survivor.surrogate.tanaka.port.greet": "A chassis. On my port. I am going to stay very calm.",
    "survivor.surrogate.tanaka.port.again": "The collar is on the west wall. I checked it twice today.",
    "survivor.surrogate.tanaka.port.empty": "Nobody home. She left on a crawler.",
    "survivor.surrogate.tanaka.aboard": "In. Sealed. Breathing something that is not sulfur. Go.",
    "survivor.surrogate.tanaka.home": "This will do. This will do very well.",
    "survivor.surrogate.tanaka.home_radio": "Sulfur Works signing off for good. Tanaka is at the pod.",
    "survivor.surrogate.brandt.port.greet": "A tin can on the wall telling me there is a tin can at the wall. Fine. What.",
    "survivor.surrogate.brandt.port.again": "Bread. Or a crawler. Ideally both.",
    "survivor.surrogate.brandt.port.empty": "Nobody home. He left on a crawler, and complained the whole way.",
    "survivor.surrogate.brandt.aboard": "Right. I am in. It smells of new paint. Go on then.",
    "survivor.surrogate.brandt.home": "It has a kitchen. I will allow it.",
    "survivor.surrogate.brandt.home_radio": "Brandt. At the pod. Do not make a fuss.",
    "block.surrogate.dock_door": "Collar Door",
    "message.surrogate.dock_door.sealed": "The collar door is sealed: nothing is docked.",
    "entity.surrogate.crawler": "Crawler",
    "item.surrogate.crawler_kit": "Crawler Kit",
    "tooltip.surrogate.crawler_kit": "Use on level ground to assemble the hull",
    "hud.surrogate.crawler": "HDG %s %s   %s m/s   CHARGE %s%%   %s",
    "hud.surrogate.crawler.docked": "COUPLED",
    "hud.surrogate.crawler.free": "NO DOCK",
    "message.surrogate.crawler.charged": "Crawler charge %s%%.",
    "message.surrogate.crawler.full": "The crawler is fully charged.",
    "message.surrogate.crawler.no_room": "No room to assemble the crawler here.",
    "block.surrogate.crawler_helm": "Crawler Helm",
    "block.surrogate.crawler_dock_console": "Docking Console",
    "block.surrogate.crawler_hatch": "Crawler Hatch",
    "block.surrogate.crawler_bay": "Chassis Bay",
    "message.surrogate.crawler.aboard": "Aboard.",
    "message.surrogate.crawler.outside": "Outside, beside the hull.",
    "message.surrogate.crawler.into_pod": "Through the collar.",
    "message.surrogate.crawler.hull_far": "The hull is not answering. Give it a moment.",
    "message.surrogate.crawler.helm": "At the helm. Movement keys drive; sneak to step away.",
    "message.surrogate.crawler.dock_seat": "At the docking console. W backs the hull onto a collar; click to lock; S pulls away; sneak to step away.",
    "message.surrogate.crawler.coupled_helm": "Coupled to a collar. Uncouple from the docking console first.",
    "message.surrogate.crawler.coupled": "Coupled. The collar is open.",
    "message.surrogate.crawler.uncoupled": "Uncoupled.",
    "message.surrogate.crawler.recalled": "Chassis taken aboard.",
    "message.surrogate.crawler.deployed": "Chassis deployed beside the hull; the chair is keyed to it.",
    "message.surrogate.crawler.bay_empty": "The bay is empty. Use a chassis on it.",
    "message.surrogate.crawler.no_room_outside": "No room beside the hull.",
    "hud.surrogate.crawler.lost": "HULL NOT ANSWERING",
    "hud.surrogate.crawler.helm": "HELM   W/S drive   A/D steer   sneak to step away",
    "hud.surrogate.crawler.dock.none": "DOCK   no collar in range   W backs up",
    "hud.surrogate.crawler.dock.reading": "DOCK   offset %sm   angle %s deg   %s",
    "hud.surrogate.crawler.dock.aligned": "ALIGNED   click to lock",
    "hud.surrogate.crawler.dock.adjust": "adjust",
    "hud.surrogate.crawler.dock.coupled": "DOCK   COUPLED   S pulls away",
    "hud.surrogate.crawler.rear_camera": "REAR CAMERA",
    "message.surrogate.crawler.use_bay": "Not in here. The bay in the wall sends a chassis out beside the hull.",
})

# ======================================================================================
# Props, ores and the vehicle fabricator (2026-09-05)
# ======================================================================================
def tex(name):
    return f"{MOD}:block/{name}"


def box(frm, to, faces, rotation=None, shade=None):
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        e["rotation"] = rotation
    if shade is not None:
        e["shade"] = shade
    return e


def all_faces(texture, uv=None, **overrides):
    faces = {side: face(texture, uv) for side in ("north", "south", "east", "west", "up", "down")}
    faces.update(overrides)
    return faces


def sides(texture, uv=None):
    return {side: face(texture, uv) for side in ("north", "south", "east", "west")}


def prop_model(name, textures, elements, display=None, particle=None):
    obj = {"parent": "minecraft:block/block",
           "textures": dict({"particle": particle or next(iter(textures.values()))}, **textures),
           "elements": elements}
    if display:
        obj["display"] = display
    model(f"block/{name}", obj)


def cross_planes(texture, y0, y1, inset=0):
    """Two crossed planes, the way a sapling is drawn."""
    return [
        box([8, y0, inset], [8, y1, 16 - inset], {"east": face(texture), "west": face(texture)}, shade=False),
        box([inset, y0, 8], [16 - inset, y1, 8], {"north": face(texture), "south": face(texture)}, shade=False),
    ]


SMALL_GUI = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}}
FM = tex("frame_metal")
MP = tex("machine_plating")
WHITE = tex("med_cabinet_side")

# The full cubes.
for name, top, side in (("supply_crate", "supply_crate_top", "supply_crate_side"),):
    model(f"block/{name}", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": tex(top), "bottom": tex(top), "side": tex(side)}})
    blockstate(name, facing_variants({"": name}))
model("block/locker", {"parent": "minecraft:block/orientable", "textures": {"front": tex("locker_front"), "side": tex("locker_side"), "top": MP}})
blockstate("locker", facing_variants({"": "locker"}))
model("block/data_rack", {"parent": "minecraft:block/orientable", "textures": {"front": tex("data_rack_front"), "side": tex("data_rack_side"), "top": MP}})
blockstate("data_rack", facing_variants({"": "data_rack"}))
for name in ("deck_grating", "hazard_plating", "deck_plating", "hull_frame"):
    cube_all(name)

# The lamp hangs under the ceiling.
prop_model("ceiling_lamp", {"top": tex("ceiling_lamp_top"), "bottom": tex("ceiling_lamp_bottom"), "frame": FM}, [
    box([2, 14, 2], [14, 16, 14], all_faces("#frame", [2, 0, 14, 2], up=face("#top", [2, 2, 14, 14]), down=face("#bottom", [2, 2, 14, 14])))],
    particle=tex("ceiling_lamp_top"))
blockstate("ceiling_lamp", {"": {"model": f"{MOD}:block/ceiling_lamp"}})

# The pipe: a column, turned onto its side by the block state.
prop_model("pipe", {"side": tex("pipe_side"), "end": tex("pipe_end")}, [
    box([5, 0, 5], [11, 16, 11], all_faces("#side", [5, 0, 11, 16], up=face("#end", [5, 5, 11, 11]), down=face("#end", [5, 5, 11, 11])))])
blockstate("pipe", {"axis=y": {"model": f"{MOD}:block/pipe"}, "axis=z": {"model": f"{MOD}:block/pipe", "x": 90},
                    "axis=x": {"model": f"{MOD}:block/pipe", "x": 90, "y": 90}})

# Handrail: two posts and two rails along the front edge.
prop_model("handrail", {"rail": tex("rail")}, [
    box([1, 0, 1], [3, 12, 3], all_faces("#rail", [0, 0, 2, 12])),
    box([13, 0, 1], [15, 12, 3], all_faces("#rail", [0, 0, 2, 12])),
    box([0, 10, 1], [16, 12, 3], all_faces("#rail", [0, 0, 16, 2])),
    box([0, 5, 1.5], [16, 6, 2.5], all_faces("#rail", [0, 0, 16, 1]))])
blockstate("handrail", facing_variants({"": "handrail"}))

# Crew seat: a cushion on a frame, the back at the rear.
prop_model("crew_seat", {"cushion": tex("chair_cushion"), "frame": FM}, [
    box([2, 6, 2], [14, 9, 14], all_faces("#frame", [2, 0, 14, 3], up=face("#cushion", [2, 2, 14, 14]))),
    box([2, 9, 11], [14, 18, 14], all_faces("#frame", [2, 0, 14, 3], north=face("#cushion", [2, 2, 14, 11]), south=face("#frame", [2, 2, 14, 11]))),
    box([3, 0, 3], [5, 6, 5], all_faces("#frame", [0, 0, 2, 6])),
    box([11, 0, 3], [13, 6, 5], all_faces("#frame", [0, 0, 2, 6])),
    box([3, 0, 11], [5, 6, 13], all_faces("#frame", [0, 0, 2, 6])),
    box([11, 0, 11], [13, 6, 13], all_faces("#frame", [0, 0, 2, 6]))], particle=tex("chair_cushion"))
blockstate("crew_seat", facing_variants({"": "crew_seat"}))

# Mess table: a brushed top on a pedestal.
prop_model("mess_table", {"top": tex("table_top"), "frame": FM}, [
    box([0, 13, 0], [16, 16, 16], all_faces("#frame", [0, 0, 16, 3], up=face("#top"), down=face("#frame"))),
    box([6, 0, 6], [10, 13, 10], all_faces("#frame", [0, 0, 4, 13])),
    box([3, 0, 3], [13, 1, 13], all_faces("#frame", [0, 0, 10, 1], up=face("#frame", [3, 3, 13, 13]), down=face("#frame", [3, 3, 13, 13])))],
    particle=tex("table_top"))
blockstate("mess_table", {"": {"model": f"{MOD}:block/mess_table"}})

# Hydroponic tray: a shallow tray of substrate with the tomato sprouts on a cross above it.
prop_model("hydroponic_tray", {"side": tex("tray_side"), "soil": tex("tray_soil"), "sprouts": tex("sprouts"), "frame": FM}, [
    box([0, 0, 0], [16, 6, 16], all_faces("#side", [0, 0, 16, 6], up=face("#soil"), down=face("#frame")))] + cross_planes("#sprouts", 6, 22, 1),
    particle=tex("tray_side"))
blockstate("hydroponic_tray", {"": {"model": f"{MOD}:block/hydroponic_tray"}})

# Med cabinet: on the wall, cross out.
prop_model("med_cabinet", {"front": tex("med_cabinet_front"), "side": WHITE}, [
    box([2, 2, 12], [14, 14, 16], all_faces("#side", [2, 2, 14, 14], north=face("#front", [2, 2, 14, 14]),
                                            east=face("#side", [0, 2, 4, 14]), west=face("#side", [0, 2, 4, 14]),
                                            up=face("#side", [2, 0, 14, 4]), down=face("#side", [2, 0, 14, 4])))])
blockstate("med_cabinet", facing_variants({"": "med_cabinet"}))

# Fire extinguisher: a cylinder on a bracket, the nozzle on top.
prop_model("fire_extinguisher", {"body": tex("extinguisher"), "bracket": tex("bracket")}, [
    box([5, 7, 14], [11, 9, 16], all_faces("#bracket", [5, 7, 11, 9])),
    box([6, 2, 11], [10, 13, 15], all_faces("#body", [4, 2, 10, 14], up=face("#bracket", [6, 6, 10, 10]), down=face("#bracket", [6, 6, 10, 10]))),
    box([7, 13, 12], [9, 15, 14], all_faces("#bracket", [7, 7, 9, 9]))], particle=tex("extinguisher"))
blockstate("fire_extinguisher", facing_variants({"": "fire_extinguisher"}))

# Wall vent: a grille flush on the wall.
prop_model("wall_vent", {"grille": tex("wall_vent"), "frame": FM}, [
    box([0, 0, 15], [16, 16, 16], all_faces("#frame", [0, 0, 16, 1], north=face("#grille"), south=face("#frame")))])
blockstate("wall_vent", facing_variants({"": "wall_vent"}))

# Coolant tank and chem drum: a box and the same box turned 45 degrees, which reads as a cylinder.
def cylinder(side_tex, top_tex, top_uv, x0=2, x1=14):
    faces = all_faces(side_tex, [x0, 0, x1, 16], up=face(top_tex, top_uv), down=face("#frame", top_uv))
    return [box([x0, 0, x0], [x1, 16, x1], faces),
            box([x0, 0, x0], [x1, 16, x1], faces, rotation={"origin": [8, 8, 8], "axis": "y", "angle": 45})]


prop_model("coolant_tank", {"side": tex("coolant_tank_side"), "top": tex("coolant_tank_top"), "frame": FM},
           cylinder("#side", "#top", [2, 2, 14, 14]), particle=tex("coolant_tank_side"))
blockstate("coolant_tank", {"": {"model": f"{MOD}:block/coolant_tank"}})
prop_model("chem_drum", {"side": tex("drum_side"), "top": tex("drum_top"), "frame": FM},
           cylinder("#side", "#top", [2, 2, 14, 14]), particle=tex("drum_side"))
blockstate("chem_drum", {"": {"model": f"{MOD}:block/chem_drum"}})

# Bunk: two blocks long, like a bed. The foot end has the legs and the blanket; the head end has the pillow.
# Both halves share the frame and mattress; only the top surface differs.
BUNK_TEX = {"top": tex("bunk_top"), "side": tex("bunk_side"), "frame": FM, "pillow": WHITE}
BUNK_BASE = [
    box([0, 3, 0], [16, 5, 16], all_faces("#frame", [0, 0, 16, 2], up=face("#frame"), down=face("#frame"))),
    box([0, 0, 0], [2, 3, 2], all_faces("#frame", [0, 0, 2, 3])),
    box([14, 0, 0], [16, 3, 2], all_faces("#frame", [0, 0, 2, 3])),
    box([0, 0, 14], [2, 3, 16], all_faces("#frame", [0, 0, 2, 3])),
    box([14, 0, 14], [16, 3, 16], all_faces("#frame", [0, 0, 2, 3])),
    box([1, 5, 1], [15, 8, 15], all_faces("#side", [0, 0, 14, 3], up=face("#top", [1, 1, 15, 15]), down=face("#frame"))),
]
prop_model("bunk_foot", BUNK_TEX, BUNK_BASE, particle=tex("bunk_top"))
prop_model("bunk_head", BUNK_TEX,
           BUNK_BASE + [box([3, 8, 2], [13, 10, 6], all_faces("#pillow", [3, 0, 13, 2], up=face("#pillow", [3, 2, 13, 6])))],
           particle=tex("bunk_top"))
# The head half sits one step along FACING, so it is drawn turned to face back down the bunk.
blockstate("bunk", {**facing_variants({"part=foot": "bunk_foot"}), **facing_variants({"part=head": "bunk_head"})})
model("item/bunk", {"parent": f"{MOD}:block/bunk_foot"})

# Survey marker: a stake and a flag on a cross of planes.
prop_model("survey_marker", {"marker": tex("survey_marker")}, cross_planes("#marker", 0, 16))
blockstate("survey_marker", {"": {"model": f"{MOD}:block/survey_marker"}})

# Pad light: a squat lamp, lens up.
prop_model("pad_light", {"side": tex("pad_light_side"), "top": tex("pad_light_top"), "frame": FM}, [
    box([4, 0, 4], [12, 5, 12], all_faces("#side", [4, 0, 12, 5], up=face("#top", [4, 4, 12, 12]), down=face("#frame", [4, 4, 12, 12])))],
    particle=tex("pad_light_top"))
blockstate("pad_light", {"": {"model": f"{MOD}:block/pad_light"}})

# Antenna mast: a lattice column, a beacon on top; stacks.
prop_model("antenna_mast", {"mast": tex("mast"), "top": tex("mast_top"), "frame": FM}, [
    box([6, 0, 6], [10, 16, 10], all_faces("#mast", [6, 0, 10, 16], up=face("#top", [6, 6, 10, 10]), down=face("#frame", [6, 6, 10, 10])))],
    particle=tex("mast"))
blockstate("antenna_mast", {"": {"model": f"{MOD}:block/antenna_mast"}})

# The vehicle fabricator: pad, two pylons behind it, a beam of emitters across their tops, lit while it builds.
for lit in (False, True):
    suffix = "_lit" if lit else ""
    prop_model(f"vehicle_fabricator{suffix}", {
        "top": tex(f"fabricator_top{suffix}"), "side": tex(f"fabricator_side{suffix}"),
        "pylon": tex(f"fabricator_pylon{suffix}"), "beam": tex(f"fabricator_beam{suffix}"), "frame": FM}, [
        box([0, 0, 0], [16, 2, 16], all_faces("#side", [0, 12, 16, 14], up=face("#top"), down=face("#frame"))),
        box([0, 2, 12], [3, 28, 16], all_faces("#pylon", [0, 0, 3, 16], north=face("#pylon", [6, 0, 9, 16]), up=face("#frame", [0, 12, 3, 16]))),
        box([13, 2, 12], [16, 28, 16], all_faces("#pylon", [0, 0, 3, 16], north=face("#pylon", [6, 0, 9, 16]), up=face("#frame", [0, 12, 3, 16]))),
        box([0, 26, 12], [16, 30, 16], all_faces("#frame", [0, 0, 16, 4], up=face("#frame", [0, 12, 16, 16]), down=face("#beam", [0, 6, 16, 10]))),
        box([5, 25, 8], [11, 27, 12], all_faces("#frame", [0, 0, 6, 2], down=face("#beam", [5, 6, 11, 10])))],
        display=SMALL_GUI, particle=tex(f"fabricator_top{suffix}"))
blockstate("vehicle_fabricator", facing_variants({"building=false": "vehicle_fabricator", "building=true": "vehicle_fabricator_lit"}))

PROPS = ["vehicle_fabricator", "supply_crate", "locker", "data_rack", "deck_grating", "hazard_plating", "deck_plating", "hull_frame", "ceiling_lamp", "pipe",
         "handrail", "crew_seat", "mess_table", "hydroponic_tray", "med_cabinet", "fire_extinguisher", "wall_vent", "coolant_tank", "bunk", "chem_drum",
         "survey_marker", "pad_light", "antenna_mast"]
ORES = ["cinnabar_ore", "halite_ore", "cobalt_ore", "deepslate_cobalt_ore", "tellurium_ore"]
for name in PROPS:
    # The bunk is two blocks and supplies its own item model above.
    if name != "bunk":
        model(f"item/{name}", {"parent": f"{MOD}:block/{name}"})
    write(f"data/{MOD}/loot_table/blocks/{name}.json", self_drop(name))

# Both halves of a bunk break together, so only the foot drops the item; otherwise one bunk yields two.
write(f"data/{MOD}/loot_table/blocks/bunk.json", {
    "type": "minecraft:block",
    "pools": [{"rolls": 1, "bonus_rolls": 0,
               "entries": [{"type": "minecraft:item", "name": mid("bunk")}],
               "conditions": [
                   {"condition": "minecraft:block_state_property", "block": mid("bunk"), "properties": {"part": "foot"}},
                   {"condition": "minecraft:survives_explosion"}]}],
    "random_sequence": mid("blocks/bunk"),
})
for name in ORES:
    cube_all(name)
for name in ("cinnabar", "salt", "raw_cobalt", "cobalt_ingot", "tellurium_crystal"):
    model(f"item/{name}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})


def ore_drop(block_name, item, lo=1, hi=1):
    functions = [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                 {"function": "minecraft:explosion_decay"}]
    if (lo, hi) != (1, 1):
        functions.insert(0, {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}, "add": False})
    write(f"data/{MOD}/loot_table/blocks/{block_name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": mid(block_name), "conditions": [SILK_TOUCH]},
            {"type": "minecraft:item", "name": mid(item), "functions": functions}]}]}],
        "random_sequence": mid(f"blocks/{block_name}"),
    })


ore_drop("cinnabar_ore", "cinnabar")
ore_drop("halite_ore", "salt", 2, 4)
ore_drop("cobalt_ore", "raw_cobalt")
ore_drop("deepslate_cobalt_ore", "raw_cobalt")
ore_drop("tellurium_ore", "tellurium_crystal")


def cooking(name, kind, ingredient, result, xp, ticks):
    write(f"data/{MOD}/recipe/{name}.json", {"type": f"minecraft:{kind}", "category": "misc", "ingredient": {"item": ingredient},
                                            "result": {"id": result}, "experience": xp, "cookingtime": ticks})


for src in ("raw_cobalt", "cobalt_ore", "deepslate_cobalt_ore"):
    cooking(f"cobalt_ingot_from_smelting_{src}", "smelting", mid(src), mid("cobalt_ingot"), 0.7, 200)
    cooking(f"cobalt_ingot_from_blasting_{src}", "blasting", mid(src), mid("cobalt_ingot"), 0.7, 100)

P = mid("hull_plating")
shaped("vehicle_fabricator", ["PCP", "SBS", "PRP"], {"P": P, "C": COPPER, "S": mid("servo_motor"), "B": "minecraft:iron_block", "R": "minecraft:redstone_block"},
       mid("vehicle_fabricator"), 1, "equipment")
shaped("supply_crate", ["PNP", "N N", "PNP"], {"P": P, "N": NUGGET}, mid("supply_crate"), 2, "building")
shaped("locker", ["PP", "PI", "PP"], {"P": P, "I": IRON}, mid("locker"), 1, "building")
shaped("data_rack", ["PRP", "PGP", "PRP"], {"P": P, "R": REDSTONE, "G": GLASS}, mid("data_rack"), 1, "building")
shaped("deck_grating", ["INI", "N N", "INI"], {"I": IRON, "N": NUGGET}, mid("deck_grating"), 8, "building")
shapeless("hazard_plating", [P, mid("sulfur")], mid("hazard_plating"), 1, "building")
shaped("deck_plating", ["PP", "PP"], {"P": P}, mid("deck_plating"), 4, "building")
shaped("hull_frame", ["P P", " I ", "P P"], {"P": P, "I": IRON}, mid("hull_frame"), 4, "building")
shaped("ceiling_lamp", ["GGG", "IRI"], {"G": GLASS, "I": IRON, "R": REDSTONE}, mid("ceiling_lamp"), 2, "building")
shaped("pipe", ["C", "C", "C"], {"C": COPPER}, mid("pipe"), 6, "building")
shaped("handrail", ["III", "I I"], {"I": IRON}, mid("handrail"), 3, "building")
shaped("crew_seat", ["P  ", "PP ", "I I"], {"P": P, "I": IRON}, mid("crew_seat"), 1, "building")
shaped("mess_table", ["PPP", " I ", " I "], {"P": P, "I": IRON}, mid("mess_table"), 1, "building")
shaped("hydroponic_tray", ["GRG", "PDP"], {"G": GLASS, "R": REDSTONE, "P": P, "D": "minecraft:coarse_dirt"}, mid("hydroponic_tray"), 1, "building")
shaped("med_cabinet", ["III", "IRI", "III"], {"I": IRON, "R": REDSTONE}, mid("med_cabinet"), 1, "building")
shaped("fire_extinguisher", ["N", "R", "I"], {"N": NUGGET, "R": REDSTONE, "I": IRON}, mid("fire_extinguisher"), 1, "building")
shaped("wall_vent", ["NNN", "N N", "NNN"], {"N": NUGGET}, mid("wall_vent"), 2, "building")
shaped("coolant_tank", ["PGP", "PWP", "PGP"], {"P": P, "G": GLASS, "W": "minecraft:water_bucket"}, mid("coolant_tank"), 1, "building")
shaped("bunk", ["P P", "PPP", "I I"], {"P": P, "I": IRON}, mid("bunk"), 1, "building")
shaped("chem_drum", [" I ", "ISI", "III"], {"I": IRON, "S": mid("sulfur")}, mid("chem_drum"), 1, "building")
shaped("survey_marker", ["R", "I", "I"], {"R": REDSTONE, "I": IRON}, mid("survey_marker"), 2, "building")
shaped("pad_light", ["G", "R", "P"], {"G": GLASS, "R": REDSTONE, "P": P}, mid("pad_light"), 2, "building")
shaped("antenna_mast", ["N", "I", "I"], {"N": NUGGET, "I": IRON}, mid("antenna_mast"), 2, "building")

# Tags: one pickaxe list for the whole mod, written here, last.
PICKAXE.extend([mid(n) for n in PROPS + ORES])
write("data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": PICKAXE})
write("data/minecraft/tags/block/needs_stone_tool.json", {"replace": False, "values": [mid("sulfur_crust"), mid("cinnabar_ore"), mid("halite_ore")]})
write("data/minecraft/tags/block/needs_iron_tool.json", {"replace": False, "values": [mid("cobalt_ore"), mid("deepslate_cobalt_ore"), mid("tellurium_ore")]})
write("data/minecraft/tags/block/climbable.json", {"replace": False, "values": [mid("antenna_mast")]})
write(f"data/{MOD}/tags/item/caustic.json", {"replace": False, "values": [mid("caustic_sand"), mid("ash"), mid("sulfur"), mid("sulfur_crust"), mid("cinnabar"), mid("cinnabar_ore")]})
write(f"data/{MOD}/tags/block/caustic.json", {"replace": False, "values": [mid("caustic_sand"), mid("ash"), mid("sulfur_crust"), mid("cinnabar_ore")]})

LANG.update({
    "block.surrogate.vehicle_fabricator": "Vehicle Fabricator",
    "block.surrogate.supply_crate": "Supply Crate",
    "block.surrogate.locker": "Locker",
    "block.surrogate.data_rack": "Data Rack",
    "block.surrogate.deck_grating": "Deck Grating",
    "block.surrogate.hazard_plating": "Hazard Plating",
    "block.surrogate.deck_plating": "Deck Plating",
    "block.surrogate.hull_frame": "Hull Frame",
    "block.surrogate.ceiling_lamp": "Ceiling Lamp",
    "block.surrogate.pipe": "Pipe",
    "block.surrogate.handrail": "Handrail",
    "block.surrogate.crew_seat": "Crew Seat",
    "block.surrogate.mess_table": "Mess Table",
    "block.surrogate.hydroponic_tray": "Hydroponic Tray",
    "block.surrogate.med_cabinet": "Med Cabinet",
    "block.surrogate.fire_extinguisher": "Fire Extinguisher",
    "block.surrogate.wall_vent": "Wall Vent",
    "block.surrogate.coolant_tank": "Coolant Tank",
    "block.surrogate.bunk": "Bunk",
    "block.surrogate.chem_drum": "Chem Drum",
    "block.surrogate.survey_marker": "Survey Marker",
    "block.surrogate.pad_light": "Pad Light",
    "block.surrogate.antenna_mast": "Antenna Mast",
    "block.surrogate.cinnabar_ore": "Cinnabar Ore",
    "block.surrogate.halite_ore": "Halite",
    "block.surrogate.cobalt_ore": "Cobalt Ore",
    "block.surrogate.deepslate_cobalt_ore": "Deepslate Cobalt Ore",
    "block.surrogate.tellurium_ore": "Tellurium Ore",
    "item.surrogate.cinnabar": "Cinnabar",
    "item.surrogate.salt": "Salt",
    "item.surrogate.raw_cobalt": "Raw Cobalt",
    "item.surrogate.cobalt_ingot": "Cobalt Ingot",
    "item.surrogate.tellurium_crystal": "Tellurium Crystal",
    "message.surrogate.fabricator.started": "Kit on the pad. Stand clear of the build site.",
    "message.surrogate.fabricator.busy": "The gantry is busy.",
    "message.surrogate.fabricator.no_room": "The ground ahead of the gantry is not clear.",
    "message.surrogate.fabricator.progress": "Fabricating: %s%%",
    "message.surrogate.fabricator.idle": "Idle. Put a vehicle kit on the pad.",
})
print("props, ores and fabricator data done")

LANG.update({
    # The weather, as the pilot HUD reads it out and as the subtitles name the loops.
    "hud.surrogate.mag": "MAG %s%%",
    "hud.surrogate.seismic": "SEIS %s%%",
    "subtitles.surrogate.storm_wind": "Storm wind rises",
    "subtitles.surrogate.acid_rain": "Acid rain hisses",
    "subtitles.surrogate.link_hiss": "Uplink hisses",
})

# ======================================================================================
# Magnetic storms and the empty wastes (2026-09-06)
# ======================================================================================
write(f"data/{MOD}/damage_type/storm.json", {"exhaustion": 0.0, "message_id": "surrogate.storm", "scaling": "never"})

LANG.update({
    "message.surrogate.storm.warning": "Mast has a front coming in. Ninety seconds. Get the chassis under something.",
    "message.surrogate.storm.clear": "Field is back down. You can go back out.",
    "message.surrogate.storm.link_lost": "Uplink gone. The storm took it.",
    "message.surrogate.radio.noise": "Nothing on the band but hiss.",
    "death.attack.surrogate.storm": "%1$s stood out in the storm",
    "subtitles.surrogate.storm_wash": "The band washes out",
    "subtitles.surrogate.storm_crack": "The sky cracks",
})
print("storm data done")

# ======================================================================================
# Geysers and the geothermal tap (2026-09-06)
# ======================================================================================
# The blockstate, the two models, the loot table, the pickaxe entry and the names are written with the rest
# of the wastes above; what is left is the recipe, the damage type and the words.
write(f"data/{MOD}/damage_type/geyser.json", {"exhaustion": 0.1, "message_id": "surrogate.geyser", "scaling": "never"})

# Late plumbing: plate, a turbine's worth of motors, and a cobalt throat that survives what comes up it.
shaped("geothermal_tap", ["PMP", "CBC", "PPP"],
       {"P": mid("hull_plating"), "M": mid("servo_motor"), "C": mid("cobalt_ingot"), "B": "minecraft:iron_block"},
       mid("geothermal_tap"), 1, "equipment")

LANG.update({
    "death.attack.surrogate.geyser": "%1$s was boiled by a geyser",
    "subtitles.surrogate.geyser_rumble": "The ground moves",
    "subtitles.surrogate.geyser_erupt": "A geyser lets go",
    "subtitles.surrogate.tap_cap": "Cap seats",
    "message.surrogate.geyser.quiet": "Vent quiet. Steam in about %s seconds.",
    "message.surrogate.geyser.steam": "Steaming at the lip. Stand off it.",
    "message.surrogate.geyser.rumble": "The ground is moving. Get off it.",
    "message.surrogate.geyser.erupting": "Erupting.",
    "message.surrogate.geyser.capped": "Capped. The throat is holding.",
    "message.surrogate.tap.capped": "Cap seated. The vent is yours.",
    "message.surrogate.tap.too_late": "The throat is already working. Nothing bolts onto that.",
    "message.surrogate.tap.info": "Geothermal tap: %s per tick, buffer %s%%.",
    "message.surrogate.tap.dead": "Nothing live under this cap.",
})
print("geyser and tap data done")

# ======================================================================================
# Chassis and crawler modules, and the three things you plant on the ground (2026-09-06)
# The four countermeasures of docs/DESIGN-hazards.md, the crawler's first module, and the mast,
# the beacon and the anchor. Blockstates, loot tables, the pickaxe tag and the block names are
# written further up with the rest of the hazard blocks; this is the rest of it.
# ======================================================================================
for name in ["relay_module", "resonance_damper", "acid_coating", "shielded_uplink", "ceramic_cladding"]:
    model(f"item/{name}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})

COBALT = mid("cobalt_ingot")
CERAMIC = mid("caustic_sandstone")
shaped("relay_module", [" G ", "CSC", "IEI"], {"G": mid("reinforced_glass"), "C": COPPER, "S": mid("servo_motor"), "I": IRON, "E": mid("power_cell")},
       mid("relay_module"), 1, "equipment")
shaped("resonance_damper", ["CRC", "ISI", "CRC"], {"C": COBALT, "R": REDSTONE, "I": IRON, "S": mid("servo_motor")},
       mid("resonance_damper"), 1, "equipment")
shaped("acid_coating", ["SSS", "SPS", "SSS"], {"S": CERAMIC, "P": P}, mid("acid_coating"), 1, "equipment")
shaped("shielded_uplink", ["CCC", "CUC", "IGI"], {"C": COBALT, "U": mid("uplink_card"), "I": IRON, "G": mid("reinforced_glass")},
       mid("shielded_uplink"), 1, "equipment")
shaped("ceramic_cladding", ["SSS", "PPP", "SSS"], {"S": CERAMIC, "P": P}, mid("ceramic_cladding"), 1, "equipment")
shaped("relay_mast", [" A ", "CRC", "PPP"], {"A": mid("antenna_mast"), "C": COPPER, "R": REDSTONE, "P": P}, mid("relay_mast"), 1, "building")
shaped("damper_beacon", ["PCP", "CSC", "PRP"], {"P": P, "C": COBALT, "S": mid("servo_motor"), "R": "minecraft:redstone_block"},
       mid("damper_beacon"), 1, "building")
shaped("span_anchor", ["PPP", "III", "PPP"], {"P": P, "I": IRON}, mid("span_anchor"), 2, "building")

LANG.update({
    "item.surrogate.relay_module": "Relay Module",
    "item.surrogate.resonance_damper": "Resonance Damper",
    "item.surrogate.acid_coating": "Acid Coating",
    "item.surrogate.shielded_uplink": "Shielded Uplink",
    "item.surrogate.ceramic_cladding": "Ceramic Cladding",

    "module.surrogate.relay": "relay",
    "module.surrogate.damper": "resonance damper",
    "module.surrogate.coating": "acid coating",
    "module.surrogate.shield": "shielded uplink",
    "module.surrogate.crawler.cladding": "ceramic cladding",

    "tooltip.surrogate.relay_module": "Adds %s m to the link and to the radio",
    "tooltip.surrogate.resonance_damper": "Your own drilling counts for %s%% below Y %s",
    "tooltip.surrogate.acid_coating": "The belt's rain takes %s%% as much off the hull",
    "tooltip.surrogate.shielded_uplink": "A storm costs the picture %s%% of what it would",
    "tooltip.surrogate.ceramic_cladding": "The belt's rain costs the crawler %s%% of what it would",
    "tooltip.surrogate.parked_only": "Bolted to a hull that is standing still",
    "tooltip.surrogate.module_fitted": "%s fitted",

    "message.surrogate.module_fitted": "This chassis already carries a %s.",
    "message.surrogate.status.modules": "Modules: %s",
    "message.surrogate.crawler.module_fitted": "This hull already carries a %s.",
    "message.surrogate.crawler.module_moving": "Stop the hull before bolting anything to it.",
    "message.surrogate.crawler.module_added": "Fitted %s to the hull.",
    "message.surrogate.relay_mast.live": "Relay mast: repeating the band another %s m. Buffer %s%%.",
    "message.surrogate.relay_mast.dark": "Relay mast: dark. %s m of reach and nothing to send it with. Buffer %s%%.",
    "message.surrogate.damper_beacon.singing": "Damper beacon: the rock is quiet for %s m. Buffer %s%%.",
    "message.surrogate.damper_beacon.silent": "Damper beacon: silent. %s m of quiet, once it has power. Buffer %s%%.",
    "message.surrogate.span_anchor": "Span anchor. The deck runs %s from here, when there is a kit to lay it.",

    "screen.surrogate.pilot_menu.modules": "Modules: %s",
    "screen.surrogate.pilot_menu.no_modules": "No modules fitted",

    "hud.surrogate.crawler.cladding": "CLADDING ON",
    "hud.surrogate.crawler.bare": "HULL BARE",
    "hud.surrogate.crawler.wear": "CORROSION %s%%",
})
print("modules and countermeasure data done")

# ======================================================================================
# The belt's rain, the borers, and the two things act one leaves lying about (2026-09-06)
# ======================================================================================
write(f"data/{MOD}/damage_type/acid.json", {"exhaustion": 0.0, "message_id": "surrogate.acid", "scaling": "never"})
write(f"data/{MOD}/damage_type/borer.json", {"exhaustion": 0.2, "message_id": "surrogate.borer", "scaling": "never"})

# What the belt can eat: the company's metal, anything of it left under an open sky.
write(f"data/{MOD}/tags/block/corrodible.json", {"replace": False, "values": [mid(n) for n in [
    "solar_collector", "power_conduit", "charging_dock", "life_support", "terminal", "relay_mast",
    "damper_beacon", "geothermal_tap", "vehicle_fabricator", "antenna_mast"]]})

# The remnant. Not a full cube, so a corroded life support unit is also a hole in the wall; no item, because
# there is nothing left to carry - a hull plate on it puts the machine that was there back.
prop_model("corroded_machine", {"skin": tex("corroded_machine"), "top": tex("corroded_machine_top")}, [
    box([1, 0, 1], [15, 13, 15], {"down": {"texture": "#skin"}, "up": {"texture": "#top"},
                                  "north": {"texture": "#skin"}, "south": {"texture": "#skin"},
                                  "west": {"texture": "#skin"}, "east": {"texture": "#skin"}})])
blockstate("corroded_machine", {"": {"model": f"{MOD}:block/corroded_machine"}})
write(f"data/{MOD}/loot_table/blocks/corroded_machine.json",
      {"type": "minecraft:block", "pools": [], "random_sequence": mid("blocks/corroded_machine")})

# The wind count's stake, and the canister the seep goes home in.
prop_model("survey_stake", {"stake": tex("survey_stake")}, cross_planes("#stake", 0, 16, 5))
blockstate("survey_stake", {"": {"model": f"{MOD}:block/survey_stake"}})
model("item/survey_stake", {"parent": f"{MOD}:block/survey_stake"})
write(f"data/{MOD}/loot_table/blocks/survey_stake.json", self_drop("survey_stake"))
shaped("survey_stake", ["N", "I", "I"], {"N": NUGGET, "I": IRON}, mid("survey_stake"), 4, "building")
model("item/sealed_sample", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/sealed_sample"}})
shaped("sealed_sample", ["III", "IGI", "III"], {"I": mid("hull_plating"), "G": "minecraft:glass_bottle"},
       mid("sealed_sample"), 2, "equipment")

LANG.update({
    "block.surrogate.corroded_machine": "Corroded Machine",
    "block.surrogate.survey_stake": "Survey Stake",
    "item.surrogate.sealed_sample": "Sealed Sample",
    "entity.surrogate.borer": "Borer",
    "death.attack.surrogate.acid": "%1$s was eaten by the rain",
    "death.attack.surrogate.borer": "%1$s went too deep",
    "message.surrogate.acid.machine_lost": "%s has gone. The rain got through it.",
    "message.surrogate.acid.needs_plate": "What is left of %s. A hull plate would put it back.",
    "message.surrogate.acid.something": "something of ours",
    "message.surrogate.acid.corroded": "Corroded %s%%. It wants a roof more than it wants a repair.",
    "message.surrogate.acid.chassis": "The rain is on the chassis. Get it under something.",
    "message.surrogate.acid.crawler_1": "Rain on the hull. Charge is going four times as fast.",
    "message.surrogate.acid.crawler_2": "Still in it. Half charge. Cladding is what stops this.",
    "message.surrogate.acid.crawler_3": "Charge is nearly out. If it stops here it stays here.",
    "message.surrogate.acid.crawler_4": "Hull is going now. Turn around.",
    "message.surrogate.borer.close": "Something is working in the rock.",
    "message.surrogate.borer.damped": "The damper has it. It has lost you.",
    "subtitles.surrogate.borer_grind": "Grinding through rock",
    "subtitles.surrogate.borer_lunge": "Something lunges",
})
# The rest of what the belt says. The four crawler_N lines above are the charge warnings; these are the hull
# itself going, the readout while it goes, and what a plate in somebody's hands is worth when they get there.
LANG.update({
    "message.surrogate.acid.hud": "ACID ON THE HULL   CHARGE %s%%",
    "message.surrogate.acid.flat": "Charge is gone. It is on the bare hull now.",
    "message.surrogate.acid.eating": "Still eating. Every minute out there is another plate to walk out.",
    "message.surrogate.acid.seized": "That is it stopped. It goes nowhere until somebody carries plate to it.",
    "message.surrogate.acid.mended": "Hull sound again. Integrity %s%%.",
    "message.surrogate.acid.mending": "Plate on the hull. Integrity %s%%. It wants more.",
    "message.surrogate.acid.hull_sound": "Nothing wrong with this hull. Keep the plate.",
    "message.surrogate.acid.patched": "Patched. Integrity %s%%, and the rain has nothing on it now.",
    "message.surrogate.acid.patching": "Some of it off. Integrity %s%%. It wants another.",
    "title.surrogate.acid.hull": "HULL CORRODING",
    "title.surrogate.acid.hull.sub": "Integrity %s%%",
    "title.surrogate.acid.seized": "HULL SEIZED",
    "message.surrogate.scanner.belt_out": "Scanner: outside the belt. Nothing falls on this ground.",
    "message.surrogate.scanner.belt_dry": "Scanner: inside the belt. Dry for now, and it will not stay dry.",
    "message.surrogate.scanner.belt_rain": "Scanner: inside the belt and it is falling. Anything of ours under open sky is being eaten.",
    "message.surrogate.scanner.machine_sound": "Scanner: no corrosion on it. Keep a roof over it and there will not be.",
})
print("acid, borer and act one data done")

# ======================================================================================
# Acts one and two: the neighbours' three runs and the man past the mast (2026-09-06)
# Every line the Research director asks for. The runs are Okafor's, then Sorensen's, then both
# of theirs; Halloran opens and closes each one, because the runs are logged through her. Every
# objective has a timeout and somebody works around it out loud, which is where they live.
# ======================================================================================
LANG.update({
    "cinematic.surrogate.research.chapter": "THE NEIGHBOURS",
    "cinematic.surrogate.research.chapter.sub": "ACT %s",
    "cinematic.surrogate.research.mast": "SITE FOUR MAST",

    # Run one: the shallow core. Okafor opens it and Okafor closes it.
    "cinematic.surrogate.research.open1": "Site Four, Site Two. Two people on this band have wanted a favour off you for a week and have been too polite to ask. Okafor first.",
    "cinematic.surrogate.research.open2": "Greenhouse Station. Okafor. I am told you have a chassis that goes down as well as along.",
    "cinematic.surrogate.research.open3": "It does. Ask, and I will log it as survey work, which keeps the company off all three of us.",
    "cinematic.surrogate.research.core1": "A column of the floor. Twelve blocks straight down, one hole. Not a scrape here and a scrape there. One hole, so the layers come up in order.",
    "cinematic.surrogate.research.core2": "I am a botanist asking for rock. I know. There is nobody left out here who is not, and I would like to know what my roots are standing in.",
    "cinematic.surrogate.research.core3": "Twelve blocks of what comes out, into the crate by your door. Take the drill and take a spare cell; it is a long hole for one charge.",
    "cinematic.surrogate.research.objective.core": "Drill twelve blocks down in one place and put the column in the crate",
    "cinematic.surrogate.research.core_nudge": "One hole, twelve down, and everything it gives you into the crate. The drill will do it. The cell will complain.",
    "cinematic.surrogate.research.core_timeout": "You are busy. I have taken what is under my own porch and I will pretend the layers are the same as yours. They are not. I will pretend.",
    "cinematic.surrogate.research.core_done": "That is the whole column, in order, and it is the first new rock I have had to look at in a year. Thank you.",
    "cinematic.surrogate.research.core4": "Ash, then sand, then two blocks of something laid down flat and fine. That is a lake bed. There was water standing here long enough to settle silt.",
    "cinematic.surrogate.research.core5": "So the salt in my trays is not blowing in off the pans. It is coming up from underneath. That is a year of bad tomatoes explained.",
    "cinematic.surrogate.research.core6": "Logged as a survey task, which it was. Sorensen has been waiting his turn and pretending he has not.",

    # Run two: the wind count. Sorensen's, and he wants it more than he will say.
    "cinematic.surrogate.research.wind1": "Survey Two. Sorensen. Mine is duller than hers and it takes longer. Four survey stakes, four kinds of ground, eighty blocks apart at the least.",
    "cinematic.surrogate.research.wind2": "He has asked me for this since spring. I keep telling him the wind is the wind.",
    "cinematic.surrogate.research.wind3": "The wind is not the wind. It comes off the mesa at dusk and I want to know how far in it reaches. Stakes are in your crate. Four. Plumb, please.",
    "cinematic.surrogate.research.objective.stakes": "Plant four survey stakes on four biomes, eighty blocks apart",
    "cinematic.surrogate.research.stakes_nudge": "Four stakes, four kinds of ground, eighty blocks between any two of them. They are in the crate by your door.",
    "cinematic.surrogate.research.stakes_timeout": "I walked two out myself before the suit made me stop and the pod's chassis carried the others. They are in. They are not plumb.",
    "cinematic.surrogate.research.wind4": "Now they stand. One night, that is all I want. And leave them up in the morning; I will want the same four again in a month.",
    "cinematic.surrogate.research.objective.night": "Leave the four stakes standing overnight",
    "cinematic.surrogate.research.night_nudge": "Nothing to do now but let it get dark. Sleep, charge the chassis, come and look at my rover.",
    "cinematic.surrogate.research.wind5": "Four counts. Sixty on the dunes at dusk and nine in the mire. There is a wall in the air out there and it stands exactly where the ridge does.",
    "cinematic.surrogate.research.wind6": "He has wanted that count for a year and I have never once heard him say why.",
    "cinematic.surrogate.research.wind7": "Because it is a quiet side, and everything we ever build should stand on it. That is a month of working outdoors in a bad suit, saved.",

    # Run three: the seep. Both of them want it and neither of them can go.
    "cinematic.surrogate.research.seep1": "Site Four, Greenhouse Station. Both of us this time, and we do not agree. The sample crate I left in the channel has a hole through the floor of it.",
    "cinematic.surrogate.research.seep2": "It has a hole in it because the crate is ceramic, and ceramic was the wrong thing to stand in a channel. That is a materials question and it is mine.",
    "cinematic.surrogate.research.seep3": "It is the water that is wrong, not the crate. Bring me a litre of it, sealed, on a bench, and then you can pick a material with something to go on.",
    "cinematic.surrogate.research.seep4": "Then take the crimped flask, it is the only one of mine that holds. And a chassis dips it. Nobody stands in that channel in a suit.",
    "cinematic.surrogate.research.seep5": "You are both right and you are both on my band. A litre out of the acid, sealed, from a chassis. Go on.",
    "cinematic.surrogate.research.objective.seep": "Take a sealed sample from an acid river, from inside a chassis",
    "cinematic.surrogate.research.seep_nudge": "The channel where it runs, not a puddle beside it. And the chassis holds the flask, not you.",
    "cinematic.surrogate.research.seep_timeout": "The pod's chassis dipped a flask on its own charge run. Wrong end of the channel, no label, and it counts. Barely.",
    "cinematic.surrogate.research.seep6": "Now get it indoors with the seal still on. Crate by your door. Open it out there and the sample is the sky, and she starts again.",
    "cinematic.surrogate.research.objective.home": "Bring the sealed sample to the crate without opening it outdoors",
    "cinematic.surrogate.research.home_nudge": "Do not use it outside. Not to look at it, not to show anybody. Straight in through the airlock and into the crate.",
    "cinematic.surrogate.research.home_timeout": "It came in on the pod's own arm in the end. The seal held, which is the only part of this I was ever going to care about.",
    "cinematic.surrogate.research.home_done": "Sealed, and in the crate, and mine. If the greenhouse smells of vinegar for a week, that is me and not the scrubber.",
    "cinematic.surrogate.research.seep7": "Fluorides. That is what is going through ceramic, and there is more of it in that litre than I would have guessed by an order.",
    "cinematic.surrogate.research.seep8": "So it is chemistry. I will line the crates with plate, and you can tell me in a month what your chemistry does to plate.",
    "cinematic.surrogate.research.schematic1": "Last thing and then I am off your band. Sending you a set of plans: a relay for the chassis. Servo, copper, a cell, a piece of reinforced glass.",
    "cinematic.surrogate.research.schematic2": "Sorensen. Say the rest of it.",
    "cinematic.surrogate.research.schematic3": "There is a fourth carrier on this band. No voice on it, a tone, keyed twice at the same hour every evening. Somebody is out past the mast.",

    # Act two: out of range. Tanaka is a carrier before he is a voice.
    "cinematic.surrogate.research.carrier1": "Carrier on the band. No modulation, no callsign, bearing steady. Source beyond mast range of 700 metres. No station on file.",
    "cinematic.surrogate.research.range1": "There it is again. Two keys, a wait, two keys. That is somebody running a schedule, and they have been running it a long time.",
    "cinematic.surrogate.research.range2": "Fit the relay and you hear fourteen hundred instead of seven. Then drive at him until the tone turns into a person.",
    "cinematic.surrogate.research.objective.relay": "Build the relay module and fit it to your chassis",
    "cinematic.surrogate.research.relay_nudge": "Fabricator, then the chassis. A servo, copper, a power cell and a piece of reinforced glass.",
    "cinematic.surrogate.research.relay_timeout": "The ugly way, then. The mast keeps a spare head and your chassis had a socket for it. It is on, it is crooked, and it hears.",
    "cinematic.surrogate.research.carrier2": "Carrier acquired. Modulation present. Voice channel open at 1400 metres. Callsign: Sulfur Works.",
    "cinematic.surrogate.research.tanaka1": "Sulfur Works. Tanaka. I have listened to the four of you for a year and not one of you has ever heard me. Say something so I know that has changed.",
    "cinematic.surrogate.research.range3": "We hear you, Sulfur Works. Halloran, Site Two. I am sorry. We had the tone down as equipment.",
    "cinematic.surrogate.research.tanaka2": "It is equipment. Mine, keyed twice a day so that anyone counting would know a person was doing it. Come out. I have something you want before you dig.",
    "cinematic.surrogate.research.objective.tanaka": "Drive out to Tanaka at Sulfur Works",
    "cinematic.surrogate.research.tanaka_nudge": "Eleven hundred metres, the wrong side of the table. That is a crawler drive, not a chassis walk. Charge everything first.",
    "cinematic.surrogate.research.tanaka_timeout": "He got tired of waiting and put his own chassis on the road with a crate on it. It is standing at your pad. He is still out there.",
    "cinematic.surrogate.research.tanaka3": "There is a chassis at my window. Somebody drove eleven hundred metres to look at a man through glass. Come round to the port.",
    "cinematic.surrogate.research.damper1": "Before anything else. I am a seismologist. Four instruments in the rock out here, and for a year they have drawn me the same thing.",
    "cinematic.surrogate.research.damper2": "Things that move through solid stone. Below the deepslate line, always, and they steer by noise. I call them borers. Nobody gave me a better word.",
    "cinematic.surrogate.research.damper3": "That is a damper. It sings into the rock at the frequency they steer by. It does not make you safe. It makes forty seconds of drilling instead of eight.",
    "cinematic.surrogate.research.damper4": "The company's assay has a deep sample on it. Marsh has been careful not to say how deep.",
    "cinematic.surrogate.research.damper5": "Then take that and be quiet with it. And go up when the rock goes quiet, because quiet is not one of them losing interest.",
    "cinematic.surrogate.research.close1": "Filed. Three runs and a man none of us could hear. That is the best week this station has had since it was a station.",
    "cinematic.surrogate.research.close2": "And I have rock, wind and water off one pilot. If anybody wants a tomato in six weeks, I am taking names.",
    "cinematic.surrogate.research.close3": "Get some sleep, Site Four. Marsh has a form for you in the morning and he looks pleased about it, which is never good.",

    # The five errands, and the one sample that got opened outdoors. None of them was ever an objective.
    "cinematic.surrogate.research.errand_seeds": "Tomato seed. Kept dry, kept cold, every one of them viable. Four trays free, so they go in tonight. ...Somebody packed these for me. Who.",
    "cinematic.surrogate.research.errand_airlock": "You put a plate on my outer door. I have talked to all of you in short sentences for two hundred days because of that door. Longer ones now.",
    "cinematic.surrogate.research.errand_cells": "Cells are in and the scrubber has stopped making the noise. I had stopped hearing the noise, which is the worse half of that.",
    "cinematic.surrogate.research.errand_kits": "Kits on the bench. Rover Two has a hull again by the end of the week. She will still not steer, but that part is mine.",
    "cinematic.surrogate.research.errand_sulfur": "Eight sulfur, weighed, and the cartridges are in the press. That is a year of breathing. Say what you want off me and it is yours.",
    "cinematic.surrogate.research.seep_spoiled": "You opened it out there. That is a litre of sky with a little of my channel in it. Go back and take another, and do not look at that one either.",

    # What the world says back while the runs are on.
    "message.surrogate.stake.close": "Too near the last stake. Eighty blocks between them, Sorensen said.",
    "message.surrogate.stake.same_biome": "This ground is already counted. He wants four different ones.",
    "message.surrogate.stake.planted": "Stake %s of %s standing.",
    "message.surrogate.sample.wrong_water": "Standing water. The sample comes out of the channel where it runs.",
    "message.surrogate.sample.needs_chassis": "Not in your own hands. A chassis takes this one.",
    "message.surrogate.sample.sealed": "The seal holds. It wants a bench, not a doorway.",
    "message.surrogate.sample.spoiled": "The flask is open and the air got to it first. That sample is the sky now.",
    "tooltip.surrogate.sealed_sample": "A litre of the channel. Opening it outdoors ruins it",
})
print("acts one and two dialogue done")

# 2026-09-06: the far side of the Rift gets its voices. Reyes and Novak had none at all; the decline lines
# the radio switches to once the assay closes had none either; and a handful of keys the campaign code was
# already sending had nothing behind them.
LANG.update({
    # Imani Reyes, Clinic Nine, behind an airlock the storm took off its frame. She asks after Novak first.
    "survivor.surrogate.reyes.name": "Dr. Imani Reyes",
    "survivor.surrogate.reyes.radio.1": "Clinic Nine, Reyes. Before anyone asks after me: has anybody had Novak on this band? His crawler went over the edge of the Rift eleven days ago and I have had nothing off him since.",
    "survivor.surrogate.reyes.radio.2": "Reyes at Clinic Nine. My outer door is lying in the porch where the storm put it and the frame is eaten through. Four hull plates from the outside and I can cycle the lock again.",
    "survivor.surrogate.reyes.radio.3": "Clinic Nine, still on the air. If anyone out there has a chassis that will take the belt: the chasm floor, under the overhang, west of the bend. Start there. Not here.",
    "survivor.surrogate.reyes.greet": "Careful of the frame. It is not a door any more, it is an edge. Talk to me from where you are.",
    "survivor.surrogate.reyes.plea": "The frame wants %s %s, plated from your side, and then the lock will cycle. Take the rebreathers off the shelf while you are at it. You will need them before I do.",
    "survivor.surrogate.reyes.thanks": "That is a door again. Both rebreathers, take them. Sixty seconds of your own air each, and down in that mist sixty seconds is the whole of it.",
    "survivor.surrogate.reyes.idle.1": "The lock cycles. Nineteen days of listening to it not cycle.",
    "survivor.surrogate.reyes.idle.2": "When you go down there, go light and come straight back up. He will not be able to help you.",
    "survivor.surrogate.reyes.rescued_radio": "Clinic Nine to all stations: the outer door holds and the lock is cycling. Whoever plated it, the chasm floor is next, and I am coming with you.",
    "survivor.surrogate.reyes.safe": "Reyes here. Nobody has needed me today. That is the report I like.",
    "survivor.surrogate.reyes.port.greet": "A chassis on the port. Do not try the airlock, there is nothing to try. Talk here.",
    "survivor.surrogate.reyes.port.blueprint": "Take the shielded uplink off the rack by the door. When a storm comes over it costs the picture less, and you are going to be a long way from your chair with weather in the way. It is no use to me. I am not going anywhere until you plate that frame.",
    "survivor.surrogate.reyes.port.again": "Frame is still open. Four plates, from your side. And the overhang on the chasm floor, west of the bend, when you have the air for it.",
    "survivor.surrogate.reyes.port.empty": "Nobody home. She left on a crawler.",
    "survivor.surrogate.reyes.aboard": "Aboard. Right. Who is hurt, who is short of air, and how far is it to the Rift.",
    "survivor.surrogate.reyes.home": "This will do for a ward. Two bunks and a door that shuts. Keep the far one clear.",
    "survivor.surrogate.reyes.home_radio": "Clinic Nine is closed. Reyes is at the pod, and she has asked for the far bunk kept made up.",
    "survivor.surrogate.reyes.decline.1": "Clinic Nine. Scrubber is at eighty-one and the intake filter is grey the whole way through. I have washed it twice. It does not wash clean any more.",
    "survivor.surrogate.reyes.decline.2": "Reyes. I have shut the ward end and moved into the dispensary. Smaller room, less of it to scrub. Everyone out here is doing the same arithmetic this week.",
    "survivor.surrogate.reyes.decline.3": "Clinic Nine. Nothing off Novak again today. I am marking the days on the wall, which is a habit I would tell a patient to stop.",

    # Aleks Novak, eleven days on the floor of the Rift. Later in the rotation is worse than earlier.
    "survivor.surrogate.novak.name": "Aleks Novak",
    "survivor.surrogate.novak.radio.1": "Novak. The hull is on its side at the bottom and I am under the overhang beside it. Do not come down the scree for me. It is not a slope, it is a fall.",
    "survivor.surrogate.novak.radio.2": "Novak. Tell Reyes the leg is the same as it was yesterday. She will know what that means.",
    "survivor.surrogate.novak.radio.3": "Novak. Going quiet a while to save the cell. Nothing is wrong. I will come back on at first light.",
    "survivor.surrogate.novak.greet": "You came down here. That was stupid. Thank you.",
    "survivor.surrogate.novak.plea": "There is nothing on me you need and nothing left in the hull worth carrying. Get back up the scree while you still have air.",
    "survivor.surrogate.novak.thanks": "Nothing to give you. Get me to the top and we will call it square.",
    "survivor.surrogate.novak.idle.1": "Sat up today. That is the whole report.",
    "survivor.surrogate.novak.idle.2": "Reyes says another week off the leg. Reyes is usually right.",
    "survivor.surrogate.novak.rescued_radio": "Novak. Up top, in a cabin, with the door shut. I am told I said thank you the whole way. I do not remember the whole way.",
    "survivor.surrogate.novak.safe": "Novak here. Still horizontal. Reyes says that is the job for now.",
    "survivor.surrogate.novak.port.greet": "That is not a port, it is a radio in a hull on its side. But I hear you.",
    "survivor.surrogate.novak.port.again": "Still down here. Still not going anywhere on my own.",
    "survivor.surrogate.novak.port.empty": "Nobody home. He was carried out of here.",
    "survivor.surrogate.novak.aboard": "In. It is warm. Drive, do not stop on my account.",
    "survivor.surrogate.novak.home": "A bed. A real one. I will be quiet now.",
    "survivor.surrogate.novak.home_radio": "Novak is at the pod. Reyes has the far bunk. That is everybody off the Rift.",
    "survivor.surrogate.novak.decline.1": "Novak. Water is fine, cell is at a third, heater is off. Do not spend anything getting to me.",
    "survivor.surrogate.novak.decline.2": "Novak. Reyes first. She has people who need her and I have a hull and a wall.",
    "survivor.surrogate.novak.decline.3": "Novak. Say again. I had the set turned down and I did not hear the start of that.",

    # Once the assay closes the shelters start losing output, and the round robin talks about that instead.
    "survivor.surrogate.okafor.decline.1": "Greenhouse Station. Scrubber is at seventy-four this morning. It was seventy-nine on Tuesday. I write the number on the wall each day so that I stop rounding it up.",
    "survivor.surrogate.okafor.decline.2": "Okafor. I have moved the trays under the one lamp that still draws and pulled up the rest. Twenty plants instead of sixty. Twenty is what this air can carry.",
    "survivor.surrogate.okafor.decline.3": "Greenhouse Station, still transmitting. The cartridge will not wash clean any more. It comes out of the water the same grey it went in.",
    "survivor.surrogate.sorensen.decline.1": "Survey Two. Scrubber is at seventy-seven and the spare is a spare in name only. I have had it in pieces on the bunk twice this week.",
    "survivor.surrogate.sorensen.decline.2": "Sorensen. Rover Two runs an hour and then wants an hour. I have stopped taking her past anywhere I can walk back from.",
    "survivor.surrogate.sorensen.decline.3": "Survey Two. I have a shop and no stock. I can build one more of anything, and after that I am building it out of the shop.",
    "survivor.surrogate.tanaka.decline.1": "Sulfur Works. Sixty-eight percent, falling about a point a day. I have done the division. I expect everyone has done the division.",
    "survivor.surrogate.tanaka.decline.2": "Tanaka. The cartridges are rebuilt out of rebuilt cartridges now. The last set lasted nine days. The set before it lasted twenty.",
    "survivor.surrogate.tanaka.decline.3": "Sulfur Works. Three new mouths in the vent field since spring and two of them upwind of me. The seismograph has been saying so for a month.",
    "survivor.surrogate.brandt.decline.1": "Brandt. Filter is grey. I beat it on the step this morning and it came back grey. That is the end of that trick.",
    "survivor.surrogate.brandt.decline.2": "Brandt. One meal, and I have moved it to the evening, which is when the cold comes in. Not asking. Reporting.",
    "survivor.surrogate.brandt.decline.3": "Brandt. Rain has been at the roof plate over the porch six days now. The ceramic holds. The plate under it does not.",

    # The port sends whatever they have left the first time a chassis calls. Only Okafor had a line for it.
    "survivor.surrogate.sorensen.port.blueprint": "Take the relay module off Rover Two while you are here. It bolts to a chassis and it puts another few hundred metres on the band. You will get more out of it than I will. I am not going anywhere.",
    "survivor.surrogate.tanaka.port.blueprint": "There is a resonance damper on the bench. I built it for the vents and never used it. Fit it and the rock stops hearing you drill, which matters more than you think below the deepslate. Take it. It is doing nothing on that bench.",
    "survivor.surrogate.brandt.port.blueprint": "Pattern for the cladding. Fired clay on a hull, four courses, and the rain stops eating it. It is what has kept this roof over me. Build it before you drive back through that, not after.",

    "crew.surrogate.tanaka.name": "Yuki Tanaka",
    # Okafor was two people: her chassis said "Grace Okafor" on the pad while her shelter said "Dr. Ada
    # Okafor" on the radio. One person, one name, and the shelter's is the one the player meets first.
    "crew.surrogate.okafor.name": "Dr. Ada Okafor",

    # What the port says about the shelter's own falling number, and what the band sounds like past its reach.
    "message.surrogate.shelter.scrubber": "Scrubber at %s%% of rated output. It has been going down since the ship left.",
    "message.surrogate.radio.carrier": "  %s: carrier only, %s. No range on it and no words in it.",
    "message.surrogate.radio.carrier_line": "A carrier opens somewhere past the band. It holds a few seconds and drops. Somebody said something.",
    # The handover is a blueprint for Okafor and hardware for everyone else, so the item names itself.
    "message.surrogate.survivor.blueprint": "%s sends something across the port: %s. It is in your pack.",

    "terminal.surrogate.shelter_reyes.name": "CLINIC NINE",
    "terminal.surrogate.shelter_reyes.1.title": "PORT LOG",
    "terminal.surrogate.shelter_reyes.1.body": "# CLINIC NINE, CHASSIS PORT\nOne ward, one dispensary, one doctor. Imani Reyes, medical programme, contract 39.\n\nThe outer door is lying in the porch where the storm left it. The frame is eaten through top to bottom and the lock will not cycle against open air. Four hull plates, from your side of it.\n\nDo not stand in the frame to do the work. I have no way to help you from in here and I would have to watch.",
    "terminal.surrogate.shelter_reyes.2.title": "PATIENT LIST",
    "terminal.surrogate.shelter_reyes.2.body": "# CLINIC NINE, ADMISSIONS\nNOVAK, A. Crawler over the Rift edge, day one. On the band that evening, conscious. Reported the leg and would not describe it. Nothing since day three.\n\nThat is the list. One name, eleven days.\n\nHe is on the chasm floor, under the overhang, west of the bend. The mist takes a chassis apart down there, so it has to be a body, and a body is a rebreather and sixty seconds. Plate my frame first. When you carry him up he will need somebody who knows what to do with him.",

    "terminal.surrogate.shelter_novak.name": "CRAWLER FOUR",
    "terminal.surrogate.shelter_novak.1.title": "HULL LOG",
    "terminal.surrogate.shelter_novak.1.body": "# CRAWLER FOUR, CABIN SET\nAleks Novak, survey, contract 39. Hull is on its side at the bottom of the Rift and the cabin is the only part of it still sealed.\n\nI have the set, a third of a cell, and whatever was strapped down when we went over. The rest is on the scree between here and the top.\n\nIf you are reading this off my port then you came down. Do not stand there long.",
    "terminal.surrogate.shelter_novak.2.title": "DAMAGE LOG",
    "terminal.surrogate.shelter_novak.2.body": "# HULL FOUR, DAMAGE, BY DAY\nDay 1. Went over at the bend. Left track gone, port drive gone, cabin held. Leg is bad. Set works.\nDay 2. Cannot right her. Cannot move her. Sealed the cabin and pulled in what I could reach.\nDay 3. Mist comes up the chasm at night and goes back down at dawn. Plating outside is going grey.\nDay 6. Cell at half. Heater off. Leg is worse than day one and I have stopped writing about the leg.\nDay 9. Mist did not go down at dawn.\nDay 11.",
})
print("reyes, novak, the decline lines and the missing keys done")

# ======================================================================================
# The borer line under Contract Seven, and Novak talking to a chassis (2026-09-06)
# ======================================================================================
# Tellurium only generates below y -8 and the borer line runs at y 8, so stage three is sixteen blocks inside
# it whatever the pilot is carrying. Halloran opens the stage with what that means, says one thing the first
# time something wakes for the drill, and says one more when the crystals are back above the line. Two
# openings, because Tanaka's damper is a reminder or an absence and never a gate.
LANG.update({
    "cinematic.surrogate.assay.core_damper": "One thing before you go down. There is something under the deepslate line that comes to noise, and a drill is all noise. You have Tanaka's damper. Fit it before the first hole, not after the first one hears you.",
    "cinematic.surrogate.assay.core_nodamper": "One thing before you go down. There is something under the deepslate line that comes to noise, and a drill is all noise. The damper for it is on a bench at the Sulfur Works and that is where it still is. So: short holes, and listen between them.",
    "cinematic.surrogate.assay.core_borer": "Your seismic just went up. That is one of them, and it came for the drill. Stop cutting, or come up. There is no third thing.",
    "cinematic.surrogate.assay.core_up": "Three crystals, and you came back up. That is the half of it I cared about.",
    # What Novak says to a chassis standing over him. Everything that could actually move him is act five.
    "survivor.surrogate.novak.chassis": "A chassis. At least somebody knows where I am now. It will not be lifting me, though. The leg wants hands, and hands have to come down here breathing.",
})
print("assay borer line and Novak chassis line done")

# ======================================================================================
# The animals, the optional work, and the survey tier (2026-09-06)
# ======================================================================================
# Blocks: a table with a facing, a beacon with a lamp that is red or green, and a pillar that lights up.
PICKAXE += [mid("survey_station"), mid("survey_beacon"), mid("long_range_scanner")]

for name in ["survey_station", "survey_beacon", "long_range_scanner"]:
    write(f"data/{MOD}/loot_table/blocks/{name}.json", self_drop(name))

# The table: a plated box with a glass top, turned to face the player who put it down.
blockstate("survey_station", {f"facing={d}": {"model": f"{MOD}:block/survey_station", "y": y}
                              for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))})
model("block/survey_station", {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": f"{MOD}:block/survey_station_side",
        "side": f"{MOD}:block/survey_station_side",
        "top": f"{MOD}:block/survey_station_top",
        "bottom": f"{MOD}:block/hull_plating",
    },
    "elements": [
        # The table itself, thirteen high, so it reads as something you lean on rather than stand on.
        {"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "north": {"uv": [0, 0, 16, 12], "texture": "#side"},
            "south": {"uv": [0, 0, 16, 12], "texture": "#side"},
            "west": {"uv": [0, 0, 16, 12], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 12], "texture": "#side"}}},
        # The glass, one pixel proud of it, which is the bit that glows.
        {"from": [1, 12, 1], "to": [15, 13, 15], "faces": {
            "down": {"uv": [1, 1, 15, 15], "texture": "#top"},
            "up": {"uv": [1, 1, 15, 15], "texture": "#top"},
            "north": {"uv": [1, 0, 15, 1], "texture": "#top"},
            "south": {"uv": [1, 0, 15, 1], "texture": "#top"},
            "west": {"uv": [1, 0, 15, 1], "texture": "#top"},
            "east": {"uv": [1, 0, 15, 1], "texture": "#top"}}},
    ]})
model("item/survey_station", {"parent": f"{MOD}:block/survey_station"})

# The beacon: a thin pole with a lamp at the top, in two colours.
for linked, suffix in ((False, ""), (True, "_linked")):
    model(f"block/survey_beacon{suffix}", {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{MOD}:block/survey_beacon", "pole": f"{MOD}:block/survey_beacon",
                     "lamp": f"{MOD}:block/survey_beacon_lamp{suffix}"},
        "elements": [
            {"from": [6.5, 0, 6.5], "to": [9.5, 13, 9.5], "faces": {
                "north": {"uv": [6, 3, 10, 16], "texture": "#pole"},
                "south": {"uv": [6, 3, 10, 16], "texture": "#pole"},
                "west": {"uv": [6, 3, 10, 16], "texture": "#pole"},
                "east": {"uv": [6, 3, 10, 16], "texture": "#pole"},
                "up": {"uv": [6, 6, 10, 10], "texture": "#pole"}}},
            {"from": [5.5, 13, 5.5], "to": [10.5, 16, 10.5], "faces": {
                "north": {"uv": [0, 0, 5, 3], "texture": "#lamp"},
                "south": {"uv": [0, 0, 5, 3], "texture": "#lamp"},
                "west": {"uv": [0, 0, 5, 3], "texture": "#lamp"},
                "east": {"uv": [0, 0, 5, 3], "texture": "#lamp"},
                "up": {"uv": [0, 0, 5, 5], "texture": "#lamp"},
                "down": {"uv": [0, 0, 5, 5], "texture": "#lamp"}}},
        ]})
blockstate("survey_beacon", {"linked=false": {"model": f"{MOD}:block/survey_beacon"},
                             "linked=true": {"model": f"{MOD}:block/survey_beacon_linked"}})
model("item/survey_beacon", {"parent": f"{MOD}:block/survey_beacon"})

# The pillar: a full-height column, banded, with a dish face that lights when it has charge.
for lit, suffix in ((False, ""), (True, "_lit")):
    model(f"block/long_range_scanner{suffix}", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": f"{MOD}:block/long_range_scanner_top",
                     "bottom": f"{MOD}:block/hull_plating",
                     "side": f"{MOD}:block/long_range_scanner{suffix}"}})
blockstate("long_range_scanner", {"lit=false": {"model": f"{MOD}:block/long_range_scanner"},
                                  "lit=true": {"model": f"{MOD}:block/long_range_scanner_lit"}})
model("item/long_range_scanner", {"parent": f"{MOD}:block/long_range_scanner"})

# The three loose items.
for name in ["bio_sampler", "specimen_bag", "analysis_disk"]:
    model(f"item/{name}", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})

# Spawn eggs would be a lie: nothing here is bred and there is a command that spawns them for testing.
# Recipes. The survey tier is late-game kit and priced like it: the table wants a data rack and glass, the
# beacons are cheap on purpose because the errand is walking them out, and the pillar is a real project.
shaped("survey_station", ["GGG", "RCR", "PPP"],
       {"G": mid("reinforced_glass"), "R": mid("data_rack"), "C": mid("robot_core"), "P": mid("hull_plating")},
       mid("survey_station"), 1, "equipment")
shaped("survey_beacon", ["L", "C", "P"],
       {"L": REDSTONE, "C": COPPER, "P": mid("hull_plating")},
       mid("survey_beacon"), 2, "equipment")
shaped("long_range_scanner", ["ADA", "PCP", "PPP"],
       {"A": mid("antenna_mast"), "D": mid("data_rack"), "P": mid("hull_plating"), "C": mid("robot_core")},
       mid("long_range_scanner"), 1, "equipment")
shaped("bio_sampler", [" S ", "SCS", " P "],
       {"S": mid("servo_motor"), "C": mid("robot_core"), "P": mid("hull_plating")},
       mid("bio_sampler"), 1, "equipment")
shaped("specimen_bag", ["WWW", "WSW", "PPP"],
       {"W": "minecraft:white_wool", "S": mid("servo_motor"), "P": mid("hull_plating")},
       mid("specimen_bag"), 1, "equipment")

LANG.update({
    # ---- The animals
    "entity.surrogate.trundle": "Trundle",
    "entity.surrogate.slagback": "Slagback",
    "entity.surrogate.tocker": "Tocker",
    "entity.surrogate.lantern_slug": "Lantern Slug",

    # ---- Okafor's table. The note is what the terminal prints once the disk is in.
    "specimen.surrogate.trundle.name": "Trundle",
    "specimen.surrogate.trundle.note": "Grazes the crust for something we have not identified. Sleeps nineteen hours. Rolls away from everything, including me, including the wind. I have never seen one hurry and I have never seen one hurt.",
    "specimen.surrogate.slagback.name": "Slagback",
    "specimen.surrogate.slagback.note": "Sits on the vent fields absorbing heat through the dorsal plates and is, in every way that matters, a rock with opinions. Do not stand on one. I have stood on one.",
    "specimen.surrogate.tocker.name": "Tocker",
    "specimen.surrogate.tocker.note": "Follows light. Repeats tones back at you, badly, about half a second late. There is no reason for this that I can find. It is not mating, it is not warning, it is not territory. It just answers. Why.",
    "specimen.surrogate.lantern_slug.name": "Lantern Slug",
    "specimen.surrogate.lantern_slug.note": "Four photophores, no mouth I can locate, no observed movement in eleven hours. It watches. If you knock one off the ceiling it does not survive the fall, so do not, and I am aware that is not a biological note.",
    "specimen.surrogate.borer.name": "Borer",
    "specimen.surrogate.borer.note": "Reading taken at two metres from a live specimen in motion. I will not be repeating the procedure and I would ask that you do not either. Segmented, blind, steers by vibration through rock. The rest of the file is your telemetry and my language.",
    "specimen.surrogate.cat.name": "Ballast",
    "specimen.surrogate.cat.note": "Felis catus. Off-world, obviously. Filed because you asked and because she is the only specimen on the table that has ever sat on my keyboard. Nine kilograms. Argumentative.",
    "specimen.surrogate.seep_water.name": "Seep Water",
    "specimen.surrogate.seep_water.note": "Not water. A little over a third of it is, and the rest is what the crust has been dissolving into it since before anyone was here. Do not put a bare hand in it and do not put a chassis joint in it twice.",
    "specimen.surrogate.biomatter.name": "Crust Biomatter",
    "specimen.surrogate.biomatter.note": "The yellow film on the pan floors. Alive, in the sense that it divides. This is the bottom of the whole column: everything else on this table eats this, or eats something that does.",

    # ---- The sampler, the crate, the disk
    "item.surrogate.bio_sampler": "Bio-Sampler",
    "item.surrogate.specimen_bag": "Specimen Crate",
    "item.surrogate.analysis_disk": "Analysis Disk",
    "tooltip.surrogate.bio_sampler": "Chassis bay. Touch a living thing to file a reading of it.",
    "tooltip.surrogate.specimen_bag": "Chassis bay. Takes one of each species, alive, for the manifest.",
    "tooltip.surrogate.analysis_disk": "Okafor's survey software. Use it on any terminal, once.",
    "message.surrogate.disk.installed": "Analysis software installed. The survey page is on the terminal.",
    "message.surrogate.disk.already": "This hub already has the software.",
    "message.surrogate.survey.filed": "Reading filed: %s (%s of %s)",
    "message.surrogate.bag.crated": "Crated: %s (%s of %s)",
    "message.surrogate.bag.already": "There is already a %s in a crate.",
    "message.surrogate.bag.roused": "Not while it is awake. Wait for it to settle.",
    "message.surrogate.bag.falling": "It has come off the ceiling. There is nothing to collect.",
    "message.surrogate.bag.borer": "No. Absolutely not. Brandt was very clear about this.",

    # ---- The survey tier
    "block.surrogate.survey_station": "Survey Station",
    "block.surrogate.survey_beacon": "Survey Beacon",
    "block.surrogate.long_range_scanner": "Long-Range Scanner",
    "message.surrogate.beacon.linked": "Beacon linked. The ground around it is on the table.",
    "message.surrogate.beacon.orphan": "Beacon out of range. Plant another between here and the last one.",
    "message.surrogate.scanner.status": "Scanner: %s units in, %s m of reach.",
    "message.surrogate.scanner.next": "Another %s units buys about %s m.",
    "screen.surrogate.survey": "Survey",
    "screen.surrogate.survey.title": "SURFACE SURVEY — SALLOW",
    "screen.surrogate.survey.reach": "REACH %s m",
    "screen.surrogate.survey.hint": "drag to turn · scroll to zoom · esc to close",
    "station.surrogate.home": "Habitat Seven",
    "station.surrogate.pad": "Pad",
    "station.surrogate.beacon": "•",
    "station.surrogate.orphan": "!",
    "advancement.surrogate.whole_map.title": "The Whole Map",
    "advancement.surrogate.whole_map.description": "Every shelter on Sallow, on one table, at one time.",

    # ---- The optional work
    "message.surrogate.errand.offered": "New: %s",
    "message.surrogate.errand.received": "Received: %s",
    "message.surrogate.errand.done": "Done: %s",

    "errand.surrogate.housewarming.title": "Housewarming",
    "errand.surrogate.housewarming.brief": "You have helped both of them. Go home and get some sleep.",
    "errand.surrogate.housewarming.tired": "You are further past tired than you noticed. The bunk is right there.",
    "errand.surrogate.housewarming.thanks": "There is a room on the slab now. It has six beds in it and none of them are yours.",

    "errand.surrogate.hot_meal.title": "Something Warm",
    "errand.surrogate.hot_meal.brief": "Sorensen has been eating out of foil for fourteen months. Cook something and carry it to him before it goes cold.",
    "errand.surrogate.hot_meal.thanks": "He did not say anything for a while. Then he asked whether there was any more.",

    "errand.surrogate.survey.title": "Okafor's Survey",
    "errand.surrogate.survey.brief": "A reading of everything alive on this planet. She has given you the sampler and the software; you have to find the rest.",
    "errand.surrogate.survey.thanks": "The table is full. She has already started arguing with it.",

    "errand.surrogate.ballast.title": "Ballast",
    "errand.surrogate.ballast.brief": "The cat is out. She has been out for some hours. Bring her back.",
    "errand.surrogate.ballast.picked_up": "She permits it.",
    "errand.surrogate.ballast.thanks": "She walks in ahead of you as though it was all arranged.",

    "errand.surrogate.vent_clear.title": "Tanaka's Cable",
    "errand.surrogate.vent_clear.brief": "A vent has opened under the Sulfur Works' power run and is cooking the insulation. Cap it before it takes the line out.",
    "errand.surrogate.vent_clear.thanks": "Capped, and the line is holding. She says the heat is welcome now it is going somewhere.",

    "errand.surrogate.burial.title": "Outside Clinic Nine",
    "errand.surrogate.burial.brief": "There is a body fifteen metres from Reyes' airlock. She has been looking at it through the window for nine weeks and her chassis has been dead for eleven.",
    "errand.surrogate.burial.name": "Petrov",
    "errand.surrogate.burial.lifted": "He weighs almost nothing. The suit is most of it.",
    "errand.surrogate.burial.too_close": "Not here. She can see this window.",
    "errand.surrogate.burial.thanks": "She watched from the window and did not say anything, and then she said thank you, and then she closed the shutter.",

    "errand.surrogate.corroded.title": "Ceramic Row",
    "errand.surrogate.corroded.brief": "Three of Brandt's neighbours' machines have been eaten by the rain. He has the plates. He does not have a chassis that can stand in it.",
    "errand.surrogate.corroded.progress": "That is one back together.",
    "errand.surrogate.corroded.thanks": "Brandt says the row is quieter with them running. He means it as a good thing.",

    "errand.surrogate.ark.title": "Brandt's Ark",
    "errand.surrogate.ark.brief": "One of each, alive, in a crate. He has watched them through a window for eleven years and he is not going to be the last person who ever sees one.",
    "errand.surrogate.ark.thanks": "Six crates on the pad, and the old man will not go inside until he has counted them twice.",

    # ---- The housewarming, in full
    "cinematic.surrogate.housewarming.wake_1": "There you are. Do not panic, it is only us, and only the chassis. Mikkel has been in your kitchen.",
    "cinematic.surrogate.housewarming.wake_2": "I have been standing in your kitchen. There is a difference and you will not convince anyone of it.",
    "cinematic.surrogate.housewarming.wake_3": "We let ourselves in. Your airlock has been keyed to both of us since the day you got the port working, which you would know if you ever read what you sign.",
    "cinematic.surrogate.housewarming.offer_1": "We have been talking. About you, mostly, and about that slab out the east side that has had nothing on it since you landed.",
    "cinematic.surrogate.housewarming.offer_2": "There is a module in a rack on the platform with your habitat's number stencilled on it. It has been there four hundred days. Nobody will send it down for one signature.",
    "cinematic.surrogate.housewarming.offer_3": "Three signatures, though. Three registered sites, all requesting the same manifest line. That, they will answer. We sent it an hour ago. Come outside.",
    "cinematic.surrogate.housewarming.wait_1": "Any minute. It is a heavy thing on a cheap parachute and the platform does not aim so much as let go.",
    "cinematic.surrogate.housewarming.wait_2": "There. That light, low, coming up out of the west. That is yours.",
    "cinematic.surrogate.housewarming.landed_1": "On the slab. Near enough on the slab. That is four hundred days of paperwork settling into your garden.",
    "cinematic.surrogate.housewarming.landed_2": "Six bunks in there. Which is six more than anyone on this planet has spare.",
    "cinematic.surrogate.housewarming.goodbye": "We are going to go and be in our own kitchens now. Sleep in your own bed tonight, not the new ones. They are not for you.",
})
print("fauna, errand and survey data done")

LANG.update({
    # The survey page on any hub terminal that has had the disk put in it.
    "terminal.surrogate.survey.title": "SURVEY",
    "terminal.surrogate.survey.header": "OKAFOR / XENOBIOLOGY — %s of %s subjects filed",
    "terminal.surrogate.survey.unread": "No reading on file.",

    # ---- Marsh's suit.
    #
    # The prologue has him walk from Site Two to the pod and back, which is two hours outside, and Halloran
    # says out loud that his suit is rated for one. Both of those are true and neither is a mistake: she is
    # looking at a contractor's suit because it is the only kind she has ever seen. His is not one.
    "terminal.surrogate.site02.4.title": "KESTREL — ISSUE NOTE",
    "terminal.surrogate.site02.4.body": "Company Field Standard 11-C, and every word of it matters if you are the one wearing it.\n\nContractor issue on this contract is the Tern: soft suit, single bottle, one hour of exposure and a fifteen minute reserve you are not supposed to touch. Halloran has a Tern. Sorensen has a Tern. Okafor's is nine years old and has been patched twice.\n\nCompany personnel travelling on inspection carry the Kestrel. Sealed hardshell. Regenerative scrubber on a six hour cycle rather than a bottle, so the limit is the cartridge and the cartridge recharges off any powered rack. Rated exposure six hours, hard ceiling nine.\n\nThe difference is not technology. Both suits were made in the same year, in the same yard. The difference is that one of us is insured as an asset and the rest of you are insured as a schedule.\n\n— T. Marsh",
    "terminal.surrogate.site02.5.title": "RE: KESTREL",
    "terminal.surrogate.site02.5.body": "Teo,\n\nYou walked here in a suit I assumed would kill you and you let me think that for four hours because you did not want to explain what was on your back.\n\nI have read the note. I understand why you did not want to explain it.\n\nWhen we get to the part of this where somebody has to go down somewhere and come back up, you are going to be the one who can. I want you to have thought about that before I ask.\n\n— I.H.",
})
print("survey page and Marsh's suit done")

# ======================================================================================
# Acts four and five: the conference, the bridge, the belt road and the Rift floor (2026-09-06)
# ======================================================================================
# docs/DESIGN-campaign.md, "Act IV: the Conference" and "Act V: the Rift". One new item — the span kit,
# which is the only piece of hardware either act adds — and a great deal of dialogue, because act four is a
# four minute scene in which eight people who have never been in a room together are in one.

model("item/span_kit", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/span_kit"}})
# Sorensen's shop builds the first one during the call. Every one after it is this, which is deliberately
# expensive: a bridge should cost about what a crawler costs, or laying plating by hand stops being a choice.
shaped("span_kit", ["PFP", "SBS", "PFP"],
       {"P": mid("hull_plating"), "F": mid("hull_frame"), "S": mid("servo_motor"), "B": "minecraft:iron_block"},
       mid("span_kit"), 1, "transportation")

LANG.update({
    "item.surrogate.span_kit": "Span Kit",
    "tooltip.surrogate.span_kit": "Use on a span anchor: surveys the gap, then lays one course of deck a use",
    "tooltip.surrogate.span_kit.cost": "%s deck plating a course. Five wide, because the hull is",

    # ---------------------------------------------------------------- the call itself
    "cinematic.surrogate.call.lost": "SIGNAL LOST",
    "cinematic.surrogate.call.no_carrier": "NO CARRIER",
    "cinematic.surrogate.conference.chapter": "THE CONFERENCE",
    "cinematic.surrogate.conference.chapter.sub": "Habitat Seven. All stations.",
    "cinematic.surrogate.conference.objective.hub": "Sit down at the hub terminal in your pod",

    # One: who is on the band. Halloran counts the room before she says anything, because the count is the
    # thing she is afraid of.
    "cinematic.surrogate.conference.roll1": "Habitat Seven to all stations. Everybody who can hear me, key once. I want to know who is on this band before I say anything else.",
    "cinematic.surrogate.conference.roll2": "Greenhouse Station. Okafor. Here, and listening.",
    "cinematic.surrogate.conference.roll3": "Survey Two. Sorensen. Here.",
    "cinematic.surrogate.conference.roll4": "Sulfur Works. Tanaka. Here. Picture is holding, for now.",
    "cinematic.surrogate.conference.roll5": "...Brandt. If you got that, it was me. Say everything twice. I am getting one word in three out here.",
    "cinematic.surrogate.conference.roll6": "That is five. Novak.",
    "cinematic.surrogate.conference.roll7": "Reyes, Clinic Nine. Brandt is on my set because his is worse than mine. Nothing off Novak. Nothing since day three.",
    "cinematic.surrogate.conference.roll8": "Noted. Marsh, you have the floor, and you are going to hate it.",

    # The schedule. The company never sends anybody a refusal; it sends a document with them left out of it.
    "cinematic.surrogate.conference.sched1": "This is the last thing the relay took off the ship before it stopped answering. It is not a message. It is a schedule.",
    "cinematic.surrogate.conference.sched2": "Eleven lines. Payload received. Mass confirmed. Burn logged. Then a heading for a station four months from here. There is no line on it that comes back down.",
    "cinematic.surrogate.conference.sched3": "Read the bottom of it. Out loud, Teo.",
    "cinematic.surrogate.conference.sched4": "Site personnel: contract fulfilled. Recovery not scheduled. That is the whole of it. That is what they sent.",

    # Two: the margins. Both of them answer with a number, because that is the only currency left.
    "cinematic.surrogate.conference.green1": "Then I will give you mine. The greenhouse has eleven days of margin at the current draw. Not eleven days of food; eleven days before the trays stop paying for themselves.",
    "cinematic.surrogate.conference.green2": "With more light and more hands it is not eleven days, it is indefinite. That is not optimism, it is arithmetic, and it is the only good number anybody is going to hear today.",
    "cinematic.surrogate.conference.shop1": "Survey Two can build one more of anything. One. I have the parts for a single serious thing and after that I am a man in a shed.",
    "cinematic.surrogate.conference.shop2": "So tell me what the one thing is and I will start tonight.",

    # Three: the ground. Tanaka has been sitting on this for a month and says so.
    "cinematic.surrogate.conference.quake1": "Before anybody decides anything, hear the ground. I have a month of traces I did not want to send to people who could not move.",
    "cinematic.surrogate.conference.quake2": "The vent field is opening. Not a swarm. Not a quake. Opening, slowly, and westward, and it will keep going whether or not this call ends well.",
    "cinematic.surrogate.conference.quake3": "Which is the other reason nobody is staying where they are.",

    # Four: the far side, badly, through the belt.
    "cinematic.surrogate.conference.far1": "...say again... no. No, I have the sense of it. You are talking about leaving.",
    "cinematic.surrogate.conference.far1b": "I have been out here longer than your station has existed and I am telling you the roof holds. The man under it is the problem...",
    "cinematic.surrogate.conference.far2": "He is saying he will come if somebody comes for him. He will not say it in that order and I am not going to make him.",
    "cinematic.surrogate.conference.far3": "Halloran. Before one other thing on this call gets decided. There is a man on the floor of the Rift and this is day eleven.",
    "cinematic.surrogate.conference.far4": "I have not forgotten him. Nobody on this band has forgotten him.",

    # Five: the storm, which is the act's argument made out of weather.
    "cinematic.surrogate.conference.storm_alert": "MAGNETIC STORM",
    "cinematic.surrogate.conference.storm_alert.sub": "Far side signal lost",
    "cinematic.surrogate.conference.storm1": "Brandt. Reyes. Say again. — Nothing. That is the front coming over the belt. We knew it was due and I would have liked ten more minutes.",
    "cinematic.surrogate.conference.storm2": "Three panels. That is what we are looking at. That is the problem, drawn for you, by the weather.",
    "cinematic.surrogate.conference.storm3": "Then that is where the argument stops and the work starts.",

    # Six: the plan, and the smallest concrete decency on the planet.
    "cinematic.surrogate.conference.plan1": "Here is what we are going to do. I am not putting it to a vote, because there is only one of it.",
    "cinematic.surrogate.conference.plan2": "One crawler. One chassis. Everybody off that side of the Rift and into that pod, in whatever order the ground allows.",
    "cinematic.surrogate.conference.plan2b": "And then we build the thing the company would never have sold us, and we go home in it.",
    "cinematic.surrogate.conference.plan3": "Then my one thing is a span kit. Five wide, laid off an anchor. A bridge you can walk over is not a bridge; it has to take a hull. It will be on your port by morning.",
    "cinematic.surrogate.conference.plan4": "And I will keep eight people fed while you do it, which is the part nobody ever puts in the plan.",
    "cinematic.surrogate.conference.plan5": "You will want the pad. It is company property. So is the gantry. So, technically, am I.",
    "cinematic.surrogate.conference.plan6": "The licence key for the fabricator is on your rack as of this morning. Every pattern behind it opens.",
    "cinematic.surrogate.conference.plan6b": "If anyone ever asks, I would like it on the record that I did this on a Tuesday and nobody had to ask me twice.",
    "cinematic.surrogate.conference.plan7": "Habitat Seven, out. Go and get them.",

    # ---------------------------------------------------------------- act five on the radio
    "message.surrogate.rescue.summon": "Everyone on the band at once, and I mean everyone. Sit down at the hub terminal in your own pod when you can. This will not fit in a radio call.",
    "message.surrogate.rescue.kit": "%s, from Survey Two. Sorensen says the anchor goes on the near lip, looking across.",
    "message.surrogate.rescue.crossing": "The narrows are at x %s, z %s. That is where the survey says the two sides come closest.",
    "message.surrogate.rescue.flow": "That vent was not there a month ago and you are not driving through it. Look upstream. There is a wall holding it on that line, and behind the wall there is somewhere for it to go.",
    "message.surrogate.rescue.flow_cut": "That is it in the basin. Give the channel a minute to go grey, then take the hull straight over.",
    "message.surrogate.rescue.reyes_sealed": "The lock will not cycle against open air. Four plates in the frame, from your side, and then I will walk out to you.",
    "message.surrogate.rescue.novak_refused": "No. Not without Reyes aboard. You will get him to the top and then you will be standing over a man with a bad leg and no doctor, and I have thought about that a great deal more than you have. Clinic Nine first.",
    "message.surrogate.rescue.novak_refused_again": "Reyes first. I said it once.",
    "message.surrogate.rescue.carrying": "Carrying. Half speed, and the air does not care.",
    "message.surrogate.rescue.set_down": "Set down.",
    "message.surrogate.rescue.everyone": "That is everybody off the far side. All stations, stand down, and come home.",
    "message.surrogate.mist.chassis": "The mist is in the joints. Get the chassis out of the chasm.",
    "message.surrogate.mist.body": "The mist. A rebreather is sixty seconds, and it is not sixty seconds of standing about.",

    # The bridge.
    "message.surrogate.span.nothing": "Nothing to span. The anchor sits on the near lip with a real gap in front of it and the far side within sight.",
    "message.surrogate.span.surveyed": "Surveyed: %s blocks across, %s courses, %s deck plating a course.",
    "message.surrogate.span.course": "Course %s of %s.",
    "message.surrogate.span.finished": "The deck is down and both tracks fit. That is the far side reachable.",
    "message.surrogate.span.done_already": "This span is finished.",
    "message.surrogate.span.needs": "Needs %s %s for the next course.",

    # ---------------------------------------------------------------- the mission board
    "terminal.surrogate.board.title": "MISSION BOARD",
    "terminal.surrogate.board.header": "# ROLL: %s OF %s HOME",
    "terminal.surrogate.board.home": "home",
    "terminal.surrogate.board.aboard": "aboard, riding",
    "terminal.surrogate.board.reached": "reached",
    "terminal.surrogate.board.unreached": "not reached",
    "terminal.surrogate.board.scrubber": "scrubber %s%%",
    "terminal.surrogate.board.blocked": "in the way: %s",
    "board.surrogate.block.riding": "in the cabin. Take them home.",
    "board.surrogate.block.collar": "nothing. Back a hull onto the collar.",
    "board.surrogate.block.unfound": "nobody has been there yet.",
    "board.surrogate.block.rift": "the Rift. Anchor and span it.",
    "board.surrogate.block.flow": "a vent across the approach. Cut the wall upstream.",
    "board.surrogate.block.belt": "the belt, and no cladding until he hands the pattern over.",
    "board.surrogate.block.airlock": "her frame. Four hull plates, from outside.",
    "board.surrogate.block.doctor": "Reyes. She has to be aboard before anybody goes down.",
    "board.surrogate.block.carry": "the mist. On foot, one rebreather, and carry him up.",
    "board.surrogate.note.span_none": "No span. Plant an anchor on the near lip, looking across.",
    "board.surrogate.note.span_part": "Span under way. Deck plating, a course a use.",
    "board.surrogate.note.span_done": "Span complete. The far side takes a hull.",
    "board.surrogate.note.flow": "A vent is running across the road to Ceramic Row.",
    "board.surrogate.note.flow_cut": "The flow is in the basin and the channel is crust.",
    "board.surrogate.note.airlock": "Clinic Nine's outer frame is open. Four plates.",
    "board.surrogate.note.done": "Everybody is off the far side.",
})
print("acts four and five done")

lang_path = os.path.join(ASSETS, "lang", "en_us.json")
with open(lang_path, encoding="utf-8") as f:
    lang = json.load(f)
lang.update(LANG)
with open(lang_path, "w", encoding="utf-8") as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)
    f.write("\n")
print("updated lang/en_us.json")
print("done")
