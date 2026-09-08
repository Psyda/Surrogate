#!/usr/bin/env python3
"""Headless check of acts four and five: the conference, the bridge, the belt road and the Rift floor.

Boots the dev server on the Toxic Wastes preset (ship week and prologue off), skips forward to a finished
contract, and then drives the whole rescue with the dev command, because none of this can be reached the
long way inside a test: act four is behind ten hours of play and act five is behind a bridge, a lava
channel, a blown airlock and a chasm.

What it is actually trying to catch:

  * The conference is a Director, and the two ways a Director goes wrong here are that it never starts
    (nobody on stage, or a null player) and that it starts and never finishes. Both look identical from
    outside, so the test watches for the opening line and then for the closing one with a clock on it.
  * The far side is a chain of gates, and every one of them has to move a readout when it opens. The board
    is the readout, so the test reads the board before and after each gate rather than looking at blocks.
  * Two gates have a person behind them who is supposed to refuse: Reyes will not board a hull while her
    frame is open, and Halloran will not let anybody go down the scree before Reyes is aboard. A gate that
    silently does nothing is indistinguishable from one that works, so both refusals are checked first,
    in the state where they should fire, and then again in the state where they should not.

Costs eight to twelve minutes of wall clock. Exits non-zero when a check fails.

    py -3.13 tools/smoke_test_campaign.py
"""
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

failures = []


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail else ""))
    if not ok:
        failures.append(name)


# ---------------------------------------------------------------------------- reading the world


def player_pos(rcon):
    out = rcon.cmd(f"{OW}data get entity Steve Pos")
    m = re.search(r"\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]", out)
    return tuple(float(v) for v in m.groups()) if m else None


def ensure_player(rcon):
    """Carpet drops a fake player on a long teleport, and act five is nothing but long teleports."""
    if player_pos(rcon) is not None:
        return True
    rcon.cmd("player Steve spawn")
    time.sleep(4)
    rcon.cmd(f"{OW}gamemode creative Steve")
    back = player_pos(rcon) is not None
    if not back:
        print("  (could not get Steve back)")
    return back


def status(rcon):
    return rcon.cmd(AS + "surrogate rescue status")


def board(rcon):
    """`/surrogate rescue board`, parsed. One row per person, in the order the shelters were made.

    RCON runs the lines of a multi-line reply together, so this scans the blob for the row shape rather
    than splitting on newlines.
    """
    out = rcon.cmd(AS + "surrogate rescue board")
    rows = []
    # The command prints the raw translation key rather than the sentence, which is what a test wants:
    # "board.surrogate.block.rift" says which branch fired, where the English would only say how it reads.
    # The lookahead is load bearing. With the lines run together the blocked key and the next row's "board"
    # are one unbroken run of letters, so a greedy key match swallows it and every other row disappears.
    for m in re.finditer(r"board ([a-z]+): (home|aboard|reached|unreached), scrubber (-?\d+), "
                         r"blocked (nothing|board\.surrogate\.block\.[a-z_]+?)(?=board [a-z]+:|note |$)", out):
        rows.append({"who": m.group(1), "state": m.group(2), "scrubber": int(m.group(3)),
                     "blocked": m.group(4)})
    return rows, out


def row_for(rows, who):
    for row in rows:
        if row["who"] == who:
            return row
    return {}


def shelter_index(rows, who):
    """The board's row order is the shelter order, which is what `crawler dockat <n>` counts in."""
    for index, row in enumerate(rows):
        if row["who"] == who:
            return index
    return -1


def block_is(rcon, pos, block):
    out = rcon.cmd(f"{OW}execute if block {pos[0]} {pos[1]} {pos[2]} {block}")
    return "Test passed" in out


def entity_near(rcon, pos, kind, radius=8):
    out = rcon.cmd(f"{OW}execute positioned {pos[0]} {pos[1]} {pos[2]} "
                   f"if entity @e[type={kind},distance=..{radius}]")
    m = re.search(r"count: (\d+)", out)
    return int(m.group(1)) if m else 0


def held_count(rcon, item):
    """How many of an item Steve has, without taking any: `clear <who> <item> 0` counts and removes none."""
    out = rcon.cmd(f"{OW}clear Steve {item} 0")
    m = re.search(r"Found (\d+) matching item", out)
    return int(m.group(1)) if m else 0


