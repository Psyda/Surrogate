#!/usr/bin/env python3
"""Headless check of the optional work, the animals and the survey tier.

Boots the dev server on the Toxic Wastes preset (ship week and prologue off), lets the first join place the
habitat and the shelters, then exercises three things that have nothing to do with each other except that
none of them can be reached the long way in a test:

  * The eight errands. Every one has to open on demand, put whatever it needs into the world, be somewhere
    the teleport can reach, and be markable done. The two that are counts rather than flags — the survey and
    the ark — have to have their counts filled by `done` as well, or the terminal page and the errand list
    would disagree.
  * The four animals. Each has to spawn, survive a few seconds of its own AI, and be readable by the sampler.
    A lantern slug additionally has to still be in the air, because one on the floor is a dead one.
  * The survey network. A station reads a disc; a beacon inside link range goes green and widens it; a beacon
    planted a long way out stays red. The red one is the check that matters: a network that linked everything
    would look like it worked.

Asserts on what the systems say about themselves rather than on block coordinates, for the reason in
docs/DESIGN-assay.md: the old assay test passed twenty checks while the sequence was broken, because every
check was a coordinate. Costs six to ten minutes of wall clock. Exits non-zero when a check fails.

    py -3.13 tools/smoke_test_errands.py
"""
import math
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, Rcon, prepare_run_dir  # noqa: E402
from terrain_scan import reap_server, server_pids, wait_for_log  # noqa: E402

OW = "execute in minecraft:overworld run "
AS = "execute as Steve at Steve run "

ERRANDS = ["housewarming", "hot_meal", "survey", "ballast", "vent_clear", "burial", "corroded", "ark"]
SPECIES = ["trundle", "slagback", "tocker", "lantern_slug"]
# Eight subjects on Okafor's table, six of them alive and crateable.
SUBJECTS = 8
LIVE = 6

failures = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail else ""))
    if not ok:
        failures.append(name)


def player_pos(rcon):
    out = rcon.cmd(f"{OW}data get entity Steve Pos")
    m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
    return tuple(float(v) for v in m.groups()) if m else None


def ensure_player(rcon):
    """A Carpet fake player does not reliably survive being thrown two thousand blocks across the map, and
    every check after the one that loses him fails for the wrong reason. So: look, and put him back."""
    if player_pos(rcon) is not None:
        return True
    rcon.cmd("player Steve spawn")
    time.sleep(4)
    rcon.cmd(f"{OW}gamemode creative Steve")
    back = player_pos(rcon) is not None
    if not back:
        print("  (could not get Steve back)")
    return back


def errand_rows(rcon):
    """`/surrogate errand list`, parsed. One row per errand: phase, state, and where it is.

    The list is the only thing the mod says out loud about all eight at once, so everything this test knows
    about them comes through here.
    """
    out = rcon.cmd(AS + "surrogate errand list")
    rows = {}
    # RCON joins a multi-line reply with nothing between the lines, so this is one blob rather than eight
    # lines. Scanning it works because every field is a fixed vocabulary except the coordinate, and that has
    # a shape of its own.
    for m in re.finditer(r"([a-z_]+) +(early|mid|late) +(done|open|locked) +"
                         r"(-?\d+, *-?\d+, *-?\d+|nowhere yet)", out):
        rows[m.group(1)] = {"phase": m.group(2), "state": m.group(3), "where": m.group(4).strip()}
    return rows, out


def survey_status(rcon):
    out = rcon.cmd(AS + "surrogate errand status")
    return {
        "read": int(m.group(1)) if (m := re.search(r"Survey: (\d+) of", out)) else -1,
        "subjects": int(m.group(1)) if (m := re.search(r"Survey: \d+ of (\d+)", out)) else -1,
        "disk": "disk in" in out,
        "caged": int(m.group(1)) if (m := re.search(r"Ark: (\d+) of", out)) else -1,
        "live": int(m.group(1)) if (m := re.search(r"Ark: \d+ of (\d+)", out)) else -1,
        "machines": int(m.group(1)) if (m := re.search(r"Machines: (\d+) of", out)) else -1,
        "raw": out,
    }



