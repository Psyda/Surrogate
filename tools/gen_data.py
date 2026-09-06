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


BIOMES = ["toxic_desert", "ash_dunes", "acid_flats", "salt_pans", "dead_grove"]

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

TERRAIN_NOISES = {
    "mesa": (-10, [1.0, 1.0, 0.5]),
    "basin": (-10, [1.0, 0.5]),
    "channel": (-9, [1.0, 1.0]),
    "floor": (-7, [1.0, 0.5]),
    "table": (-8, [1.0, 1.0]),
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


mesa_n, basin_n, channel_n, floor_n, table_n = (dnoise(n) for n in ("mesa", "basin", "channel", "floor", "table"))
valley = df_write("valley", flat_cache(dmax(add(CORRIDOR, neg(dabs(mesa_n))), add(-BASIN_EDGE, neg(basin_n)))))
floor_mask = df_write("floor_mask", flat_cache(clamp(mul(valley, CLIFF_GAIN), 0.0, 1.0)))
sea = df_write("sea", flat_cache(ramp(neg(basin_n), 0.55, 0.72)))
river = df_write("river", flat_cache(mul(mul(ramp(add(0.075, neg(dabs(channel_n))), 0.0, 0.06),
                                             ramp(neg(basin_n), 0.15, 0.35)), floor_mask)))
floor_height = df_write("floor_height", flat_cache(add(add(FLOOR_Y, mul(FLOOR_ROLL, floor_n)),
                                                       add(mul(-SEA_DEPTH, sea), mul(-RIVER_DEPTH, river)))))
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
router["initial_density_without_jaggedness"] = shape(base)
router["final_density"] = final_density

noise_settings = dict(vanilla_noise)
noise_settings["surface_rule"] = surface
noise_settings["noise_router"] = router
# Spawn on a valley floor, away from the acid and the rivers.
noise_settings["spawn_target"] = [{"temperature": [-1.0, 1.0], "humidity": [-1.0, 1.0], "continentalness": [-0.95, -0.65],
                                   "erosion": [-1.5, 1.5], "depth": 0.0, "weirdness": [-1.0, 1.0], "offset": 0.0}]
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
biome_layout = [
    {"biome": mid("acid_flats"), "parameters": params(continentalness=SEA_C)},
    {"biome": mid("acid_flats"), "parameters": params(continentalness=[-1.05, 1.0], depth=[0.7, 1.0])},
    {"biome": mid("ash_dunes"), "parameters": params(continentalness=TABLE_C, depth=LAND)},
    {"biome": mid("salt_pans"), "parameters": params(continentalness=FLOOR_C, erosion=[-1.5, -0.35], depth=LAND)},
    {"biome": mid("dead_grove"), "parameters": params(humidity=[0.1, 1.0], continentalness=FLOOR_C, erosion=[-0.35, 1.5], depth=LAND)},
    {"biome": mid("toxic_desert"), "parameters": params(humidity=[-1.0, 0.1], continentalness=FLOOR_C, erosion=[-0.35, 1.5], depth=LAND)},
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
    ["minecraft:fossil_upper", "minecraft:fossil_lower", "minecraft:monster_room", "minecraft:monster_room_deep"],
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
CUSTOM_ORDER = ["sulfur_patch", "scrap_heap", "vent", "petrified_tree"]
# Ores of Sallow, always in this order in the underground_ores step. Halite only where the ground is salt.
ORE_ORDER = ["ore_cinnabar", "ore_halite", "ore_cobalt", "ore_tellurium"]
COMMON_ORES = ["ore_cinnabar", "ore_cobalt", "ore_tellurium"]
SALT_ORES = COMMON_ORES + ["ore_halite"]

MONSTERS = [
    {"type": "minecraft:spider", "weight": 100, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:zombie", "weight": 19, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:skeleton", "weight": 100, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:creeper", "weight": 100, "minCount": 4, "maxCount": 4},
    {"type": "minecraft:enderman", "weight": 10, "minCount": 1, "maxCount": 4},
    {"type": "minecraft:witch", "weight": 5, "minCount": 1, "maxCount": 1},
    {"type": "minecraft:husk", "weight": 80, "minCount": 4, "maxCount": 4},
]


def biome(name, *, temperature, downfall, precipitation, fog, sky, water, water_fog, grass, foliage,
          vegetal, custom, music="minecraft:music.overworld.desert", particle=None, extra_monsters=(), ores=COMMON_ORES):
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
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
        "features": features,
        "spawners": {
            "monster": MONSTERS + list(extra_monsters),
            "creature": [],
            "ambient": [{"type": "minecraft:bat", "weight": 10, "minCount": 8, "maxCount": 8}],
            "axolotls": [], "misc": [],
            "underground_water_creature": [{"type": "minecraft:glow_squid", "weight": 10, "minCount": 4, "maxCount": 6}],
            "water_ambient": [], "water_creature": [],
        },
        "spawn_costs": {},
    }


SCRUB = ["minecraft:glow_lichen", "minecraft:patch_dead_bush_2", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"]

write(f"data/{MOD}/worldgen/biome/toxic_desert.json", biome(
    "toxic_desert", temperature=2.0, downfall=0.0, precipitation=False,
    fog=0xC9B85E, sky=0x9FA050, water=0x6E8B2B, water_fog=0x2F4A0F, grass=0x8A8A3A, foliage=0x7A7A2A,
    vegetal=SCRUB, custom=["sulfur_patch", "scrap_heap"], ores=SALT_ORES))
write(f"data/{MOD}/worldgen/biome/ash_dunes.json", biome(
    "ash_dunes", temperature=1.6, downfall=0.0, precipitation=False,
    fog=0x77746A, sky=0x6E6B5F, water=0x4A5A3A, water_fog=0x1F2A14, grass=0x5C5C40, foliage=0x4E4E36,
    vegetal=["minecraft:glow_lichen"], custom=["sulfur_patch", "scrap_heap", "vent"],
    particle=("minecraft:white_ash", 0.02)))
write(f"data/{MOD}/worldgen/biome/acid_flats.json", biome(
    "acid_flats", temperature=1.0, downfall=0.4, precipitation=True,
    fog=0x9CB35A, sky=0x8E9E4E, water=0x7FBF2A, water_fog=0x3E6A10, grass=0x7D8F3A, foliage=0x6C7D2E,
    vegetal=["minecraft:glow_lichen", "minecraft:patch_dead_bush_2"], custom=["scrap_heap"], ores=SALT_ORES,
    extra_monsters=[{"type": "minecraft:drowned", "weight": 40, "minCount": 1, "maxCount": 2}]))
write(f"data/{MOD}/worldgen/biome/salt_pans.json", biome(
    "salt_pans", temperature=1.9, downfall=0.0, precipitation=False,
    fog=0xD9D7B0, sky=0xA9AA6E, water=0x86A648, water_fog=0x3B4F1A, grass=0xA0A070, foliage=0x8C8C5E,
    vegetal=["minecraft:glow_lichen", "minecraft:patch_dead_bush_2"], custom=["sulfur_patch", "scrap_heap"], ores=SALT_ORES))
write(f"data/{MOD}/worldgen/biome/dead_grove.json", biome(
    "dead_grove", temperature=1.2, downfall=0.3, precipitation=True,
    fog=0xA8A66A, sky=0x8E9450, water=0x6E8B2B, water_fog=0x2F4A0F, grass=0x6B6B2F, foliage=0x5B5B25,
    vegetal=SCRUB, custom=["scrap_heap", "petrified_tree"], music="minecraft:music.overworld.forest"))

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
           mid("reinforced_glass"), mid("airlock_door"), mid("caustic_sandstone"), mid("sulfur_crust"), mid("vent")]
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

for name in ["life_support", "solar_collector", "power_conduit", "decon_shower", "hull_plating", "caustic_sand", "caustic_sandstone", "ash", "scrap_heap", "vent"]:
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


for name in ["caustic_sand", "ash", "sulfur_crust", "scrap_heap", "hull_plating", "reinforced_glass", "power_conduit"]:
    cube_all(name)

blockstate("caustic_sandstone", {"": {"model": f"{MOD}:block/caustic_sandstone"}})
model("block/caustic_sandstone", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{MOD}:block/caustic_sandstone_top", "bottom": f"{MOD}:block/caustic_sandstone_top", "side": f"{MOD}:block/caustic_sandstone"}})
model("item/caustic_sandstone", {"parent": f"{MOD}:block/caustic_sandstone"})

blockstate("vent", {"": {"model": f"{MOD}:block/vent"}})
model("block/vent", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{MOD}:block/vent_top", "bottom": f"{MOD}:block/vent_side", "side": f"{MOD}:block/vent_side"}})
model("item/vent", {"parent": f"{MOD}:block/vent"})

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
    "item.surrogate.sulfur": "Sulfur",
    "item.surrogate.mining_drill": "Mining Drill",
    "item.surrogate.arc_cutter": "Arc Cutter",
    "item.surrogate.atmo_scanner": "Atmosphere Scanner",

    "biome.surrogate.toxic_desert": "Toxic Desert",
    "biome.surrogate.ash_dunes": "Ash Dunes",
    "biome.surrogate.acid_flats": "Acid Flats",
    "biome.surrogate.salt_pans": "Salt Pans",
    "biome.surrogate.dead_grove": "Dead Grove",
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
    "tooltip.surrogate.scanner.use": "Use to read the air where you stand",
    "tooltip.surrogate.scanner.block": "Sneak-use on a block to check if it is airtight",

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
    "message.surrogate.survivor.blueprint": "%s sends a file: CRAWLER BLUEPRINT. It is in your pack.",
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

lang_path = os.path.join(ASSETS, "lang", "en_us.json")
with open(lang_path, encoding="utf-8") as f:
    lang = json.load(f)
lang.update(LANG)
with open(lang_path, "w", encoding="utf-8") as f:
    json.dump(lang, f, indent=2, ensure_ascii=False)
    f.write("\n")
print("updated lang/en_us.json")
print("done")
