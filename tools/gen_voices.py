"""Records the spoken lines with ElevenLabs.

    python tools/gen_voices.py --lines                  # every line and who says it (from the scripts and lang)
    python tools/gen_voices.py --audition               # one sample per candidate voice, to run/voice_auditions
    python tools/gen_voices.py --audition halloran marsh
    python tools/gen_voices.py --audition --proposed --models eleven_multilingual_v2 eleven_v3 --round round2
    python tools/gen_voices.py --render                 # every line of every cast character -> sounds/voice/*.ogg
    python tools/gen_voices.py --render ship --force    # redo one character
    python tools/gen_voices.py --estimate               # characters that a full render would bill

The API key comes from the ELEVENLABS_API_KEY environment variable and never leaves this machine except in
the request header to api.elevenlabs.io. Casting lives in tools/voices.json: a character records only once its
`voice` is set (the user approves candidates from an audition first). Who says which line is read out of the
Java scripts (`say(C, "drop1")`, `radio(H, ...)`, `system("...ship", "system1")`) and, for keys the scripts do
not name a speaker for, from the `speakers` prefixes in voices.json. Lines delivered by radio or intercom get
the matching band-limited filter so they sound like the set they come out of. Recordings are ogg vorbis under
assets/surrogate/sounds/voice/<key>.ogg where <key> is the lang key minus its `<type>.surrogate.` prefix;
gen_sounds.py lists them in sounds.json, so run it after this."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile

import requests

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
JAVA = os.path.join(ROOT, "src", "main", "java", "dev", "psyda", "surrogate")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "surrogate", "lang", "en_us.json")
VOICE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "surrogate", "sounds", "voice")
MANIFEST = os.path.join(VOICE_DIR, "manifest.json")
AUDITIONS = os.path.join(ROOT, "run", "voice_auditions")
CAST = os.path.join(ROOT, "tools", "voices.json")
API = "https://api.elevenlabs.io/v1"

SCRIPTS = {
    "transit": (os.path.join(JAVA, "transit", "Transit.java"), "cinematic.surrogate.transit."),
    "prologue": (os.path.join(JAVA, "prologue", "Prologue.java"), "cinematic.surrogate.prologue."),
    # The housewarming is a Director like the other two and its lines were never on this list, so the whole
    # scene has been text-only since it was written. The conference, the assay and the research runs are in
    # the same position and are not here yet: they speak through their own helpers rather than say/radio.
    "housewarming": (os.path.join(JAVA, "errand", "Housewarming.java"), "cinematic.surrogate.housewarming."),
}
CREW_LETTERS = {"C": "castellanos", "F": "ferreira", "T": "teague", "H": "halloran", "M": "marsh"}
STYLE_OF = {"say": "speech", "line": "speech", "radio": "radio", "intercom": "intercom", "system": "system", "until": "speech", "nudge": "speech"}
# ffmpeg filters per delivery. Radio: a narrow band, a little grit and a squelch-level floor; intercom: a wider
# band with a hint of room; the synthetic voice and speech in the room are left alone.
FILTERS = {
    "radio": "highpass=f=320,lowpass=f=3300,acompressor=threshold=-18dB:ratio=4:attack=5:release=80,volume=4dB,alimiter=limit=0.9",
    "intercom": "highpass=f=180,lowpass=f=5200,aecho=0.6:0.25:18:0.18,acompressor=threshold=-16dB:ratio=3,volume=2dB,alimiter=limit=0.9",
    "system": "highpass=f=90,acompressor=threshold=-14dB:ratio=2.5,alimiter=limit=0.95",
    "speech": "alimiter=limit=0.95",
    # A television across a room: a small speaker, a little boxy, and a touch of the room it is in.
    "television": "highpass=f=200,lowpass=f=4200,aecho=0.5:0.2:12:0.12,acompressor=threshold=-18dB:ratio=3,volume=2dB,alimiter=limit=0.9",
}


def key():
    value = os.environ.get("ELEVENLABS_API_KEY", "")
    if not value:
        sys.exit("ELEVENLABS_API_KEY is not set")
    return value


def headers():
    return {"xi-api-key": key()}


def load_cast():
    with open(CAST, encoding="utf-8") as f:
        return json.load(f)


def load_lang():
    with open(LANG, encoding="utf-8") as f:
        return json.load(f)


def collect_lines(cast, lang):
    """Every spoken lang key with its speaker and delivery: {key: (character, style, text)}."""
    lines = {}
    for name, (path, prefix) in SCRIPTS.items():
        with open(path, encoding="utf-8") as f:
            src = f.read()
        letters = dict(CREW_LETTERS)
        for letter, crew in re.findall(r"Crew (\w) = Crew\.(\w+);", src):
            letters[letter] = crew.lower()
        # say(C, "x") / radio(H, "x") / intercom(F, "x") / line(M, "x") / line(M, "x", STYLE) / nudges in until(...)
        for verb, who, line_key in re.findall(r"\b(say|radio|intercom|line)\(([A-Z]|Crew\.[A-Z]+), \"([a-z0-9_]+)\"", src):
            who = letters.get(who, who.replace("Crew.", "").lower())
            style = STYLE_OF[verb]
            if verb == "line":
                m = re.search(r"line\((?:[A-Z]|Crew\.[A-Z]+), \"%s\", CinematicPayloads\.(\w+)\)" % line_key, src)
                if m:
                    style = m.group(1).lower()
            lines[prefix + line_key] = (who, style, lang.get(prefix + line_key, ""))
        # The nudge delay may be a literal or a named constant; both are the same beat as far as this is
        # concerned, and insisting on a digit quietly dropped every nudge written with a constant.
        for who, line_key in re.findall(r"until\([^;]*?, ([A-Z]|Crew\.[A-Z]+), \"([a-z0-9_]+)\", \w+", src):
            who = letters.get(who, who.replace("Crew.", "").lower())
            lines.setdefault(prefix + line_key, (who, "radio" if name == "prologue" else "speech", lang.get(prefix + line_key, "")))
        for who, line_key in re.findall(r"rest\(\"([a-z0-9_]+)\", ([A-Z])\)", src):
            pass
        for speaker_key, line_key in re.findall(r"system\(\"([a-z.]+)\", \"([a-z0-9_]+)\"", src):
            lines[prefix + line_key] = (character_for(cast, speaker_key), "system", lang.get(prefix + line_key, ""))
        for line_key in re.findall(r"system\(POD, \"([a-z0-9_]+)\"", src):
            lines[prefix + line_key] = ("ship", "system", lang.get(prefix + line_key, ""))
        for line_key in re.findall(r"sendLine\(POD, key \+ \"([a-z0-9_]+)\"", src):
            lines[prefix + line_key] = ("ship", "system", lang.get(prefix + line_key, ""))
        for line_key, who in re.findall(r"rest\(\"([a-z0-9_]+)\", ([A-Z])\)", src):
            lines.setdefault(prefix + line_key, (letters.get(who, who), "speech", lang.get(prefix + line_key, "")))
    # Lines without a script: the survivors on the radio, Halloran on the field radio, the crew when clicked.
    for lang_key, text in lang.items():
        who = character_for(cast, lang_key)
        # A key that is exactly a speaker prefix is the speaker's name on the subtitle, not a line.
        if who and lang_key not in lines and lang_key not in cast["speakers"]:
            style = "radio" if ".radio" in lang_key or lang_key.startswith("survivor.") else "speech"
            lines[lang_key] = (who, style, text)
    return {k: v for k, v in lines.items() if v[2]}


def character_for(cast, lang_key):
    best = None
    for prefix, who in cast["speakers"].items():
        if prefix.startswith("_"):
            continue
        if lang_key.startswith(prefix) and (best is None or len(prefix) > len(best[0])):
            best = (prefix, who)
    return best[1] if best else None


def voice_key(lang_key):
    return re.sub(r"^[a-z]+\.surrogate\.", "", lang_key)


def speakable(text):
    """Strips markup that reads badly aloud; keeps the ellipses and dashes that shape a delivery."""
    text = text.replace("%s", "").replace("...", "… ")
    return re.sub(r"\s+", " ", text).strip()


def settings_for(model, settings, cast):
    """v3 takes a stability preset (0 creative, 0.5 natural, 1 robust) and no style; the others take the full set."""
    if model.startswith("eleven_v3"):
        return {"stability": cast.get("v3_stability", 0.0), "similarity_boost": settings.get("similarity_boost", 0.75), "use_speaker_boost": True}
    return settings


def tts(voice_id, text, model, settings):
    r = requests.post(f"{API}/text-to-speech/{voice_id}", headers=headers(), params={"output_format": "mp3_44100_128"},
                      json={"text": text, "model_id": model, "voice_settings": settings}, timeout=120)
    if r.status_code != 200:
        sys.exit(f"text-to-speech failed ({r.status_code}): {r.text[:300]}")
    return r.content, int(r.headers.get("character-cost", len(text)))


def to_ogg(mp3_bytes, out_path, style):
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "line.mp3")
        with open(src, "wb") as f:
            f.write(mp3_bytes)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-af", FILTERS.get(style, FILTERS["speech"]),
                        "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out_path], check=True)


def load_manifest():
    if os.path.exists(MANIFEST):
        with open(MANIFEST, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_manifest(manifest):
    os.makedirs(VOICE_DIR, exist_ok=True)
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, sort_keys=True)
        f.write("\n")


def fingerprint(voice_id, model, settings, text, style):
    return hashlib.sha1(json.dumps([voice_id, model, settings, text, style], sort_keys=True).encode("utf-8")).hexdigest()[:16]


def cmd_lines(cast, lang):
    lines = collect_lines(cast, lang)
    by_who = {}
    for k, (who, style, text) in sorted(lines.items()):
        by_who.setdefault(who, []).append((k, style, text))
    for who, items in sorted(by_who.items()):
        chars = sum(len(speakable(t)) for _, _, t in items)
        cast_voice = cast["characters"].get(who, {}).get("voice")
        print(f"== {who}: {len(items)} lines, {chars} characters, voice {cast_voice or 'NOT CAST'}")
        for k, style, text in items:
            print(f"   [{style:8s}] {voice_key(k)}: {speakable(text)[:90]}")


def cmd_estimate(cast, lang):
    lines = collect_lines(cast, lang)
    total = 0
    for who, spec in cast["characters"].items():
        chars = sum(len(speakable(t)) for k, (w, s, t) in lines.items() if w == who)
        total += chars
        print(f"{who:12s} {chars:6d} chars {'(cast)' if spec.get('voice') else '(not cast)'}")
    print(f"{'total':12s} {total:6d} chars for everything")
    r = requests.get(f"{API}/user/subscription", headers=headers(), timeout=30)
    if r.ok:
        j = r.json()
        print(f"account: {j.get('character_count')} of {j.get('character_limit')} characters used this period")


def cmd_audition(cast, who_list, models, proposed_only, round_name):
    """One sample per candidate voice and model, as mp3 the user can click, plus an index.html that plays them."""
    folder = os.path.join(AUDITIONS, round_name) if round_name else AUDITIONS
    os.makedirs(folder, exist_ok=True)
    models = models or [cast["model"]]
    index = ["<!doctype html><meta charset=utf-8><title>Voice auditions</title>",
             "<style>body{font:15px system-ui;max-width:900px;margin:2em auto;color:#ddd;background:#111}h2{margin-top:2em}"
             "div{margin:.6em 0}audio{vertical-align:middle;margin-left:1em}small{color:#888}</style>",
             "<h1>Surrogate voice auditions</h1><p>Each character reads the same sample in every candidate voice. Reply with the winner per character.</p>"]
    spent = 0
    for who, spec in cast["characters"].items():
        if who_list and who not in who_list:
            continue
        candidates = dict(spec.get("candidates", {}))
        if spec.get("voice") and not candidates:
            candidates = {"cast": spec["voice"]}
        if proposed_only and spec.get("proposed") in candidates:
            candidates = {spec["proposed"]: candidates[spec["proposed"]]}
        if not candidates:
            continue
        index.append(f"<h2>{who}</h2><p><small>{spec.get('name', '')}</small><br><em>{spec['sample']}</em></p>")
        for label, voice_id in candidates.items():
            for model in models:
                short = "v3" if model.startswith("eleven_v3") else "v2"
                out = os.path.join(folder, f"{who}__{label}__{short}.mp3")
                if not os.path.exists(out):
                    audio, cost = tts(voice_id, speakable(spec["sample"]), model, settings_for(model, spec["settings"], cast))
                    spent += cost
                    with open(out, "wb") as f:
                        f.write(audio)
                    print(f"wrote {os.path.relpath(out, ROOT)} ({cost} chars)")
                else:
                    print(f"keeping {os.path.relpath(out, ROOT)}")
                index.append(f"<div><b>{label}</b> <small>{voice_id} {model}</small><audio controls src=\"{os.path.basename(out)}\"></audio></div>")
    with open(os.path.join(folder, "index.html"), "w", encoding="utf-8") as f:
        f.write("\n".join(index))
    print(f"auditions in {os.path.relpath(folder, ROOT)} ({spent} characters billed)")


def cmd_render(cast, lang, who_list, force, dry):
    lines = collect_lines(cast, lang)
    manifest = load_manifest()
    model = cast["model"]
    spent = 0
    written = 0
    for lang_key, (who, style, text) in sorted(lines.items()):
        spec = cast["characters"].get(who)
        if not spec or not spec.get("voice"):
            continue
        if who_list and who not in who_list:
            continue
        spoken = speakable(text)
        # A character can insist on a delivery: the television is a television whatever the line looks like.
        style = spec.get("style", style)
        vk = voice_key(lang_key)
        out = os.path.join(VOICE_DIR, vk + ".ogg")
        print_ = fingerprint(spec["voice"], spec.get("model", model), settings_for(spec.get("model", model), spec["settings"], cast), spoken, style)
        if not force and os.path.exists(out) and manifest.get(vk) == print_:
            continue
        if dry:
            print(f"would render {vk} [{style}] as {who}: {spoken[:70]}")
            spent += len(spoken)
            continue
        audio, cost = tts(spec["voice"], spoken, spec.get("model", model), settings_for(spec.get("model", model), spec["settings"], cast))
        to_ogg(audio, out, style)
        manifest[vk] = print_
        save_manifest(manifest)
        spent += cost
        written += 1
        print(f"wrote {os.path.relpath(out, ROOT)} [{style}] ({cost} chars)")
    print(f"{'would bill' if dry else 'billed'} {spent} characters; {written} files written")
    if written:
        print("now run: python tools/gen_sounds.py   (to list the new lines in sounds.json)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--lines", action="store_true", help="list every spoken line with its speaker")
    parser.add_argument("--estimate", action="store_true", help="count characters a full render would bill")
    parser.add_argument("--audition", nargs="*", metavar="WHO", help="record candidate samples (optionally for these characters)")
    parser.add_argument("--render", nargs="*", metavar="WHO", help="record every line of the cast characters (optionally only these)")
    parser.add_argument("--models", nargs="*", default=None, help="with --audition: models to compare, e.g. eleven_multilingual_v2 eleven_v3")
    parser.add_argument("--proposed", action="store_true", help="with --audition: only the proposed candidate per character")
    parser.add_argument("--round", default="", help="with --audition: subfolder of run/voice_auditions to write to")
    parser.add_argument("--force", action="store_true", help="re-record lines that already exist")
    parser.add_argument("--dry", action="store_true", help="with --render: only say what would be recorded")
    args = parser.parse_args()
    cast = load_cast()
    lang = load_lang()
    if args.lines:
        cmd_lines(cast, lang)
    if args.estimate:
        cmd_estimate(cast, lang)
    if args.audition is not None:
        cmd_audition(cast, args.audition, args.models, args.proposed, args.round)
    if args.render is not None:
        cmd_render(cast, lang, args.render, args.force, args.dry)
    if not (args.lines or args.estimate or args.audition is not None or args.render is not None):
        parser.print_help()


if __name__ == "__main__":
    main()
