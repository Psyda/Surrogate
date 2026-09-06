"""Boots the dev server on the Toxic Wastes preset, spawns a Carpet fake player over RCON so the habitat
builds, then reads back block data to confirm life support and solar are doing their job."""
import os
import re
import socket
import struct
import subprocess
import sys
import time

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SCRATCH = os.path.join(ROOT, "build")
LOG = os.path.join(SCRATCH, "server.log")
RCON_PORT = 25575
RCON_PASS = "surrogate"
RUN = os.path.join(ROOT, "run")
SERVER_PROPERTIES = """level-type=surrogate:toxic_wastes
level-name=toxic_test
online-mode=false
enable-rcon=true
rcon.port=25575
rcon.password=surrogate
view-distance=6
simulation-distance=6
spawn-protection=0
gamemode=survival
difficulty=normal
max-tick-time=-1
sync-chunk-writes=false
"""


def prepare_run_dir(config=None):
    """Fresh test world every time; the server needs the EULA accepted and RCON turned on. `config` is a dict of
    surrogate.json overrides for this run (the mod fills in every field it does not name); the file is reset
    to defaults when None, so one test cannot leave its settings behind for the next."""
    import json
    import shutil
    os.makedirs(RUN, exist_ok=True)
    os.makedirs(SCRATCH, exist_ok=True)
    os.makedirs(os.path.join(RUN, "config"), exist_ok=True)
    shutil.rmtree(os.path.join(RUN, "toxic_test"), ignore_errors=True)
    with open(os.path.join(RUN, "eula.txt"), "w", encoding="utf-8") as f:
        f.write("eula=true\n")
    with open(os.path.join(RUN, "server.properties"), "w", encoding="utf-8") as f:
        f.write(SERVER_PROPERTIES)
    with open(os.path.join(RUN, "config", "surrogate.json"), "w", encoding="utf-8") as f:
        json.dump(config or {}, f, indent=2)


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection(("127.0.0.1", RCON_PORT), timeout=20)
        self.req = 0
        self._send(3, RCON_PASS)
        self._recv()

    def _send(self, kind, payload):
        self.req += 1
        body = struct.pack("<ii", self.req, kind) + payload.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(body)) + body)

    def _recv(self):
        raw = self.sock.recv(4)
        (length,) = struct.unpack("<i", raw)
        data = b""
        while len(data) < length:
            data += self.sock.recv(length - len(data))
        return data[8:-2].decode("utf-8", "replace")

    def cmd(self, command):
        self._send(2, command)
        out = self._recv()
        print(f"> {command}\n{out}")
        sys.stdout.flush()
        return out


