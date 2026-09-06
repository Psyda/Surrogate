#!/usr/bin/env python3
"""Headless check of the props, the ores and the vehicle fabricator: boots the dev server on the Toxic Wastes
preset (ship week and prologue off), places every new block, then stands a Carpet fake player at a
fabricator with a crawler kit, uses it, and waits for the hull to appear on the ground ahead of the gantry.
Exits non-zero when a check fails."""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, Rcon, prepare_run_dir  # noqa: E402
from terrain_scan import reap_server, server_pids, wait_for_log  # noqa: E402

OW = "execute in minecraft:overworld run "
failures = []

PROPS = ["supply_crate", "locker", "data_rack", "deck_grating", "hazard_plating", "deck_plating", "hull_frame", "ceiling_lamp", "pipe",
         "handrail", "crew_seat", "mess_table", "hydroponic_tray", "med_cabinet", "fire_extinguisher", "wall_vent", "coolant_tank", "bunk",
         "chem_drum", "survey_marker", "pad_light", "antenna_mast"]
ORES = ["cinnabar_ore", "halite_ore", "cobalt_ore", "deepslate_cobalt_ore", "tellurium_ore"]


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail else ""))
    if not ok:
        failures.append(name)


def pos_of(rcon, selector):
    out = rcon.cmd(f"{OW}data get entity {selector} Pos")
    m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
    return tuple(float(v) for v in m.groups()) if m else None


