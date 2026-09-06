#!/usr/bin/env python3
"""Headless check of the crawler: boots the dev server on the Toxic Wastes preset (ship week and prologue
off), spawns a Carpet fake player at the pod, couples a hull to the collar, and walks the player through the
collar into the cabin and back, drives from the helm, docks in reverse from the console, and deploys a
chassis from the bay, then couples at the first shelter (survivor boards), talks to its chassis port
(blueprint) and couples at home (survivor steps into the pod). Exits non-zero when a check fails."""
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


def pos_of(rcon, selector):
    out = rcon.cmd(f"{OW}data get entity {selector} Pos")
    m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
    return tuple(float(v) for v in m.groups()) if m else None


def number_of(rcon, selector, path):
    out = rcon.cmd(f"{OW}data get entity {selector} {path}")
    m = re.search(r"has the following entity data: (-?[\d.]+)", out)
    return float(m.group(1)) if m else None


def yaw_of(rcon, selector):
    out = rcon.cmd(f"{OW}data get entity {selector} Rotation")
    m = re.search(r"\[(-?[\d.]+)f, (-?[\d.]+)f\]", out)
    return float(m.group(1)) if m else None


def main():
    if server_pids():
        print("a dev server is still running; refusing to start another")
        return 1
    prepare_run_dir({"transit": False, "prologue": False, "crawlerPorthole": True})
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    env = dict(os.environ)
    env["JAVA_TOOL_OPTIONS"] = "-Dsurrogate.debugSeats=true"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, env=env)
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
        # The docking station: the collar door in the west wall, sealed, and a player cannot open it.
        collar = (ox - 4, oy + 1, oz + 3)
        door = rcon.cmd(f"{OW}execute if block {collar[0]} {collar[1]} {collar[2]} surrogate:dock_door[half=lower,docked=false,open=false]")
        check("collar door built and sealed", "Test passed" in door, door[:80])
        frame = rcon.cmd(f"{OW}execute if block {collar[0]} {collar[1] + 2} {collar[2]} surrogate:reinforced_glass")
        check("collar frame is glass", "Test passed" in frame, frame[:80])
        rcon.cmd(f"{OW}tp Steve {collar[0] - 1.5} {oy + 1} {collar[2] + 0.5}")
        rcon.cmd(f"{OW}player Steve look at {collar[0] + 0.5} {collar[1] + 1} {collar[2] + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Steve use once")
        time.sleep(1)
        still = rcon.cmd(f"{OW}execute if block {collar[0]} {collar[1]} {collar[2]} surrogate:dock_door[open=false]")
        check("collar door refuses to open by hand", "Test passed" in still, still[:80])
        mast = rcon.cmd(f"{OW}execute if block {ox + 4} {oy + 13} {oz - 4} minecraft:lightning_rod")
        check("mast stands on the roof", "Test passed" in mast, mast[:80])
        # The collar: couple a hull to it, and the door is the way into the cabin, not a door that swings.
        CABIN = "execute in surrogate:crawler_cabin run "
        crawler = "@e[type=surrogate:crawler,limit=1]"
        rcon.cmd("surrogate crawler dock")
        time.sleep(1)
        docked_hull = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:crawler,nbt={{Docked:1b}}]")
        check("dock command couples a hull", "Test passed" in docked_hull, docked_hull[:80])
        docked_door = rcon.cmd(f"{OW}execute if block {collar[0]} {collar[1]} {collar[2]} surrogate:dock_door[docked=true]")
        check("dock command unlocks the collar door", "Test passed" in docked_door, docked_door[:80])
        rcon.cmd(f"{OW}player Steve use once")
        time.sleep(2)
        still_shut = rcon.cmd(f"{OW}execute if block {collar[0]} {collar[1]} {collar[2]} surrogate:dock_door[open=false]")
        check("collar door never swings open", "Test passed" in still_shut, still_shut[:80])
        where = rcon.cmd("data get entity Steve Dimension")
        check("collar door puts the player in the cabin", "crawler_cabin" in where, where[:100])
        # The cabin was built at slot 0, and the ground ahead of the porthole is painted in.
        floor = rcon.cmd(f"{CABIN}execute if block 8 100 8 surrogate:hull_plating")
        check("cabin floor exists", "Test passed" in floor, floor[:80])
        helm = rcon.cmd(f"{CABIN}execute if block 8 101 3 surrogate:crawler_helm")
        check("helm is under the porthole", "Test passed" in helm, helm[:80])
        time.sleep(2)
        ground = rcon.cmd(f"{CABIN}execute unless block 8 100 0 minecraft:air")
        check("ground painted in front of the porthole", "Test passed" in ground, ground[:80])
        # The hatch: coupled, it leads through the collar into the pod.
        rcon.cmd("execute as Steve run surrogate crawler leave")
        time.sleep(1)
        where = rcon.cmd("data get entity Steve Dimension")
        pos = pos_of(rcon, "Steve")
        check("hatch leads into the pod while coupled", "overworld" in where and pos is not None and abs(pos[0] - (collar[0] + 1.5)) < 1.0, f"{where[:60]} {pos}")
        # Uncouple, go back aboard from outside, and drive from the helm.
        rcon.cmd("surrogate crawler undock")
        time.sleep(1)
        rcon.cmd("execute as Steve run surrogate crawler board")
        time.sleep(1)
        where = rcon.cmd("data get entity Steve Dimension")
        check("boarding from the pod side", "crawler_cabin" in where, where[:100])
        p0 = pos_of(rcon, crawler)
        e0 = number_of(rcon, crawler, "Energy")
        rcon.cmd("execute as Steve run surrogate crawler seat helm")
        time.sleep(0.5)
        rcon.cmd(f"{CABIN}player Steve move forward")
        time.sleep(4)
        rcon.cmd(f"{CABIN}player Steve stop")
        time.sleep(1.5)
        p2 = pos_of(rcon, crawler)
        moved = p0 and p2 and (abs(p2[0] - p0[0]) + abs(p2[2] - p0[2]))
        check("helm drives the hull", moved is not None and moved > 3.0, f"{p0} -> {p2}")
        check("hull drove the way it faces", p2 and p0 and p2[0] < p0[0] - 2 and abs(p2[2] - p0[2]) < 1.5, f"{p0} -> {p2}")
        e1 = number_of(rcon, crawler, "Energy")
        check("driving burned charge", e0 and e1 is not None and e1 < e0, f"{e0} -> {e1}")
        y0 = yaw_of(rcon, crawler)
        rcon.cmd(f"{CABIN}player Steve move forward")
        rcon.cmd(f"{CABIN}player Steve move left")
        time.sleep(3)
        rcon.cmd(f"{CABIN}player Steve stop")
        time.sleep(1)
        y1 = yaw_of(rcon, crawler)
        check("hull turns while moving", y0 is not None and y1 is not None and abs(((y1 - y0 + 180) % 360) - 180) > 10, f"{y0} -> {y1}")
        rcon.cmd("execute as Steve run surrogate crawler stand")
        # Docking from the console: line the hull up on the apron west of the collar, then reverse onto it.
        # (The apron ends twelve blocks out; any further and the hull sits in the natural ground.)
        rcon.cmd(f"{OW}tp {crawler} {collar[0] - 9.5} {oy + 1} {collar[2] + 0.5} 90 0")
        rcon.cmd(f"{OW}data merge entity {crawler} {{Speed:0f}}")
        time.sleep(1)
        rcon.cmd("execute as Steve run surrogate crawler seat dock")
        time.sleep(0.5)
        # The console faces aft: W backs the hull up. Coupling wants a click on the console once the ring is
        # inside the window, so the fake player clicks the console every half second while it backs down.
        # Clicks off the window are refused with a clunk and do nothing.
        rcon.cmd(f"{CABIN}player Steve look at 9.5 101.0 13.5")
        rcon.cmd(f"{CABIN}player Steve move forward")
        coupled = False
        for i in range(44):
            time.sleep(0.5)
            # The click: through the console block on even turns, through the lock command on odd ones
            # (the block path is what a real client hits; the command is what its Control packet does).
            if i % 2 == 0:
                rcon.cmd(f"{CABIN}player Steve use once")
            else:
                rcon.cmd("execute as Steve run surrogate crawler lock")
            out = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:crawler,nbt={{Docked:1b}}]")
            if i % 2 == 0:
                print("DOCKING", pos_of(rcon, crawler), number_of(rcon, crawler, "Speed"), yaw_of(rcon, crawler))
            if "Test passed" in out:
                coupled = True
                break
        rcon.cmd(f"{CABIN}player Steve stop")
        check("W from the docking console backs onto the collar and a click couples", coupled, str(pos_of(rcon, crawler)))
        door_docked = rcon.cmd(f"{OW}execute if block {collar[0]} {collar[1]} {collar[2]} surrogate:dock_door[docked=true]")
        check("coupling unlocks the collar", "Test passed" in door_docked, door_docked[:80])
        rcon.cmd(f"{CABIN}player Steve move backward")
        time.sleep(1.5)
        rcon.cmd(f"{CABIN}player Steve stop")
        time.sleep(0.5)
        uncoupled = rcon.cmd(f"{OW}execute if entity @e[type=surrogate:crawler,nbt={{Docked:0b}}]")
        check("S from the docking console uncouples", "Test passed" in uncoupled, uncoupled[:80])
        rcon.cmd("execute as Steve run surrogate crawler stand")
        # The bay: a chassis used on it is assembled outside beside the hull.
        rcon.cmd(f"{CABIN}item replace entity Steve weapon.mainhand with surrogate:robot_chassis")
        rcon.cmd(f"{CABIN}tp Steve 10.5 101 10.5")
        rcon.cmd(f"{CABIN}player Steve look at 11.5 101.2 10.5")
        time.sleep(0.5)
        rcon.cmd(f"{CABIN}player Steve use once")
        time.sleep(1.5)
        hull_now = pos_of(rcon, crawler) or (0, 64, 0)
        robots = rcon.cmd(f"{OW}execute positioned {hull_now[0]} {hull_now[1]} {hull_now[2]} if entity @e[type=surrogate:robot,distance=..12]")
        check("bay deploys a chassis outside beside the hull", "Test passed" in robots, robots[:80])
        print("CHAIR after deploy", rcon.cmd(f"{CABIN}data get block 6 101 10")[:200])
        print("ROBOT DIM", rcon.cmd(f"{OW}data get entity @e[type=surrogate:robot,limit=1] Dimension")[:120])
        # Dive from the cabin chair (keyed by the deploy) and stay out: the body in the chair must keep breathing
        # cabin air the whole time, which needs the cabin kept alive while no player stands in it.
        rcon.cmd(f"{CABIN}item replace entity Steve weapon.mainhand with minecraft:air")
        rcon.cmd(f"{CABIN}tp Steve 7.5 101 10.5")
        rcon.cmd(f"{CABIN}player Steve look at 6.5 101.4 10.5")
        time.sleep(0.5)
        rcon.cmd(f"{CABIN}player Steve use once")
        time.sleep(8)
        print("CHAIR", rcon.cmd(f"{CABIN}data get block 6 101 10")[:200])
        linked = rcon.cmd(f"{OW}data get entity @e[type=surrogate:robot,limit=1] Pilot")
        check("chair links the pilot into the deployed chassis", "[I;" in linked, linked[:100])
        time.sleep(30)
        alive = rcon.cmd(f"{OW}execute if entity Steve")
        health = number_of(rcon, "Steve", "Health")
        check("pilot survives half a minute out with the body in the cabin chair", "Test passed" in alive and health is not None and health > 0, f"{alive[:40]} health {health}")
        rcon.cmd("execute as Steve run surrogate crawler board")
        rcon.cmd(f"{OW}kill @e[type=surrogate:robot]")
        time.sleep(1)
        # Back outside through the hatch (uncoupled), beside the hull.
        rcon.cmd("execute as Steve run surrogate crawler leave")
        time.sleep(1)
        where = rcon.cmd("data get entity Steve Dimension")
        p6 = pos_of(rcon, crawler)
        ps = pos_of(rcon, "Steve")
        check("hatch leads outside when uncoupled", "overworld" in where and ps is not None and p6 is not None and abs(ps[0] - p6[0]) + abs(ps[2] - p6[2]) < 12, f"{where[:60]} {ps} {p6}")
        # The researcher: the first shelter sits within a chassis walk of the pod and has a collar in its west
        # wall and a chassis port in its south wall. A crawler coupled to the collar takes the survivor
        # aboard; coupled at home, they step into the pod. A chassis at the port is handed the blueprint.
        m = re.search(r"Survivor site for okafor at x=(-?\d+), z=(-?\d+)", open(LOG, encoding="utf-8", errors="replace").read())
        check("first shelter placed", m is not None)
        if m:
            sx, sz = int(m.group(1)), int(m.group(2))
            reach = ((sx - ox) ** 2 + (sz - oz) ** 2) ** 0.5
            check("first shelter within a chassis walk", 100 < reach < 400, f"{reach:.0f} blocks")
        out = ""
        for _ in range(6):
            out = rcon.cmd("surrogate crawler dockat 0")
            if "coupled at shelter" in out:
                break
            time.sleep(2)
        m = re.search(r"\((-?\d+), (-?\d+), (-?\d+)\)", out)
        check("crawler couples at the first shelter", m is not None, out[:120])
        door = tuple(int(v) for v in m.groups()) if m else (0, 64, 0)
        shelter_collar = rcon.cmd(f"{OW}execute if block {door[0]} {door[1]} {door[2]} surrogate:dock_door[docked=true]")
        check("shelter has a collar and it unlocked", "Test passed" in shelter_collar, shelter_collar[:80])
        # (The cabin is empty, so its chunks and the passenger in them unload until someone boards; the log says.)
        boarded = wait_for_log(r"okafor boarded the crawler", 6, proc)
        check("survivor boards the coupled crawler", boarded is not None)
        hull_now = pos_of(rcon, crawler) or (door[0] - 4.5, door[1], door[2] + 0.5)
        rcon.cmd(f"{OW}tp Steve {hull_now[0]} {hull_now[1]} {hull_now[2] + 4}")
        time.sleep(0.5)
        rcon.cmd("execute as Steve run surrogate crawler board")
        time.sleep(1)
        # A chassis out of the bay, dived into from the chair, then set down at the port.
        rcon.cmd(f"{CABIN}item replace entity Steve weapon.mainhand with surrogate:robot_chassis")
        rcon.cmd(f"{CABIN}tp Steve 10.5 101 10.5")
        rcon.cmd(f"{CABIN}player Steve look at 11.5 101.2 10.5")
        time.sleep(0.5)
        rcon.cmd(f"{CABIN}player Steve use once")
        time.sleep(1.5)
        rcon.cmd(f"{CABIN}item replace entity Steve weapon.mainhand with minecraft:air")
        rcon.cmd(f"{CABIN}tp Steve 7.5 101 10.5")
        rcon.cmd(f"{CABIN}player Steve look at 6.5 101.4 10.5")
        time.sleep(0.5)
        rcon.cmd(f"{CABIN}player Steve use once")
        time.sleep(6)
        port = (door[0] + 5, door[1], door[2] + 3)
        rcon.cmd(f"{OW}tp @e[type=surrogate:robot,limit=1] {port[0] + 0.5} {port[1]} {port[2] + 2.5}")
        time.sleep(1)
        linked = rcon.cmd(f"{OW}data get entity @e[type=surrogate:robot,limit=1] Pilot")
        check("pilot link survives the chassis being moved to the port", "[I;" in linked, linked[:100])
        rcon.cmd(f"{OW}player Steve look at {port[0] + 0.5} {port[1] + 0.5} {port[2] + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{OW}player Steve use once")
        # The line reads "okafor handed <item> to Steve" since there are four handovers rather than one; the
        # item is named in it, so the check still asserts which one came across and not merely that one did.
        handed = wait_for_log(r"okafor handed (\S+) to Steve", 8, proc)
        check("chassis at the port is handed the crawler blueprint",
              handed is not None and "blueprint" in handed.group(1),
              handed.group(1) if handed else "no handover line")
        rcon.cmd("execute as Steve run surrogate crawler board")
        rcon.cmd(f"{OW}kill @e[type=surrogate:robot]")
        time.sleep(1)
        out = ""
        for _ in range(6):
            out = rcon.cmd("surrogate crawler dockat home")
            if "coupled at home" in out:
                break
            time.sleep(2)
        check("crawler couples at home", "coupled at home" in out, out[:120])
        time.sleep(2)
        home = rcon.cmd(f"{OW}execute positioned {collar[0] + 1} {collar[1]} {collar[2]} if entity @e[type=surrogate:survivor,distance=..6]")
        check("survivor steps into the pod at home", "Test passed" in home, home[:80])
        rescued = wait_for_log(r"okafor rescued: home at", 3, proc)
        check("survivor marked rescued", rescued is not None)
        rcon.cmd("execute as Steve run surrogate crawler leave")
        time.sleep(1)
        p6 = pos_of(rcon, crawler)
        # Leave the world in daylight with a charged hull on the collar, so tools/dev_client.py can look at it.
        rcon.cmd("time set 6000")
        rcon.cmd(f"{OW}data merge entity {crawler} {{Energy:100000}}")
        # Whoever opens this world next in the client is there to look, not to breathe.
        rcon.cmd("defaultgamemode creative")
        print("CRAWLER_AT", p6)
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
        print("---- seat lines ----")
        for l in text.splitlines():
            if "Seat " in l:
                print(l.strip()[:240])
        print("---- log lines of interest ----")
        for l in issues[:40]:
            print(l.strip()[:300])
        if not clean:
            print("NOTE: the server hung in shutdown and was killed")
    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
