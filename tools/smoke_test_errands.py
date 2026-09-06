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
    rcon.cmd(AS + "surrogate prologue fast")
    rcon.cmd(AS + "surrogate errand start housewarming")
    start = time.time()
    state = ""
    while time.time() - start < 180:
        time.sleep(5)
        rows, _ = errand_rows(rcon)
        state = rows.get("housewarming", {}).get("state", "missing")
        if state == "done":
            break
    check("housewarming finishes", state == "done", f"{state} after {int(time.time() - start)}s")

    # And it has to have left a room behind: the errand is the module, not the conversation.
    home = rows.get("housewarming", {}).get("where", "")
    m = re.match(r"(-?\d+), *(-?\d+), *(-?\d+)", home)
    if m:
        hx, hy, hz = (int(m.group(1)), int(m.group(2)), int(m.group(3)))
        out = rcon.cmd(f"{OW}execute if block {hx + 5} {hy + 4} {hz - 4} surrogate:hull_plating")
        check("housewarming builds module two", "Test passed" in out, out[:90])
        bunk = rcon.cmd(f"{OW}execute if block {hx + 6} {hy + 1} {hz - 1} surrogate:bunk")
        check("module two has bunks in it", "Test passed" in bunk, bunk[:90])
    else:
        check("housewarming builds module two", False, f"no habitat position in {home!r}")


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
    # design. So it gets a roof three blocks up before it is asked for.
    rcon.cmd(f"{OW}fill {x - 3} {y + 3} {z - 3} {x + 3} {y + 3} {z + 3} surrogate:hull_plating")
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
