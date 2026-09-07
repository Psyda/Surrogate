# Surrogate

A Fabric mod for Minecraft 1.21.1 built around a surface you cannot survive on.
You never go outside. You lie down in a **dive chair** and pilot a one block tall, durable, repairable
**robot chassis** instead, while your body stays at base and quietly gets hungrier and more tired.

It ships with its own world type, the **Toxic Wastes**, where the open air kills an unprotected body in
about a minute and the only safe places are the rooms you seal yourself.

## The loop

1. Craft a **Robot Chassis** and deploy it on the ground (it comes with a 20% charge).
2. Craft an **Uplink Card**, use it on the chassis to key it, then use it on a **Dive Chair** to bind them.
3. Use the chair. The chassis boots (10 s by default), your view transfers, and you drive it like a mount:
   WASD, sprint, sneak. Sneaking is careful mode (half speed, it will not drive off ledges).
4. Press the **pilot menu key** (`Z` by default) to disconnect. You can leave the chassis running for an
   instant reconnect, but it keeps burning power. Shutting it down takes 5 s and stops the drain.
5. Drive up to a **Charging Dock** and use it to park: the chassis powers down, is stored inside the dock, and
   charges. Using the chair again pulls it straight back out of the dock.

## The Toxic Wastes

Pick **Toxic Wastes** as the world type when creating a world. Sallow is mesa country: wide, flat valley
floors just above the acid, tables that rise twenty-odd blocks from them across a few blocks, acid seas in
the deepest basins and acid rivers in the lowest channels. The floors are made for a slow, heavy vehicle (see
`docs/DESIGN-crawler.md`): a one block step is a ramp, a two block step is a wall, and every base is placed
on floor the crawler can reach from the pod. Vanilla caves and ores run underneath, never opening on a floor,
and the only chasm is the Rift, which is in the noise and not in a carver, so the mod always knows where it
is. Seven biomes of its own sit on that shape:

| Biome | Surface | Notes |
| --- | --- | --- |
| Toxic Desert | caustic sand over caustic sandstone | the valley corridors; sulfur crust and scrap heaps |
| Ash Dunes | ash over tuff, banded rock beneath | the tables; smoking fumaroles, geysers, ash on the wind |
| Acid Flats | mud and clay under green water | the seas and rivers; nothing comes out of them |
| Salt Pans | calcite | the open basins; sulfur crust |
| Dead Grove | coarse dirt and podzol | the damp corridors; petrified trees are the only wood |
| Caustic Mire | mud and clay over caustic sand | the belt, downwind of the vent field; it rains here |
| The Rift | raw section, mud at the bottom | the chasm across the floors; the only way over is a bridge |

Nothing lives out there. Every biome's spawner lists are empty, the dungeons are out of the underground
steps, and the server discards any hostile that arrives by some other route, so the only things that move on
Sallow are chassis, the crawler, six people in six sealed rooms, and what is under the rock. Put the vanilla
mobs back with `vanillaMonsters` in the config. What replaced them is in `docs/DESIGN-hazards.md`: borers in
the deep, magnetic storms, geysers, and acid rain in the belt.

**Sulfur crust** drops sulfur, which makes life support units and, with charcoal and bone meal, gunpowder.
**Scrap heaps** hold iron nuggets, some copper, and the odd servo motor. Petrified logs are stripped dark oak.

On first join the mod builds a sealed **starter habitat** at spawn: a glass-roofed pod with a dive chair,
charging dock, life support, solar collectors, a bed, a farm plot, a two door airlock, and a chest with a
chassis, tools, plating and a field manual. You spawn inside it. Turn it off with `spawnStarterHabitat`.

### Exposure

Every biome in the wastes is tagged `surrogate:toxic`. A body breathing that air fills with **toxin** in about
ten seconds (`exposureSeconds`). The first breaths bring nausea, then the world goes dark and slow, then the
lungs give out: from stepping outside to dead is about ten seconds at default settings. Clean air clears the
toxin in two minutes. The HUD shows what your body is breathing and how much toxin it carries.

While you are linked, your body is in the chair, and that is where the air matters. The pilot panel shows a
**BODY** line, and if the chair room breaches while you are out you get a title, an alarm, and a blinking
warning. Come home.

