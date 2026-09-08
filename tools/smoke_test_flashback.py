#!/usr/bin/env python3
"""Headless check of the flashback: the night before the contract, and the suitcase that survives it.

Boots the dev server on the Toxic Wastes preset (ship week and prologue off), then walks the whole of one
night through with the fake player, at home:

  * The dimension exists and the scene puts the player in it, in a corridor with three named doors.
  * Walking through the HOME door is an answer: the house behind it gets built, with a couch to be sat on.
  * A slice of pizza counts as eating; the figure in the kitchen asks two questions; both answers are kept.
  * The dog can be called into its carrier, the carrier picked up with the dog still in it, and packed.
  * The evening moves on: the case is upstairs, the porch opens, and walking out wakes the player up.
  * The player's own inventory is put away for the dream and given back on waking, and what was packed
    exists in exactly one place afterwards: a case beside the bed, not the dream.

That last pair is the point of the test. Everything else is scenery; those two are an inventory.

    py -3.13 tools/smoke_test_flashback.py
"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, Rcon, prepare_run_dir  # noqa: E402
from terrain_scan import reap_server, server_pids, wait_for_log  # noqa: E402

OW = "execute in minecraft:overworld run "
DREAM = "execute in surrogate:dream run "
AS = "execute as Steve at Steve run "

# DreamDimension.ORIGIN, and the offsets DreamBuilder lays out from it. Block centres are arithmetic, never
# f"{x}.5": the centre of block -4 is -3.5, and getting that wrong stands the player next door.
OX, OY, OZ = 0, 100, 0
DOOR_Z = OZ - 5
THRESHOLD_Z = OZ - 7
DOOR_X = {"home": -4, "work": 0, "bar": 4}
# The house: DreamBuilder.SITE_HOME and the spots inside it.
HOME = (OX, OY, OZ - 70)


def home(dx, dy, dz):
    return (HOME[0] + dx, HOME[1] + dy, HOME[2] + dz)


COUCH = home(-5, 1, 3)
PIZZA = home(-6, 2, 4)
# A clear bit of living-room floor (the rug between the table and the set) for a second player to stand on.
RUG = home(-7, 1, 3)
TV = home(-8, 2, 3)
CARRIER = home(-3, 1, -3)
SUITCASE = home(-6, 6, -4)
BEDROOM = (home(-5, 5, -3)[0] + 0.5, home(-5, 5, -3)[1], home(-5, 5, -3)[2] + 0.5)
PORCH_WALL = home(0, 1, 8)
EXIT = (home(0, 1, 9)[0] + 0.5, home(0, 1, 9)[1], home(0, 1, 9)[2] + 0.5)

# What the test packs, and what it leaves in the player's own pockets to get back.
PACKED = "minecraft:diamond"
CARRIED = "minecraft:golden_apple"

failures = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail else ""))
    if not ok:
        failures.append(name)


def habitat(rcon):
    """Where the pod is, read off the errand list, which names it as the housewarming's own address."""
    out = rcon.cmd(AS + "surrogate errand list")
    m = re.search(r"housewarming\s+\S+\s+\S+\s+(-?\d+), *(-?\d+), *(-?\d+)", out)
    return tuple(int(v) for v in m.groups()) if m else None


def dimension_of(rcon):
    out = rcon.cmd(f"{OW}data get entity Steve Dimension")
    m = re.search(r'"([a-z_]+:[a-z_/]+)"', out)
    return m.group(1) if m else out.strip()[:60]


def status(rcon):
    """`/surrogate flashback status`, parsed. The scene's own account of itself."""
    out = rcon.cmd(AS + "surrogate flashback status")

    def grab(pattern, default="?", conv=str):
        m = re.search(pattern, out)
        return conv(m.group(1)) if m else default

    stashed = grab(r"inventory stashed: (\w+)", None)
    return {
        "raw": out,
        "played": "played" in out.split("\n")[0] if out else False,
        "running": "(running)" in out,
        "where": grab(r"where: (\S+)"),
        "why": grab(r"why: (\S+)"),
        "packed": grab(r"packed: (\d+)", -1, int),
        "stashed": (stashed == "true") if stashed else None,
        "visited": grab(r"visited: (\S+)"),
        "answers": grab(r"answers: (\d+)", -1, int),
        "asking": grab(r"asking: (\S+)"),
        "label": grab(r"label: (\S+)"),
    }


