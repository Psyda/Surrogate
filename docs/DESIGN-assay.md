# The Sallow Assay: Contract Seven

The corporation's research task, and the first long goal the player and the other researchers share. It picks
up the line Marsh reads out on day two of the prologue and pays it off:

> Sallow Assay, Contract Seven. Samples to the crate, numbers to the relay. When the assay is complete, the
> company sends the ship.

The company does send the ship. That is the point. Everyone gets what they were promised and it is worse than
being lied to. This document is the plan for that arc, from the first stake in the ground to the moment the
mini rocket leaves and the ship that came for the payload does not come down.

Related: [DESIGN-crawler.md](DESIGN-crawler.md) for the vehicle and docking; the props and the vehicle
fabricator (`surrogate:vehicle_fabricator`) already exist and this arc is what they are for.

Where it sits in the whole game: act three of eight. Acts one and two (the research runs, and the range gate
at Tanaka) now run before it and are what hands the player the resonance damper; act four picks up the tick
after this one ends. See [DESIGN-campaign.md](DESIGN-campaign.md).

## Shape

Five stages, each a survey site's worth of work, tracked in `AssayState` (a `PersistentState` beside
`HabitatState`). The whole thing is one `Director` script (`assay/Assay.java`) built the same way as
`Prologue`, so it resumes across restarts, can be skipped a stage at a time, and works around anything the
player ignores.

| Stage | Name | The player does | The others do |
| --- | --- | --- | --- |
| 1 | The Stake | Drives to the designated area and plants the survey marker | Halloran reads out the site brief |
| 2 | The Pad | Builds the launch pad platform, course by course | Three chassis arrive, each fitting its own module |
| 3 | The Core | Mines tellurium deep underground and brings it up | Marsh files the paperwork nobody reads |
| 4 | The Payload | Loads the sample capsule with the assay's samples | Okafor patches in from Survey Two |
| 5 | The Ignition | Ignites the rocket with the tellurium | Everyone watches; the ship arrives overhead |

## Stage one: the stake

The company designates a site. It is chosen the way Site Two is (`Valleys.pickSite`): a flat valley floor,
600 to 900 blocks from the pod, reachable by crawler without a climb, and away from anyone's shelter. Saved in
`AssayState.padSite` on first join, resolved when its chunk loads.

The objective is to get there and plant a survey marker. The crawler is the intended way; walking a chassis
works and takes longer, which is the point of having built the crawler. On arrival:

* A ring of `survey_marker` blocks appears at the corners as Halloran reads out the brief.
* The site is force-loaded from here to the end of the arc, so the build does not unload mid-cinematic.
* `AssayState.stage = STAGE_PAD`.

## Stage two: the pad, and the others arriving

The centrepiece. A cinematic that runs in four movements, with real work between them, so the structure
visibly grows and the player did some of it.

The pad is authored as layer text, north at the top, exactly like `HabitatBuilder.LAYERS`: a 13x13 apron of
`deck_plating` with a `hazard_plating` border, four `hull_frame` uprights framing the rocket stand, a
`pad_light` at each corner of the border, an `antenna_mast` and two `chem_drum`s on the apron, and the gantry
in the middle. It is built in **courses**, not all at once: `PadBuilder.course(world, origin, n)` places
course `n` and returns false when there are no more.

Everything above course one stands at y+1 on top of the apron, so nothing in a course may sit over a square
the apron does not cover, or it floats. The corner lights are the exception: they rest on the hazard border,
which is itself a solid block at y+0.

Each movement is one contributor arriving and one course going down.

1. **The apron.** The player lays it. The kit is a `supply_crate` the crawler brings; the objective is to
   place deck plating over the marked square. Anything they do not place, Halloran's chassis fills in when
   the timeout expires, complaining about it.
2. **Sorensen's chassis** walks in from the south with the legs and the frame. `spawnRobot` scripted, a
   `robotWalk` to the pad, a pause at each leg, and the course appears behind it. He talks over the radio
   from Survey Two while his chassis works, because his own rover is dead and this is the only way he gets to
   help.
