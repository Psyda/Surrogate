"""Records the flashback's sound effects with ElevenLabs text-to-sound-effects.

    py -3.13 tools/gen_sfx.py --list          # every effect, its prompt, and what a full run would cost
    py -3.13 tools/gen_sfx.py                 # record whatever is missing or whose prompt changed
    py -3.13 tools/gen_sfx.py car_pass creak  # just these
    py -3.13 tools/gen_sfx.py --force         # everything again

The API key comes from the ELEVENLABS_API_KEY environment variable and never leaves this machine except in
the request header to api.elevenlabs.io. Sound effects are billed at 40 credits a second of audio when a
duration is given, out of the same monthly allowance the voices use, so this script only records an effect
whose file is missing or whose prompt has changed (tracked in sounds/flashback/manifest.json), and prints the
bill as it goes. Output is ogg vorbis under assets/surrogate/sounds/flashback/<name>.ogg; gen_sounds.py lists
the events in sounds.json, so run it after this."""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile

import requests

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "surrogate", "sounds", "flashback")
MANIFEST = os.path.join(OUT, "manifest.json")
API = "https://api.elevenlabs.io/v1/sound-generation"
CREDITS_PER_SECOND = 40

# name: (prompt, seconds, prompt influence, loop)
EFFECTS = {
    "car_pass": ("A single car driving past a quiet suburban house at night, tyres on damp asphalt, "
                 "approaching then receding, no horn, distant", 5.0, 0.5, False),
    "car_horn": ("A taxi outside a house at night sounding its horn twice, two short polite beeps, "
                 "heard from inside through a window", 2.0, 0.6, False),
    "creak": ("Old sofa springs and a wooden floorboard creaking as someone slowly stands up, quiet room", 1.6, 0.5, False),
    "footsteps_upstairs": ("Muffled footsteps on a wooden floor in the room above, heard through the ceiling, "
                           "slow, someone getting ready for bed", 4.0, 0.5, False),
    "door_upstairs": ("A bedroom door closing softly upstairs, muffled through a ceiling, quiet house at night", 1.8, 0.5, False),
    "front_door": ("A front door opening and closing with keys jangling, from inside a quiet house at night", 2.2, 0.5, False),
    "tv_switch": ("An old television changing channel: a plastic click and a short burst of static", 0.9, 0.6, False),
    "tv_credits": ("Soft melancholy solo piano end credits music from a small television speaker late at night, "
                   "slow, thin, slightly tinny", 12.0, 0.4, True),
    "news_sting": ("Television evening news programme opening sting, brass and synth fanfare with a timpani hit, "
                   "heard through a small TV speaker", 3.0, 0.5, False),
    "dart_hit": ("A dart thudding into a cork dartboard, close, dry, single impact", 0.8, 0.6, False),
    "glass_clink": ("Two pint glasses clinking together and being set down on a wooden bar", 1.2, 0.5, False),
    "pub_murmur": ("Quiet pub ambience late at night: a few murmuring voices, a chair scrape, a glass set down, "
                   "muffled music from another room", 8.0, 0.4, True),
    "fluorescent_hum": ("Fluorescent office ceiling lights humming with a faint electrical buzz and a ventilation "
                        "hush, empty office at night, steady", 6.0, 0.4, True),
    "lift_ding": ("An office elevator arriving with a soft bell ding and its doors sliding open", 1.8, 0.5, False),
    "printer": ("An office laser printer waking up, printing one page and pushing it out into the tray", 4.0, 0.5, False),
    "pizza_box": ("A cardboard pizza box lid being lifted open on a coffee table, cardboard flex, close", 1.0, 0.5, False),
    "light_switch": ("A domestic wall light switch clicked on, single sharp plastic click, small room", 0.5, 0.6, False),
}


def key():
    value = os.environ.get("ELEVENLABS_API_KEY", "")
    if not value:
        sys.exit("ELEVENLABS_API_KEY is not set")
    return value


def fingerprint(spec):
    return hashlib.sha1(json.dumps(spec, sort_keys=True).encode("utf-8")).hexdigest()[:16]


def load_manifest():
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_manifest(manifest):
    os.makedirs(OUT, exist_ok=True)
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, sort_keys=True)
        f.write("\n")


def generate(prompt, seconds, influence, loop):
    body = {"text": prompt, "duration_seconds": seconds, "prompt_influence": influence,
            "model_id": "eleven_text_to_sound_v2"}
    if loop:
        body["loop"] = True
    r = requests.post(API, headers={"xi-api-key": key()}, params={"output_format": "mp3_44100_128"}, json=body, timeout=180)
    if r.status_code != 200:
        sys.exit("sound generation failed (%d): %s" % (r.status_code, r.text[:300]))
    return r.content


def to_ogg(mp3_bytes, out_path):
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "fx.mp3")
        with open(src, "wb") as f:
            f.write(mp3_bytes)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-af", "alimiter=limit=0.95",
                        "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out_path], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("names", nargs="*", help="effects to record; all of them when empty")
    parser.add_argument("--list", action="store_true", help="show every effect and the cost of a full run")
    parser.add_argument("--force", action="store_true", help="record even if the file exists and the prompt is unchanged")
    args = parser.parse_args()
    if args.list:
        total = 0.0
        for name, (prompt, seconds, influence, loop) in EFFECTS.items():
            total += seconds
            print("%-20s %5.1fs %s%s" % (name, seconds, prompt[:70], " (loop)" if loop else ""))
        print("%.1f seconds, about %d credits for everything" % (total, int(total * CREDITS_PER_SECOND)))
        return 0
    manifest = load_manifest()
    spent = 0.0
    written = 0
    for name, spec in EFFECTS.items():
        if args.names and name not in args.names:
            continue
        prompt, seconds, influence, loop = spec
        out = os.path.join(OUT, name + ".ogg")
        print_ = fingerprint(list(spec))
        if not args.force and os.path.exists(out) and manifest.get(name) == print_:
            print("keeping", name)
            continue
        audio = generate(prompt, seconds, influence, loop)
        to_ogg(audio, out)
        manifest[name] = print_
        save_manifest(manifest)
        spent += seconds
        written += 1
        print("wrote %s (%.1fs, about %d credits)" % (os.path.relpath(out, ROOT), seconds, int(seconds * CREDITS_PER_SECOND)))
    print("%d files written, about %d credits" % (written, int(spent * CREDITS_PER_SECOND)))
    if written:
        print("now run: py -3.13 tools/gen_sounds.py   (to list the events in sounds.json)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