def log_pos(pattern, text):
    m = re.search(pattern + r"BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}", text)
    return (int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None


def log_short(pattern, text):
    """The other half of the log vocabulary: BlockPos.toShortString(), which is "x, y, z"."""
    m = re.search(pattern + r"(-?\d+), (-?\d+), (-?\d+)", text)
    return (int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None


def read_log():
    with open(LOG, encoding="utf-8", errors="replace") as f:
        return f.read()


def tp(rcon, where):
    out = rcon.cmd(AS + f"surrogate rescue tp {where}")
    time.sleep(2)
    ensure_player(rcon)
    return out


# ---------------------------------------------------------------------------- act four


def check_conference(rcon, proc):
    out = status(rcon)
    check("a fresh world has not had the call", "stage 0" in out and "not called" in out, out.strip()[:120])

    rcon.cmd(AS + "surrogate prologue fast")
    rcon.cmd(AS + "surrogate rescue call")
    started = wait_for_log(r"Conference: everybody on one screen", 30, proc) is not None
    check("the conference takes the stage", started)
    if not started:
        return

    # Fast mode quarters every wait and every line, so the whole four minute call should be well under two.
    # If it is not, the director is being ticked from somebody else's clock, which is how the housewarming
    # broke: every wait ten times too long and no scene ever finishing.
    finished = wait_for_log(r"Conference: the plan is one crawler", 180, proc) is not None
    check("the conference finishes", finished, "no plan line inside 180 s")
    time.sleep(2)

    out = status(rcon)
    check("the call is recorded", "called" in out and "not called" not in out, out.strip()[:120])
    check("the act moves on to the plan", "stage 2" in out, out.strip()[:120])
    check("Sorensen sends the span kit", held_count(rcon, "surrogate:span_kit") >= 1)

    rows, raw = board(rcon)
    check("the board has a row per person", len(rows) == 6, f"{len(rows)} rows: {raw[:200]}")
    far = [row_for(rows, who) for who in ("brandt", "reyes", "novak")]
    check("the far side is blocked by the Rift", all("block.rift" in row.get("blocked", "") for row in far),
          "; ".join(row.get("blocked", "missing") for row in far)[:160])


# ---------------------------------------------------------------------------- the bridge


def check_span(rcon):
    out = status(rcon)
    m = re.search(r"crossing (-?\d+),(-?\d+)", out)
    check("the narrows are found", m is not None and "unknown" not in out, out.strip()[:140])

    built = rcon.cmd(AS + "surrogate rescue span")
    m = re.search(r"Span of (\d+) blocks from (-?\d+), (-?\d+), (-?\d+) facing (\w+)", built)
    check("a span is laid across it", m is not None, built.strip()[:160])
    if not m:
        return
    length = int(m.group(1))
    anchor = (int(m.group(2)), int(m.group(3)), int(m.group(4)))
    facing = m.group(5)
    step = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
    check("the gap is a chasm rather than a kerb", length >= 8, f"{length} blocks")
    # Deck, not air: three blocks out along the bearing, which is inside the first course whatever the
    # length turned out to be.
    deck = (anchor[0] + step[0] * 3, anchor[1], anchor[2] + step[1] * 3)
    check("there is deck over the gap", block_is(rcon, deck, "surrogate:deck_plating"), str(deck))
    # And it is five wide, which is the whole point: a footbridge is not a crossing for a hull.
    side = (anchor[0] + step[0] * 3 - step[1] * 2, anchor[1], anchor[2] + step[1] * 3 + step[0] * 2)
    check("the deck is five wide", block_is(rcon, side, "surrogate:deck_plating"), str(side))
    check("the span reads as finished", "(done)" in status(rcon))
    # And it is over something. A deck laid on flat ground would pass every check above.
    mid = (anchor[0] + step[0] * (length // 2), anchor[1] - 1, anchor[2] + step[1] * (length // 2))
    air = block_is(rcon, mid, "minecraft:air")
    water = block_is(rcon, mid, "minecraft:water")
    check("the deck is over a chasm and not over ground", air or water, str(mid))
    if water:
        print("  note: the chasm is flooded to sea level here; the deck crosses water, not air")

    rows, _ = board(rcon)
    far = [row_for(rows, who) for who in ("brandt", "reyes", "novak")]
    check("the Rift stops being the answer", not any("block.rift" in row.get("blocked", "") for row in far),
          "; ".join(row.get("blocked", "missing") for row in far)[:160])
    check_span_kit(rcon, anchor, facing, step)


def check_span_kit(rcon, anchor, facing, step):
    """The item, which is how a player actually does this.

    A second anchor beside the first, because the command's span is already finished and a kit used on a
    finished one only says so. Driven with `rescue kit` rather than `player Steve use`: a Carpet fake player
    puts its actions into the interaction manager instead of through the play network handler, so neither an
    item's use on a block nor a use on an entity happens for one at all -- twenty uses against an anchor at
    eye level move nothing. What is left untested is the six-line useOnBlock wrapper; everything the kit
    actually does is below this line.
    """
    side = (-step[1], step[0])
    at = (anchor[0] + side[0] * 8, anchor[1], anchor[2] + side[1] * 8)
    rcon.cmd(f"{OW}setblock {at[0]} {at[1]} {at[2]} surrogate:span_anchor[facing={facing}]")
    rcon.cmd(f"{OW}tp Steve {at[0] - step[0] * 2} {at[1] + 1} {at[2] - step[1] * 2}")
    time.sleep(1)
    surveyed = rcon.cmd(AS + "surrogate rescue kit")
    time.sleep(1)
    after = status(rcon)
    check("the kit surveys the gap in front of an anchor",
          "Used the kit" in surveyed and "span 0/" in after and "span 0/0" not in after,
          (surveyed.strip() + " | " + after.strip())[:200])
    rcon.cmd(AS + "surrogate rescue kit")
    time.sleep(1)
    laid = status(rcon)
    check("and lays a course a use", re.search(r"span [1-9]\d*/", laid) is not None, laid.strip()[:140])
    # And it refuses an anchor pointing inland rather than laying a deck across a field. The refusal is a
    # message and no state, so what is checked is that nothing moved: a kit that surveyed it would have
    # reset the courses to zero and pointed the whole span somewhere else.
    away = {"north": "south", "south": "north", "east": "west", "west": "east"}[facing]
    inland = (at[0] - step[0] * 24, at[1], at[2] - step[1] * 24)
    rcon.cmd(f"{OW}setblock {inland[0]} {inland[1]} {inland[2]} surrogate:span_anchor[facing={away}]")
    rcon.cmd(f"{OW}tp Steve {inland[0]} {inland[1] + 1} {inland[2]}")
    time.sleep(1)
    before = status(rcon)
    rcon.cmd(AS + "surrogate rescue kit")
    time.sleep(1)
    check("and refuses an anchor with nothing in front of it", status(rcon) == before, before.strip()[:140])


# ---------------------------------------------------------------------------- the road to Ceramic Row


def check_flow(rcon):
    tp(rcon, "brandt")
    laid = rcon.cmd(AS + "surrogate rescue flow")
    m = re.search(r"(?:Flow across the approach at|Flow already at) (-?\d+), (-?\d+), (-?\d+)", laid)
    check("a vent is laid across the approach", m is not None, laid.strip()[:160])
    if not m:
        return
    centre = tuple(int(g) for g in m.groups())
    rcon.cmd(f"{OW}forceload add {centre[0] - 32} {centre[2] - 32} {centre[0] + 32} {centre[2] + 32}")
    time.sleep(1)
    check("the channel is running", block_is(rcon, centre, "minecraft:lava"), str(centre))

    rcon.cmd(AS + "surrogate rescue cut")
    time.sleep(1)
    check("the channel crusts over", not block_is(rcon, centre, "minecraft:lava"), str(centre))
    check("one seam of it stays hot", block_is(rcon, centre, "minecraft:magma_block"), str(centre))
    pond = log_short(r"the basin at ", read_log())
    check("it ponds where the basin is", pond is not None and block_is(rcon, pond, "minecraft:lava"), str(pond))
    check("the flow reads as cut", "flow cut" in status(rcon))
    rcon.cmd(f"{OW}forceload remove {centre[0] - 32} {centre[2] - 32} {centre[0] + 32} {centre[2] + 32}")


# ---------------------------------------------------------------------------- Clinic Nine


def check_clinic(rcon):
    tp(rcon, "reyes")
    origin = log_pos(r"Built reyes's shelter at ", read_log())
    check("Clinic Nine is built", origin is not None)
    if origin is None:
        return None

    # Her outer frame: both jambs, top and bottom, and every one of them a hole until somebody plates it.
    frame = [(-1, 1, 3), (1, 1, 3), (-1, 2, 3), (1, 2, 3)]
    open_plates = sum(1 for d in frame
                      if block_is(rcon, (origin[0] + d[0], origin[1] + d[1], origin[2] + d[2]),
                                  "surrogate:breached_plating"))
    check("her frame is open in four places", open_plates == 4, f"{open_plates} of 4")

    rows, _ = board(rcon)
    check("the board says what is in the way", "block.airlock" in row_for(rows, "reyes").get("blocked", ""),
          row_for(rows, "reyes").get("blocked", "missing"))
    return origin


def prepare_crawler(rcon):
    """A hull with a room in it, made at the pod where `crawler spawn` puts it.

    Boarding it once is the only thing in the mod that ever builds a cabin, and a hull without one takes
    nobody aboard at a collar: it locks, it plays the sound, and the survivor stays where they were, with no
    message and no log line. Everything after this depends on it, so it happens first and in one place.
    """
    tp(rcon, "hub")
    rcon.cmd(AS + "surrogate crawler spawn")
    time.sleep(1)
    boarded = rcon.cmd(AS + "surrogate crawler board")
    time.sleep(3)
    rcon.cmd(AS + "surrogate crawler leave")
    time.sleep(3)
    ensure_player(rcon)
    check("the crawler has a cabin", "within thirty-two blocks" not in boarded, boarded.strip()[:120])


def check_reyes_refuses(rcon, rows):
    """She will not open a lock that has nothing to seal against, and she says so."""
    index = shelter_index(rows, "reyes")
    if index < 0:
        check("Clinic Nine is on the board", False)
        return
    rcon.cmd(AS + f"surrogate crawler dockat {index}")
    time.sleep(2)
    after, _ = board(rcon)
    check("Reyes refuses the collar with her frame open", row_for(after, "reyes").get("state") != "aboard",
          row_for(after, "reyes").get("state", "missing"))


def check_reyes_boards(rcon, index):
    rcon.cmd(AS + "surrogate rescue plate")
    time.sleep(3)
    out = status(rcon)
    check("the frame reads as plated", "airlock plated" in out, out.strip()[:140])
    rows, _ = board(rcon)
    check("her row stops naming the frame", "block.airlock" not in row_for(rows, "reyes").get("blocked", ""),
          row_for(rows, "reyes").get("blocked", "missing"))

    rcon.cmd(AS + f"surrogate crawler dockat {index}")
    time.sleep(2)
    rows, _ = board(rcon)
    check("Reyes comes aboard once it is plated", row_for(rows, "reyes").get("state") == "aboard",
          row_for(rows, "reyes").get("state", "missing"))

    rcon.cmd(AS + "surrogate crawler dockat home")
    time.sleep(3)
    ensure_player(rcon)
    rows, _ = board(rcon)
    check("and home from there", row_for(rows, "reyes").get("state") == "home",
          row_for(rows, "reyes").get("state", "missing"))


# ---------------------------------------------------------------------------- the Rift floor


def check_mist(rcon):
    tp(rcon, "novak")
    wreck = log_pos(r"Laid novak's wreck on the Rift floor at ", read_log())
    check("the wreck is on the floor", wreck is not None)
    if wreck is None:
        return None
    check("Novak is in it", entity_near(rcon, wreck, "surrogate:survivor", 12) >= 1)
    # The Rift cuts well below sea level and fills. The wreck goes on the real floor of it in a sealed
    # pocket, so the square Novak stands in has to be air: dry, at the bottom, and not thirty blocks up on
    # the surface of the water, which is where the old placement put it.
    beside = (wreck[0] + 3, wreck[1] + 1, wreck[2])
    check("the overhang is dry", block_is(rcon, beside, "minecraft:air"),
          "water" if block_is(rcon, beside, "minecraft:water") else str(beside))
    check("and it is at the bottom rather than in a scratch", wreck[1] < 50, f"y={wreck[1]}")

    # A chassis parked in the pocket. The mist is the one hazard in the game that no module answers, so what
    # is being checked is that it bites at all and that it bites hard.
    rcon.cmd(f"{OW}summon surrogate:robot {wreck[0]} {wreck[1] + 1} {wreck[2]}")
    time.sleep(1)
    before = robot_health(rcon)
    time.sleep(6)
    after = robot_health(rcon)
    check("the mist takes a chassis apart", before is not None and (after is None or after < before - 3.0),
          f"{before} -> {after}")
    rcon.cmd(f"{OW}kill @e[type=surrogate:robot]")
    return wreck


def robot_health(rcon):
    out = rcon.cmd(f"{OW}data get entity @e[type=surrogate:robot,limit=1] Health")
    m = re.search(r"entity data: ([\d.]+)f", out)
    return float(m.group(1)) if m else None


def face_and_use(rcon, wreck):
    """Stand next to the man and use him.

    Through the command rather than through `player Steve use`. Carpet drives a fake player's actions
    straight into the interaction manager instead of through the play network handler, and Fabric's
    UseEntityCallback is injected into the handler -- so the one interaction act five adds is invisible to a
    fake player, and a test built on pressing the button passes whether or not anything is wired up.
    """
    rcon.cmd(f"{OW}tp Steve {wreck[0] - 1} {wreck[1] + 1} {wreck[2]}")
    time.sleep(1)
    rcon.cmd(AS + "surrogate rescue lift")
    time.sleep(2)


def carrying(rcon):
    out = rcon.cmd(f"{OW}data get entity Steve Passengers")
    return "surrogate:survivor" in out


def check_carry_refused(rcon, wreck):
    face_and_use(rcon, wreck)
    check("nobody goes down the scree before the doctor is up", not carrying(rcon))


def check_carry(rcon, wreck):
    face_and_use(rcon, wreck)
    lifted = carrying(rcon)
    check("Novak can be picked up once Reyes is home", lifted)
    if not lifted:
        return
    check("carrying is recorded", "novak lifted" in status(rcon))
    # A hull within six blocks is where he gets put down. The crawler is at the pod after taking Reyes home,
    # so it comes to him rather than the other way round.
    rcon.cmd(f"{OW}tp @e[type=surrogate:crawler,limit=1] {wreck[0] + 3} {wreck[1] + 1} {wreck[2]}")
    time.sleep(3)
    rows, _ = board(rcon)
    check("and put down in the cabin", row_for(rows, "novak").get("state") == "aboard",
          row_for(rows, "novak").get("state", "missing"))


# ---------------------------------------------------------------------------- the run


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

        print("---- act four: the conference ----")
        check_conference(rcon, proc)
        print("---- a hull with a room in it ----")
        prepare_crawler(rcon)
        print("---- act five: the bridge ----")
        check_span(rcon)
        print("---- act five: the road to Ceramic Row ----")
        check_flow(rcon)
        print("---- act five: Clinic Nine ----")
        check_clinic(rcon)
        rows, _ = board(rcon)
        index = shelter_index(rows, "reyes")
        check_reyes_refuses(rcon, rows)
        print("---- act five: the Rift floor, before the doctor ----")
        wreck = check_mist(rcon)
        if wreck is not None:
            check_carry_refused(rcon, wreck)
        print("---- act five: Clinic Nine, plated ----")
        tp(rcon, "reyes")
        if index >= 0:
            check_reyes_boards(rcon, index)
        print("---- act five: the Rift floor, after ----")
        if wreck is not None:
            tp(rcon, "novak")
            check_carry(rcon, wreck)

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
        text = read_log()
        held = [l for l in text.splitlines() if "camera still held" in l.lower()]
        threw = [l for l in text.splitlines() if "beat threw at" in l.lower()]
        check("the camera is always released", not held, held[0][:160] if held else "")
        check("no beat threw", not threw, threw[0][:160] if threw else "")
        print("---- rescue log lines ----")
        for l in text.splitlines():
            if "Conference:" in l or "Rescue:" in l or "Magma flow" in l or "Span complete" in l:
                print(l.strip()[:200])
        issues = [l for l in text.splitlines()
                  if re.search(r"ERROR|Exception|Failed to|error in", l)
                  and "No data fixer registered for" not in l]
        if issues:
            print("---- log lines of interest ----")
            for l in issues[:30]:
                print(l.strip()[:300])
            failures.append("exceptions in the log")

    print("RESULT:", "PASS" if not failures else "FAIL " + ", ".join(failures))
    return 0 if not failures else 2


if __name__ == "__main__":
    sys.exit(main())