3. **Halloran's chassis** brings the gantry: the `vehicle_fabricator` itself, set down in the middle of the
   pad. This is the payoff for the fabricator existing. Her chassis is the one from the prologue, so the
   player recognises it.
4. **Marsh** walks in on foot, in a suit, with a clipboard, and stands at the edge doing nothing useful. He
   files the site as complete before it is complete. Halloran says so.

Between movements the camera is released and the player has work to do; the cinematic shots are short (four
to six seconds) and always start ahead of the player's eyes (`shotFromEyes`), the rule the prologue already
follows.

At the end of stage two the pad stands, the gantry is on it, and the rocket is not built yet.

## Stage three: the core

The company wants a deep sample. This is what `tellurium_ore` was added for: it only generates below y -8, in
small pockets, half of them lost to caves, so getting three crystals is a real trip underground with a
chassis and a drill.

* Objective: three `tellurium_crystal` in the assay crate at the pad.
* The crawler is the way to carry them back if the seam is far; the radar (`CrawlerSonar`) marks the pocket
  when close, which is what the radar is for.
* Halloran does not like this stage. Nobody has been asked to go that deep before, and she says so.
* **The borer line runs at y 8** ([DESIGN-hazards.md](DESIGN-hazards.md)), and tellurium is sixteen blocks
  inside it. Tanaka's resonance damper, from act two, is what makes this stage survivable; a player who
  skipped act two can still do it, badly and loudly, which is the rule the whole game keeps.

## Stage four: the payload

The rocket is built at the gantry, from a `rocket_kit` (a new `VehicleKit`, so it uses the fabricator exactly
the way the crawler does; the hologram rises, the hull appears). It is a static entity, not drivable: a
`RocketEntity` that stands on the pad, four blocks of it, with a capsule hatch.

* Objective: load the capsule. The assay's samples (sulfur, cinnabar, salt, whatever the crate has collected
  across the arc) go into the capsule block entity.
* Okafor patches in from Survey Two on the radio while it is loaded. This is the first time a rescue-arc
  survivor speaks during a corporation mission, and it seeds stage three of the campaign.

## Stage five: the ignition, and the ship

The ending of the arc, and the turn.

1. Everyone gathers on the pad. Halloran, Marsh, Sorensen's chassis, Halloran's chassis. The player stands
   with them. This is the closest thing to the room the crew wanted.
2. Objective: put the tellurium in the rocket's core. One crystal, used on the rocket.
3. **The ignition sequence.** Letterboxed. The mast lights, the pad lights strobe amber, the gantry retracts
   (its `building` state runs backwards), and a countdown plays over the intercom.
4. **The ship.** Before the rocket goes, the clouds above the valley go dark. A `CompanyShipRenderer` draws a
   massive hull sliding into the sky overhead, the same technique as `TransitSkyRenderer` (a baked vertex
   buffer, drawn in a dimension effects pass). It is far too big and it does not descend. It is not coming
   down. The camera holds on it while nobody says anything.
5. **The launch.** The rocket lifts on a column of particles, the payload goes up, the ship takes it, and the
   ship leaves. Not a word to the people on the ground.
6. Marsh's radio goes quiet. The last line of the arc is Halloran's, and it is not to the company.

The abandonment lands here. `AssayState.stage = STAGE_DONE` and the rescue arc opens: the survivors' habitats
start degrading, and the crawler becomes the only way anyone gets home.

## What exists and what is new

Already built:

* `Director` and `Beat`: the whole script vocabulary, including shots, walks, objectives, hints, timeouts.
* `vehicle_fabricator` and `VehicleKit`: the gantry and the hologram build.
* The pad props: `deck_plating`, `hazard_plating`, `hull_frame`, `pad_light`, `antenna_mast`, `supply_crate`,
  `survey_marker`, `chem_drum`.