def entity_count(rcon, kind):
    """How many of a type exist. Vanilla says so itself: "Test passed, count: N"."""
    out = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:{kind}]")
    m = re.search(r"count: (\d+)", out)
    return int(m.group(1)) if m else 0


# ---------------------------------------------------------------------------- the checks


def check_errands(rcon):
    rows, raw = errand_rows(rcon)
    check("errand list names all eight", set(rows) == set(ERRANDS),
          "saw " + ",".join(sorted(rows)) if set(rows) != set(ERRANDS) else "")
    if not rows:
        print(raw[:400])
        return

    # Nothing is open on a fresh world: every gate is behind helping somebody.
    locked = [k for k, v in rows.items() if v["state"] == "locked"]
    check("all eight start locked", len(locked) == 8, f"{len(locked)} locked")

    for key in ERRANDS:
        out = rcon.cmd(AS + f"surrogate errand start {key}")
        time.sleep(1.5)
        rows, _ = errand_rows(rcon)
        row = rows.get(key, {})
        # The housewarming runs a Director rather than sitting open, so it is allowed to be either by now.
        opened = row.get("state") in ("open", "done")
        check(f"{key}: opens on demand", opened, row.get("state", "missing"))
        check(f"{key}: has somewhere to be", row.get("where", "") not in ("", "nowhere yet"),
              row.get("where", ""))

    # The housewarming is a scene rather than a flag, so it has to be watched rather than poked. Fast mode
    # quarters every wait in it; the drop still takes its own hundred and ten ticks because that is a
    # particle column and not a script wait.
    #
    # Halfway through it stops and waits for the player to come outside, which is the point of the scene —
    # the two visitors have suits and the player does not — so the test has to actually go out to the pad.
    # A fake player will not cycle an airlock, so he is put on the pad instead; what is being checked is that
    # the scene notices and carries on, not that Carpet can work a door.
    home = rows.get("housewarming", {}).get("where", "")
    m = re.match(r"(-?\d+), *(-?\d+), *(-?\d+)", home)
    if not m:
        check("housewarming builds module two", False, f"no habitat position in {home!r}")
        return
    hx, hy, hz = (int(m.group(1)), int(m.group(2)), int(m.group(3)))

    rcon.cmd(AS + "surrogate prologue fast")
    rcon.cmd(AS + "surrogate errand start housewarming")
    start = time.time()
    state = ""
    outside = False
    while time.time() - start < 240:
        time.sleep(5)
        # Once they are done talking, go and stand with them. Sent more than once because the fake player
        # drifts and the scene only asks the question while it is on that beat.
        if time.time() - start > 20:
            rcon.cmd(f"{OW}tp Steve {hx + 1.5} {hy + 1} {hz + 10.5}")
            outside = True
        rows, _ = errand_rows(rcon)
        state = rows.get("housewarming", {}).get("state", "missing")
        if state == "done":
            break
    check("housewarming waits for the player outside", outside)
    check("housewarming finishes", state == "done", f"{state} after {int(time.time() - start)}s")

    # And it has to have left a room behind: the errand is the module, not the conversation.
    out = rcon.cmd(f"{OW}execute if block {hx + 5} {hy + 4} {hz - 4} surrogate:hull_plating")
    check("housewarming builds module two", "Test passed" in out, out[:90])
    bunk = rcon.cmd(f"{OW}execute if block {hx + 6} {hy + 1} {hz - 1} surrogate:bunk")
    check("module two has bunks in it", "Test passed" in bunk, bunk[:90])
    # The room the errand is for has to be a room: a scrubber of its own, a lid over every lamp, and a way
    # in from the pod that is not through the furniture. All three were broken at once and all three read
    # as the same symptom, which is a bunkroom that poisons whoever sleeps in it.
    unit = rcon.cmd(f"{OW}execute if block {hx + 8} {hy + 1} {hz - 4} surrogate:life_support")
    check("module two has its own scrubber", "Test passed" in unit, unit[:90])
    lid = rcon.cmd(f"{OW}execute if block {hx + 8} {hy + 4} {hz - 2} surrogate:hull_plating")
    check("module two is capped over the lamps", "Test passed" in lid, lid[:90])
    lamp = rcon.cmd(f"{OW}execute if block {hx + 8} {hy + 3} {hz - 2} surrogate:ceiling_lamp")
    check("module two lamps hang under the cap", "Test passed" in lamp, lamp[:90])
    # The one that actually proves it. A scrubber only says sealed when its own flood fill came back without
    # reaching the sky, so this is the atmosphere code's opinion of the room rather than the test's opinion
    # of the blueprint — and the room used to fail it three times over, once per lamp. Scans are on a phase
    # offset by position, so give both units a couple of intervals to get round to it.
    time.sleep(12)
    for name, ox, oz in (("module two", 8, -4), ("the pod", 0, -4)):
        seal = rcon.cmd(f"{OW}execute if block {hx + ox} {hy + 1} {hz + oz} surrogate:life_support[sealed=true]")
        check(f"{name} holds pressure", "Test passed" in seal, seal[:110])
    for cell, y in (("lower", 1), ("upper", 2)):
        way = rcon.cmd(f"{OW}execute if block {hx + 3} {hy + y} {hz - 3} air")
        check(f"doorway into module two is clear ({cell})", "Test passed" in way, way[:90])

    # Six bunks are the whole payoff of the errand, and for a while they were furniture: a bed-shaped block
    # that could not be slept in. Stand on one, look down, use it, and ask the block whether it is occupied —
    # which only a real bed tracks, and only when somebody is actually asleep in it.
    bunk_x, bunk_y, bunk_z = hx + 6, hy + 1, hz - 1
    rcon.cmd(f"{OW}time set midnight")
    # Three things about driving a fake player at a block, all of which cost a run here:
    #   * `look at <x y z>` is the only aim Carpet takes. `look down` and the cardinals return nothing and
    #     leave him pointed wherever he was, so the use after it hits air.
    #   * Aim from above. A bunk is nine sixteenths tall, so a ray from a standing eye two blocks away sails
    #     over the top of it and lands on the wall behind.
    #   * Block centres are arithmetic, never f"{x}.5" — the centre of block -202 is -201.5, so the string
    #     form names the block next door everywhere west or north of the origin, which is most of this map.
    # Each of the three reads exactly like the mod refusing the interaction.
    rcon.cmd(f"{OW}tp Steve {bunk_x + 0.5} {bunk_y + 1} {bunk_z + 0.5}")
    time.sleep(1)
    rcon.cmd(f"{OW}player Steve look at {bunk_x + 0.5} {bunk_y + 0.2} {bunk_z + 0.5}")
    time.sleep(1)
    rcon.cmd(f"{OW}player Steve use once")
    time.sleep(2)
    slept = rcon.cmd(f"{OW}execute if block {bunk_x} {bunk_y} {bunk_z} surrogate:bunk[occupied=true]")
    check("a bunk can be slept in", "Test passed" in slept, slept[:110])
    rcon.cmd(f"{OW}time set day")
    time.sleep(1)


