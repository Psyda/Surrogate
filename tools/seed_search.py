#!/usr/bin/env python3
"""Boots the dev server with nothing scripted running and asks the mod to score a run of candidate seeds, so
one map can be chosen for the campaign and written down. Prints the ranked table the mod produced, the
winner and the two lines to paste, and copies run/seed_search.json to build/seed_search.json.

    python tools/seed_search.py [count] [from]        (default 240 1)
    python tools/seed_search.py 240 1 --player        also joins a fake player, to watch the world build

The rubric lives in SeedSearch.java; this drives it and reports, and knows nothing about what a good map is
beyond the floor below. A seed costs about a second of one core and the sweep runs on the server thread, so
the server sits stalled for the whole of it and RCON hears nothing back until it is done: the default 240
seeds is a couple of minutes on an eight-core box, 4096 is most of an hour.

Exits 1 when the server or the command failed, 2 when the sweep came back empty or under the floor.
"""
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from smoke_test_server import LOG, ROOT, RUN, SCRATCH, Rcon, prepare_run_dir  # noqa: E402
from terrain_scan import reap_server, server_pids, wait_for_log  # noqa: E402

# What the winner has to be worth before this exits clean, out of the 100 SeedSearch.java hands out. A
# flawless near side -- pod 15, sites 20, drive 15, wall 5, var 5 -- is worth exactly 60 on its own, so 60 is
# the line above which a seed must have found some part of act five: a far side, a crossing, a belt behind
# it. Under it the sweep found somewhere to live and nowhere for the campaign to end, and a wider run is
# wanted rather than a locked seed. Real winners land in the low seventies.
FLOOR = 60.0

# The command caps count at 4096 (TerrainScan.java); catch it here rather than at the parser.
MAX_COUNT = 4096

# Where run/seed_search.json is written and where it is kept.
OUT = "seed_search.json"


def drain(rcon, seconds=2.0):
    """Whatever is left of a reply that Rcon.cmd only read the first packet of. RCON splits at about four
    kilobytes and twelve scored rows can pass that, so the rest is swallowed here instead of being left in
    the socket for the next command to read as its own reply. Costs `seconds` of waiting, once."""
    rcon.sock.settimeout(seconds)
    rest = []
    try:
        while True:
            part = rcon._recv()
            if not part:
                break
            rest.append(part)
    except (OSError, struct.error):
        pass
    return "".join(rest)


def log_table():
    """The ranked rows out of the server log, for when the RCON reply came back short or empty. The mod logs
    every line it sends as feedback, so the log is the copy that cannot be truncated by a packet boundary."""
    with open(LOG, encoding="utf-8", errors="replace") as f:
        text = f.read()
    rows = []
    for line in text.splitlines():
        m = re.search(r"(Seeds[:\s].*)$", line.strip())
        if m and "Seed sweep failed" not in line:
            rows.append(m.group(1)[:300])
    return rows


