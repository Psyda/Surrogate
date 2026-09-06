# Art to make by hand

Everything below has a placeholder in the build already, so nothing is blocked. Each entry says where the
placeholder came from, what the real thing should be, and where to drop the file so the generators pick it
up (`python tools/gen_textures.py` after; it keeps hand-made files and only paints what is missing).

## The ship (the week on the Provender)

| What | Placeholder today | Wanted | Drop it here |
| --- | --- | --- | --- |
| Sallow from orbit | Nano Banana render, cut out (`tools/art/sallow.png`) | A 512x512 painted disc of the planet on transparency: ochre and sulfur deserts, grey ash belts, green acid seas, white salt pans, a thin hazy limb. Fully lit; the renderer shades the night side itself. | `tools/art/sallow.png` |
| "YOUR BODY STAYS HOME" poster | Nano Banana (`tools/art/poster_body.png`) | A 1:2 corporate poster, navy, sulfur yellow, off-white. Person in a chair, chassis walking off. The generator splits it into two 64x64 block faces. | `tools/art/poster_body.png` |
| Sallow survey chart | Nano Banana (`tools/art/poster_sallow.png`) | A 1:2 mission chart: the globe, nine habitat markers, "SALLOW / SITE SURVEY / ATMOSPHERE: LETHAL". | `tools/art/poster_sallow.png` |
| Provender plaque | Nano Banana (`tools/art/poster_provender.png`) | 1:1 brushed steel plaque, "S.S.V. PROVENDER", ship silhouette, "SUPPLY RUN 41". | `tools/art/poster_provender.png` |
| Passenger manifest chart | Procedural (bars per sleeper, row 07 in red) | 1:1 wall chart: twelve rows, name, rotation, days. Row seven flagged. | `tools/art/poster_manifest.png` |
| Console screens | Procedural (cyan bars; red version for alarms) | Two 64x64 screens: a calm nav display, and the same thing in red with a breach warning. | `src/main/resources/assets/surrogate/textures/block/poster_console.png`, `poster_console_alert.png` |
| Crew skins | Procedural flight suits with painted faces: Castellanos (navy, gold trim), Ferreira (white, teal), Teague (orange, black), sleeper (pale green gown) | Proper 64x64 player skins. Castellanos is older, grey at the temples, navy flight suit with a captain's bar. Ferreira: white medical coat over a suit, teal collar. Teague: hi-vis orange, tool belt, forearms bare. Sleeper: a hospital gown, a port behind the left ear, eyes closed. | `src/main/resources/assets/surrogate/textures/entity/crew_castellanos.png`, `crew_ferreira.png`, `crew_teague.png`, `crew_sleeper.png` |
| Rebreather item | 16x16 ASCII map in `gen_textures.py` | A half-mask with a cyan visor and two short hoses. Also an 18x18 status effect icon of the same thing. | `textures/item/rebreather.png`, `textures/mob_effect/rebreather.png` |
| Galley unit | Procedural (steel box, dark window, lamp) | Four 16x16 faces: the door with a grille window and a handle (and a lit version with the window glowing warm), a vented side, a plain top. Only rows 7 to 15 of the front and side are shown; the unit is 9 px tall. | `textures/block/microwave_front.png`, `microwave_front_lit.png`, `microwave_side.png`, `microwave_top.png` |
| Wall terminal | Procedural (dark screen, green bars, key strip) | A 64x64 company terminal face: a CRT bezel, SHIPNET header, a few lines of green text, a cursor, a keyboard strip under it. | `textures/block/terminal.png` |
| Breached plating | Procedural (hull plating with a hole punched through) | A 16x16 torn hull plate: a ragged transparent hole in the middle, scorched and peeled at the rim. The alpha is what makes the stars show through. | `textures/block/breached_plating.png` |
| Title screen | Code (starfield, the Sallow disc) | Optional: a painted backdrop of the Provender against Sallow to replace the drawn starfield. | (would need a `TitleScreenMixin` change) |

## Sound (all synthesized placeholders in `tools/gen_sounds.py`; drop a real .ogg over the file)