def main():
    if server_pids():
        print("a dev server is still running; refusing to start another")
        return 1
    prepare_run_dir({"transit": False, "prologue": False})
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    start = time.time()
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
        time.sleep(3)
        # A flat test slab well north of the pod and above the mesa tops, so it sits in daylight for the client.
        sx, sy, sz = ox, oy + 90, oz - 40
        rcon.cmd(f"{OW}forceload add {sx - 16} {sz - 16} {sx + 16} {sz + 16}")
        time.sleep(2)
        rcon.cmd(f"{OW}fill {sx - 12} {sy - 1} {sz - 12} {sx + 12} {sy - 1} {sz + 12} minecraft:stone")
        rcon.cmd(f"{OW}fill {sx - 12} {sy} {sz - 12} {sx + 12} {sy + 30} {sz + 12} minecraft:air")
        # Every prop and ore placed and read back.
        for i, name in enumerate(PROPS + ORES):
            x = sx - 12 + (i % 14) * 2
            z = sz + 8 + (i // 14) * 2
            rcon.cmd(f"{OW}setblock {x} {sy} {z} surrogate:{name}")
            out = rcon.cmd(f"{OW}execute if block {x} {sy} {z} surrogate:{name}")
            check(f"{name} places", "Test passed" in out, out[:80])
        # Wall props take the wall they are placed against.
        out = rcon.cmd(f"{OW}execute if block {sx - 12} {sy} {sz + 8} surrogate:supply_crate[facing=north]")
        check("facing prop has a facing", "Test passed" in out, out[:80])
        out = rcon.cmd(f"{OW}setblock {sx + 10} {sy} {sz + 12} surrogate:pipe[axis=x]")
        out = rcon.cmd(f"{OW}execute if block {sx + 10} {sy} {sz + 12} surrogate:pipe[axis=x]")
        check("pipe lies along x", "Test passed" in out, out[:80])
        # The fabricator: gantry at the south edge facing north, kit in the fake player's hand.
        gx, gz = sx, sz + 4
        rcon.cmd(f"{OW}setblock {gx} {sy} {gz} surrogate:vehicle_fabricator[facing=north]")
        out = rcon.cmd(f"{OW}execute if block {gx} {sy} {gz} surrogate:vehicle_fabricator[facing=north,building=false]")
        check("fabricator placed idle", "Test passed" in out, out[:80])
        rcon.cmd(f"{OW}tp Steve {gx + 0.5} {sy} {gz + 1.5}")
        rcon.cmd("gamemode survival Steve")
        # Survival so the kit is consumed, but the air out here kills a body in seconds.
        rcon.cmd("effect give Steve minecraft:resistance infinite 5 true")
        rcon.cmd("clear Steve")
        rcon.cmd("give Steve surrogate:crawler_kit 1")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Steve look at {gx + 0.5} {sy + 0.1} {gz + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Steve use once")
        time.sleep(1.5)
        out = rcon.cmd(f"{OW}execute if block {gx} {sy} {gz} surrogate:vehicle_fabricator[building=true]")
        check("kit starts the build", "Test passed" in out, out[:80])
        out = rcon.cmd(f"{OW}data get block {gx} {sy} {gz} Kit.id")
        check("gantry holds the kit", "crawler_kit" in out, out[:100])
        out = rcon.cmd("clear Steve surrogate:crawler_kit 0")
        check("kit left the hand", "No items were found" in out or "Removed 0" in out, out[:80])
        none_yet = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:crawler]")
        check("no hull before the build ends", "Test failed" in none_yet or "passed" not in none_yet, none_yet[:80])
        time.sleep(11.5)
        built = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:crawler]")
        check("hull built", "Test passed" in built, built[:80])
        p = pos_of(rcon, "@e[type=surrogate:crawler,limit=1]")
        check("hull stands four blocks ahead of the gantry", p is not None and abs(p[0] - (gx + 0.5)) < 0.6 and abs(p[2] - (gz - 4 + 0.5)) < 0.6, str(p))
        out = rcon.cmd(f"{OW}execute if block {gx} {sy} {gz} surrogate:vehicle_fabricator[building=false]")
        check("gantry idle again", "Test passed" in out, out[:80])
        # A second kit with the hull still in the way: refused, nothing consumed. A Carpet fake player only
        # lands its first use reliably, so a fresh one is spawned for each later use.
        rcon.cmd("player Steve kill")
        time.sleep(1)
        rcon.cmd("player Bob spawn")
        time.sleep(2)
        rcon.cmd(f"{OW}tp Bob {gx + 0.5} {sy} {gz + 1.5}")
        rcon.cmd("gamemode survival Bob")
        rcon.cmd("effect give Bob minecraft:resistance infinite 5 true")
        rcon.cmd("clear Bob")
        rcon.cmd("give Bob surrogate:crawler_kit 1")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Bob look at {gx + 0.5} {sy + 0.1} {gz + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Bob use once")
        time.sleep(1.0)
        out = rcon.cmd(f"{OW}execute if block {gx} {sy} {gz} surrogate:vehicle_fabricator[building=false]")
        check("blocked site refuses a kit", "Test passed" in out, out[:80])
        out = rcon.cmd("clear Bob surrogate:crawler_kit 0")
        check("refused kit stays in hand", "Found 1" in out or "found 1" in out, out[:80])
        rcon.cmd("player Bob kill")
        time.sleep(1)
        # A second gantry, started just before the server stops, so whoever opens the world in the client
        # watches the hologram finish.
        g2x, g2z = sx - 8, sz + 4
        rcon.cmd(f"{OW}setblock {g2x} {sy} {g2z} surrogate:vehicle_fabricator[facing=north]")
        rcon.cmd("player Carl spawn")
        time.sleep(2)
        rcon.cmd(f"{OW}tp Carl {g2x + 0.5} {sy} {g2z + 1.5}")
        rcon.cmd("gamemode survival Carl")
        rcon.cmd("effect give Carl minecraft:resistance infinite 5 true")
        rcon.cmd("clear Carl")
        rcon.cmd("give Carl surrogate:crawler_kit 1")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Carl look at {g2x + 0.5} {sy + 0.1} {g2z + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Carl use once")
        time.sleep(1.0)
        out = rcon.cmd(f"{OW}execute if block {g2x} {sy} {g2z} surrogate:vehicle_fabricator[building=true]")
        check("second gantry starts a build for the client to watch", "Test passed" in out, out[:80])
        if "Test passed" not in out:
            # Leave a build running anyway so the client can be pointed at the hologram.
            rcon.cmd(f"{OW}data merge block {g2x} {sy} {g2z} {{Kit:{{id:\"surrogate:crawler_kit\",count:1}},Progress:0}}")
            rcon.cmd(f"{OW}setblock {g2x} {sy} {g2z} surrogate:vehicle_fabricator[facing=north,building=true] keep")
        rcon.cmd("player Carl kill")
        time.sleep(1)
        rcon.cmd("time set 6000")
        rcon.cmd("defaultgamemode creative")
        # Where the props stand, for whoever opens this world in the client next.
        print("PROPS_AT", sx, sy, sz)
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
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        issues = [l for l in text.splitlines() if re.search(r"ERROR|Exception|Failed|error in", l) and "data fixer" not in l]
        print("---- log lines of interest ----")
        for l in issues[:40]:
            print(l.strip()[:300])
        if not clean:
            print("NOTE: the server hung in shutdown and was killed")
    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
