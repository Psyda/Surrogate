#!/usr/bin/env python3
"""Headless check of Contract Seven: boots the dev server on the Toxic Wastes preset (ship week and prologue
off), starts the assay in fast mode, and walks it stage by stage, asserting that the pad grows course by
course, the ores and the gantry end up where the script says, and the arc reaches the ship. Exits non-zero
when a check fails."""
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


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail else ""))
    if not ok:
        failures.append(name)


AS = "execute as Steve at Steve run "


def status(rcon):
    """The assay status line, parsed into a dict."""
    out = rcon.cmd(AS + "surrogate assay status")
    site = re.search(r"site (-?\d+), *(-?\d+), *(-?\d+)", out)
    return {
        "stage": int(m.group(1)) if (m := re.search(r"Assay stage (\d+)", out)) else -1,
        "courses": int(m.group(1)) if (m := re.search(r"courses (\d+)", out)) else -1,
        "core": int(m.group(1)) if (m := re.search(r"core (\d+)/", out)) else -1,
        "label": m.group(1).strip() if (m := re.search(r"running at (\S+)", out)) else "",
        "ship": "ship seen" in out,
        "site": tuple(int(v) for v in site.groups()) if site else None,
        "raw": out,
    }


def wait_for_stage(rcon, want, seconds=180):
    """Lets the script run until it reaches `want`, skipping whatever objective it is waiting on."""
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        st = status(rcon)
        if st["stage"] >= want:
            return st
        if st["label"] != last:
            last = st["label"]
        # The script is waiting on the player doing something; the skip key is what a player would press.
        rcon.cmd(AS + "surrogate assay skip")
        time.sleep(4)
    return status(rcon)


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
        if wait_for_log(r"Built the starter habitat at BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}", 120, proc) is None:
            print("no habitat")
            return 1
        time.sleep(3)
        rcon.cmd("gamemode creative Steve")
        rcon.cmd(AS + "surrogate assay fast")

        # ---- The site is chosen on the first join, well away from the pod.
        st = status(rcon)
        check("a pad site was chosen", st["site"] is not None, st["raw"][:120])
        site = st["site"]
        if site is None:
            return 2

        # ---- Stage one: the stake. Starting it levels nothing until the player arrives.
        rcon.cmd(AS + "surrogate assay start")
        time.sleep(3)
        st = status(rcon)
        check("assay starts at the stake", st["stage"] == 1, st["raw"][:120])
        # Drive out: the command teleports to the designated ground, which is what the crawler is for.
        rcon.cmd(AS + "surrogate assay site")
        time.sleep(8)
        st = status(rcon)
        site = st["site"]
        # A y at or below the world bottom means the heightmap was read in an unloaded chunk.
        check("arriving at the site resolves its ground", site is not None and 0 < site[1] < 200, str(site))
        # The origin is resolved once and then frozen, so everything below is measured from it and it must
        # never move again.
        rcon.cmd(f"{OW}forceload add {site[0] - 32} {site[2] - 32} {site[0] + 32} {site[2] + 32}")
        time.sleep(4)
        marker = rcon.cmd(f"{OW}execute if block {site[0] - 7} {site[1] + 1} {site[2] - 7} surrogate:survey_marker")
        check("the site is staked at its corners", "Test passed" in marker, marker[:80])

        # ---- Stage two: the pad grows course by course.
        st = wait_for_stage(rcon, 2, 120)
        check("reaches the pad stage", st["stage"] >= 2, st["raw"][:120])
        # The build sequence plays here. The apron wait is timeout()*6 (60s even in fast mode) before the
        # script gives up on the player and lays it itself, so give it longer than that.
        time.sleep(75)
        st = status(rcon)
        check("courses are laid as the script runs", st["courses"] >= 1, "courses=%s" % st["courses"])
        st = wait_for_stage(rcon, 3, 240)
        check("reaches the core stage", st["stage"] >= 3, st["raw"][:120])
        check("all four courses stand", st["courses"] == 4, "courses=%s" % st["courses"])
        check("the pad origin never moved", st["site"] == site, "%s vs %s" % (st["site"], site))
        apron = rcon.cmd(f"{OW}execute if block {site[0]} {site[1]} {site[2]} surrogate:deck_plating")
        check("the apron is plated", "Test passed" in apron, apron[:80])
        border = rcon.cmd(f"{OW}execute if block {site[0] - 6} {site[1]} {site[2]} surrogate:hazard_plating")
        check("the apron has a hazard border", "Test passed" in border, border[:80])
        legs = rcon.cmd(f"{OW}execute if block {site[0] - 4} {site[1] + 2} {site[2] - 4} surrogate:hull_frame")
        check("the frame legs stand", "Test passed" in legs, legs[:80])
        mast = rcon.cmd(f"{OW}execute if block {site[0]} {site[1] + 3} {site[2] - 5} surrogate:antenna_mast")
        check("the mast stands three tall", "Test passed" in mast, mast[:80])
        gantry = rcon.cmd(f"{OW}execute if block {site[0]} {site[1] + 1} {site[2] + 2} surrogate:vehicle_fabricator")
        check("the gantry is on the pad", "Test passed" in gantry, gantry[:80])

        # ---- Stage three and four: the deep sample and the payload.
        st = wait_for_stage(rcon, 4, 180)
        check("reaches the payload stage", st["stage"] >= 4, st["raw"][:120])
        check("the core sample is accounted for", st["core"] >= 3, "core=%s" % st["core"])

        # ---- Stage five: the rocket stands, then goes.
        st = wait_for_stage(rcon, 5, 180)
        check("reaches the ignition stage", st["stage"] >= 5, st["raw"][:120])
        rocket = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:rocket]")
        check("the rocket stands on the pad", "Test passed" in rocket, rocket[:80])
        st = wait_for_stage(rcon, 6, 240)
        check("the contract completes", st["stage"] >= 6, st["raw"][:120])
        check("the ship was seen overhead", st["ship"], st["raw"][:120])
        # The rocket flies itself away and discards at the top of the climb.
        time.sleep(14)
        gone = rcon.cmd(f"{OW}execute unless entity @e[type=surrogate:rocket]")
        check("the rocket left the pad", "Test passed" in gone, gone[:80])

        # The kit the script tells the player to find must actually be in the crate.
        kit = rcon.cmd(f"{OW}execute if data block {site[0] + 5} {site[1] + 1} {site[2] - 1} Items[{{id:\"surrogate:rocket_kit\"}}]")
        check("the sample vehicle kit is in the relay crate", "Test passed" in kit, kit[:100])

        # NPCs are chassis, not people standing in lethal air.
        crew = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:crew,x={site[0]},y={site[1]},z={site[2]},distance=..24]")
        check("nobody is standing on the pad in person", "Test failed" in crew or "passed" not in crew, crew[:80])
        chassis = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:robot,x={site[0]},y={site[1]},z={site[2]},distance=..24]")
        check("chassis are on the pad instead", "Test passed" in chassis, chassis[:80])
        painted = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:robot,nbt={{Paint:2}}]")
        check("an NPC chassis wears its own paint", "Test passed" in painted, painted[:80])

        # The softlock: a card must refuse to key to somebody else's chassis.
        rcon.cmd("player Steve kill")
        time.sleep(1)
        rcon.cmd("player Dave spawn")
        time.sleep(2)
        rcon.cmd(f"{OW}tp Dave {site[0]} {site[1] + 1} {site[2]}")
        rcon.cmd("gamemode creative Dave")
        rcon.cmd("effect give Dave minecraft:resistance infinite 5 true")
        rcon.cmd("clear Dave")
        rcon.cmd("give Dave surrogate:uplink_card 1")
        time.sleep(1)
        npc = rcon.cmd(f"{OW}data get entity @e[type=surrogate:robot,limit=1,sort=nearest,x={site[0]},y={site[1]},z={site[2]}] UUID")
        rcon.cmd(f"{OW}execute as Dave at Dave run player Dave look at entity @e[type=surrogate:robot,limit=1,sort=nearest]")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Dave use once")
        time.sleep(1)
        keyed = rcon.cmd(f"{OW}execute if data entity Dave SelectedItem.components.\"surrogate:uplink_target\"")
        check("an uplink card refuses an NPC chassis", "Test failed" in keyed or "passed" not in keyed, keyed[:100])

        # Leave the world in daylight at the pad, so tools/dev_client.py can look at it.
        rcon.cmd("time set 6000")
        rcon.cmd("weather clear")
        rcon.cmd(AS + "surrogate assay site")
        rcon.cmd("defaultgamemode creative")
        print("PAD_AT", site[0], site[1], site[2])
        rcon.cmd("player Steve kill")
        time.sleep(1)
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
        held = [l for l in text.splitlines() if "camera still held" in l.lower()]
        threw = [l for l in text.splitlines() if "beat threw at" in l.lower()]
        check("the camera is always released", not held, held[0][:160] if held else "")
        check("no beat threw", not threw, threw[0][:160] if threw else "")
        print("---- assay log lines ----")
        for l in text.splitlines():
            if "Assay:" in l:
                print(l.strip()[:200])
        print("---- log lines of interest ----")
        for l in issues[:40]:
            print(l.strip()[:300])
        if not clean:
            print("NOTE: the server hung in shutdown and was killed")
    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
