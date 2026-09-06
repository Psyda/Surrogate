"""Launches the dev client straight into a world, optionally restarting the week aboard the ship at a day.

    python tools/dev_client.py                  # the last smoke-test world, from the thaw
    python tools/dev_client.py --day 4          # the same world, turnover day
    python tools/dev_client.py --world "Prologue Test 2" --day 7
    python tools/dev_client.py --day 5 --shots 40 --quit 3000 --fast   # screenshots to run/screenshots, then quit
    python tools/dev_client.py --prologue 3 --fast                      # skip the ship; day three on the ground

The smoke tests leave a Toxic Wastes world in run/toxic_test; this copies it to run/saves/<name> (a server
world and a singleplayer save have the same layout) and runs `gradle runClient -PquickPlay=<name>
-PdevTransit=day<n>`. The client log is run/logs/latest.log. Never run this while a smoke test is running:
they share run/."""
import argparse
import os
import shutil
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RUN = os.path.join(ROOT, "run")


def keep_running_unfocused():
    """The game pauses when it loses focus, which freezes a scene that is being watched from another window."""
    options = os.path.join(RUN, "options.txt")
    if not os.path.exists(options):
        return
    with open(options, encoding="utf-8") as f:
        lines = [line for line in f.read().splitlines() if not line.startswith("pauseOnLostFocus:")]
    lines.append("pauseOnLostFocus:false")
    with open(options, "w", encoding="utf-8") as f:
        f.write(os.linesep.join(lines) + os.linesep)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--world", default="toxic_test", help="save name under run/saves (default: a copy of run/toxic_test)")
    parser.add_argument("--day", type=int, default=1, help="restart the ship week at this day, 1 to 7")
    parser.add_argument("--drop", action="store_true", help="restart at the drop instead of a day")
    parser.add_argument("--no-transit", action="store_true", help="do not restart the ship week on join")
    parser.add_argument("--prologue", type=int, default=0, help="skip the ship and restart the days on the ground at this day, 1 to 3")
    parser.add_argument("--board", default="", choices=["", "cabin", "helm", "dock"], help="go aboard the nearest crawler on join, standing at that console")
    parser.add_argument("--shots", type=int, default=0, help="save a screenshot to run/screenshots every N ticks")
    parser.add_argument("--quit", type=int, default=0, help="close the game after N ticks in a world")
    parser.add_argument("--fast", action="store_true", help="quarter-length scenes, like /surrogate transit fast")
    parser.add_argument("--look", default="", help="yaw,pitch to hold the player at whenever no scene has the camera")
    parser.add_argument("--screen", default="", help="open this screen in-world for five seconds, ten seconds in: log, or terminal:<unit>")
    parser.add_argument("--tp", default="", help="x,y,z,afterTicks: put the player there every five seconds from that tick on")
    parser.add_argument("--run", default="", help="chat command to send once, as 'command@ticks' (default 200 ticks in)")
    args = parser.parse_args()

    saves = os.path.join(RUN, "saves")
    os.makedirs(saves, exist_ok=True)
    target = os.path.join(saves, args.world)
    source = os.path.join(RUN, "toxic_test")
    if args.world == "toxic_test":
        if not os.path.isdir(source):
            print("no run/toxic_test world; run a smoke test first", file=sys.stderr)
            return 1
        shutil.rmtree(target, ignore_errors=True)
        shutil.copytree(source, target)
        print("copied run/toxic_test to run/saves/toxic_test")
    elif not os.path.isdir(target):
        print("no such save:", target, file=sys.stderr)
        return 1

    keep_running_unfocused()

    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    command = [os.path.join(ROOT, wrapper), "runClient", "--console=plain", "-PquickPlay=" + args.world]
    if args.board:
        command.append("-PdevBoard=" + args.board)
    if args.prologue:
        command.append("-PdevPrologue=day%d" % max(1, min(3, args.prologue)))
    elif not args.no_transit:
        command.append("-PdevTransit=" + ("drop" if args.drop else "day%d" % max(1, min(7, args.day))))
    if args.shots:
        command.append("-PdevShots=%d" % args.shots)
        shots = os.path.join(RUN, "screenshots")
        shutil.rmtree(shots, ignore_errors=True)
        os.makedirs(shots, exist_ok=True)
    if args.quit:
        command.append("-PdevQuit=%d" % args.quit)
    if args.fast:
        command.append("-PdevFast=true")
    if args.look:
        command.append("-PdevLook=" + args.look)
    if args.screen:
        command.append("-PdevScreen=" + args.screen)
    if args.tp:
        command.append("-PdevTp=" + args.tp)
    if args.run:
        command.append("-PdevRun=" + args.run)
    print(" ".join(command))
    return subprocess.call(command, cwd=ROOT)


if __name__ == "__main__":
    sys.exit(main())