def wait_for(rcon, predicate, seconds, step=2):
    """Poll until the scene has caught up. Every beat here waits on a fade or a line."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(step)
    return False


def block_is(rcon, pos, block):
    out = rcon.cmd(f"{DREAM}execute if block {pos[0]} {pos[1]} {pos[2]} {block}")
    return "Test passed" in out, out[:110]


def check_trigger(rcon, pod):
    """The way a player actually gets here: go to bed, early on, on the planet.

    Worth its own pass because the gate is a day count that only the prologue used to set, and these worlds
    are built with the prologue off - so this is the check that would have caught the flashback never firing
    for anybody who played the game the normal way.
    """
    hx, hy, hz = pod
    bunk = (hx + 3, hy + 1, hz + 3)
    rcon.cmd(f"{OW}time set midnight")
    rcon.cmd(f"{OW}tp Steve {bunk[0] + 0.5} {bunk[1] + 1} {bunk[2] + 0.5}")
    time.sleep(2)
    rcon.cmd(f"{OW}player Steve look at {bunk[0] + 0.5} {bunk[1] + 0.2} {bunk[2] + 0.5}")
    time.sleep(1)
    rcon.cmd(f"{OW}player Steve use once")
    dreamt = wait_for(rcon, lambda: dimension_of(rcon) == "surrogate:dream", 40)
    check("sleeping in the first days starts it by itself", dreamt, dimension_of(rcon))
    # Put it back the way it was, so the long pass below starts from nothing.
    rcon.cmd(AS + "surrogate flashback skip")
    time.sleep(4)
    rcon.cmd(AS + "surrogate flashback reset")
    rcon.cmd(f"{OW}time set day")
    rcon.cmd(f"{OW}tp Steve {hx + 0.5} {hy + 1} {hz + 0.5}")
    time.sleep(2)
    st = status(rcon)
    check("and it can be put back for a second look", not st["played"] and st["stashed"] is not True, st["raw"][:120])


def check_flashback(rcon):
    # Something of the player's own, so waking up can be checked for having given it back. Read with
    # `execute if data entity <name> <path>`: a bare player name takes no selector arguments, so the
    # `Steve[nbt={...}]` form every instinct reaches for is a parse error that reads as a failing check.
    rcon.cmd(f"{OW}item replace entity Steve weapon.mainhand with {CARRIED}")
    before = rcon.cmd(f'{OW}execute if data entity Steve Inventory[{{id:"{CARRIED}"}}]')
    check("the player starts with something of their own", "Test passed" in before, before[:90])

    rcon.cmd(AS + "surrogate flashback start")
    went_under = wait_for(rcon, lambda: dimension_of(rcon) == "surrogate:dream", 40)
    check("the dream is a dimension and the player goes to it", went_under, dimension_of(rcon))
    if not went_under:
        return

    st = status(rcon)
    check("the player's own inventory is put away", st["stashed"] is True, st["raw"][:120])
    empty = rcon.cmd(f'{OW}execute if data entity Steve Inventory[{{id:"{CARRIED}"}}]')
    check("and is not in their pockets in the dream", "Test passed" not in empty, empty[:90])

    # The corridor: a floor under the player, a door standing open at the end of it with its name over it.
    ok, detail = block_is(rcon, (OX, OY, OZ + 5), "minecraft:polished_deepslate")
    check("the corridor is built", ok, detail)
    ok, detail = block_is(rcon, (DOOR_X["home"], OY + 1, DOOR_Z), "minecraft:oak_door")
    check("the door home is a door, and open", ok, detail)
    ok, detail = block_is(rcon, (DOOR_X["home"], OY + 3, DOOR_Z + 1), "minecraft:oak_wall_sign")
    check("with its name on a sign over it", ok, detail)

    # Walk through it. Which place gets built is the answer.
    rcon.cmd(f"{DREAM}tp Steve {DOOR_X['home'] + 0.5} {OY + 1} {THRESHOLD_Z + 0.5}")
    chose = wait_for(rcon, lambda: status(rcon)["where"] == "home", 40)
    check("walking through a door answers the first question", chose, status(rcon)["where"])

    built = wait_for(rcon, lambda: block_is(rcon, home(0, 0, 0), "minecraft:oak_planks")[0], 60)
    check("the house behind it is built", built)
    ok, detail = block_is(rcon, COUCH, "surrogate:couch")
    check("with a couch in the living room", ok, detail)
    ok, detail = block_is(rcon, SUITCASE, "surrogate:suitcase[on_bed=true]")
    check("and the case upstairs, on the bed", ok, detail)
    ok, detail = block_is(rcon, TV, "surrogate:television[channel=news]")
    check("and the news on", ok, detail)
    seated = wait_for(rcon, lambda: "Test passed" in rcon.cmd(f"{OW}execute if data entity Steve RootVehicle"), 30)
    check("the player is sat on the couch", seated)
    dog = rcon.cmd(f"{DREAM}execute if entity @e[type=minecraft:wolf]")
    check("there is a dog in the house", "count: 1" in dog, dog[:90])

    # A slice of the pizza, eaten: the first thing the evening waits for. A Carpet fake player only lands its
    # first use reliably, and Steve has already used the bunk, so a fresh one does the eating: any player in
    # the dream counts. Spawned straight into the dream, on the rug, looking at the ceiling so that the use
    # lands on the slice rather than on whatever block is in front of him.
    rcon.cmd(f"player Bob spawn at {RUG[0] + 0.5} {RUG[1]} {RUG[2] + 0.5} facing 0 -70 in surrogate:dream")
    time.sleep(4)
    rcon.cmd(f"{OW}item replace entity Bob weapon.mainhand with surrogate:pizza_slice")
    rcon.cmd(f"{DREAM}player Bob use continuous")
    ate = wait_for(rcon, lambda: "ate" in status(rcon)["raw"].split("done:")[-1], 20)
    rcon.cmd(f"{OW}player Bob stop")
    check("a slice of the pizza counts as eating", ate, status(rcon)["raw"][-160:])
    rcon.cmd("player Bob kill")

    # The props have to drop when broken with nothing in your hands, or the case can never be filled with
    # any of them. `loot mine` with no tool argument is exactly that question. Mined virtually, so the room
    # is left standing for the rest of the run.
    rcon.cmd(f"{OW}clear Steve surrogate:pizza_box")
    got = rcon.cmd(f"{DREAM}loot give Steve mine {PIZZA[0]} {PIZZA[1]} {PIZZA[2]}")
    held = rcon.cmd(f'{OW}execute if data entity Steve Inventory[{{id:"surrogate:pizza_box"}}]')
    check("a prop comes off in your hands", "Test passed" in held, (got + " | " + held)[:130])
    rcon.cmd(f"{OW}clear Steve surrogate:pizza_box")
    # And the shell does not: the floor is in #surrogate:dream_fixed and is the thing between the player and
    # two hundred blocks of nothing. Checked as a block rather than by breaking it, which the tag forbids.
    ok, detail = block_is(rcon, home(0, 0, 0), "minecraft:oak_planks")
    check("the floor is still the floor", ok, detail)

    # Somebody asks two questions. Answered through the command, which is the same entry point the button
    # uses: the screen itself is the one thing a headless client cannot click.
    for i, pick in enumerate((0, 1)):
        asked = wait_for(rcon, lambda: status(rcon)["asking"] not in ("none", "?"), 150, step=3)
        check(f"the figure asks question {i + 1}", asked, status(rcon)["asking"] + " " + status(rcon)["label"])
        if not asked:
            break
        taken = rcon.cmd(AS + f"surrogate flashback answer {pick}")
        check(f"question {i + 1} takes an answer", "Answered" in taken, taken[:90])
        time.sleep(2)
    st = status(rcon)
    check("and the answers are kept", st["answers"] >= 2, f"{st['answers']} answers: {st['raw'][-220:]}")
    check("the second answer is why they were there", st["why"] == "money", st["why"])

    # The dog goes in the carrier, the carrier comes off the floor with the dog still in it, and it goes in
    # the case. Nobody is going to say out loud that this is allowed. Another fresh fake player does the
    # clicking, for the same reason as the eating, and the dog is brought to heel first: it wanders, and the
    # carrier only calls a dog within four blocks.
    # Carl stands two blocks north of the carrier facing south and a little down, which puts the crate under
    # his crosshair; the dog is sat down to the west, within call but out of his line of sight. A Carpet use
    # goes to whatever entity the ray crosses first, and the spot west of the crate is where the figure is
    # still standing at this point in the evening: a player put down there clicks the figure, silently.
    rcon.cmd(f"player Carl spawn at {CARRIER[0] + 0.5} {CARRIER[1]} {CARRIER[2] - 1.5} facing 0 28 in surrogate:dream")
    time.sleep(4)
    rcon.cmd(f"{DREAM}tp @e[type=minecraft:wolf] {CARRIER[0] - 3.5} {CARRIER[1]} {CARRIER[2] + 0.5}")
    rcon.cmd(f"{DREAM}data merge entity @e[type=minecraft:wolf,limit=1] {{Sitting:1b}}")
    time.sleep(1)
    rcon.cmd(f"{DREAM}player Carl look at {CARRIER[0] + 0.5} {CARRIER[1] + 0.4} {CARRIER[2] + 0.5}")
    time.sleep(1)
    print(rcon.cmd(AS + "surrogate flashback aim Carl").strip())
    rcon.cmd(f"{DREAM}player Carl use once")
    captured = wait_for(rcon, lambda: block_is(rcon, CARRIER, "surrogate:dog_carrier[occupied=true]")[0], 20)
    if not captured:
        # A control: can this fake player click anything at all? A lever where the crate is, then the crate back.
        rcon.cmd(f"{DREAM}setblock {CARRIER[0]} {CARRIER[1] + 1} {CARRIER[2]} minecraft:lever[face=floor,facing=west]")
        time.sleep(1)
        rcon.cmd(f"{DREAM}player Carl look at {CARRIER[0] + 0.5} {CARRIER[1] + 1.2} {CARRIER[2] + 0.5}")
        time.sleep(1)
        print(rcon.cmd(AS + "surrogate flashback aim Carl").strip())
        rcon.cmd(f"{DREAM}player Carl use once")
        time.sleep(3)
        print("control lever:", rcon.cmd(f"{DREAM}execute if block {CARRIER[0]} {CARRIER[1] + 1} {CARRIER[2]} minecraft:lever[powered=true]").strip())
        rcon.cmd(f"{DREAM}setblock {CARRIER[0]} {CARRIER[1] + 1} {CARRIER[2]} minecraft:air")
    rcon.cmd("player Carl kill")
    check("the dog can be called into its carrier", captured, block_is(rcon, CARRIER, "surrogate:dog_carrier")[1])
    gone = rcon.cmd(f"{DREAM}execute if entity @e[type=minecraft:wolf]")
    check("and is then nowhere else", "Test failed" in gone or "count: 0" in gone, gone[:90])
    rcon.cmd(f"{OW}clear Steve surrogate:dog_carrier")
    rcon.cmd(f"{DREAM}loot give Steve mine {CARRIER[0]} {CARRIER[1]} {CARRIER[2]}")
    time.sleep(1)
    carried = rcon.cmd(f'{OW}execute if data entity Steve Inventory[{{id:"surrogate:dog_carrier",components:{{"surrogate:pet_data":{{id:"minecraft:wolf"}}}}}}]')
    check("the carrier comes up with the dog inside it", "Test passed" in carried, carried[:110])

    # The evening moves on. Once it has, the case is the thing, and it is upstairs.
    later = wait_for(rcon, lambda: status(rcon)["label"] in ("later", "pack", "wake"), 240, step=4)
    check("the evening moves on without them", later, status(rcon)["label"])
    rcon.cmd(f"{DREAM}tp Steve {BEDROOM[0]} {BEDROOM[1]} {BEDROOM[2]}")
    packing = wait_for(rcon, lambda: status(rcon)["label"] in ("pack", "wake"), 120, step=3)
    check("going up gets to the packing", packing, status(rcon)["label"])
    opened = wait_for(rcon, lambda: block_is(rcon, PORCH_WALL, "minecraft:air")[0], 60)
    check("the way out opens once it is time to go", opened)

    # Pack something that could not possibly have come from anywhere else, and the dog.
    rcon.cmd(f"{DREAM}item replace block {SUITCASE[0]} {SUITCASE[1]} {SUITCASE[2]} container.9 with {PACKED}")
    rcon.cmd(f"{DREAM}item replace block {SUITCASE[0]} {SUITCASE[1]} {SUITCASE[2]} container.10 from entity Steve weapon.mainhand")
    rcon.cmd(f"{OW}clear Steve surrogate:dog_carrier")
    time.sleep(1)
    packed_in = rcon.cmd(f"{DREAM}execute if block {SUITCASE[0]} {SUITCASE[1]} {SUITCASE[2]} "
                         f"surrogate:suitcase{{Items:[{{id:\"{PACKED}\"}}]}}")
    check("something can be packed into the case", "Test passed" in packed_in, packed_in[:110])

    # Out through the front door, which is the end of the night.
    rcon.cmd(f"{DREAM}tp Steve {EXIT[0]} {EXIT[1]} {EXIT[2]}")
    woke = wait_for(rcon, lambda: dimension_of(rcon) == "minecraft:overworld", 60)
    check("walking out wakes the player up", woke, dimension_of(rcon))
    if not woke:
        return

    st = status(rcon)
    # Not "done": the arc finishes when all three doors have been used, and this was one of them. What has to
    # be true after one night is that the night was banked and there is still somewhere left to go.
    check("the night is banked and the arc is not over", not st["played"], st["raw"][:140])
    check("the stash is handed back and cleared", st["stashed"] is False, st["raw"][:140])
    back = rcon.cmd(f'{OW}execute if data entity Steve Inventory[{{id:"{CARRIED}"}}]')
    check("the player has their own things again", "Test passed" in back, back[:90])
    check("the case is counted", st["packed"] >= 2, str(st["packed"]))

    # And the packed things came home in the same case beside the bed, rather than in their pockets or nowhere.
    pos = rcon.cmd(f"{OW}data get entity Steve Pos")
    m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", pos)
    if not m:
        check("the case comes home", False, "no player position")
        return
    px, py, pz = (int(float(v) // 1) for v in m.groups())
    found = None
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in (0, 1, -1):
                probe = rcon.cmd(f"{OW}execute if block {px + dx} {py + dy} {pz + dz} "
                                 f"surrogate:suitcase{{Items:[{{id:\"{PACKED}\"}}]}}")
                if "Test passed" in probe:
                    found = (px + dx, py + dy, pz + dz)
                    break
    check("what was packed comes home in the case", found is not None,
          f"at {found}" if found else f"no case holding a {PACKED} near {px}, {py}, {pz}")
    if found:
        dog_home = rcon.cmd(f"{OW}execute if block {found[0]} {found[1]} {found[2]} "
                            f"surrogate:suitcase{{Items:[{{id:\"surrogate:dog_carrier\",components:{{\"surrogate:pet_data\":{{id:\"minecraft:wolf\"}}}}}}]}}")
        check("and so does the dog", "Test passed" in dog_home, dog_home[:110])

    # Nothing may be in two places at once: the dream's own copy has to be gone.
    left = rcon.cmd(f"{DREAM}execute if block {SUITCASE[0]} {SUITCASE[1]} {SUITCASE[2]} "
                    f"surrogate:suitcase{{Items:[{{id:\"{PACKED}\"}}]}}")
    # "not loaded" would pass this for the wrong reason, which is why the chunks are held open below.
    check("and is not still in the dream", "Test failed" in left, left[:110])
    check("the place is marked visited", st["visited"] == "home", st["visited"])


def check_second_night(rcon):
    """The dream comes back, and the door already used is bricked up.

    This is the whole reason it repeats: three rooms and one night would be two thirds of a scene nobody ever
    reads. What matters here is that the second dream is not simply the first one again.
    """
    rcon.cmd(AS + "surrogate flashback start")
    if not wait_for(rcon, lambda: dimension_of(rcon) == "surrogate:dream", 40):
        check("a second night happens", False, dimension_of(rcon))
        return
    check("a second night happens", True)
    ok, detail = block_is(rcon, (DOOR_X["home"], OY + 1, DOOR_Z), "minecraft:polished_blackstone_bricks")
    check("the door already used is bricked up", ok, detail)
    for name, door in (("work", "minecraft:spruce_door"), ("bar", "minecraft:dark_oak_door")):
        ok, detail = block_is(rcon, (DOOR_X[name], OY + 1, DOOR_Z), door)
        check(f"the {name} door is still open", ok, detail)
    rcon.cmd(AS + "surrogate flashback skip")
    wait_for(rcon, lambda: dimension_of(rcon) == "minecraft:overworld", 40)
    back = rcon.cmd(f'{OW}execute if data entity Steve Inventory[{{id:"{CARRIED}"}}]')
    check("a skipped night still hands the inventory back", "Test passed" in back, back[:90])


def main():
    stale = server_pids()
    if stale:
        print("a dev server is still running; refusing to start another:", stale)
        return 1
    prepare_run_dir({"transit": False, "prologue": False})
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    try:
        if wait_for_log(r"Done \(.*For help", 480, proc) is None:
            print("server never reported Done")
            return 1
        time.sleep(3)
        rcon = Rcon()
        rcon.sock.settimeout(300)
        rcon.cmd("player Steve spawn")
        time.sleep(8)
        rcon.cmd(f"{OW}gamemode survival Steve")
        rcon.cmd(f"{OW}gamerule doDaylightCycle false")
        rcon.cmd(f"{OW}time set day")
        rcon.cmd(f"{OW}difficulty peaceful")
        time.sleep(2)

        pod = habitat(rcon)
        if pod is None:
            print("no starter habitat in the log; nothing to sleep in")
            return 1
        # The dream's chunks are nobody's spawn chunks and unload the moment the player leaves it, and an
        # unloaded position answers every question with "not loaded" - which reads as a pass.
        rcon.cmd(f"{DREAM}forceload add {OX - 8} {OZ - 12} {OX + 8} {OZ + 12}")
        rcon.cmd(f"{DREAM}forceload add {HOME[0] - 12} {HOME[2] - 10} {HOME[0] + 6} {HOME[2] + 12}")
        time.sleep(2)

        print("---- the trigger ----")
        check_trigger(rcon, pod)
        print("---- flashback ----")
        check_flashback(rcon)
        print("---- the second night ----")
        check_second_night(rcon)
        rcon.cmd(f"{DREAM}forceload remove all")

        rcon.cmd("player Steve kill")
        time.sleep(2)
        rcon.cmd("stop")
    finally:
        clean = reap_server(120)
        for _ in range(20):
            if proc.poll() is not None:
                break
            time.sleep(3)
        if proc.poll() is None:
            proc.kill()
        log.close()
        if not clean:
            failures.append("server hung in shutdown")
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        issues = [l for l in text.splitlines()
                  if re.search(r"ERROR|Exception|Failed to|error in", l)
                  and "No data fixer registered for" not in l]
        if issues:
            print("---- log lines of interest ----")
            for l in issues[:30]:
                print(l.strip()[:300])
            failures.append("exceptions in the log")

    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