Chassis do not breathe. Only players are affected, and only in toxic biomes, so on a normal world type none
of this happens.

### Sealing a room

A **Life Support Unit** flood fills the room its vent faces, up to `maxSealedVolume` blocks. If the fill spills
past that, or reaches the sky, the room is not sealed. The unit's front lamp says what it thinks: green for
sealed, red for a leak, dark for no power.

* **Airtight**: any full block (stone, planks, glass, hull plating, reinforced glass), any liquid, and a closed
  airlock door.
* **Not airtight**: ordinary doors, trapdoors, slabs, stairs, fences, walls, iron bars.

A sealed, powered room recovers to full air in 30 s. A breached room loses its air in 10 s. A sealed room with
no power goes stale over three minutes. Air quality scales the exposure rate, so a half-scrubbed room is
half as bad as outside.

The unit burns 2 E/t from a 60k E buffer, fed by power cells or anything that pushes Team Reborn Energy.
A **Solar Collector** makes up to 6 E/t in full sun and nothing at night, and pushes it into whatever it
touches. **Power Conduits** carry it further: they level against each other and empty into docks and life
support units, and they are full blocks, so a run of them can be part of a wall. Two collectors keep one life
support unit running through the night with room to spare.

**Airlock Doors** are airtight when closed and slide shut on their own three seconds after opening. Two of them
with a one block chamber between are a proper airlock: open one, drive the chassis in, let it close, open the
other. Opening a single door straight to the outside vents the room.

To expand: build the new room against the old one with the chassis, seal it, put a life support unit in it (or
leave it to share air once joined), then knock through the shared wall from inside.

### Contamination and the decon shower

A chassis out in the open picks up **grime** (the HUD shows it) and keeps it until it is washed. Anything
dirty inside a sealed room drags the air quality down, and so does caustic material: caustic sand, ash,
sulfur and sulfur crust in someone's pockets or lying on the floor, and caustic blocks built into the walls.
The life support unit and the scanner both report the sources. Caustic items in a chest are fine.

A **Decon Shower** goes in the ceiling of an airlock chamber. When something dirty is in the chamber with both
doors shut it runs a spray cycle (three seconds by default) and the door into the base stays interlocked until
the cycle finishes. Caustic cargo sets off an alarm instead: the inner door will not open until it leaves. The
outer door never locks, so you can always back out.

### Sleep

You can sleep at any hour. A night's sleep still ends at dawn. A daytime nap advances the clock by a couple of
hours plus more the more tired you are, never past dusk, and either way fatigue resets. In multiplayer the
usual sleeping-percentage rule decides whether the clock moves.

### Survivors and the radio

You are not the only one out here. On first join the mod scatters a few **shelters** a few hundred blocks from
spawn, each with a survivor holed up inside. Carry a **Field Radio** (or sit in a chassis, which has one built
in) and every couple of minutes one of them calls in. Use the radio for a list of signals with strength, rough
distance and a compass bearing.

There are six of them, and where each one is placed is the campaign in map form. **Okafor** and **Sorensen**
are within a drive. **Tanaka** is eleven hundred blocks out, past what the pod's mast can hear, so until you
extend the band he is a bearing and a carrier with nothing on it. **Brandt** and **Reyes** are on the far
side of the Rift, in the belt, where it rains something that eats machines; their shelters are built to a
different pattern for it. **Novak** is not in a shelter at all. See `docs/DESIGN-campaign.md`.

Each survivor needs something, says so, and pays back with a chassis upgrade: a fabricator module, a cargo
bay, a battery expansion, reinforced plating, and the three modules that open the country past them. Every
shelter has a **chassis port** in its south wall: a chassis that uses it talks to the room through a
terminal, no door opened, and the nearest one, Greenhouse Station, sends the **Crawler Blueprint** over it,
which the crawler kit recipe needs on the bench (and hands back). Every shelter also has a **collar** in its
west wall: back the crawler onto it and the survivor walks aboard; couple at home and they step into the
pod. That is the rescue. Once home they stay on the air with small talk, less often.

Once the company's ship has gone, every shelter's scrubber starts losing a little each day. Nobody dies of
it. It is a graph going the wrong way in six places at once, and it is what turns a contract into a rescue.

## Before the landing: the Provender

