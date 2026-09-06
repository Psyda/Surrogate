#!/usr/bin/env python3
"""Boots the dev server on the Toxic Wastes preset, lets the first join place the pod, Site Two and the
shelters, then asks the mod to scan the straight runs between them for the crawler and to paint a map of the
valleys. Prints the scan, copies the map to build/terrain_map.png, and exits non-zero if a run is blocked.

    python tools/terrain_scan.py [radius] [step]      (default 1024 8)
"""
import os
import re
import shutil
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, RUN, SCRATCH, Rcon, prepare_run_dir  # noqa: E402


def server_pids():
    """The dev server's own java process: Gradle's daemon spawns it, so killing the wrapper never reaches it."""
    if os.name != "nt":
        return []
    out = subprocess.run(["powershell", "-NoProfile", "-Command",
                          "Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -match 'fabric.dli.env=server' } | ForEach-Object { $_.ProcessId }"],
                         capture_output=True, text=True).stdout
    return [int(p) for p in out.split() if p.strip().isdigit()]


def reap_server(timeout):
    """Waits for the server java to exit after `stop`; a server that hangs in shutdown gets a thread dump in
    build/server_hang.txt and is killed, so the next run can bind its ports."""
    start = time.time()
    while time.time() - start < timeout:
        if not server_pids():
            return True
        time.sleep(3)
    pids = server_pids()
    if not pids:
        return True
    jstack = "C:/Program Files/Java/jdk-21/bin/jstack.exe" if os.name == "nt" else "jstack"
    with open(os.path.join(SCRATCH, "server_hang.txt"), "w", encoding="utf-8") as f:
        for pid in pids:
            try:
                f.write(subprocess.run([jstack, str(pid)], capture_output=True, text=True, timeout=60).stdout)
            except (OSError, subprocess.TimeoutExpired) as e:
                f.write(f"jstack {pid} failed: {e}\n")
    for pid in pids:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)
    print(f"server hung in shutdown; killed {pids}, thread dump in build/server_hang.txt")
    return False


def wait_for_log(pattern, timeout, proc):
    start = time.time()
    while time.time() - start < timeout:
        time.sleep(3)
        if proc.poll() is not None:
            return None
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        m = re.search(pattern, text)
        if m:
            return m
    return None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = {a for a in sys.argv[1:] if a.startswith("--")}
    radius = int(args[0]) if len(args) > 0 else 1024
    step = int(args[1]) if len(args) > 1 else 8
    # --no-player: boot and stop only; --no-scan / --no-map: skip those commands (to bisect a hang).
    with_player = "--no-player" not in flags
    with_scan = "--no-scan" not in flags
    with_map = "--no-map" not in flags
    # --keep-player: leave the fake player logged in through `stop` (the default removes it first, because a
    # Carpet fake player still aboard keeps the shutdown from finishing).
    kill_player = "--keep-player" not in flags
    stale = server_pids()
    if stale:
        print("a dev server is still running; refusing to start another:", stale)
        return 1
    prepare_run_dir({"transit": False, "prologue": False})
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    start = time.time()
    ok = False
    try:
        if wait_for_log(r"Done \(.*For help", 480, proc) is None:
            print("server never reported Done")
            return 1
        print(f"server ready after {int(time.time() - start)} s")
        time.sleep(3)
        rcon = Rcon()
        ok = True
        if with_player:
            rcon.cmd("player Steve spawn")
            if wait_for_log(r"Site Two \(Halloran\) at x=(-?\d+), z=(-?\d+)", 120, proc) is None:
                print("the sites were never placed")
                return 1
            with open(LOG, encoding="utf-8", errors="replace") as f:
                text = f.read()
            for line in text.splitlines():
                if re.search(r"starter habitat at|Site Two \(Halloran\)|Survivor shelter for|Valleys:", line):
                    print(line.strip()[:200])
            rcon.sock.settimeout(600)
            if with_scan:
                scan = rcon.cmd("surrogate terrain scan")
                ok = "every site is drivable" in scan
            if with_map:
                rcon.cmd(f"surrogate terrain map {radius} {step}")
                src = os.path.join(RUN, "terrain_map.png")
                if os.path.exists(src):
                    os.makedirs(SCRATCH, exist_ok=True)
                    dst = os.path.join(SCRATCH, "terrain_map.png")
                    shutil.copyfile(src, dst)
                    print("map copied to", dst)
            if kill_player:
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
            ok = False
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        issues = [l for l in text.splitlines() if re.search(r"ERROR|Exception|Failed|error in", l)]
        print("---- log lines of interest ----")
        for l in issues[:40]:
            print(l.strip()[:300])
        print("---- terrain lines ----")
        for l in text.splitlines():
            if "Terrain" in l or "Valleys" in l:
                print(l.strip()[:300])
    print("RESULT:", "drivable" if ok else "BLOCKED")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