| Event | File | Wanted |
| --- | --- | --- |
| Ship hum (loop) | `sounds/transit/hum.ogg` | A low engine and air-plant drone, seamless, 6 to 10 s. |
| Breach klaxon (loop) | `sounds/transit/klaxon.ogg` | Two-tone alarm, seamless, 2 s. |
| Engine cut / relight | `engine_cut.ogg`, `engine_relight.ogg` | The drone winding down to nothing; a hard relight with the hull complaining. |
| Thaw monitor | `thaw.ogg` | Slow monitor beeps and a hiss, waking up. |
| Chair alarm | `flatline.ogg` | Fast beeps, then a flat tone. |
| Intercom chime | `intercom.ogg` | Two-note ship chime. |
| Hatch seal | `hatch.ogg` | Hydraulics and a clunk. |
| Decompression | `decompress.ogg` | A bang and a long hiss. |
| Channel bleed | `bleed.ogg` | Static and a warble cutting into the link. |
| Descent | `descent.ogg` | Rumble rising into a roar, 9 s. |
| Sedation | `sedate.ogg` | A heartbeat slowing under a low tone. |

## Voice

Every line plays a recording if one exists at `sounds/voice/<key>.ogg`, where the key is the lang key without
its `<type>.surrogate.` prefix (`transit.thaw1.ogg`, `prologue.call1.ogg`, `okafor.radio.1.ogg`).
`tools/gen_voices.py` records them with ElevenLabs from the casting in `tools/voices.json`: `--lines` lists
every line and who says it, `--audition` writes one sample per candidate voice to `run/voice_auditions/`
(open `index.html` there), `--render` records every line of every cast character, applying a radio or intercom
band-pass to lines delivered that way. A character is only rendered once `voice` is set in `voices.json`.

Cast (all approved 2026-09-05): the ship and pod system voice (`kkBYq92Aop0da4iwQJ9h`, stiff on purpose,
`eleven_multilingual_v2`); everyone else on `eleven_v3` with the creative preset: Castellanos as Lily, Ferreira
as Bella, Teague as Callum, Halloran as Sarah, Okafor as Jessica, and Marsh as Chris (Roger was too flat; swap
Chris if he stands out). Every line is recorded; new or changed lines re-record with `--render`. About 14,000
characters for everything; the account resets monthly at 40,000.

## Props and people on the ground

| What | Where | Notes |
| --- | --- | --- |
| Tomato seed packet | `textures/item/tomato_seeds.png` | Placeholder: a paper packet with a tomato on it. |
| Crew holding things | `CrewEntityRenderer` | The crew now render what they hold (Marsh and the wrench); the pose is the vanilla item pose, a proper two-handed grip would look better. |
| Halloran and Marsh skins | `textures/entity/crew/halloran.png`, `marsh.png` | Procedural suits; two hundred days in, they should look worn. |
| The docking collar | west wall of the pod | A ring of plating and glass and a sign; a proper collar block set when the crawler exists. |
| Module two slab | east of the pod | Bare plating with a sign. Foundation bolts, a stub of conduit, and crate outlines would sell it. |

## Props, ores and the vehicle fabricator (2026-09-05)

All procedural in `tools/gen_textures.py` (the "Props, ores and the vehicle fabricator" section), 16x16, in the
steel, navy and sulfur palette. `py tools/render_models.py --sheet` draws every block model as an isometric
sprite to `run/showcase/` for a quick look without booting the game; `py tools/smoke_test_props.py` places
every block on a live server and builds a crawler at a fabricator.

