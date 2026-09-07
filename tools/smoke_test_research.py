#!/usr/bin/env python3
"""Headless check of acts one and two: boots the dev server on the Toxic Wastes preset (ship week and prologue
off), runs the neighbours' three research runs and the range gate at Tanaka in fast mode, and asserts on what
the arc does rather than on where it puts its blocks. Each stage has to advance when its objective is met and
advance anyway when nobody does it; a stake has to count on a second biome and be refused on the same ground
twice; a sealed sample opened in the open air has to be ruined; Tanaka has to stay a carrier until a relay
module is fitted; a skip has to land on `done` with Contract Seven open behind it. Boots the server twice,
because the only honest way to show the file survives a restart is to restart. Costs fifteen to twenty
minutes of wall clock. Exits non-zero when a check fails.

The lesson of docs/DESIGN-assay.md, "What the first play test found": the old test passed twenty checks while
the sequence was broken, because every check was a block coordinate. The only coordinates asserted here are
the ones an objective is impossible without.
"""
import math
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, SCRATCH, Rcon, prepare_run_dir  # noqa: E402
from terrain_scan import reap_server, server_pids, wait_for_log  # noqa: E402

OW = "execute in minecraft:overworld run "
AS = "execute as Steve at Steve run "
RESTART_LOG = os.path.join(SCRATCH, "server_research_restart.log")
# The surface biomes of the preset. crawler_cabin and vacuum are the two off-world ones and never hold a stake.
BIOMES = ["surrogate:acid_flats", "surrogate:ash_dunes", "surrogate:caustic_mire", "surrogate:dead_grove",
          "surrogate:rift", "surrogate:salt_pans", "surrogate:toxic_desert"]
# The two a stake cannot stand in: acid, and thirty-four blocks down a chasm.
WATER_BIOMES = {"surrogate:acid_flats", "surrogate:rift"}
failures = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail else ""))
    if not ok:
        failures.append(name)


def expect(rcon, command, name):
    """A vanilla `execute if ...`: its reply says "Test passed" when the thing holds."""
    out = rcon.cmd(command)
    ok = "Test passed" in out
    check(name, ok, out[:90])
    return ok


def status(rcon):
    """The research status line, parsed into a dict. Everything this test knows about the arc comes through
    here, because it is the only thing the server says out loud about the runs."""
    out = rcon.cmd(AS + "surrogate research status")
    return {
        "stage": int(m.group(1)) if (m := re.search(r"Research stage (\d+)", out)) else -1,
        "core": int(m.group(1)) if (m := re.search(r"core (\d+)/", out)) else -1,
        "stakes": int(m.group(1)) if (m := re.search(r"stakes (\d+)/", out)) else -1,
        "seep": m.group(1) if (m := re.search(r"seep (none|taken|home)", out)) else "",
        "schematic": ", schematic" in out,
        "relay": ", relay" in out,
        "damper": ", damper" in out,
        "errands": int(m.group(1)) if (m := re.search(r"errands (\d+)/", out)) else -1,
        "label": m.group(1) if (m := re.search(r"running at ([a-z0-9]+)", out)) else "",
        "running": "not running" not in out,
        "fast": ", fast" in out,
        "raw": out,
    }


def wait_until(rcon, want, seconds, poll=2.0):
    """Polls the status line until `want(status)` holds, and returns (status, seconds waited). Nothing is
    skipped or nudged in here: which of the arc's own paths got there first is the whole question."""
    start = time.time()
    st = status(rcon)
    while not want(st) and time.time() - start < seconds:
        time.sleep(poll)
        st = status(rcon)
    return st, time.time() - start


# ---------------------------------------------------------------------------- reading and moving the player


def player_pos(rcon):
    out = rcon.cmd(f"{OW}data get entity Steve Pos")
    m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
    return tuple(float(v) for v in m.groups()) if m else None


def feet(pos):
    return int(math.floor(pos[0])), int(math.floor(pos[1])), int(math.floor(pos[2]))