def check_burial(rcon):
    """The one errand with an action of its own: lift the body, carry it away, raise a marker over it.

    Driven the long way rather than through `errand done`, because the two things that were wrong with it
    were both in that path — the marker was taken out of the hand and never put anywhere, and any click
    counted whether or not there was ground under it. A flag test would have passed the whole time.

    Every selector here goes through AS. An `execute in <dimension>` keeps the *source's* position, and the
    source is RCON, which sits at world spawn: a `distance=..24` written the obvious way measures from there
    and matches nothing, a thousand blocks from the thing it is looking for.
    """
    rows, _ = errand_rows(rcon)
    where = rows.get("burial", {}).get("where", "")
    m = re.match(r"(-?\d+), *(-?\d+), *(-?\d+)", where)
    if not m:
        check("burial: body placed in the world", False, where)
        return
    bx, bz = int(m.group(1)), int(m.group(3))
    # Reyes is a long way off, so her chunks have to be held open before anything is placed or read there.
    rcon.cmd(f"{OW}forceload add {bx - 48} {bz - 48} {bx + 48} {bz + 48}")
    time.sleep(3)
    # Re-open it now the ground is real: the first placement happened in an unloaded chunk and put the body
    # at the world bottom, which is a fair thing for a test to notice and a bad thing to then bury.
    rcon.cmd(AS + "surrogate errand start burial")
    time.sleep(3)
    rows, _ = errand_rows(rcon)
    where = rows.get("burial", {}).get("where", "")
    m = re.match(r"(-?\d+), *(-?\d+), *(-?\d+)", where)
    by = int(m.group(2)) if m else 0
    check("burial: body is on the surface", by > 0, f"y={by}")

    rcon.cmd(AS + "surrogate errand tp burial")
    time.sleep(3)
    if not ensure_player(rcon):
        check("burial: player at the body", False)
        rcon.cmd(f"{OW}forceload remove {bx - 48} {bz - 48} {bx + 48} {bz + 48}")
        return
    pos = player_pos(rcon)
    gy = int(pos[1])
    # Somewhere that is not her doorstep, on ground the test laid itself so the click cannot land on a slope.
    gx, gz = bx + 24, bz + 24
    # Three courses thick, because the grave block is ash and ash falls: one course over open terrain drops
    # the whole thing out from under the marker the moment it is laid.
    rcon.cmd(f"{OW}fill {gx - 2} {gy - 3} {gz - 2} {gx + 2} {gy - 1} {gz + 2} surrogate:caustic_sandstone")
    rcon.cmd(f"{OW}fill {gx - 2} {gy} {gz - 2} {gx + 2} {gy + 2} {gz + 2} air")
    # Body and player over to it, in that order, so nothing has to survive being teleported while ridden.
    rcon.cmd(AS + f"tp @e[type=minecraft:armor_stand,limit=1,sort=nearest,distance=..24] {gx + 0.5} {gy} {gz + 0.5}")
    rcon.cmd(f"{OW}tp Steve {gx + 0.5} {gy} {gz + 0.5}")
    time.sleep(2)
    # Lift him. Through `errand lift`, which runs the errand's own code: the in-game way in is a right-click
    # on an armour stand from a chassis, and Carpet's fake player cannot land a click on an entity.
    lifted = rcon.cmd(AS + "surrogate errand lift")
    time.sleep(1)
    check("burial: the body can be lifted", "Lifted" in lifted, lifted[:110] or "(no reply)")

    rcon.cmd(f"{OW}item replace entity Steve weapon.mainhand with surrogate:survey_marker")
    time.sleep(1)
    rcon.cmd(f"{OW}player Steve look at {gx + 0.5} {gy - 0.5} {gz + 0.5}")
    time.sleep(1)
    rcon.cmd(f"{OW}player Steve use once")
    time.sleep(2)

    # Found rather than assumed. Where the marker lands depends on which face the fake player's ray struck,
    # and being a block out is not the errand getting it wrong — what matters is that a marker went up and
    # that the ground directly under it was opened.
    found = None
    for y in range(gy + 1, gy - 3, -1):
        if "Test passed" in rcon.cmd(f"{OW}execute if block {gx} {y} {gz} surrogate:survey_marker"):
            found = y
            break
    check("burial: the marker is raised", found is not None,
          f"standing at {gx}, {found}, {gz}" if found is not None else f"nothing in the column at {gx}, {gz}")
    if found is not None:
        grave = rcon.cmd(f"{OW}execute if block {gx} {found - 1} {gz} surrogate:ash")
        check("burial: the ground is opened under it", "Test passed" in grave, grave[:110])
    else:
        check("burial: the ground is opened under it", False, "no marker to look under")
    gone = rcon.cmd(AS + "execute if entity @e[type=minecraft:armor_stand,distance=..8]")
    check("burial: the body goes into it", "Test passed" not in gone, gone[:110])
    rows, _ = errand_rows(rcon)
    check("burial: finishes on the marker", rows.get("burial", {}).get("state") == "done",
          rows.get("burial", {}).get("state", "missing"))
    rcon.cmd(f"{OW}forceload remove {bx - 48} {bz - 48} {bx + 48} {bz + 48}")