* `tellurium_ore` and `tellurium_crystal`: the deep resource, already generating.
* `Valleys.pickSite`, layer-text building, scripted robots and crew, `CrawlerSonar`.

New for this arc:

| Piece | Where | Status |
| --- | --- | --- |
| `AssayState` | `assay/AssayState.java` | Built. Stage, pad site, courses laid, samples, whether the ship has come |
| `Assay` | `assay/Assay.java` | Built. The director; the script, top to bottom like `Prologue` |
| `PadBuilder` | `assay/PadBuilder.java` | Built. Course text, placed one course at a time |
| `PadSite` | `assay/PadSite.java` | Built. Picks the designated site and resolves its ground |
| Dialogue | `lang/en_us.json` | Built. 65 entries under `cinematic.surrogate.assay.` |
| `/surrogate assay` | `prologue/PrologueCommand.java` | Built. `start`, `stage <name>`, `skip`, `site`, `status`, `fast` |
| `smoke_test_assay.py` | `tools/` | Built. Drives all five stages headlessly |
| The rocket | `entity/RocketEntity.java`, `item/RocketKitItem.java` | Built. A real entity the gantry assembles from a `rocket_kit` (a `VehicleKit`, like the crawler), loaded by using samples on it, lit with a tellurium crystal, and it flies its own climb |
| The gantry hologram | `client/render/VehicleFabricatorRenderer.java` | Built. Picks its ghost model from the kit on the pad, so a rocket build shows a rocket |
| The ship overhead | `entity/CompanyShipEntity.java` | Built. A huge entity that crosses fast and low to take the payload in passing, drawn with culling off because vanilla culls on a collision box a fraction of the hull's size |
| NPC chassis | `entity/RobotPaint.java` | Built. Five paints; NPC chassis wear their owner's colour and cannot be keyed, docked, wrenched or dived into |
| Voices | `tools/voices.json` | Not recorded. Sorensen and Okafor have no cast voice yet; every line plays as text until one is set |

## What the first play test found

The headless test passed twenty checks while the sequence was still broken, because it asserted block
coordinates rather than behaviour. Seven faults came out of one play session:

1. **The pad drifted.** `HabitatBuilder.findSite` re-centres on whatever it is given, so every stage command
   rebuilt the pad a few blocks off the last one. The origin is now resolved once through `PadSite.resolve`
   and frozen in `AssayState.padResolved`, and a stage restart clears the envelope before rebuilding.
2. **The camera stuck.** Locks and releases were hand-paired across beats and one pair was missing entirely.
   `Director` now owns the pairing (`beginShot`/`endShot`) with four backstops: a jump, leaving the stage,
   running off the end of the script, or a beat throwing all hand the camera back.
3. **The rocket did not exist.** It is now a real entity built from a real kit, and the kit is stocked in the
   relay crate the script tells the player to look in - the objective was previously uncompletable.
4. **Marsh stood in lethal air.** Everyone at the pad arrives by chassis now. His day-three walk in a suit
   "rated for one" is a short trip to a pod he can nearly see; the pad is 600 to 900 blocks out.
5. **NPC chassis could softlock the player.** Nothing checked `isScripted` on any link path. Six guards now
   do, anchored on `RobotEntity.isClaimable`, and NPC chassis wear their own paint.
6. **The flyover was nothing.** It is an entity that crosses the sky.
7. **The bunk was one block.** It is two, like a bed.

## Rules this arc keeps

* Nothing is a hard block. Every objective times out and is worked around by someone, with a line about it.
  The player who ignores the whole thing still sees the ship arrive.
* No one important dies for cheap. Halloran and Marsh are the colony
  ([surrogate-direction](DESIGN-crawler.md)); they are alive at the end of this and needed later.
* The company is never a villain in dialogue. It is a form, a schedule and a ship that does not stop. Marsh
  is the closest thing to it on the ground, and he is a man doing a job he has stopped believing in.
* Shots start ahead of the player's eyes, never inside their head.