The story does not start in the pod. The first player into a fresh Toxic Wastes world wakes in the med bay of
the **SSV Provender**, a supply hulk six days out from **Sallow**, the planet the wastes are on. The ship lives
in its own dimension (`surrogate:transit`): a void with no sky light, a starfield and a sun drawn by the mod,
and the planet outside the windows, which starts the week as a point of light off the bow and ends it filling
the aft glass. Outside the hull is vacuum, and the ship's scrubbers are the only reason it is not.

There are three people awake aboard. **Mags Castellanos** flies the ship and has flown this run forty-one
times. **Dr. Nadia Ferreira** thaws you out and fits the port behind your ear. **Bram Teague** keeps the
engine and the air going. In the hold, twelve more pilots sit in dive chairs with their eyes shut: they were
linked to chassis on Sallow before you woke, and they stay linked, because transit is billable while the link
is up.

The week is seven days, and each is a chapter:

1. **Thaw.** You wake. The captain shows you the light in the window and tells you what the job is. Eat.
   Sleep in the bunk with your name on it.
2. **The port.** Ferreira sits you in the link chair and calibrates the port on a unit in the hold: your
   first dive, ten seconds of boot and then the hold through a lens. Something on the relay bleeds through.
   It is Marsh, on the planet, asking who you are.
3. **Manifest.** Teague needs hands on the coolant line to the rack. While you are down there, chair seven
   alarms. Vasquez, third rotation, six hundred and eleven days. They cannot wake him in time.
4. **Turnover.** Halfway. The main engine cuts, you weigh nothing, loose things float, and the whole sky
   swings past the port window while the ship turns to burn the other way. The relight opens a seam in the
   hold. Teague hands you a rebreather and hull plating and you go into a venting compartment to plate the
   hole, with the scrubber losing and your lungs filling. Afterwards, Halloran calls from Site Two on Sallow.
5. **Sallow.** The planet is astern now, and big. Castellanos names the biomes from the aft window and
   tells you what the run really is: bodies go down, freight comes up. Nobody comes back.
6. **Contract.** The captain offers to log you unfit and fly you home. The ship's own system answers before
   you can: descent scheduled, medical hold denied. Halloran gives you her two rules over the relay.
7. **Drop.** The pod. Sedation is standard. "You slept through the landing, which is the correct way to do it."