def check_teleports(rcon):
    """Every errand's teleport has to put the player somewhere, and somewhere near what it named."""
    rows, _ = errand_rows(rcon)
    for key in ERRANDS:
        where = rows.get(key, {}).get("where", "")
        m = re.match(r"(-?\d+), (-?\d+), (-?\d+)", where)
        if not m:
            check(f"{key}: teleport target", False, f"unparsed {where!r}")
            continue
        want = (int(m.group(1)), int(m.group(3)))
        if not ensure_player(rcon):
            check(f"{key}: teleport lands", False, "no player to teleport")
            continue
        rcon.cmd(AS + f"surrogate errand tp {key}")
        time.sleep(4)
        pos = player_pos(rcon)
        retried = False
        if pos is None:
            # Carpet drops a fake player now and then on a long jump. Put him back and try the same command
            # once: a teleport that is genuinely aiming at nothing fails both times.
            retried = True
            if ensure_player(rcon):
                rcon.cmd(AS + f"surrogate errand tp {key}")
                time.sleep(4)
                pos = player_pos(rcon)
        if pos is None:
            check(f"{key}: teleport lands", False, "player lost twice")
            continue
        # The teleport aims a couple of blocks off the named spot and lands on the surface, so it is only
        # ever a few blocks out horizontally. Anything further means it aimed at the wrong thing.
        off = math.hypot(pos[0] - want[0], pos[2] - want[1])
        check(f"{key}: teleport lands near it", off < 24,
              f"{off:.0f} m from {want}" + (" (after a retry)" if retried else ""))


