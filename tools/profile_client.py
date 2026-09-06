"""A poor man's sampling profiler for the dev client: launches it into the last smoke-test world at a chosen
day (see dev_client.py), then dumps the JVM's threads with jcmd every few hundred milliseconds and counts
where the render thread and the integrated server thread spend their time. Prints the most common frames.

    python tools/profile_client.py --day 4 --seconds 90
"""
import argparse
import collections
import functools
import os
import re
import subprocess
import sys
import time

print = functools.partial(print, flush=True)  # noqa: A001
ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
JDK = "C:/Program Files/Java/jdk-21/bin"
JCMD = os.path.join(JDK, "jcmd.exe" if os.name == "nt" else "jcmd")
THREADS = ("Render thread", "Server thread")
BORING = ("java.lang.Thread", "jdk.internal", "java.util.concurrent", "sun.nio", "java.lang.Object.wait",
          "net.minecraft.util.thread.ThreadExecutor.waitForTasks", "net.minecraft.util.thread.ThreadExecutor.runTasks",
          "net.minecraft.server.MinecraftServer.runServer", "java.util.concurrent.locks", "org.lwjgl.glfw.GLFW.glfwWaitEventsTimeout",
          "net.minecraft.client.MinecraftClient.run", "net.minecraft.client.MinecraftClient.render")


def client_pid():
    out = subprocess.run([JCMD, "-l"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        # The client launched by dev_client.py, not a dedicated server (nogui) or a Gradle daemon.
        if "quickPlaySingleplayer" in line and "nogui" not in line:
            return int(line.split()[0])
    return None


def interesting(frames):
    """The first frame that is not idle plumbing, plus the first frame in this mod if any."""
    top = None
    for frame in frames:
        if not any(frame.startswith(b) for b in BORING):
            top = frame
            break
    mine = next((f for f in frames if f.startswith("dev.psyda")), None)
    return top, mine


JSTACK = os.path.join(JDK, "jstack.exe" if os.name == "nt" else "jstack")
attach_errors = collections.Counter()


def dump(pid):
    """A thread dump from jcmd, or jstack if jcmd will not attach; errors are counted, not hidden."""
    for command in ([JCMD, str(pid), "Thread.print"], [JSTACK, str(pid)]):
        proc = subprocess.run(command, capture_output=True, text=True)
        if proc.stdout and '"' in proc.stdout:
            return proc.stdout
        attach_errors[(os.path.basename(command[0]), (proc.stderr or proc.stdout).strip()[:160])] += 1
    return ""


def sample(pid):
    out = dump(pid)
    result = {}
    current = None
    frames = []
    for line in out.splitlines():
        if line.startswith('"'):
            if current:
                result[current] = frames
            name = line.split('"')[1]
            current = name if name in THREADS else None
            frames = []
        elif current and line.strip().startswith("at "):
            frames.append(line.strip()[3:].split("(")[0])
    if current:
        result[current] = frames
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--day", type=int, default=4)
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--interval", type=float, default=0.4)
    parser.add_argument("--fast", action="store_true")
    parser.add_argument("--shots", type=int, default=0, help="also save a screenshot every N ticks")
    args = parser.parse_args()

    command = [sys.executable, os.path.join(ROOT, "tools", "dev_client.py"), "--day", str(args.day),
               "--quit", str(int((args.seconds + 60) * 20))]
    if args.fast:
        command.append("--fast")
    if args.shots:
        command += ["--shots", str(args.shots)]
    game = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    pid = None
    start = time.time()
    while time.time() - start < 180 and pid is None:
        time.sleep(3)
        pid = client_pid()
    if pid is None:
        print("client JVM not found")
        game.kill()
        return
    print("client pid", pid, "; waiting for the world")
    # Only the client logs its sky renderer coming up, and only once it is in the ship, so an older
    # server log lying in run/logs cannot fool this.
    latest = os.path.join(ROOT, "run", "logs", "latest.log")
    while time.time() - start < 360:
        try:
            with open(latest, encoding="utf-8", errors="replace") as f:
                if "Transit sky: rendering" in f.read():
                    break
        except OSError:
            pass
        time.sleep(2)
    time.sleep(6)
    print("sampling for", args.seconds, "s")
    tops = {t: collections.Counter() for t in THREADS}
    mine = {t: collections.Counter() for t in THREADS}
    stacks = {t: collections.Counter() for t in THREADS}
    samples = 0
    end = time.time() + args.seconds
    empty = 0
    while time.time() < end and game.poll() is None:
        snap = sample(pid)
        if not snap:
            empty += 1
            if empty > 8:
                print("no thread dumps could be taken; attach errors:")
                for (tool, err), n in attach_errors.most_common(5):
                    print(f"  {n}x {tool}: {err}")
                break
            time.sleep(1)
            continue
        samples += 1
        for thread, frames in snap.items():
            top, own = interesting(frames)
            if top:
                tops[thread][top] += 1
            if own:
                mine[thread][own] += 1
            stacks[thread][" < ".join(frames[:4])] += 1
        time.sleep(args.interval)
    print("samples:", samples, "; attach errors:", sum(attach_errors.values()))
    for (tool, err), n in attach_errors.most_common(3):
        print(f"  {n}x {tool}: {err}")
    for thread in THREADS:
        print(f"==== {thread}: top frames ====")
        for frame, n in tops[thread].most_common(18):
            print(f"  {100.0 * n / max(1, samples):5.1f}%  {frame}")
        print(f"==== {thread}: frames in this mod ====")
        for frame, n in mine[thread].most_common(12):
            print(f"  {100.0 * n / max(1, samples):5.1f}%  {frame}")
        print(f"==== {thread}: most common stack tops ====")
        for stack, n in stacks[thread].most_common(8):
            print(f"  {100.0 * n / max(1, samples):5.1f}%  {stack}")
    try:
        game.wait(timeout=240)
    except subprocess.TimeoutExpired:
        game.kill()


if __name__ == "__main__":
    main()