def main():
    prepare_run_dir()
    log = open(LOG, "w", encoding="utf-8")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    proc = subprocess.Popen([os.path.join(ROOT, wrapper), "runServer", "-PwithCarpet", "--console=plain"],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    start = time.time()
    ready = False
    seen = 0
    try:
        while time.time() - start < 480:
            time.sleep(3)
            with open(LOG, encoding="utf-8", errors="replace") as f:
                lines = f.readlines()
            for line in lines[seen:]:
                if "Done (" in line and "For help" in line:
                    ready = True
            seen = len(lines)
            if proc.poll() is not None:
                print("server exited early")
                return
            if ready:
                break
        if not ready:
            print("server never reported Done")
            return
        print(f"server ready after {int(time.time() - start)} s")
        time.sleep(3)
        rcon = Rcon()
        rcon.cmd("player Steve spawn")
        origin = None
        for _ in range(20):
            time.sleep(3)
            with open(LOG, encoding="utf-8", errors="replace") as f:
                text = f.read()
            m = re.search(r"Built the starter habitat at BlockPos\{x=(-?\d+), y=(-?\d+), z=(-?\d+)\}", text)
            if m:
                origin = tuple(int(v) for v in m.groups())
                break
        print("habitat origin:", origin)
        if origin is None:
            # A Carpet fake player still aboard keeps the shutdown from finishing: remove it first.
            rcon.cmd("player Steve kill")
            time.sleep(2)
            rcon.cmd("stop")
            return
        ox, oy, oz = origin
        life = (ox, oy + 1, oz - 4)
        solar = (ox, oy + 6, oz - 4)
        dock = (ox - 1, oy + 1, oz - 3)
        ow = "execute in minecraft:overworld run "
        rcon.cmd("time set 6000")
        rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]}")
        rcon.cmd(f"{ow}data get block {dock[0]} {dock[1]} {dock[2]} Energy")
        rcon.cmd(f"{ow}data get entity Steve Pos")
        rcon.cmd(f"{ow}locate biome surrogate:dead_grove")
        rcon.cmd(f"{ow}locate biome surrogate:ash_dunes")
        rcon.cmd(f"{ow}locate biome surrogate:acid_flats")
        rcon.cmd(f"{ow}locate biome surrogate:salt_pans")
        for i in range(3):
            time.sleep(20)
            rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]}")
            rcon.cmd(f"{ow}data get block {solar[0]} {solar[1]} {solar[2]} Energy")
            rcon.cmd(f"{ow}data get block {dock[0]} {dock[1]} {dock[2]} Energy")
            rcon.cmd(f"{ow}execute if block {life[0]} {life[1]} {life[2]} surrogate:life_support[active=true,sealed=true]")
        # Airlock, driven by the fake player using the doors like a real one would.
        inner = (ox, oy + 1, oz + 4)
        outer = (ox, oy + 1, oz + 7)
        rcon.cmd(f"{ow}tp Steve {ox + 0.5} {oy + 1} {oz + 2.5}")
        rcon.cmd(f"{ow}player Steve look at {inner[0] + 0.5} {inner[1] + 1} {inner[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1.5)
        rcon.cmd(f"{ow}execute if block {inner[0]} {inner[1]} {inner[2]} surrogate:airlock_door[open=true]")
        # Only the inner door is open: the chamber joins the room and it stays sealed.
        rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]} Volume")
        rcon.cmd(f"{ow}execute if block {life[0]} {life[1]} {life[2]} surrogate:life_support[sealed=true]")
        # The door should have slid shut on its own by now.
        time.sleep(3)
        rcon.cmd(f"{ow}execute if block {inner[0]} {inner[1]} {inner[2]} surrogate:airlock_door[open=false]")
        rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]} Volume")
        # Now stand in the chamber and open both doors within the close delay: that is a breach.
        rcon.cmd(f"{ow}tp Steve {ox + 0.5} {oy + 1} {oz + 5.5}")
        rcon.cmd(f"{ow}player Steve look at {inner[0] + 0.5} {inner[1] + 1} {inner[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(0.5)
        rcon.cmd(f"{ow}player Steve look at {outer[0] + 0.5} {outer[1] + 1} {outer[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1.5)
        rcon.cmd(f"{ow}execute if block {outer[0]} {outer[1]} {outer[2]} surrogate:airlock_door[open=true]")
        rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]}")
        rcon.cmd(f"{ow}execute if block {life[0]} {life[1]} {life[2]} surrogate:life_support[sealed=false]")
        # Both doors close on their own and the room reseals.
        time.sleep(4)
        rcon.cmd(f"{ow}execute if block {outer[0]} {outer[1]} {outer[2]} surrogate:airlock_door[open=false]")
        rcon.cmd(f"{ow}execute if block {life[0]} {life[1]} {life[2]} surrogate:life_support[sealed=true]")
        rcon.cmd(f"{ow}tp Steve {ox + 0.5} {oy + 1} {oz + 0.5}")
        # Breach: pull a roof pane.
        rcon.cmd(f"{ow}setblock {ox} {oy + 5} {oz} minecraft:air")
        time.sleep(4)
        rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]}")
        rcon.cmd(f"{ow}execute if block {life[0]} {life[1]} {life[2]} surrogate:life_support[sealed=false]")
        rcon.cmd(f"{ow}setblock {ox} {oy + 5} {oz} surrogate:reinforced_glass")
        time.sleep(4)
        rcon.cmd(f"{ow}execute if block {life[0]} {life[1]} {life[2]} surrogate:life_support[sealed=true]")
        rcon.cmd(f"{ow}data get block {life[0]} {life[1]} {life[2]} Quality")

        # Decon: caustic cargo in the chamber trips the alarm and holds the inner door shut.
        rcon.cmd(f"{ow}item replace entity Steve hotbar.0 with surrogate:caustic_sand 8")
        rcon.cmd(f"{ow}tp Steve {ox + 0.5} {oy + 1} {oz + 5.5}")
        time.sleep(2)
        # The alarm lamp blinks, so sample it a few times.
        for _ in range(3):
            rcon.cmd(f"{ow}execute if block {ox} {oy + 3} {oz + 5} surrogate:decon_shower[lit=true]")
            time.sleep(0.4)
        rcon.cmd(f"{ow}player Steve look at {inner[0] + 0.5} {inner[1] + 1} {inner[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1)
        rcon.cmd(f"{ow}execute if block {inner[0]} {inner[1]} {inner[2]} surrogate:airlock_door[open=false]")
        # Dump the sand: the door opens again.
        rcon.cmd(f"{ow}item replace entity Steve hotbar.0 with minecraft:air")
        time.sleep(1.5)
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1)
        rcon.cmd(f"{ow}execute if block {inner[0]} {inner[1]} {inner[2]} surrogate:airlock_door[open=true]")
        time.sleep(4)

        # A real dive: key a card on a summoned chassis, bind the chair, sit down, and check the sealed bays.
        robot = (ox + 2, oy + 1, oz)
        chair = (ox - 3, oy + 1, oz + 2)
        rcon.cmd(f"{ow}summon surrogate:robot {robot[0] + 0.5} {robot[1]} {robot[2] + 0.5} {{Energy:36000}}")
        # Try keying the card the way a player would; the fake player's entity raycast is unreliable, so fall
        # back to writing the card's component from the chassis UUID.
        rcon.cmd(f"{ow}item replace entity Steve weapon.mainhand with surrogate:uplink_card")
        rcon.cmd(f"{ow}tp Steve {ox + 0.5} {oy + 1} {oz + 0.5}")
        rcon.cmd(f"{ow}player Steve look at {robot[0] + 0.5} {robot[1] + 0.5} {robot[2] + 0.5}")
        time.sleep(0.5)
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1)
        selected = rcon.cmd(f"{ow}data get entity Steve SelectedItem")
        if "uplink_target" not in selected:
            uuid = rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,limit=1] UUID")
            m = re.search(r"\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]", uuid)
            if m:
                ints = ",".join(m.groups())
                rcon.cmd(f"{ow}item replace entity Steve weapon.mainhand with surrogate:uplink_card[surrogate:uplink_target={{robot:[I;{ints}],name:\"Chassis\"}}]")
        rcon.cmd(f"{ow}tp Steve {chair[0] + 1.5} {oy + 1} {chair[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve look at {chair[0] + 0.5} {chair[1] + 0.4} {chair[2] + 0.5}")
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(1)
        rcon.cmd(f"{ow}data get block {chair[0]} {chair[1]} {chair[2]}")
        rcon.cmd(f"{ow}item replace entity Steve weapon.mainhand with minecraft:air")
        time.sleep(1)
        rcon.cmd(f"{ow}player Steve use once")
        time.sleep(3)
        rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,limit=1] Pilot")
        rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,limit=1] State")
        rcon.cmd(f"{ow}execute if data entity Steve Inventory[{{Slot:18b,id:\"surrogate:locked_bay\"}}]")
        rcon.cmd(f"{ow}execute if data entity Steve Inventory[{{Slot:35b,id:\"surrogate:locked_bay\"}}]")
        rcon.cmd(f"{ow}execute unless data entity Steve Inventory[{{Slot:17b,id:\"surrogate:locked_bay\"}}]")
        time.sleep(9)
        rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,limit=1] State")
        # Fit a cargo bay while a pilot is aboard: refused. Then check the hold opens by hand later is a manual test.
        rcon.cmd(f"{ow}data get entity @e[type=surrogate:robot,limit=1] CargoTier")

        # A survivor shelter: walk the chunk in and see it get built.
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        m = re.search(r"Survivor shelter for (\w+) at x=(-?\d+), z=(-?\d+)", text)
        if m:
            sx, sz = int(m.group(2)), int(m.group(3))
            rcon.cmd(f"{ow}forceload add {sx} {sz}")
            time.sleep(6)
            rcon.cmd(f"{ow}execute if entity @e[type=surrogate:survivor]")
            rcon.cmd(f"{ow}data get entity @e[type=surrogate:survivor,limit=1] CustomName")
            rcon.cmd(f"{ow}forceload remove {sx} {sz}")
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
        with open(LOG, encoding="utf-8", errors="replace") as f:
            text = f.read()
        issues = [l for l in text.splitlines() if re.search(r"ERROR|Exception|Failed|error in", l)]
        print("---- log lines of interest ----")
        for l in issues[:60]:
            print(l.strip()[:300])
        print("---- surrogate lines ----")
        for l in text.splitlines():
            if "(surrogate)" in l:
                print(l.strip()[:300])


if __name__ == "__main__":
    main()