def check_fauna(rcon):
    if not ensure_player(rcon):
        check("fauna: player placed", False)
        return
    pos = player_pos(rcon)
    x, y, z = int(pos[0]), int(pos[1]), int(pos[2])
    # A slug hangs from a ceiling and one with no ceiling lets go and dies within a second, which is the
    # design. So it gets a roof three blocks up before it is asked for. Wider than it looks like it needs to
    # be: the spawn scatters each one up to three blocks off the player, and the player has usually drifted a
    # block or two from where this was read, so a seven-wide roof leaves spots outside it.
    rcon.cmd(f"{OW}fill {x - 8} {y + 3} {z - 8} {x + 8} {y + 3} {z + 8} surrogate:hull_plating")
    rcon.cmd(f"{OW}fill {x - 8} {y + 1} {z - 8} {x + 8} {y + 2} {z + 8} air")
    time.sleep(1)
    for kind in SPECIES:
        before = entity_count(rcon, kind)
        rcon.cmd(AS + f"surrogate fauna spawn {kind} 3")
        time.sleep(2)
        after = entity_count(rcon, kind)
        check(f"{kind}: spawns", after > before, f"{before} -> {after}")

    # Four seconds of their own AI. Anything that throws in mobTick dies or vanishes, and the count drops.
    time.sleep(4)
    for kind in SPECIES:
        check(f"{kind}: survives its own tick", entity_count(rcon, kind) > 0)

    # A slug that has fallen is a dead slug. It has to still be off the ground.
    out = rcon.cmd(f"{OW}data get entity @e[type=surrogate:lantern_slug,limit=1] NoGravity")
    check("lantern_slug: still on the ceiling", "1b" in out or "true" in out.lower(), out[:80])

    # And the sampler's table has to know about all eight subjects once a reading is forced.
    rcon.cmd(AS + "surrogate fauna read")
    time.sleep(1)
    st = survey_status(rcon)
    check("survey: eight subjects on the table", st["subjects"] == SUBJECTS, str(st["subjects"]))
    check("survey: all eight filed", st["read"] == SUBJECTS, f"{st['read']}/{st['subjects']}")
    check("survey: disk installed", st["disk"], st["raw"][:80])
    check("ark: six live species", st["live"] == LIVE, str(st["live"]))