Time aboard is told three ways: the readout in the corner (day, distance, ETA, the ship's clock and what is
wrong with it), the ship itself (lamps dim at twenty hundred, a lamp in the corridor lights for each day
survived, the bridge screens go red when something breaks, chair seven's sign changes), and the windows.
Days end when you sleep in your bunk; if you refuse, Ferreira ends them for you.

The ship runs on the same rules as the ground. The compartments are sealed volumes with life support units;
the breach on day four is a real breach, with the same air and toxin readouts, and the **Rebreather** it
introduces is a real item: sixty seconds of your own air for a body that has to go somewhere it should not.
Hold **jump** to skip a cutscene. Every objective has a timeout and the crew will do it for you. The week
survives restarts and picks up at the current day. Turn it off with `transit` in the config, and the story
starts in the pod as before.

Press the **mission log** key (`J`) at any time, mid-cutscene included, for the current objective, the hint
for it in someone's own words, and a transcript of everything said so far; the objective banner shows the
key. The day-four breach is a **Breached Plating** block, a torn frame with the stars through it: use a hull
plate on it to seal it, or break it out and build a plate into the hole. The **Wall Terminals** around the
ship (bridge, med bay, engineering, hold, drop bay) and in the pod on Sallow open the company's terminal OS:
a live status page and the logs, notes and manifests of whoever sits at that station. The galley has a
**Galley Unit**: put food in, wait for the ding, and do not press the button while it runs. Ballast the cat
is Castellanos's, then yours; she rides the pod down and waits by the bed.

## The opening

The first player into a fresh Toxic Wastes world does not get a chest and a chat message. After the week
aboard the Provender the drop pod puts them down at **Site Four**: Habitat Seven, sealed, powered, and empty,
with Ballast the cat by the bed. The two people on Sallow, **Ines Halloran** (station lead) and **Teo Marsh**
(the company's representative, who fixes things), are six hundred metres away at **Site Two**, and for the
first days they are voices on the radio. The company landed you where the survey grid wanted you, not where
the people were.

**Day one.** The pod's own voice reports the landing; then Halloran calls, and the first objective is to find
the field radio in the chest and answer her. She talks you through the scrubber lamp, the airlock, your kit,
the chassis, the card, the chair and the first dive, over the radio. If you dawdle, the pod's cargo arm and
Marsh's remote keying do it for you. Take the chassis outside and she tells you about the wastes; then sleep,
or wait for the light.

**Day two.** Her chassis has walked over in the night and stands on your pad with a crate: plates, a power
cell, and her *Common Room, draft 3*, the room where four people could sit at one table. Marsh reads out what
the company wants (the **Sallow Assay**: samples to the crate, numbers to the relay, and then, allegedly, a
ship). Drill a lump of sulfur crust and drop it in the **assay crate** by your door; walk with her chassis to
the scrap heap; hear about the slab east of your pod where module two was never shipped.

**Day three.** Marsh walks over in person, two hours in a suit rated for one. Cycle the airlock for him (inner
door, wait, outer door; he lets himself in if you will not), and he is the first face in the room. He fixes
your charging dock (hand him the wrench, or he uses his own), notices the cat, asks about the seeds Castellanos
sent, and leaves his clipboard of company forms on your table on the way out. Dusk, the first survivor's voice
on the pod set, and a title card.

Around the pod: the landing pad, the assay crate and its sign, the plated slab of module two, and a docking
collar on the west wall with nothing docked to it yet (see `docs/DESIGN-crawler.md`). Site Two is a real
place: the same pod with a crew annex bolted on, built when its chunk first loads, with Halloran in her chair,
Marsh by the scrubber and her chassis on the pad. The field radio lists it as a signal with a bearing; sneak
and use the radio to raise Halloran for whatever is on her mind.

The sequence is cinematic: letterbox bars, a scripted camera, subtitles, chapter cards. Hold **jump** to skip
the rest of a day. It runs once per world, survives restarts, and only for the first player; everyone after
gets the plain welcome. Turn it off with `prologue` in the config, replay it with `/surrogate prologue start`,
or jump to a day with `/surrogate prologue day 2`. Replaying the ship week (`/surrogate transit start`) resets
the opening so the landing tells it again, with one cat.

## The research runs, and what is out there

After the three days in the pod and before the company's contract, the two people within a drive have work
for you. **Okafor** wants a twelve-block core sample out of the valley floor. **Sorensen** wants four
**survey stakes** on four different biomes, eighty blocks apart, left overnight. Then the two of them
together want a **sealed sample** out of an acid channel, brought home without opening it in the open air.
Halloran opens and closes each on the radio, every objective times out and somebody works around it, and
five errands sit around the edges and are never objectives: the packet of tomato seeds Castellanos put in
your kit, a hull plate on Sorensen's blown airlock, and the three things people have been asking for.

Out of it comes the **relay schematic**, and with it the fourth voice on the band. `/surrogate research
start|stage <name>|skip|fast|status` drives it; `docs/DESIGN-campaign.md` is the whole plan, eight acts of it.

Nothing lives on Sallow. The vanilla mobs are gone from the biomes and swept up by the server if they arrive
some other way; what replaced them is weather, geology and one animal that has never seen the sky
(`docs/DESIGN-hazards.md`):

* **Borers.** Below y 8 the rock is somebody's. They hunt vibration, and a mining drill is the loudest thing
  that has ever happened to them. A borer damages the stone it passes through using the same crack overlay
  your own mining draws, so one pass leaves a seam and one circling a spot chews out a cavern — which is the
  failure mode: you hear it working, you keep drilling, and then the ceiling is gone. Hull plating slows one
  to a crawl, so a plated shaft is a real defence. There is always warning: a grind through the rock, dust
  off the ceiling, and a seismic bar on the HUD. Tanaka's **resonance damper** buys you forty seconds of
  drilling instead of eight; a **damper beacon** does the same for a mine that has to stay put.
* **Magnetic storms.** Ninety seconds of warning, then twenty minutes of the sky going wrong. The uplink
  tears and desaturates and eventually drops you back into your chair wherever the chassis was standing; the
  radio goes to noise; the sonar fills with ghosts; solar collectors make almost nothing, so a base runs on
  its buffer. A sealed room, a dock or a docked crawler shields the link completely. The rule you learn is:
  when the mast calls it, park.
* **Geysers.** A fumarole that still works, on a ninety-second clock you can watch: quiet, steam at the lip,
  four seconds of rumble, then eight of a scalding column ten blocks high. Watch one cycle and you can walk
  past it forever. Each eruption lays fresh sulfur crust, so a geyser field is a sulfur mine for anyone
  working to a clock, and a **geothermal tap** capped on during the quiet part turns one into about a
  hundred solar collectors.
* **Acid rain.** Only in the **belt**, past the Rift, and on its own clock. It eats machines — collectors,
  conduits, docks, life support, terminals, masts — and a machine's output falls as it corrodes until the
  thing breaks and leaves a stub a hull plate can put back. A chassis in it loses hull; a crawler drains
  four times as fast and then starts losing hull. Anything with a solid block above it is untouched, which
  is the only building lesson the belt teaches, and it is why the shelters over there have sloped roofs and
  no bare metal. **Acid coating** and **ceramic cladding** make it survivable, not pleasant.

**The Rift** is a chasm in the noise, sixteen to forty blocks across and thirty-four deep, cut through every
valley floor it crosses. It is continuous, so together with the tables it partitions the floor a crawler can
drive: the far side is unreachable until somebody builds a bridge, and that is where the last three people
are.

## Chassis tools

| Item | What it does |
| --- | --- |
| Mining Drill | Pickaxe and shovel in one, fast, iron-tier drops. Never wears out; each block costs the chassis 30 E. Dead weight outside a chassis. |
| Arc Cutter | A 7 damage blade. Never wears out; each strike costs the chassis 60 E. Will not swing outside a chassis. |
| Atmosphere Scanner | Reads the air where you stand: sealed and how big, or breached and where (it marks the leak with particles). Sneak-use on a block to ask if it is airtight. Works in the hand too. |
| Field Radio | Hears the survivors while it is in your pack. Use it for a bearing on every signal. |

### Cargo and crafting

A fresh chassis has 18 cargo slots (the hotbar and one row) and cannot craft: the rest of your inventory shows
as **sealed bays** while linked, and the pocket crafting grid is dead. A **Cargo Bay** upgrade opens another
row, twice. A **Fabricator Module** fits a full 3x3 bench, opened from the pilot menu (`Z`). Both are fitted
like any other upgrade, on a powered-down chassis, and survive wrecks and rebuilds.

To get at a parked chassis' hold as a person, sneak-use it with an empty hand.

A chassis nobody is linked to cannot be attacked by mobs or players. Explosions, fire, lava and falls still
hurt it.

## What makes it hard

* **No regeneration.** Hull only comes back from **Repair Kits**, used on a powered-down chassis or on a dock
  holding one. You cannot patch the chassis you are sitting in; bring it home, or send a second robot.
* **Wrecks.** At zero hull the chassis is replaced by a **Wrecked Chassis** lying where it died, holding
  everything it carried. Loot it with another robot, or salvage it with a **Chassis Wrench** for a
  **Scrap Chassis** that rebuilds (2 iron ingots + servo motor) with its upgrades and chair binding intact,
  but with 1 hull point and no charge.
* **Power.** A base chassis holds 30 minutes of idle run time. Sprinting drains three times faster and booting
  costs a minute of charge up front. At zero power the chassis goes dark wherever it is and drops the link.
  Chassis left running in unloaded chunks are billed for the time when they load again.
* **Your body.** While linked you cannot eat or drink, hunger keeps ticking, and starvation reaches your real
  body. Fatigue builds whenever you are awake (about 50 minutes by default). Past the limit you pass out for
  30 seconds, lose the link, and wake up still exhausted. Only a full night's sleep resets fatigue.
* **Cargo is the robot's.** Your own inventory stays in the chair. What you pick up in the field is the
  chassis' cargo, and it goes down with the chassis.
* **The air.** See above. The base is only as safe as its worst wall.

## Upgrades

Apply by using the item on a powered-down chassis.

| Item | Effect |
| --- | --- |
| Reinforced Plating | Hull 10 → 20 |
| Hardened Plating | Hull 20 → 40 (needs Reinforced first) |
| Battery Expansion | Power capacity ×2, stacks twice |
| Cargo Bay | +9 cargo slots, stacks twice |
| Fabricator Module | 3x3 crafting from the pilot menu |
| Relay Module | Doubles link and radio range. Sorensen's schematic; it is what reaches Tanaka |
| Resonance Damper | Plays a tone into the rock the borers steer by, so your drilling counts for a fraction and one that has fixed on you loses the fix. Tanaka's |
| Acid Coating | Most of the belt's rain off a chassis |
| Shielded Uplink | Halves what a magnetic storm does to the picture, and doubles the time before the link drops. Reyes' |

The crawler takes modules too, fitted by using the item on a parked hull. There is one so far: **Ceramic
Cladding**, Brandt's pattern, which is the difference between a dash into the belt and being able to work
there.

## Blocks and power

* **Dive Chair**: bind with an uplink card, use to dive, sneak-use for status, sneak-use with a wrench to unbind.
  While you are out, your body is rendered sitting in it.
* **Charging Dock**: stores one chassis and charges it at 60 E/t from a 100k E buffer. Feed the buffer with
  **Power Cells** (6,000 E each) or with any Team Reborn Energy producer or cable (Tech Reborn, Modern
  Industrialization, and friends) from any side. Use a repair kit on it to repair the docked chassis. Use an
  uplink card on it to key the card to the docked chassis.
* **Life Support Unit**: see Sealing a room. Use it for a status line.
* **Solar Collector**: a 3 px panel that must see the sky. Pushes power into every neighbour. Not airtight, so
  it goes on top of the roof, not in it.
* **Power Conduit**: 6 from 4 hull plating, 4 copper and a redstone. Airtight. Chains power from collectors to
  docks and life support units through walls and floors.
* **Decon Shower**: hull plating, iron, a water bucket, copper and redstone. Airtight, goes in an airlock
  chamber ceiling. See Contamination.
* **Hull Plating**: 8 from 8 cobblestone and an iron ingot. Blast resistant, airtight, looks the part.
* **Reinforced Glass**: 4 from 4 glass, 4 iron nuggets and an ingot. Airtight, so it is how a base gets windows.
* **Airlock Door**: 6 iron and a redstone.
* **Galley Unit**: 6 iron, glass, redstone and copper. Use with food to load it; it hums for five seconds with
  the light on, dings, and anything with a smelting recipe comes out cooked. Use with an empty hand to take
  the result. Pressing it while it runs hurries it; the fourth press ends the casserole.
* **Wall Terminal**: 5 iron, glass, 2 redstone and copper. Airtight. Opens the terminal OS: a live status page
  (the ship aboard, the air and your body on the ground) and the pages of whichever unit it is wired to. One
  you place yourself carries the pilot manual. Pages live under `terminal.surrogate.<unit>.<n>.title/body`.
* **Breached Plating**: not craftable; what a hull plate turns into when it lets go. A torn frame, not
  airtight. Use a hull plate on it to make it a wall again.
* **Geyser**: not craftable in any useful sense; it generates. A fumarole that still works, on its own
  ninety-second clock. Standing in the column kills a body and nearly kills a chassis.
* **Geothermal Tap**: capped onto a live geyser during the quiet part of its cycle, and it pushes power into
  its neighbours the way a solar collector does, on the order of a hundred of them. Try it during the rumble
  and it comes straight back off.
* **Relay Mast**: repeats the pod's band another 400 m while something feeds it. Two in a chain is how the
  far side gets covered.
* **Damper Beacon**: holds the rock quiet around a mine that has to stay put, for as long as it has power.
* **Span Anchor**: the near end of a bridge. It remembers which way the deck runs; the kit that lays the
  deck is act five and is not built yet.
* **Survey Stake**: a ranging rod. Planting four of them on four biomes is Sorensen's wind count.
* **Corroded Machine**: what the belt leaves of a machine that stood in its rain. Not a full cube, so a
  corroded life support unit is also a hole in the wall, which is usually how you find out. A hull plate
  puts back what it was.

## Configuration

`config/surrogate.json` is written on first launch. Every number above is in there: hull per plating tier,
capacities, drain rates, boot and shutdown times, repair amount, cell energy, dock rates, fatigue limit,
blackout length, how much fatigue you wake up with, and the whole atmosphere section: exposure and recovery
times, air recovery, leak and stale times, sealed volume cap, life support and solar rates, airlock close
delay, tool energy costs, contamination rates and the decon cycle length, cargo slots per bay, nap length,
survivor count, distance and radio interval, and whether to build the starter habitat. `toxicAtmosphere`
turns exposure off entirely. `prologue` turns the opening sequence off, `prologueObjectiveTimeoutTicks` is
how long the crew wait before doing an objective for you, and `prologueReadSpeed` (characters per second)
paces the subtitles. `transit` turns the week on the ship off, `transitDayTicks` is how long a ship day runs
while you are awake, `transitObjectiveTimeoutTicks` is how long the ship's crew wait before doing something
for you, and `transitRestTimeoutTicks` is how long you can ignore your bunk before the doctor steps in.
`research` turns acts one and two off, and `assay` the contract.

The whole hazard layer is in there too, in one block: `hazards` turns all four of them off at once, and
`vanillaMonsters` puts the ordinary mobs back. Under that sit the borer line and how loud a broken block is,
the storm's interval, warning, peak and what intensity drops an unshielded link, the geyser's cycle and what
the tap makes, and the belt's rain clock, corrosion rate and what it does to a hull. `lockedSeed` is the map
the campaign was designed against; the new-world screen offers it for a Toxic Wastes world unless you type
your own, and blank offers nothing.

## Development

```
cd surrogate
gradle build                       # jar lands in build/libs
gradle runClient                   # dev client
gradle runServer -PwithCarpet      # dev server with Carpet, so /player fake players can drive the dive flow
python3 tools/gen_textures.py      # regenerate all textures (needs Pillow)
python3 tools/gen_data.py          # regenerate worldgen, tags, loot, recipes, block states, models, lang
python3 tools/gen_sounds.py        # synthesize the cinematic sound effects and write sounds.json (numpy, scipy, ffmpeg)
python3 tools/smoke_test_server.py # headless check: boots a Toxic Wastes server, spawns a fake player, tests the pod
python3 tools/smoke_test_prologue.py # headless check: the three days on the ground with a fake player, ship week off, fast mode
python3 tools/smoke_test_transit.py  # headless check: runs the whole week aboard the ship with a fake player in fast mode
python3 tools/smoke_test_crawler.py  # headless check: collar door, cabin, helm drive, click-to-couple docking, bay, chair dive, the first shelter (boarding, blueprint at the port, homecoming)
python3 tools/smoke_test_research.py # headless check: acts one and two, the three runs and the range gate at Tanaka
python3 tools/seed_search.py [n] [from]        # scores n candidate seeds with no world behind them and names a winner
python3 tools/terrain_scan.py [radius] [step]  # headless check: places the sites, drives the reachability grid from the pod to each, paints build/terrain_map.png
gradle runClient -PquickPlay=<world> -PdevTransit=day4   # dev client straight into a world, restarting the ship week at a day
gradle runClient -PquickPlay=<world> -PdevPrologue=day2  # the same, skipping the ship and restarting the days on the ground at a day
python3 tools/gen_voices.py --lines | --audition | --render   # ElevenLabs voice lines (ELEVENLABS_API_KEY; see tools/voices.json)
python3 tools/dev_client.py --day 5 --shots 40 --quit 2400 --fast --look 60,-6   # the same, screenshotting to run/screenshots and quitting itself
```

`/surrogate prologue start|skip|day <n>|fast|status` (op level 2) replays the opening for you, cuts it short, toggles
quarter-length timings for the next run, or reports where it is. `/surrogate research start|skip|stage <name>|fast|status`
does the same for acts one and two. `/surrogate transit start|skip|day <1-7>|fast|status`
does the same for the ship: start over, jump to the drop, jump to a day, or say where the week is. Both
`fast` switches are the same switch. `/surrogate terrain scan|map [radius] [step]|here` reads the mesa
valleys (see `docs/DESIGN-crawler.md`): the drives from the pod to every site and whether a crawler could take
them, a painted map to `terrain_map.png` in the run directory, or what the ground under you is. `/surrogate terrain seeds <count> [from]` scores that many candidate seeds
in one run without creating a world for any of them, prints a ranked table and names a winner: that is how
seed 3878 was chosen (`docs/DESIGN-campaign.md`, "The map"), and `tools/smoke_test_server.py` pins it so
every headless run is the same map.
`/surrogate crawler dockat home|<n>` moves the hull onto a collar and couples it there (a survivor boards at
their shelter, steps into the pod at home). `/surrogate crawler spawn|dock|undock|charge` puts a charged hull on the starter pod's apron, backs it onto the
collar and unlocks the collar door (or seals it again), or tops up every hull near the pod; `board`, `leave`,
`seat helm|dock` and `stand` do what using the hull, the hatch and the consoles do. The game rule
`surrogateIntro` (Game Rules on the create-world screen, or `/gamerule surrogateIntro false` before the first
join) skips the ship week and the pod days for a replay. `tools/dev_client.py` copies the last smoke-test world into `run/saves`,
boots the client straight into it at a chosen day, and with `--shots`, `--quit` and `--look` records frames to
`run/screenshots` and closes on its own, so a scene can be checked from files without anyone at the keyboard.

The ship is built by `ShipBuilder` (rooms as boxes, the hull grown around them, furniture by coordinate) and
run by `Transit`, a second `Director` sharing the beat vocabulary with `Prologue`. Its sky is
`TransitSkyRenderer`, its readout `TransitHud`, and its lines live under `cinematic.surrogate.transit.*`. The
posters and screens on its walls are `poster` blocks; the source art for them and for the planet sits in
`tools/art/` and `gen_textures.py` prefers a painted file there over its own placeholder (see `TODO-ART.md`).

`gradle runClient -PdevShots=100 -PdevQuit=600` with no `quickPlay` screenshots the title screen (the mod's
own: Sallow in a starfield, the vanilla buttons underneath, drawn by `TitleScreenMixin`), and
`-PdevCreateWorld=true` opens the new world screen from it after three seconds, where the Toxic Wastes preset
is preselected (`CreateWorldScreenMixin`). The "experimental settings" warning vanilla puts on every world
with modded worldgen or an extra dimension is off: `RegistryLoaderMixin` files data-pack registry entries as
stable and `DimensionOptionsRegistryHolderMixin` does the same for the dimension set.

The builders (`ShipBuilder`, `HabitatBuilder`, `AnnexBuilder`) place blocks with `FORCE_STATE`, that is with
no shape updates: the world strips `SKIP_DROPS` from the updates it sends to neighbours, so a sign placed
before its wall or a bed half before its partner would pop off as an item. Sweeps for dropped items go through
`world.iterateEntities()`, because `getOtherEntities` only sees chunk sections the server is already tracking
and a freshly loaded dimension has none; that pairing was the source of the piles of signs, crops and doors
that used to appear on the deck after a rebuild.

The cinematic sounds are placeholders synthesized from noise and sine waves. To replace one, drop a real
`.ogg` over the file in `assets/surrogate/sounds/` and run `gen_sounds.py` again (it keeps files that exist;
`--force` rebuilds them). Recorded voice lines go in `assets/surrogate/sounds/voice/`, named after the lang
key without its `<type>.surrogate.` prefix (`prologue.wake1.ogg`, `okafor.radio.1.ogg`); `gen_sounds.py`
lists whatever it finds there and the client plays a line's recording under its subtitle when one exists.

The smoke test needs Carpet (it is fetched by `-PwithCarpet`). It writes `run/eula.txt` and a
`run/server.properties` with RCON on and the Toxic Wastes preset, deletes and recreates the `run/toxic_test`
world, then checks the pod is sealed and powered, that the airlock cycles, and that pulling a roof pane
registers as a breach. Output goes to `build/server.log`.

The textures are generated: the item icons are ASCII pixel maps in `tools/gen_textures.py`, the robot skin,
block faces and mod icon are painted procedurally by the same script. Edit the maps and rerun it.

The data-driven half of the Toxic Wastes (world preset, noise settings with custom surface rules, biomes,
features, tags, loot tables, recipes, block states, models, and their lang entries) is generated by
`tools/gen_data.py`; the hand-written files for the original blocks and items are left alone. The vanilla
overworld noise settings it starts from are kept in `tools/` so the output is reproducible.
