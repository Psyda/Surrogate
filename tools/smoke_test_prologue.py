"""Boots the dev server on the Toxic Wastes preset with the ship week switched off and the story in fast mode,
spawns a Carpet fake player so the habitat is built, and drives the three days on the ground through to the end:
Steve answers the radio, ignores the chassis and the chair (the pod arm and Marsh do it for him), sits in the
chair, never leaves, sleeps through fast-forwarded nights, sees Halloran's chassis and its crate, drops a sulfur
sample in the assay crate, lets Marsh in by doing nothing, and hears the first survivor. Asserts on each day."""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, Rcon, prepare_run_dir  # noqa: E402


def log_text():
    with open(LOG, encoding="utf-8", errors="replace") as f:
        return f.read()


def wait_for(pattern, timeout, what):
    start = time.time()
    while time.time() - start < timeout:
        m = re.search(pattern, log_text())
        if m:
            print(f"[ok] {what} after {int(time.time() - start)} s")
            return m
        time.sleep(0.5)
    print(f"[FAIL] {what}: no match for {pattern!r} within {timeout} s")
    return None


def count(out):
    m = re.search(r"count: (\d+)", out)
    return int(m.group(1)) if m else (1 if "passed" in out else 0)


def main():
    prepare_run_dir({"transit": False})
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    failures = []

    def expect(command, what):
        out = rcon.cmd(command)
        if "passed" not in out:
            failures.append(what)
        return out

    try:
        if not wait_for(r"Done \(.*For help", 480, "server ready"):
            return
        time.sleep(2)
        rcon = Rcon()
        rcon.cmd("surrogate prologue fast")
        rcon.cmd("player Steve spawn")
        m = wait_for(r"Built the starter habitat at BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}", 60, "habitat built")
        if not m:
            # A Carpet fake player still aboard keeps the shutdown from finishing: remove it first.
            rcon.cmd("player Steve kill")
            time.sleep(2)
            rcon.cmd("stop")
            return
        ox, oy, oz = (int(v) for v in m.groups())
        ow = "execute in minecraft:overworld run "
        if not wait_for(r"Site Two \(Halloran\) at x=", 10, "Site Two chosen"):
            failures.append("no Site Two")
        if not wait_for(r"Prologue: begins for Steve", 20, "prologue begins"):
            failures.append("prologue did not begin")
            # A Carpet fake player still aboard keeps the shutdown from finishing: remove it first.
            rcon.cmd("player Steve kill")
            time.sleep(2)
            rcon.cmd("stop")
            return
        # The layout around the pod: the assay crate with its sign, the slab, the collar sign, and no annex.
        expect(f"{ow}execute if block {ox + 2} {oy + 1} {oz + 8} minecraft:chest", "assay crate")
        expect(f"{ow}execute if block {ox + 2} {oy + 1} {oz + 7} minecraft:dark_oak_wall_sign", "assay sign")
        expect(f"{ow}execute if block {ox + 8} {oy} {oz} surrogate:hull_plating", "module two slab")
        expect(f"{ow}execute if block {ox + 6} {oy + 1} {oz} minecraft:dark_oak_sign", "module sign")
        expect(f"{ow}execute if block {ox - 6} {oy + 1} {oz} minecraft:dark_oak_sign", "dock sign")
        if "passed" in rcon.cmd(f"{ow}execute if block {ox + 7} {oy + 1} {oz - 4} surrogate:life_support"):
            failures.append("an annex was built at the starter pod")
        if count(rcon.cmd(f"{ow}execute if entity @e[type=surrogate:crew,distance=..60]")) != 0:
            failures.append("crew present at the pod on day one")

        # ---- Day one. Steve answers the radio, then does nothing; the pod and Marsh work around him.
        wait_for(r"Prologue: day1", 60, "day one")
        time.sleep(1)
        rcon.cmd(f"{ow}item replace entity Steve weapon.mainhand with surrogate:field_radio")
        time.sleep(0.5)
        rcon.cmd(f"{ow}player Steve use once")
        if not wait_for(r"Prologue: radio answered", 15, "radio answered"):
            rcon.cmd(f"{ow}player Steve use once")
            if not wait_for(r"Prologue: radio answered", 15, "radio answered (second try)"):
                failures.append("the radio hook did not fire")
        rcon.cmd(f"{ow}item replace entity Steve weapon.mainhand with minecraft:air")
        wait_for(r"Prologue: kit", 120, "kit beat")
        wait_for(r"Prologue: bind", 120, "bind beat (the pod arm deployed the chassis)")
        if "passed" not in rcon.cmd(f"{ow}execute if entity @e[type=surrogate:robot,limit=1,nbt={{Scripted:0b}}]"):
            failures.append("pod arm did not deploy a chassis")
        wait_for(r"Prologue: dive", 120, "dive beat (Marsh keyed the chair)")
        chair = (ox - 3, oy + 1, oz + 2)
        if "Robot" not in rcon.cmd(f"{ow}data get block {chair[0]} {chair[1]} {chair[2]}"):
            failures.append("chair not bound")
        # Now Steve does sit down.
        time.sleep(2)
        rcon.cmd(f"{ow}tp Steve {chair[0] + 1.5} {oy + 1} {chair[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve look at {chair[0] + 0.5} {chair[1] + 0.4} {chair[2] + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1.5)
        out = rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,nbt={{Scripted:0b}},limit=1] Pilot")
        if "[I;" not in out:
            rcon.cmd(f"{ow}player Steve use once")
            time.sleep(1.5)
            out = rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,nbt={{Scripted:0b}},limit=1] Pilot")
        if "[I;" not in out:
            failures.append("Steve did not link")
        wait_for(r"Prologue: outside", 120, "outside beat (Steve is online)")
        # He never leaves; the lesson times out into the night.
        wait_for(r"Prologue: night1", 180, "night one")
        if not wait_for(r"Prologue: stage 2", 120, "day two reached"):
            failures.append("day two never dawned")

        # ---- Day two. Her chassis walks in with the crate; Steve puts sulfur in the assay crate.
        wait_for(r"Prologue: day2", 30, "day two beat")
        if not wait_for(r"Prologue: chassis on the pad", 120, "chassis on the pad"):
            failures.append("chassis never reached the pad")
        time.sleep(1)
        if "passed" not in rcon.cmd(f"{ow}execute if entity @e[type=surrogate:robot,nbt={{Scripted:1b}},distance=..40]"):
            failures.append("no scripted chassis near the pad")
        wait_for(r"Prologue: assay", 120, "assay beat")
        time.sleep(1)
        expect(f"{ow}execute if block {ox - 2} {oy + 1} {oz + 10} minecraft:chest", "crate unloaded on the pad")
        out = rcon.cmd(f"{ow}data get block {ox - 2} {oy + 1} {oz + 10} Items[0].id")
        if "written_book" not in out:
            failures.append("no book in the crate")
        rcon.cmd(f"{ow}item replace block {ox + 2} {oy + 1} {oz + 8} container.0 with surrogate:sulfur 1")
        if not wait_for(r"Prologue: sample logged", 60, "sample logged"):
            failures.append("sample not logged")
        wait_for(r"Prologue: colony", 240, "colony beat")
        if not wait_for(r"Prologue: stage 3", 240, "day three reached"):
            failures.append("day three never dawned")
        time.sleep(2)
        if "passed" in rcon.cmd(f"{ow}execute if entity @e[type=surrogate:robot,nbt={{Scripted:1b}}]"):
            failures.append("her chassis did not go home")

        # ---- Day three. Marsh walks in, fixes the dock, leaves his clipboard.
        wait_for(r"Prologue: day3", 30, "day three beat")
        if not wait_for(r"Prologue: Marsh is walking", 60, "Marsh spawned"):
            failures.append("Marsh never set out")
        if not wait_for(r"Prologue: dock", 240, "Marsh inside (dock beat)"):
            failures.append("Marsh never got inside")
        time.sleep(1)
        n = count(rcon.cmd(f"{ow}execute if entity @e[type=surrogate:crew,distance=..20]"))
        if n != 1:
            failures.append(f"expected Marsh alone in the pod, found {n} crew")
        dock = (ox - 1, oy + 1, oz - 3)
        if not wait_for(r"Prologue: dock fixed", 120, "dock fixed"):
            failures.append("dock never fixed")
        rcon.cmd(f"{ow}data get block {dock[0]} {dock[1]} {dock[2]}")
        if not wait_for(r"Prologue: Marsh has gone home", 240, "Marsh gone"):
            failures.append("Marsh never left")
        time.sleep(1)
        if "passed" not in rcon.cmd(f"{ow}execute if entity @e[type=minecraft:item,nbt={{Item:{{id:\"minecraft:written_book\"}}}},distance=..12]"):
            failures.append("no clipboard left on the table")
        if count(rcon.cmd(f"{ow}execute if entity @e[type=surrogate:crew,distance=..60]")) != 0:
            failures.append("a crew member is still at the pod after the visit")
        wait_for(r"Prologue: finale", 120, "finale beat")
        if not wait_for(r"Prologue: radio call survivor.surrogate.okafor.radio", 120, "radio call"):
            failures.append("no radio call")
        if not wait_for(r"Prologue: complete", 120, "prologue complete"):
            failures.append("prologue did not complete")
        rcon.cmd("surrogate prologue status")
        # A Carpet fake player still aboard keeps the shutdown from finishing: remove it first.
        rcon.cmd("player Steve kill")
        time.sleep(2)
        rcon.cmd("stop")
    finally:
        for _ in range(40):
            if proc.poll() is not None:
                break
            time.sleep(3)
        if proc.poll() is None:
            proc.kill()
        log.close()
        text = log_text()
        issues = [l for l in text.splitlines() if re.search(r"ERROR|Exception|Failed|error in", l)]
        print("---- log lines of interest ----")
        for l in issues[:60]:
            print(l.strip()[:300])
        print("---- prologue lines ----")
        for l in text.splitlines():
            if "Prologue" in l or "Site Two" in l:
                print(l.strip()[:200])
        print("---- result ----")
        if failures:
            print("FAILURES:")
            for f in failures:
                print("  ", f)
        else:
            print("ALL CHECKS PASSED")


if __name__ == "__main__":
    main()
