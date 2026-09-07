#!/usr/bin/env python3
"""Static audit of the mod's own assets: every reference has to point at a file that exists.

Minecraft is quiet about most of this. A blockstate naming a model that is not there logs one line among
thousands; a model naming a texture that is not there does not log at all and simply renders as the missing
texture, which on a dark block in a dark room is easy to miss for a long time. So this walks the whole
resource tree and resolves every edge:

  * blockstates -> models
  * models -> their parent, and every texture they name
  * lang -> every key the Java source builds by hand as a literal prefix

and, in the other direction, reports textures nothing references, which are usually a rename that only got
done on one side.

    py -3.13 tools/check_assets.py

Exits non-zero if anything is dangling. Costs about a second and needs no server.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = "surrogate"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MOD)
DATA = os.path.join(ROOT, "src", "main", "resources", "data", MOD)

problems = []


def note(kind, where, what):
    problems.append(f"{kind}: {where} -> {what}")


def read(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def rel(path):
    return os.path.relpath(path, ROOT).replace("\\", "/")


def split_id(ident):
    """"surrogate:block/foo" -> ("surrogate", "block/foo"); a bare path means vanilla."""
    if ":" in ident:
        namespace, path = ident.split(":", 1)
        return namespace, path
    return "minecraft", ident


def model_path(ident):
    namespace, path = split_id(ident)
    if namespace != MOD:
        return None
    return os.path.join(ASSETS, "models", *path.split("/")) + ".json"


def texture_path(ident):
    namespace, path = split_id(ident)
    if namespace != MOD:
        return None
    return os.path.join(ASSETS, "textures", *path.split("/")) + ".png"


def walk(folder, suffix=".json"):
    for base, _, files in os.walk(folder):
        for name in files:
            if name.endswith(suffix):
                yield os.path.join(base, name)


def check_blockstates(used_models):
    folder = os.path.join(ASSETS, "blockstates")
    if not os.path.isdir(folder):
        return
    for path in walk(folder):
        try:
            state = read(path)
        except json.JSONDecodeError as e:
            note("bad json", rel(path), str(e))
            continue
        entries = []
        if "variants" in state:
            for value in state["variants"].values():
                entries += value if isinstance(value, list) else [value]
        for part in state.get("multipart", []):
            value = part.get("apply", {})
            entries += value if isinstance(value, list) else [value]
        for entry in entries:
            ident = entry.get("model")
            if not ident:
                continue
            used_models.add(ident)
            target = model_path(ident)
            if target and not os.path.isfile(target):
                note("missing model", rel(path), ident)


def check_models(used_models, used_textures):
    folder = os.path.join(ASSETS, "models")
    if not os.path.isdir(folder):
        return
    for path in walk(folder):
        try:
            model = read(path)
        except json.JSONDecodeError as e:
            note("bad json", rel(path), str(e))
            continue
        parent = model.get("parent")
        if parent:
            used_models.add(parent)
            target = model_path(parent)
            if target and not os.path.isfile(target):
                note("missing parent", rel(path), parent)
        for key, ident in (model.get("textures") or {}).items():
            # "#side" is a reference to another entry in the same model, resolved at load; only real
            # identifiers point at files.
            if not isinstance(ident, str) or ident.startswith("#"):
                continue
            used_textures.add(ident)
            target = texture_path(ident)
            if target and not os.path.isfile(target):
                note("missing texture", rel(path), ident)


def check_entity_textures(used_textures):
    """Entity textures are named in Java rather than in json, so they are found by reading the source."""
    src = os.path.join(ROOT, "src", "main", "java")
    pattern = re.compile(r'Surrogate\.id\("(textures/[^"]+)"\)')
    for base, _, files in os.walk(src):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            for match in pattern.finditer(text):
                ident = f"{MOD}:{match.group(1)}"
                used_textures.add(ident.replace("textures/", "").replace(".png", ""))
                target = os.path.join(ASSETS, *match.group(1).split("/"))
                if not os.path.isfile(target):
                    note("missing entity texture", rel(path), match.group(1))


def check_composed_entity_textures():
    """The entity textures whose names are built at run time from an enum key rather than written out.

    `SurvivorEntityRenderer` asks for "survivor_" plus the character's key, so a survivor with no skin is
    not a dangling reference anywhere a grep can see: it is a magenta cube the first time somebody stands in
    front of that particular person. Reyes and Novak shipped like that from the day they went into the
    roster until act five went looking for their faces. The enum is short and the rule is exact, so check it.
    """
    src = os.path.join(ROOT, "src", "main", "java", "dev", "psyda", "surrogate")
    survivors = os.path.join(src, "survivor", "Survivor.java")
    if os.path.isfile(survivors):
        with open(survivors, encoding="utf-8") as f:
            text = f.read()
        # Entries look like `OKAFOR("okafor", () -> ...`; the key is the first string of each.
        for key in re.findall(r'^\t[A-Z_]+\("([a-z_]+)"', text, re.MULTILINE):
            want = os.path.join("entity", "survivor_%s.png" % key)
            if not os.path.isfile(os.path.join(ASSETS, "textures", want)):
                note("missing entity texture", "survivor/Survivor.java", want.replace("\\", "/"))
    # The conference call names each face's sheet outright, so those can simply be read off.
    panels = os.path.join(src, "rescue", "CallPanel.java")
    if os.path.isfile(panels):
        with open(panels, encoding="utf-8") as f:
            text = f.read()
        for skin in re.findall(r'^\t[A-Z_]+\("[a-z_]+", "([a-z_]+)"\)', text, re.MULTILINE):
            want = os.path.join("entity", "%s.png" % skin)
            if not os.path.isfile(os.path.join(ASSETS, "textures", want)):
                note("missing entity texture", "rescue/CallPanel.java", want.replace("\\", "/"))


def check_lang():
    """Every translation key the Java source names as a whole literal has to exist.

    Keys built at runtime from a prefix and a variable are skipped: they are the ones a grep cannot resolve,
    and they are checked by the smoke tests actually playing the scenes that use them.
    """
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    if not os.path.isfile(lang_path):
        note("missing lang", "assets", "lang/en_us.json")
        return set()
    lang = read(lang_path)
    src = os.path.join(ROOT, "src", "main", "java")
    # Only whole keys. A literal followed by " + " is a prefix the caller completes at run time —
    # "message.surrogate.acid.crawler_" plus a stage, "book.surrogate.log.page" plus a number — and there is
    # no way to resolve one from here. Those are covered by the smoke tests playing the scenes that build
    # them. The lookahead is what tells the two apart.
    pattern = re.compile(r'Text\.translatable\("([a-z][a-z0-9_.]*\.surrogate\.[a-z0-9_.]+)"(?!\s*\+)')
    seen = set()
    for base, _, files in os.walk(src):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            for match in pattern.finditer(text):
                key = match.group(1)
                seen.add(key)
                if key not in lang:
                    note("missing lang key", rel(path), key)
    return seen


def check_every_key_literal():
    """The same idea as check_lang, but for keys the code never hands to Text.translatable.

    The client builds several pages by handing plain strings to I18n.translate -- the terminal's survey and
    mission board pages, the call panel's labels -- and the mission board goes further and passes a key
    around as data, choosing between nine of them. None of that is visible to a search for translatable(),
    and a key that is missing renders as itself on a page nobody is reading by eye.

    A literal that is a prefix of keys that do exist is a stem the code completes at run time, so it is not
    a missing key and is passed over. That is the same rule check_lang uses, applied by shape rather than by
    the shape of the call.
    """
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    if not os.path.isfile(lang_path):
        return
    lang = read(lang_path)
    src = os.path.join(ROOT, "src", "main", "java")
    pattern = re.compile(r'"((?:terminal|board|cinematic|message|item|tooltip|survivor|crew|screen|hud|title'
                         r'|key|subtitles|block|entity|specimen|errand)\.surrogate\.[a-z0-9_.]+)"')
    found = []
    for base, _, files in os.walk(src):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(base, name)
            with open(path, encoding="utf-8") as f:
                text = f.read()
            for match in pattern.finditer(text):
                key = match.group(1)
                if key not in lang:
                    found.append((path, key))
    stems = {key for _, key in found if any(full.startswith(key) for full in lang)}
    for path, key in found:
        if key not in stems:
            note("missing lang key", rel(path), key)


def check_loot_and_recipes():
    """Every block that drops itself needs a loot table, or it drops nothing and nobody notices until they
    break one. Read off the blockstates, which is the list of blocks that actually exist."""
    states = os.path.join(ASSETS, "blockstates")
    loot = os.path.join(DATA, "loot_table", "blocks")
    if not os.path.isdir(states) or not os.path.isdir(loot):
        return
    have = {os.path.splitext(f)[0] for f in os.listdir(loot)}
    # Blocks that deliberately drop nothing: doors and halves that drop from their other half, the crawler's
    # own fittings, which exist only inside a vehicle nobody mines, and the stake, which is consumed.
    EXEMPT = {"airlock_door", "dock_door", "crawler_hatch", "crawler_bay", "chassis_port", "poster",
              "survey_stake", "breached_plating", "deck_grating", "handrail", "pipe", "conduit",
              "crawler_helm", "crawler_dock_console"}
    for name in sorted(os.path.splitext(f)[0] for f in os.listdir(states)):
        if name in have or name in EXEMPT:
            continue
        note("no loot table", "blockstates/" + name, "data/loot_table/blocks/" + name + ".json")


def check_orphan_models(used_models):
    """Models nothing points at.

    A block model is reached from a blockstate or from another model's parent; an item model is reached by
    having the same name as a registered item. Anything else is a rename or a deletion that only happened on
    one side, and Minecraft says so once, in one line, during the resource reload:
    "Invalid path in mod resource-pack surrogate: surrogate:models/block/OLD_chem_drum.json, ignoring".
    """
    folder = os.path.join(ASSETS, "models")
    if not os.path.isdir(folder):
        return
    items = set()
    for name in ("ModItems.java", "ModBlocks.java"):
        path = os.path.join(ROOT, "src", "main", "java", "dev", "psyda", "surrogate", "registry", name)
        if not os.path.isfile(path):
            continue
        with open(path, encoding="utf-8") as f:
            items.update(re.findall(r'register\("([a-z0-9_]+)"', f.read()))
    for path in walk(folder):
        ident = MOD + ":" + os.path.relpath(path, folder).replace("\\", "/")[:-5]
        if ident in used_models:
            continue
        # An item model is named after its item rather than referenced by anything.
        if ident.startswith(MOD + ":item/") and ident[len(MOD) + 6:] in items:
            continue
        note("orphan model", rel(path), "nothing references it and no item is named after it")


def check_orphan_textures(used_textures):
    """Block and item textures nothing points at. Usually a rename that only happened on one side.

    Only block/ and item/, because those are the two trees where every reference lives in a model file and
    an orphan therefore means something. Everywhere else the name is composed in Java — RobotPaint builds
    "robot_" plus a person, Crew builds "crew_" plus a key, the effect icon is found by registry id — and an
    audit that cannot resolve the reference has no business calling the file dead.
    """
    folder = os.path.join(ASSETS, "textures")
    if not os.path.isdir(folder):
        return
    for path in walk(folder, ".png"):
        ident = os.path.relpath(path, folder).replace("\\", "/")[:-4]
        if not ident.startswith(("block/", "item/")):
            continue
        if f"{MOD}:{ident}" in used_textures:
            continue
        note("orphan texture", rel(path), "nothing references it")


def main():
    used_models = set()
    used_textures = set()
    check_blockstates(used_models)
    check_models(used_models, used_textures)
    check_entity_textures(used_textures)
    check_composed_entity_textures()
    check_lang()
    check_every_key_literal()
    check_loot_and_recipes()
    check_orphan_models(used_models)
    check_orphan_textures(used_textures)

    if not problems:
        print("RESULT: PASS (every asset reference resolves)")
        return 0
    by_kind = {}
    for line in problems:
        by_kind.setdefault(line.split(":", 1)[0], []).append(line)
    for kind in sorted(by_kind):
        print(f"---- {kind} ({len(by_kind[kind])}) ----")
        for line in by_kind[kind][:40]:
            print("  " + line)
        if len(by_kind[kind]) > 40:
            print(f"  ... and {len(by_kind[kind]) - 40} more")
    print(f"RESULT: FAIL ({len(problems)} dangling references)")
    return 2


if __name__ == "__main__":
    sys.exit(main())