def stand_at(rcon, x, z):
    """Puts Steve on the surface within a few blocks of (x, z) and reads back where he actually landed.
    spreadplayers finds the ground for us; a spot it refuses (acid, a hole) gets a drop from the sky instead.
    None means the ground could not be found, and every caller treats that as the test having failed to set
    itself up rather than as the mod being wrong."""
    rcon.cmd(f"{OW}spreadplayers {x} {z} 0 8 false Steve")
    time.sleep(2)
    pos = player_pos(rcon)
    if pos is None or math.hypot(pos[0] - x, pos[2] - z) > 24:
        rcon.cmd(f"{OW}tp Steve {x + 0.5} 250 {z + 0.5}")
        time.sleep(9)
        pos = player_pos(rcon)
    if pos is None:
        print(f"could not put Steve on the ground at {x}, {z}")
    return pos


def stand_exactly(rcon, x, z):
    """Puts Steve on the ground at exactly (x, z) by dropping him onto it.

    stand_at uses spreadplayers, which lands him up to eight blocks off, and that is fine when any nearby
    ground will do. It is not fine when the point of the check is which biome he is standing in: the run that
    found this probed ash dunes, planted in salt pans eight blocks away, and reported the mod as broken.
    """
    rcon.cmd(f"{OW}tp Steve {x + 0.5} 250 {z + 0.5}")
    time.sleep(9)
    pos = player_pos(rcon)
    if pos is None or math.hypot(pos[0] - x, pos[2] - z) > 2:
        print(f"could not drop Steve onto {x}, {z}")
        return None
    return pos


def biome_at(rcon, x, y, z):
    """Which of the preset's biomes covers that block, or None if it is one this test does not know about."""
    for biome in BIOMES:
        if "Test passed" in rcon.cmd(f"{OW}execute positioned {x} {y} {z} if biome ~ ~ ~ {biome}"):
            return biome
    return None


def locate_biome(rcon, x, y, z, biome):
    """Vanilla's biome search, from (x, y, z). Returns (x, z) or None when the preset has none in range.

    `locate biome` reports a real height - "is at [-180, 86, -570]" - where `locate structure` reports "~".
    Matching only the "~" form meant every lookup here came back empty and the stake checks reported the map
    as having one biome in it.
    """
    out = rcon.cmd(f"{OW}execute positioned {x} {y} {z} run locate biome {biome}")
    m = re.search(r"\[(-?\d+), (?:~|-?\d+), (-?\d+)\]", out)
    return (int(m.group(1)), int(m.group(2))) if m else None


def plant_stake(rcon, pos):
    """Steve plants a stake at his own feet: the block has no collision, so straight down is a legal place to
    put one. Returns the block position it should have gone into."""
    x, y, z = feet(pos)
    rcon.cmd(f"{OW}item replace entity Steve weapon.mainhand with surrogate:survey_stake 4")
    rcon.cmd(f"{OW}player Steve look at {x + 0.5} {y - 0.5} {z + 0.5}")
    time.sleep(0.5)
    rcon.cmd(f"{OW}player Steve use once")
    time.sleep(1.5)
    return x, y, z


# ---------------------------------------------------------------------------- the server


