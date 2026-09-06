"""Profiles the dedicated server with the vanilla `/perf` recorder (a fixed ten second window) at three
points of the week aboard the ship: day one loose on the ship, day four while the hold is venting, and after
the patch. Boots the server with Carpet, spawns a fake player so the ship is built, and prints the busiest
sections of each report (the profiling.txt inside run/debug/profiling/*.zip) plus the tick times recorded."""
import glob
import io
import os
import re
import subprocess
import sys
import time
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, RUN, Rcon, prepare_run_dir  # noqa: E402

WINDOW_SECONDS = 14
PROFILING = os.path.join(RUN, "debug", "profiling")


def log_text():
    with open(LOG, encoding="utf-8", errors="replace") as f:
        return f.read()


def wait_for(pattern, timeout, what):
    start = time.time()
    while time.time() - start < timeout:
        if re.search(pattern, log_text()):
            print(f"[ok] {what} after {int(time.time() - start)} s")
            return True
        time.sleep(0.5)
    print(f"[FAIL] {what}: no match for {pattern!r} within {timeout} s")
    return False


def profile(rcon, label):
    before = set(glob.glob(os.path.join(PROFILING, "*.zip")))
    out = rcon.cmd("perf start")
    print(f"---- {label}: {out.strip()}")
    for _ in range(WINDOW_SECONDS * 2):
        time.sleep(0.5)
        new = [p for p in glob.glob(os.path.join(PROFILING, "*.zip")) if p not in before]
        if new:
            time.sleep(1)
            break
    new = [p for p in glob.glob(os.path.join(PROFILING, "*.zip")) if p not in before]
    if not new:
        print("no report written")
        return
    report(sorted(new)[-1], label)


def report(path, label):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        text_name = next((n for n in names if n.endswith("profiling.txt")), None)
        if text_name is None:
            print("no profiling.txt in", path, names)
            return
        lines = z.read(text_name).decode("utf-8", "replace").splitlines()
        # Tick times, if the recorder sampled them: one row per tick in nanoseconds.
        for n in names:
            if n.endswith(".csv") and "tick" in os.path.basename(n).lower():
                rows = z.read(n).decode("utf-8", "replace").splitlines()
                values = []
                for row in rows[1:]:
                    parts = row.split(",")
                    for part in parts[1:]:
                        try:
                            values.append(float(part))
                        except ValueError:
                            pass
                if values:
                    values.sort()
                    ms = [v / 1e6 for v in values]
                    print(f"  {os.path.basename(n)}: {len(ms)} samples, median {ms[len(ms)//2]:.2f} ms, p95 {ms[int(len(ms)*0.95)]:.2f} ms, max {ms[-1]:.2f} ms")
    print(f"---- report {os.path.basename(path)} ({label}) ----")
    for line in lines[:12]:
        if line.startswith("Time span") or line.startswith("Tick span") or line.startswith("//"):
            print(line)
    rows = []
    for line in lines:
        m = re.match(r"\[(\d+)\]\s+((?:\|\s+)*)(.+?) - ([\d.]+)%/([\d.]+)%", line)
        if not m:
            continue
        depth = int(m.group(1))
        name = m.group(3).strip()
        of_parent = float(m.group(4))
        of_total = float(m.group(5))
        rows.append((of_total, depth, name, of_parent))
    # The sections that matter: anything over half a percent of the whole tick, deepest names first.
    rows.sort(key=lambda r: -r[0])
    seen = 0
    for of_total, depth, name, of_parent in rows:
        if of_total < 0.5 or name in ("root", "tick", "levels", "unspecified"):
            continue
        print(f"  {of_total:6.2f}% total  {of_parent:6.2f}% of parent  [{depth}] {name}")
        seen += 1
        if seen >= 45:
            break


def main():
    prepare_run_dir()
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    try:
        if not wait_for(r"Done \(.*For help", 480, "server ready"):
            return
        time.sleep(2)
        rcon = Rcon()
        rcon.cmd("surrogate transit fast")
        rcon.cmd("player Steve spawn")
        if not wait_for(r"Transit: begins for Steve", 60, "transit begins"):
            rcon.cmd("stop")
            return
        # Day one, the player loose on the ship: steady state.
        wait_for(r"Transit: free1b", 240, "day one free roam")
        time.sleep(3)
        profile(rcon, "day 1, steady state")
        time.sleep(2)
        profile(rcon, "day 1, second window")
        # Day four: jump there, let the turnover play, then profile while the hold is venting.
        rcon.cmd("execute as Steve run surrogate transit day 4")
        if wait_for(r"Transit: the hold is breached", 400, "breach"):
            time.sleep(2)
            profile(rcon, "day 4, hold venting")
        # And once it is patched and resealed.
        wait_for(r"Transit: day 5", 300, "day five")
        time.sleep(3)
        profile(rcon, "day 5, after the patch")
        rcon.cmd("stop")
    finally:
        for _ in range(40):
            if proc.poll() is not None:
                break
            time.sleep(3)
        if proc.poll() is None:
            proc.kill()
        log.close()


if __name__ == "__main__":
    main()