def report(data, wall):
    """Prints the winner, why it won and the two lines the user pastes. Returns its score, or None when the
    sweep scored nothing at all."""
    ranked = data.get("ranked") or []
    winner = data.get("winner")
    if not ranked or not winner:
        print("the sweep scored nothing: no candidate came back")
        return None
    seed = winner["seed"]
    total = float(winner.get("total", 0.0))
    seconds = float(data.get("seconds", 0.0))
    rate = data["count"] / seconds if seconds > 0 else 0.0
    print()
    print(f"---- {data['count']} seeds from {data['from']}, {seconds:.0f} s in the sweep, "
          f"{wall:.0f} s of wall clock, {rate:.1f} seeds/s ----")
    print(f"read at {data.get('fidelity', 'unknown fidelity')}")
    print()
    print(f"Winner: seed {seed}   score {total:.1f} of 100   (floor {FLOOR:.0f})")
    for name, part in (winner.get("parts") or {}).items():
        print(f"    {name:<6} {part.get('points', 0):>5} / {part.get('max', 0):<5} {part.get('detail', '')}")
    for name in ("spawn", "pod", "crossing"):
        point = winner.get(name)
        if point:
            print(f"    {name:<6} x={point['x']} z={point['z']}")
    if winner.get("note"):
        print(f"    note   {winner['note']}")
    print()
    print("Paste these two, then delete run/toxic_test so the world is regenerated:")
    print()
    print("  run/server.properties")
    print(f"      level-seed={seed}")
    print("  run/config/surrogate.json")
    print(f'      "lockedSeed": "{seed}",')
    print()
    print("A winner is a coarse reading. Boot it once and run  /surrogate terrain scan  before trusting it.")
    return total


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = {a for a in sys.argv[1:] if a.startswith("--")}
    count = int(args[0]) if len(args) > 0 else 240
    start_seed = int(args[1]) if len(args) > 1 else 1
    # --player: join a Carpet fake player first, so the pod and the sites get built and the log shows them.
    # Off by default: the sweep reads seeds out of the registry and wants no world of its own, and a fake
    # player logged in through a stall of several minutes is one more thing that can go wrong.
    with_player = "--player" in flags
    if not 1 <= count <= MAX_COUNT:
        print(f"count must be 1..{MAX_COUNT}; the command will not take {count}")
        return 1
    stale = server_pids()
    if stale:
        print("a dev server is still running; refusing to start another:", stale)
        return 1
    # A sweep from a previous run would otherwise be read back as this one's.
    prior = os.path.join(RUN, OUT)
    if os.path.exists(prior):
        os.remove(prior)
    # Everything scripted off: the transit week, the prologue, the neighbours' research and Contract Seven.
    # Nothing here needs them and the first join is quicker without.
    prepare_run_dir({"transit": False, "prologue": False, "research": False, "assay": False})
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    start = time.time()
    score = None
    reply = ""
    wall = 0.0
    try:
        if wait_for_log(r"Done \(.*For help", 480, proc) is None:
            print("server never reported Done")
            return 1
        print(f"server ready after {int(time.time() - start)} s")
        time.sleep(3)
        rcon = Rcon()
        if with_player:
            rcon.cmd("player Steve spawn")
            if wait_for_log(r"Built the starter habitat at BlockPos", 120, proc) is None:
                print("no habitat; carrying on with the sweep anyway")
            # Removed again before the stall: a Carpet fake player still aboard keeps the shutdown from
            # finishing, and there is nothing for one to do while the server thread is busy scoring.
            rcon.cmd("player Steve kill")
            time.sleep(2)
        # The reply only arrives when the sweep is done, and the server thread is busy for all of it. Four
        # seconds a seed is slack enough for a four-core box; server.properties already has max-tick-time=-1,
        # so the watchdog will not shoot the server out from under it.
        rcon.sock.settimeout(max(600, count * 4))
        print(f"sweeping {count} seeds from {start_seed}; the server is stalled until it finishes")
        sys.stdout.flush()
        began = time.time()
        reply = rcon.cmd(f"surrogate terrain seeds {count} {start_seed}")
        rest = drain(rcon)
        wall = time.time() - began
        rcon.sock.settimeout(30)  # drain left it at two seconds; `stop` still wants a reply.
        if rest:
            print(rest)
        if "Seeds" not in reply and "Seeds" not in rest:
            print("---- ranked table, from the log ----")
            for row in log_table():
                print(row)
        src = os.path.join(RUN, OUT)
        data = None
        if os.path.exists(src):
            os.makedirs(SCRATCH, exist_ok=True)
            dst = os.path.join(SCRATCH, OUT)
            shutil.copyfile(src, dst)
            with open(src, encoding="utf-8") as f:
                data = json.load(f)
            print("sweep copied to", dst)
        else:
            print(f"the mod wrote no {OUT}; the sweep failed or the command was not found")
        if data is not None:
            score = report(data, wall)
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
            score = None
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        issues = [l for l in text.splitlines() if re.search(r"ERROR|Exception|Failed|error in", l)]
        if issues:
            print("---- log lines of interest ----")
            for l in issues[:40]:
                print(l.strip()[:300])
    if score is None:
        print("RESULT: no sweep")
        return 2
    ok = score >= FLOOR
    print(f"RESULT: {'seed found' if ok else 'nothing good enough'} "
          f"({score:.1f} of 100, floor {FLOOR:.0f})")
    if not ok:
        print("Widen the run: try a different [from], or ask for more seeds.")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