| What | Placeholder today | Wanted | Drop it here |
| --- | --- | --- | --- |
| Ores | Crystal blobs painted onto the vanilla stone, deepslate and tuff pulled out of the client jar | Hand-drawn veins: cinnabar red on tuff, halite white on caustic sandstone, cobalt blue on stone and deepslate, tellurium silver with a faint glow on deepslate | `textures/block/cinnabar_ore.png`, `halite_ore.png`, `cobalt_ore.png`, `deepslate_cobalt_ore.png`, `tellurium_ore.png` |
| Ore drops | ASCII maps | Cinnabar lump, salt crystals, raw cobalt, cobalt ingot, tellurium shard | `textures/item/cinnabar.png`, `salt.png`, `raw_cobalt.png`, `cobalt_ingot.png`, `tellurium_crystal.png` |
| Data rack front | Four procedural frames of drive lights | A 16x64 animated strip (frametime in the .mcmeta) | `textures/block/data_rack_front.png` |
| Fabricator | Procedural pad, pylon and beam, idle and lit | Pad with a cyan ring, pylon with a light strip up the inside face, beam underside with emitters; the `_lit` set glows | `textures/block/fabricator_top.png`, `fabricator_side.png`, `fabricator_pylon.png`, `fabricator_beam.png` and the `_lit` versions |
| Fabricator hologram | The crawler model tinted cyan, growing out of the ground (`VehicleFabricatorRenderer`) | A wireframe or scan-line pass would sell it better; sounds are vanilla beacon chords for now | `client/render/VehicleFabricatorRenderer.java`, `tools/gen_sounds.py` |
| Everything else | Procedural crate, locker, lamp, pipe, rail, seat, table, tray, cabinet, extinguisher, vent, tank, bunk, drum, marker, pad light, mast | Hand-painted versions of any of them; the model UVs are in `tools/gen_data.py` | `textures/block/` under the names the generator prints |

## The hazards and the campaign (2026-09-06)

The four things on Sallow (`docs/DESIGN-hazards.md`) and the modules that answer them. All procedural or
ASCII placeholders in `tools/gen_textures.py`.

| What | Placeholder today | Wanted | Drop it here |
| --- | --- | --- | --- |
| The borer | Procedural segmented body | A blind thing built for rock: banded chitin, a ring of grinding plates instead of a face, dust in every seam. It is mostly seen as a shape inside its own dust cloud, so silhouette matters more than detail. | `textures/entity/borer.png` |
| Geyser throat | Procedural vent top, hot and cold | A crusted throat looking down into something orange; the hot version glows and the crust is wet. | `textures/block/geyser_top.png`, `geyser_top_hot.png` |
| Geothermal tap | Procedural machine cube | A cap bolted over a throat: a heavy flange, a pressure line, a gauge, scale and salt crusted up the sides. | `textures/block/geothermal_tap.png` |
| Modules | ASCII maps | Relay module (a folded dish and a coil), resonance damper (a tuning fork over a coil pack), acid coating (a ceramic tile and a brush), shielded uplink (a caged aerial), ceramic cladding (a stack of sintered plates) | `textures/item/relay_module.png`, `resonance_damper.png`, `acid_coating.png`, `shielded_uplink.png`, `ceramic_cladding.png` |
| Relay mast, damper beacon, span anchor | Procedural cubes | Three real props: a guyed lattice mast, a squat drum with a subwoofer face, and a bolted deck plate with an eye for a cable. All three are cube_all today and want proper models as much as textures. | `textures/block/relay_mast.png`, `damper_beacon.png`, `span_anchor.png` and models in `tools/gen_data.py` |
| Survey stake | ASCII map | A ranging rod with a company tag knotted to it. | `textures/block/survey_stake.png` |
| Sealed sample | ASCII map | A screw-top canister with a green window and a seal ring. | `textures/item/sealed_sample.png` |
| Belt shelter | Layer text in `SurvivorShelter` | The three far-side shelters are built from a different kit: sloped ceramic roof, gutters, panels under a canopy, no bare metal. It should read as somebody's answer to the weather from two hundred metres away. | `survivor/SurvivorShelter.java` |
| Storm sky | Fog shift and a screen overlay | An aurora band in the fog and a proper interference pass on the pilot view; the tear bands are drawn with quads. | `client/` |
| Acid rain | Client particles and screen streaks | Streaks that read as falling acid rather than rain, and running green marks down the edges of the pilot view. | `client/` |
| Reyes and Novak | Procedural suits | Two more skins: Reyes in a medical layer over a suit, Novak in a torn one, favouring one side. | `textures/entity/` |
| New sounds | Synthesized | Storm interference wash, storm crack, borer grinding through rock (should get louder as it closes and be genuinely unpleasant), borer lunge, geyser rumble and erupt, tap cap-on, acid rain hiss. | `tools/gen_sounds.py`, then `sounds/` |

## Nice to have

* A painting-style loading card for each day (the chapter cards are text only).
* A proper drop pod exterior seen from the hold window: the current one is a cluster of deepslate tiles and copper.
* Stars with a real nebula band; the current sky is procedural points.
