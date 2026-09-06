"""Boots the dev server on the Toxic Wastes preset with the story in fast mode, spawns a Carpet fake player
so the habitat and the ship are built, and lets the week aboard the Provender run through with a passenger who
never lifts a finger: every objective times out, the crew do it for him, the doctor sedates him every night,
and at the end the drop pod hands him to the pod opening on the ground. Asserts on each day and on the ship."""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, Rcon, prepare_run_dir  # noqa: E402

TRANSIT = "execute in surrogate:transit run "
ORIGIN = (0, 100, 0)


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


def rel(x, y, z):
    return f"{ORIGIN[0] + x} {ORIGIN[1] + y} {ORIGIN[2] + z}"


def main():
    prepare_run_dir()
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
        if not wait_for(r"Built the starter habitat at BlockPos", 60, "habitat built"):
            # A Carpet fake player still aboard keeps the shutdown from finishing: remove it first.
            rcon.cmd("player Steve kill")
            time.sleep(2)
            rcon.cmd("stop")
            return
        if not wait_for(r"Transit: built the Provender", 30, "ship built"):
            failures.append("ship not built")
        if not wait_for(r"Transit: begins for Steve", 20, "transit begins"):
            failures.append("transit did not begin")
        time.sleep(3)
        out = rcon.cmd("data get entity Steve Dimension")
        if "surrogate:transit" not in out:
            failures.append("Steve is not aboard")
        expect(f"{TRANSIT}execute if entity @e[type=surrogate:crew,limit=15]", "fifteen crew aboard (three crew, twelve sleepers)")
        expect(f"{TRANSIT}execute if entity @e[type=surrogate:crew,nbt={{Seated:1b}},limit=12]", "twelve sleepers seated")
        expect(f"{TRANSIT}execute if entity @e[type=surrogate:robot]", "calibration unit in the hold")
        expect(f"{TRANSIT}execute if block {rel(6, 1, -9)} surrogate:dive_chair", "link chair")
        expect(f"{TRANSIT}execute if block {rel(7, 2, 12)} surrogate:hull_plating", "hull whole before turnover")
        expect(f"{TRANSIT}execute if block {rel(-3, 1, -21)} surrogate:reinforced_glass", "bridge window")
        # The scrubbers should have the ship sealed within a couple of scans.
        sealed = False
        for _ in range(6):
            time.sleep(2)
            if "passed" in rcon.cmd(f"{TRANSIT}execute if block {rel(7, 1, -5)} surrogate:life_support[sealed=true]"):
                sealed = True
                break
        if not sealed:
            failures.append("forward compartment not sealed")
            rcon.cmd(f"{TRANSIT}data get block {rel(7, 1, -5)}")
        if "passed" not in rcon.cmd(f"{TRANSIT}execute if block {rel(6, 1, 5)} surrogate:life_support[sealed=true]"):
            time.sleep(3)
            expect(f"{TRANSIT}execute if block {rel(6, 1, 5)} surrogate:life_support[sealed=true]", "hold sealed")

        # Day one: Steve ignores the galley, then actually goes to bed once he is allowed to.
        wait_for(r"Transit: free1b", 240, "bridge scene done")
        time.sleep(24)
        rcon.cmd(f"{TRANSIT}tp Steve {ORIGIN[0] - 4.5} {ORIGIN[1] + 1} {ORIGIN[2] - 11.5}")
        rcon.cmd(f"{TRANSIT}player Steve look at {rel(-6.5, 1.3, -11.5)}")
        time.sleep(0.5)
        rcon.cmd(f"{TRANSIT}player Steve use once")
        time.sleep(1.5)
        slept = "passed" in rcon.cmd(f"{TRANSIT}execute if data entity Steve sleeping_pos")
        if not slept:
            rcon.cmd(f"{TRANSIT}player Steve use once")
            time.sleep(1.5)
            slept = "passed" in rcon.cmd(f"{TRANSIT}execute if data entity Steve sleeping_pos")
        print("[info] Steve went to bed himself" if slept else "[info] Steve would not sleep; the doctor will")
        if not wait_for(r"Transit: day 2", 400, "day two"):
            failures.append("day two never came")
        if slept and "rest_forced" in log_text():
            failures.append("Steve slept but the day was still forced")
        # Day two: the calibration dive.
        if not wait_for(r"Transit: free2b", 400, "calibration done"):
            failures.append("calibration never finished")
        out = rcon.cmd("data get entity Steve Dimension")
        if "surrogate:transit" not in out:
            failures.append("Steve left the ship during calibration")
        if not wait_for(r"Transit: day 3", 400, "day three"):
            failures.append("day three never came")
        # Day three: the coolant lever and chair seven.
        if not wait_for(r"Transit: free3c", 500, "chair seven"):
            failures.append("chair seven scene never finished")
        time.sleep(2)
        expect(f"{TRANSIT}execute if block {rel(-6, 2, 19)} minecraft:lever[powered=true]", "coolant bypass closed")
        expect(f"{TRANSIT}execute if entity @e[type=surrogate:crew,nbt={{Collapsed:1b}}]", "Vasquez on the floor")
        if not wait_for(r"Transit: day 4", 400, "day four"):
            failures.append("day four never came")
        # Day four: turnover and the breach.
        if not wait_for(r"Transit: the hold is breached", 700, "hold breached"):
            failures.append("no breach")
        time.sleep(4)
        expect(f"{TRANSIT}execute if block {rel(6, 1, 5)} surrogate:life_support[sealed=false]", "hold reads breached")
        if not wait_for(r"Transit: free4b", 60, "patch objective"):
            failures.append("patch objective never came")
        out = rcon.cmd("data get entity Steve Inventory")
        if "surrogate:rebreather" not in out:
            failures.append("Steve was not given a rebreather")
        # Teague patches it when Steve does not.
        patched = False
        for _ in range(60):
            time.sleep(3)
            if "passed" in rcon.cmd(f"{TRANSIT}execute if block {rel(7, 2, 12)} surrogate:hull_plating"):
                patched = True
                break
        if not patched:
            failures.append("breach never patched")
        if not wait_for(r"Transit: day 5", 500, "day five"):
            failures.append("day five never came")
        time.sleep(2)
        if "passed" in rcon.cmd(f"{TRANSIT}execute if entity @e[type=surrogate:crew,nbt={{Collapsed:1b}}]"):
            failures.append("body still in the hold on day five")
        expect(f"{TRANSIT}execute if entity @e[type=surrogate:crew,nbt={{Seated:1b}},limit=11]", "eleven sleepers left")
        if not wait_for(r"Transit: day 6", 500, "day six"):
            failures.append("day six never came")
        if not wait_for(r"Transit: day 7", 500, "day seven"):
            failures.append("day seven never came")
        if not wait_for(r"Transit: drop", 400, "the drop"):
            failures.append("drop never came")
        if not wait_for(r"Transit: complete, landing Steve", 200, "landing"):
            failures.append("never landed")
        if not wait_for(r"Prologue: begins for Steve", 30, "pod opening begins"):
            failures.append("pod opening did not begin after landing")
        time.sleep(3)
        out = rcon.cmd("data get entity Steve Dimension")
        if "minecraft:overworld" not in out:
            failures.append("Steve is not on the ground")
        ow = "execute in minecraft:overworld run "
        wait_for(r"Prologue: day1", 90, "pod opening under way")
        out = rcon.cmd(f"{ow}execute if entity @e[type=minecraft:cat,distance=..64]")
        if "count: 1" not in out:
            failures.append("expected exactly one cat after the landing: " + out.strip())
        # Replay: the week starts over and the drop comes again; there must still be one cat and a fresh opening.
        rcon.cmd("execute as Steve run surrogate transit start")
        if not wait_for(r"(?s)Transit: begins for Steve.*Transit: begins for Steve", 30, "week replayed"):
            failures.append("replay did not start")
        time.sleep(2)
        rcon.cmd("execute as Steve run surrogate transit skip")
        if not wait_for(r"(?s)Transit: complete, landing Steve.*Transit: complete, landing Steve", 240, "landed again"):
            failures.append("replay never landed")
        if not wait_for(r"(?s)Prologue: begins for Steve.*Prologue: begins for Steve", 30, "opening told again"):
            failures.append("replayed landing did not restart the opening")
        wait_for(r"(?s)Prologue: day1.*Prologue: day1", 90, "second opening under way")
        time.sleep(2)
        out = rcon.cmd(f"{ow}execute if entity @e[type=minecraft:cat,distance=..64]")
        if "count: 1" not in out:
            failures.append("cat duplicated on the replayed drop: " + out.strip())
        rcon.cmd("surrogate transit status")
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
        print("---- transit lines ----")
        for l in text.splitlines():
            if "Transit" in l or "Prologue:" in l:
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