def boot(path):
    """Starts the dev server with its stdout in `path`. The caller owns reaping it."""
    log = open(path, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    return proc, log


def wait_in(path, pattern, timeout, proc):
    """wait_for_log, but on a log that is not build/server.log. The shared helper only ever reads that one."""
    start = time.time()
    while time.time() - start < timeout:
        time.sleep(3)
        if proc.poll() is not None:
            return None
        with open(path, encoding="utf-8", errors="replace") as f:
            m = re.search(pattern, f.read())
        if m:
            return m
    return None


def shutdown(rcon, proc, log):
    """A Carpet fake player still aboard keeps the shutdown from finishing, so Steve goes first."""
    try:
        rcon.cmd("player Steve kill")
        time.sleep(1)
        rcon.cmd("stop")
    except OSError as e:
        print("could not stop the server cleanly:", e)
    clean = reap_server(120)
    for _ in range(20):
        if proc.poll() is not None:
            break
        time.sleep(3)
    if proc.poll() is None:
        proc.kill()
    log.close()
    if not clean:
        print("NOTE: the server hung in shutdown and was killed")


def report(path, label):
    """The log lines this arc is judged on, plus the two the Director complains with."""
    with open(path, encoding="utf-8", errors="replace") as f:
        text = f.read()
    held = [l for l in text.splitlines() if "camera still held" in l.lower()]
    threw = [l for l in text.splitlines() if "beat threw at" in l.lower()]
    check(f"the camera is always released ({label})", not held, held[0][:160] if held else "")
    check(f"no beat threw ({label})", not threw, threw[0][:160] if threw else "")
    print(f"---- research log lines ({label}) ----")
    for l in text.splitlines():
        if "Research:" in l or "Assay: " in l:
            print(l.strip()[:200])
    print(f"---- log lines of interest ({label}) ----")
    issues = [l for l in text.splitlines() if re.search(r"ERROR|Exception|Failed|error in", l) and "data fixer" not in l]
    for l in issues[:40]:
        print(l.strip()[:300])
    return text


# ---------------------------------------------------------------------------- acts one and two


def acts():
    """The whole arc in one boot. Returns 0 when it ran, 1 when the server never got far enough to test."""
    proc, log_file = boot(LOG)
    start = time.time()
    rcon = None
    try:
        if wait_for_log(r"Done \(.*For help", 480, proc) is None:
            print("server never reported Done")
            return 1
        print(f"server ready after {int(time.time() - start)} s")
        time.sleep(3)
        rcon = Rcon()
        rcon.cmd("player Steve spawn")
        m = wait_for_log(r"Built the starter habitat at BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}", 120, proc)
        if m is None:
            print("no habitat")
            return 1
        ox, oy, oz = (int(v) for v in m.groups())
        crate = (ox + 2, oy + 1, oz + 8)
        print("HABITAT_AT", ox, oy, oz)
        tanaka = wait_for_log(r"Survivor site for tanaka at x=(-?\d+), z=(-?\d+)", 60, proc)
        if tanaka is None:
            print("no site was chosen for Tanaka; act two has nowhere to drive to")
            return 1
        tx, tz = (int(v) for v in tanaka.groups())
        print("TANAKA_AT", tx, tz)
        time.sleep(3)
        rcon.cmd("gamemode creative Steve")
        rcon.cmd("effect give Steve minecraft:resistance infinite 5 true")
        # The pod's crate is read and written from a thousand blocks away in this test; keep its chunk up.
        rcon.cmd(f"{OW}forceload add {ox - 16} {oz - 16} {ox + 16} {oz + 16}")

        # ---- Ground work, before the arc starts: where the three stakes are going to go. Doing this now
        # rather than during the wind count keeps that stage's eighty-second timeout out of the way of it.
        near = stand_at(rcon, ox + 24, oz + 24)
        if near is None:
            return 1
        home_biome = biome_at(rcon, int(near[0]), int(near[1]), int(near[2]))
        check("the pod stands in a biome this test knows", home_biome is not None, str(home_biome))
        # The reference for both stake checks is the biome the FIRST STAKE goes into, which is not this one:
        # the first stake is planted beside the shaft and the shaft can be on a table twenty-eight blocks up
        # while the pod is on the floor. Reading the pod's biome and then planting in the table's was the
        # whole of two failures that looked like the count being wrong when it was right.
        stake_ref = None

        # ---- Run one: the shallow core. Twelve blocks down in one place and the column back to the crate.
        rcon.cmd(AS + "surrogate research fast")
        st = status(rcon)
        if not st["fast"]:
            rcon.cmd(AS + "surrogate research fast")
            st = status(rcon)
        check("fast mode is on before the arc starts", st["fast"], st["raw"][:120])
        t_start = time.time()
        rcon.cmd(AS + "surrogate research start")
        if wait_for_log(r"Research: the neighbours want a hand", 30, proc) is None:
            check("the neighbours open act one", False, "no start line in the log")
            return 1
        # The label is read a beat later than the stage: a script that has just been handed the stage has not
        # reached its first label yet, and says "start" until its hundred-tick opening delay is out.
        st = status(rcon)
        check("the arc starts on the shallow core", st["stage"] == 1 and st["running"], st["raw"][:140])

        # Dig the shaft the objective asks for, put the column in the crate, and stand at the bottom of it.
        fx, fy, fz = feet(near)
        # One block wide, so the ground the sweep measures against - the heightmap two blocks to each side -
        # is the ground the shaft was cut through and not the floor of the shaft itself.
        bottom = max(-60, fy - 13)
        rcon.cmd(f"{OW}fill {fx} {bottom} {fz} {fx} {fy} {fz} minecraft:air")
        rcon.cmd(f"{OW}item replace block {crate[0]} {crate[1]} {crate[2]} container.13 with minecraft:cobblestone 16")
        rcon.cmd(f"{OW}tp Steve {fx + 0.5} {bottom} {fz + 0.5}")
        st, waited = wait_until(rcon, lambda s: s["core"] >= 12, 30)
        check("the sweep counts a shaft the player is standing in", st["core"] >= 12, "core=%s after %ds" % (st["core"], waited))
        # The core objective's timeout is timeout()*8 = 1600 ticks = 80 s in fast mode, and it cannot even
        # start counting until the six opening lines have played. Reaching stage two inside seventy seconds
        # of the start is therefore the objective doing it, not the clock.
        st, waited = wait_until(rcon, lambda s: s["stage"] >= 2, 70 - (time.time() - t_start))
        check("the core stage advances because the objective was met", st["stage"] >= 2,
              "stage=%s %ds after the start" % (st["stage"], int(time.time() - t_start)))

        # ---- A restart from the top: the runs are forgotten and the arc still works afterwards. Steve comes
        # up out of the shaft first, or the sweep hands the depth straight back before the status is read.
        if stand_at(rcon, fx + 12, fz + 12) is None:
            return 1
        rcon.cmd(AS + "surrogate research start")
        time.sleep(8)
        st = status(rcon)
        # The story resets; the ground does not. reset() says so in as many words, and the shaft Steve just dug
        # is still in the world, so the sweep credits its depth straight back. Asserting core == 0 here was
        # asserting that a restart fills a hole in.
        check("a restart forgets the runs and begins again at the core",
              st["stage"] == 1 and st["seep"] == "none" and st["label"] == "core", st["raw"][:140])
        t_start = time.time()
        rcon.cmd(f"{OW}tp Steve {fx + 0.5} {bottom} {fz + 0.5}")
        st, waited = wait_until(rcon, lambda s: s["stage"] >= 2, 70)
        check("the core stage advances again after a restart", st["stage"] >= 2,
              "stage=%s after %ds" % (st["stage"], int(waited)))
        if st["stage"] < 2:
            return 1

        # ---- Run two: the wind count. Four stakes, far apart, on ground nobody has counted yet. The crate is
        # stocked two lines into the run rather than at the label, so give Sorensen those two lines first.
        time.sleep(8)
        expect(rcon, f"{OW}execute if data block {crate[0]} {crate[1]} {crate[2]} Items[{{id:\"surrogate:survey_stake\"}}]",
               "the stakes the objective wants are in the pod crate")
        first = stand_at(rcon, fx + 6, fz + 6)
        if first is None:
            return 1
        block = plant_stake(rcon, first)
        expect(rcon, f"{OW}execute if block {block[0]} {block[1]} {block[2]} surrogate:survey_stake",
               "the first stake goes into the ground")
        st = status(rcon)
        check("the first stake counts", st["stakes"] == 1, st["raw"][:140])
        stake_ref = biome_at(rcon, *feet(first))
        # Both spots are found with vanilla's biome locator rather than by probing columns. `execute if biome`
        # reads the world, so past the loaded chunks around Steve it answers nothing at all, and a ring probe
        # at four hundred blocks silently finds no ground of any kind; `locate biome` reads the biome source
        # and needs no chunk. Locate is aimed from several offset origins because it returns the NEAREST
        # instance, and the nearest one is usually inside the eighty-block spacing the count wants cleared.
        def find_biome(want, same):
            for dx, dz in ((0, 0), (300, 0), (-300, 0), (0, 300), (0, -300), (600, 600), (-600, -600)):
                ox2 = int(first[0]) + dx
                oz2 = int(first[2]) + dz
                # Never the acid or the chasm floor: a stake wants ground under it, and the count is
                # about which country you walked to, not which puddle you fell in.
                candidates = [want] if same else [b for b in BIOMES if b != want and b not in WATER_BIOMES]
                for biome in candidates:
                    if biome is None:
                        continue
                    found = locate_biome(rcon, ox2, int(first[1]), oz2, biome)
                    if found is None:
                        continue
                    away = math.hypot(found[0] - first[0], found[1] - first[2])
                    if 100 < away < 1600:
                        return found
            return None

        same_spot = find_biome(stake_ref, True) if stake_ref else None
        other_spot = find_biome(stake_ref, False) if stake_ref else None
        print("STAKE_SPOTS first", stake_ref, "same", same_spot, "other", other_spot)

        # A second stake on the same ground, well past the eighty blocks the spacing rule wants: the spacing
        # is satisfied and the count still has to refuse it, because it is the same biome twice. The block
        # going into the ground is asserted separately from the count, so a stake that was never placed reads
        # as this test failing to plant one rather than as the rule working.
        pos = stand_exactly(rcon, *same_spot) if same_spot else None
        if pos is None:
            check("a stake on the same biome is refused", False,
                  "no same-biome spot was found to test with" if same_spot is None else "could not stand there")
        else:
            here = biome_at(rcon, *feet(pos))
            far = math.hypot(pos[0] - first[0], pos[2] - first[2])
            block = plant_stake(rcon, pos)
            placed = "Test passed" in rcon.cmd(f"{OW}execute if block {block[0]} {block[1]} {block[2]} surrogate:survey_stake")
            st = status(rcon)
            check("a stake past the spacing but on the same biome is refused",
                  placed and here == stake_ref and far > 80 and st["stakes"] == 1,
                  "%d blocks out, placed=%s biome=%s stakes=%s stage=%s" % (far, placed, here, st["stakes"], st["stage"]))

        # And one on ground nobody has counted: the same rule that refused the last one has to take this one.
        pos = stand_exactly(rcon, *other_spot) if other_spot else None
        if pos is None:
            check("a stake on a second biome counts", False,
                  "locate biome found no second biome at all" if other_spot is None else "could not stand there")
        else:
            here = biome_at(rcon, *feet(pos))
            block = plant_stake(rcon, pos)
            placed = "Test passed" in rcon.cmd(f"{OW}execute if block {block[0]} {block[1]} {block[2]} surrogate:survey_stake")
            st = status(rcon)
            check("a stake on a second biome counts", placed and here != stake_ref and st["stakes"] == 2,
                  "placed=%s biome=%s stakes=%s stage=%s" % (placed, here, st["stakes"], st["stage"]))

        # Nobody is going to plant four. The count has to give up on the player and file what it has: this is
        # the rule the whole campaign keeps, and the only proof of it is the stage moving with stakes short.
        planted = status(rcon)["stakes"]
        check("the count is short before the clock runs out", planted < 4, "stakes=%s" % planted)
        st, waited = wait_until(rcon, lambda s: s["stage"] >= 3, 150)
        check("the wind count gives up on the player and files anyway", st["stage"] >= 3,
              "stage=%s stakes=%s after %ds" % (st["stage"], st["stakes"], int(waited)))
        # Whatever went into the ground is what is on the record. The count is never topped up to four on the
        # player's behalf, because act six weighs what the player actually did (Research.finishWind).
        check("it filed with the stakes the player planted and no more", st["stakes"] == planted,
              "stakes=%s planted=%s" % (st["stakes"], planted))
        if st["stage"] < 3:
            return 1

        # ---- Run three: the seep. Nobody goes, so the pod's own chassis brings a flask back on its charge
        # run: seepTaken flips with the player a thousand blocks away and no flask ever in his hand.
        check("the seep run starts with nothing taken", st["seep"] == "none", st["raw"][:140])
        st, waited = wait_until(rcon, lambda s: s["seep"] != "none", 150)
        t_take = time.time()
        check("a sample nobody fetched is worked around", st["seep"] == "taken",
              "seep=%s after %ds" % (st["seep"], int(waited)))

        # The seal is the whole point: opened in the open air the sample is the air, and the run says so.
        pos = stand_at(rcon, ox + 30, oz + 30)
        if pos is None:
            return 1
        rcon.cmd(f"{OW}item replace entity Steve weapon.mainhand with surrogate:sealed_sample 1")
        rcon.cmd(f"{OW}player Steve look at {pos[0]} {pos[1] + 3} {pos[2]}")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Steve use once")
        time.sleep(2)
        spoiled = wait_for_log(r"Research: the seep sample was opened outdoors", 20, proc) is not None
        st = status(rcon)
        check("a sealed sample opened in the open air is ruined", spoiled and st["seep"] == "none",
              "logged=%s seep=%s" % (spoiled, st["seep"]))

        # Filing it properly: a sealed flask in the pod's crate is the objective, and meeting it has to be
        # what moves the run on. The home timeout is timeout()*6 = 60 s from when the take timed out.
        rcon.cmd(f"{OW}item replace block {crate[0]} {crate[1]} {crate[2]} container.14 with surrogate:sealed_sample 1")
        st, waited = wait_until(rcon, lambda s: s["seep"] == "home", 20)
        check("a sealed flask in the crate files the run", st["seep"] == "home" and waited < 15,
              "seep=%s after %ds" % (st["seep"], int(waited)))
        check("the flask filed it, not the clock", time.time() - t_take < 55,
              "%ds after the take timed out, against a sixty-second home timeout" % int(time.time() - t_take))
        st, waited = wait_until(rcon, lambda s: s["stage"] >= 4, 60)
        check("act one closes and act two opens", st["stage"] >= 4, "stage=%s after %ds" % (st["stage"], int(waited)))
        check("Sorensen handed the relay schematic over", st["schematic"], st["raw"][:140])

        # ---- A stage jump. Jumping in past a run has to leave the world the lines after it talk about:
        # the core filed, the seep home, the schematic handed over, and nothing act two has yet to deliver.
        rcon.cmd(AS + "surrogate research stage seep")
        time.sleep(8)
        st = status(rcon)
        check("a jump back to the seep re-lays the work under it",
              st["stage"] == 3 and st["core"] >= 12 and st["label"] == "seep" and not st["schematic"], st["raw"][:160])
        rcon.cmd(AS + "surrogate research stage range")
        time.sleep(8)
        st = status(rcon)
        check("a jump to the range gate arrives with act one already filed",
              st["stage"] == 4 and st["core"] >= 12 and st["seep"] == "home" and st["schematic"] and st["label"] == "range",
              st["raw"][:160])
        t_range = time.time()

        # ---- Act two: out of range. Tanaka is a carrier on the band and nothing else until a relay module
        # is on a chassis. His lines are a client payload and a chat message to one player, and a Carpet fake
        # player has no client and the server logs neither, so what he says cannot be read over RCON. What
        # can be read is the gate his lines hang off: no relay, no damper, and no way past either.
        check("Tanaka is a carrier with nothing to show for it yet", not st["relay"] and not st["damper"], st["raw"][:160])
        time.sleep(12)
        st = status(rcon)
        check("the range gate does not open on its own in the first seconds", not st["relay"] and not st["damper"],
              st["raw"][:160])

        # Fit one. The relay timeout is timeout()*6 = 60 s from the start of that beat, so a flag inside
        # twenty-five seconds of the summon is the module doing it rather than Sorensen talking around it.
        rcon.cmd(f"{AS}summon surrogate:robot ~ ~ ~1 {{Energy:36000,Modules:1}}")
        time.sleep(1)
        mask = rcon.cmd(f"{AS}data get entity @e[type=surrogate:robot,limit=1,sort=nearest] Modules")
        fitted = re.search(r"entity data: (\d+)", mask)
        check("the chassis beside the player carries a relay module",
              fitted is not None and bool(int(fitted.group(1)) & 1), mask[:100])
        st, waited = wait_until(rcon, lambda s: s["relay"], 25, poll=1.5)
        check("fitting a relay module opens the range gate", st["relay"] and waited < 25,
              "after %ds, %ds into act two" % (int(waited), int(time.time() - t_range)))

        # Drive out to the man the mast cannot hear. The shelter is built the tick its chunk loads, so the
        # forceload goes in first and the arrival has somewhere to arrive at. The arrival timeout is
        # timeout()*12 = 120 s from the start of that beat, itself about forty-five seconds into the act, so
        # a damper inside a minute of standing there is Tanaka answering the person in front of him.
        rcon.cmd(f"{OW}forceload add {tx - 16} {tz - 16} {tx + 16} {tz + 16}")
        time.sleep(6)
        if stand_at(rcon, tx, tz) is None:
            return 1
        expect(rcon, f"{AS}execute if entity @e[type=surrogate:survivor,nbt={{Character:2}},distance=..48]",
               "Tanaka's shelter is built and he is in it")
        st, waited = wait_until(rcon, lambda s: s["damper"], 60, poll=2.0)
        check("Tanaka answers once somebody is standing there", st["damper"] and waited < 60,
              "after %ds, %ds into act two" % (int(waited), int(time.time() - t_range)))

        # ---- Nothing softlocks. A skip from here has to deliver what act three needs and file the arc, and
        # the contract has to open behind it the moment the stage is free.
        rcon.cmd(AS + "surrogate research skip")
        st, waited = wait_until(rcon, lambda s: s["stage"] >= 5, 40)
        check("a skip files the arc", st["stage"] >= 5, "stage=%s after %ds" % (st["stage"], int(waited)))
        check("the filed arc leaves the stage", not st["running"], st["raw"][:160])
        check("the skip still handed over the module and the damper", st["relay"] and st["damper"] and st["schematic"],
              st["raw"][:160])
        assay = rcon.cmd(AS + "surrogate assay status")
        opened = re.search(r"Assay stage (\d+)", assay)
        check("Contract Seven opens behind it", opened is not None and int(opened.group(1)) >= 1, assay[:160])
        check("the contract took the stage the research left", "running at" in assay, assay[:160])

        # Leave the world somewhere a dev client can look at it, and hand the numbers on to the restart boot.
        rcon.cmd("time set 6000")
        rcon.cmd("weather clear")
        rcon.cmd(f"{OW}tp Steve {ox + 0.5} {oy + 1} {oz + 0.5}")
        rcon.cmd("defaultgamemode creative")
        return 0
    finally:
        if rcon is not None:
            shutdown(rcon, proc, log_file)
        else:
            reap_server(120)
            if proc.poll() is None:
                proc.kill()
            log_file.close()
        report(LOG, "the arc")


# ---------------------------------------------------------------------------- the restart


def restart():
    """A second boot on the same world. The state is a PersistentState, which is only ever proved by reading
    it back out of a server that has been down. Costs another boot; there is no cheaper way to say it."""
    proc, log_file = boot(RESTART_LOG)
    start = time.time()
    rcon = None
    try:
        if wait_in(RESTART_LOG, r"Done \(.*For help", 480, proc) is None:
            print("the restarted server never reported Done")
            return 1
        print(f"restarted server ready after {int(time.time() - start)} s")
        time.sleep(3)
        rcon = Rcon()
        rcon.cmd("player Steve spawn")
        time.sleep(12)
        st = status(rcon)
        check("the runs survive a restart", st["stage"] >= 5 and st["core"] >= 12 and st["seep"] == "home",
              st["raw"][:160])
        check("so does everything act three is owed", st["schematic"] and st["relay"] and st["damper"], st["raw"][:160])
        check("a filed arc is not run again on the next join", not st["running"], st["raw"][:160])
        with open(RESTART_LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        check("the neighbours do not open act one a second time",
              "the neighbours want a hand" not in text, "")
        resumed = wait_in(RESTART_LOG, r"Assay: resuming at stage", 30, proc) is not None
        check("the contract picks itself up instead", resumed, "")
        return 0
    finally:
        if rcon is not None:
            shutdown(rcon, proc, log_file)
        else:
            reap_server(120)
            if proc.poll() is None:
                proc.kill()
            log_file.close()
        report(RESTART_LOG, "the restart")


def main():
    if server_pids():
        print("a dev server is still running; refusing to start another")
        return 1
    prepare_run_dir({"transit": False, "prologue": False})
    if acts() != 0:
        print("RESULT:", "FAIL " + ", ".join(failures) if failures else "FAIL (the arc never got far enough to test)")
        return 1
    if restart() != 0:
        print("RESULT:", "FAIL " + ", ".join(failures) if failures else "FAIL (the restart never came up)")
        return 1
    # Not covered here, and not worth a weaker assertion: the lines themselves (they are a client payload and
    # a chat message to one player, and a fake player has neither), the five errands (each wants a rescued
    # survivor standing in a built shelter, which is the survivor arc's own test), and the acid seep taken by
    # hand (it wants a player linked into a chassis with its feet in river water, which is the crawler's).
    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