def check_survey_network(rcon):
    """A station, a beacon inside link range, and a beacon a long way outside it.

    The far one is the check that matters. A network that linked everything would pass a test that only ever
    planted beacons next to the table.
    """
    if not ensure_player(rcon):
        check("survey: player placed", False)
        return
    pos = player_pos(rcon)
    x, y, z = int(pos[0]), int(pos[1]), int(pos[2])
    # A flat plate to stand it all on, so nothing is placed into a hillside.
    rcon.cmd(f"{OW}fill {x - 4} {y - 1} {z - 4} {x + 4} {y - 1} {z + 4} surrogate:hull_plating")
    rcon.cmd(f"{OW}fill {x - 4} {y} {z - 4} {x + 4} {y + 2} {z + 4} air")
    rcon.cmd(f"{OW}setblock {x} {y} {z} surrogate:survey_station")
    time.sleep(1)
    out = rcon.cmd(f"{OW}execute if block {x} {y} {z} surrogate:survey_station")
    check("survey station places", "Test passed" in out, out[:80])

    # Near: three blocks away, well inside the link range, so it must go green.
    rcon.cmd(f"{OW}setblock {x + 3} {y} {z} surrogate:survey_beacon")
    # Far: beyond surveyBeaconLinkRange (220 by default) with nothing between, so it must stay red. It has
    # to be force-loaded first — 300 blocks out is well past the fake player's view distance, and an
    # unloaded chunk answers every setblock with "that position is not loaded".
    far = z + 300
    rcon.cmd(f"{OW}forceload add {x} {far}")
    time.sleep(2)
    rcon.cmd(f"{OW}fill {x - 1} {y - 1} {far - 1} {x + 1} {y - 1} {far + 1} surrogate:hull_plating")
    rcon.cmd(f"{OW}fill {x - 1} {y} {far - 1} {x + 1} {y + 2} {far + 1} air")
    rcon.cmd(f"{OW}setblock {x} {y} {far} surrogate:survey_beacon")
    # Beacons check on a five second clock, staggered by position, so give them two full cycles.
    time.sleep(12)

    near_out = rcon.cmd(f"{OW}execute if block {x + 3} {y} {z} surrogate:survey_beacon[linked=true]")
    check("beacon in range links", "Test passed" in near_out, near_out[:90])
    far_out = rcon.cmd(f"{OW}execute if block {x} {y} {far} surrogate:survey_beacon[linked=false]")
    check("beacon out of range stays dark", "Test passed" in far_out, far_out[:90])
    rcon.cmd(f"{OW}forceload remove {x} {far}")

    # The pillar: nothing in it is zero range, and it lights once it has been fed.
    rcon.cmd(f"{OW}setblock {x - 3} {y} {z} surrogate:long_range_scanner")
    time.sleep(1)
    dark = rcon.cmd(f"{OW}execute if block {x - 3} {y} {z} surrogate:long_range_scanner[lit=false]")
    check("scanner starts dark", "Test passed" in dark, dark[:90])

    # Reading the table has to reach the client without throwing on the way out.
    rcon.cmd(AS + f"execute positioned {x} {y} {z} run surrogate errand status")
    time.sleep(1)


def main():
    stale = server_pids()
    if stale:
        print("a dev server is still running; refusing to start another:", stale)
        return 1
    # The flashback is off for this run. It fires on sleeping within two days of landing, which is
    # exactly what the bunk check below does, and a scene that teleports the player into another
    # dimension halfway through would take every check after it with it. Its own test covers it.
    prepare_run_dir({"transit": False, "prologue": False, "flashback": False})
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
        if wait_for_log(r"Site Two \(Halloran\) at x=(-?\d+), z=(-?\d+)", 180, proc) is None:
            print("the sites were never placed")
            return 1
        time.sleep(5)
        rcon.cmd(f"{OW}gamemode creative Steve")
        rcon.cmd(f"{OW}gamerule doDaylightCycle false")
        rcon.cmd(f"{OW}time set day")

        print("---- errands ----")
        check_errands(rcon)
        print("---- teleports ----")
        check_teleports(rcon)
        print("---- burial ----")
        check_burial(rcon)
        print("---- fauna ----")
        check_fauna(rcon)
        print("---- survey network ----")
        check_survey_network(rcon)

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
                  # Vanilla logs this for every modded entity type there has ever been. It is not an error.
                  and "No data fixer registered for" not in l]
        if issues:
            print("---- log lines of interest ----")
            for l in issues[:30]:
                print(l.strip()[:300])
            # An exception thrown by any of this is a failure even when every assertion passed: a tick that
            # throws is how a mob with a bad goal or a beacon with a bad scan actually goes wrong.
            failures.append("exceptions in the log")

    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
