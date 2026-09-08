# The campaign: from the drop to the ascent

The whole game, end to end, and where each piece of it stands. The two arcs that exist
([the week aboard](../README.md#before-the-landing-the-provender) and
[Contract Seven](DESIGN-assay.md)) are the first and third acts of it; this note is the spine that joins
them and carries on to the launch.

The shape is one sentence: **the company sends you down to take samples, takes the samples, and leaves; the
eight people it leaves behind get off the planet anyway, in something they build themselves, if you can reach
all of them.**

Related: [DESIGN-assay.md](DESIGN-assay.md) (act three), [DESIGN-crawler.md](DESIGN-crawler.md) (the
vehicle), [DESIGN-hazards.md](DESIGN-hazards.md) (the four things on Sallow that stop you).

## The acts

| Act | Name | What it is | Gate out of it | Status |
| --- | --- | --- | --- | --- |
| 0 | The Provender | Seven days aboard the supply hulk | The drop | **Built** |
| 0 | Site Four | Three days in the pod, learning the chassis | Day three ends | **Built** |
| I | The Neighbours | Okafor and Sorensen: three research runs and the errands around them | Sorensen's relay schematic | **Built** |
| II | Out of Range | Tanaka is past the mast's reach | The relay module | **Built** |
| III | Contract Seven | The assay: stake, pad, core, payload, ignition | The ship leaves | **Built** |
| IV | The Conference | Everything starts failing; the call from the hub terminal | The rescue plan | **Built** |
| V | The Rift | The bridge, the acid belt, three more people | Everyone aboard | **Built** |
| VI | The Vote | Where eight people and a cat are going | A destination | New |
| VII | Ascent | Build it, house them, feed them, leave | The ending card | New |

Acts I, II, IV, V, VI and VII are the work this note plans. Each is a `Director` script the same way
`Prologue`, `Transit` and `Assay` are, so all of them resume across restarts, skip a beat at a time, and work
around anything the player ignores.

Acts one and two are `research/Research.java`, driven by `/surrogate research start|stage <name>|skip|fast|
status` and checked headlessly by `tools/smoke_test_research.py`. Acts four and five are `rescue/`, driven by
`/surrogate rescue call|skip|plan|span|flow|cut|plate|lift|tp <where>|board|status|reset` and checked by
`tools/smoke_test_campaign.py`. The map underneath all of it is chosen rather than rolled; see
[The map](#the-map).

Only act four is a `Director`. Act five is three obstacles in whatever order the world allows, so it is a
sweep and a set of watchers rather than a list of beats: nothing there asks the player to accept anything,
and each piece notices when the world has changed and says so on the radio.

## The people

Eight, and a cat. Two of them are the colony; six are out in shelters and every one of them is a seat in the
rocket at the end. The names and the station names here are the ones the game ships, which is not what this
note first said: Okafor was two people for a while, "Grace" on the pad and "Dr. Ada" on the radio, and she is
one person now.

| Who | Where | Reached in | Needs | Gives |
| --- | --- | --- | --- | --- |
| **Ines Halloran** | Site Two, 600 m | Prologue | — | The station lead. Chairs everything. |
| **Teo Marsh** | Site Two | Prologue day three | — | The company's man, and the first to stop being it |
| **Dr. Ada Okafor** | Greenhouse Station, 160–280 m | Act I | Power cells | Fabricator module, the crawler blueprint |
| **Mikkel Sorensen** | Survey Two | Act I | Repair kits | Cargo bay, the relay schematic |
| **Yuki Tanaka** | Sulfur Works, past the mast | Act II | Sulfur | Battery expansion, **the resonance damper** |
| **Old Brandt** | Ceramic Row, past the Rift, in the belt | Act V | Bread | Hardened plating, **the cladding pattern** |
| **Imani Reyes** | Clinic Nine, past the Rift, in the belt | Act V | A hull plate for her airlock | The medic, and the **shielded uplink**. Without her, Novak does not come up. |
| **Aleks Novak** | Crawler Four, on the floor of the Rift | Act V | Carrying | Nothing. He is the one you save for nothing. |
| **Ballast** | Wherever the food is | — | — | A seat of her own |

All six are `Survivor` enum entries with shelters, radio lines, terminal pages and chassis ports. Brandt's
and Reyes' shelters are built from the belt kit — sloped ceramic roof, gutters, panels under a canopy, no
bare metal — because they live where it rains. Novak has no shelter at all: he is in a wrecked crawler on the
floor of the Rift, and act five is the only thing that reaches him.

Every shelter has a **porch**: a two-cell airlock chamber bolted onto the south wall, inner door in the wall
and outer door in the far face. It is not decoration. Both of the shelters the player is asked to repair are
damaged in their *outer* wall — Sorensen's one missing plate, Reyes' whole eaten frame — and a hole in a wall
is an untight block, so with a single door those two were standing in open air on a planet that empties a
lung in ten seconds, radioing about it. In a porch the inner door holds, they are alive, and what is broken
is the lock: it will not cycle against open sky. That is the actual reason neither of them has walked out,
and it is why plating the frame from outside is the thing that frees them.

## Act I: the Neighbours

Two people within a chassis walk, three research runs, and the errands that fill the days between them. This
is where the player learns that the survivors are a system and not a chat channel: they ask for real things,
they pay in modules, and their sites are places you go back to.

The three runs are **on rails** — Halloran opens each on the radio, the objective goes in the mission log,
and the crew work around a player who ignores it, as everywhere else.

1. **Core sample, shallow.** Okafor wants a column of the valley floor: drill down twelve blocks somewhere
   flat, in one place, and bring back what comes out. Teaches the drill, the chassis' energy budget, and the
   fact that going down is a thing you do here.
2. **The wind count.** Sorensen wants four **survey stakes** planted on four different biomes, at least
   eighty blocks apart, and left for a night. Teaches the map, the biomes, and coming back.
3. **The seep.** Okafor and Sorensen together: something in the acid at the bottom of a channel is eating the
   sample crate. Take a sealed sample from an acid river, from a chassis, without letting the crate open in
   the open air. Teaches contamination and the decon shower, and it is the first time the two of them argue
   on the radio about whose experiment it is.

Between and around them, five **errands** that are never objectives and are always noticed:

* **The seeds.** Castellanos put a packet of tomato seeds in your kit on day six of the week aboard. Okafor
  is a botanist with nothing green. Give her the packet and Greenhouse Station starts producing food, which
  is what feeds eight people in act seven.
* **Sorensen's airlock.** His outer door has a plate letting go (a `breached_plating` in the frame). Put a
  hull plate on it. Until you do, his radio lines are short, because he is holding his breath.
* **A power cell for the greenhouse**, **repair kits for the shop**, and **eight sulfur for Tanaka** — the
  needs that already exist, now with somebody asking on the radio instead of a silent inventory check.

Out of act one: Sorensen hands over the **relay schematic** and says the thing that starts act two — there is
a fourth voice on the band, too weak to answer.

## Act II: Out of Range

Tanaka is eleven hundred blocks out, on the far side of a table, and the pod's mast cannot hear him. The
chassis has a radio; the radio has a range; that range is now a number the player can see and change.

* The **field radio** and the sonar gain a range ring. Tanaka's signal shows as a bearing with no distance
  and no voice: a carrier, not a call.
* The **relay module** (Sorensen's schematic, built at the fabricator from a servo motor, copper, a power
  cell and reinforced glass) is a chassis upgrade that doubles link and radio range, and a **relay mast**
  block that can be planted anywhere and repeats the pod's band another 400 blocks while it has power. Two
  masts in a chain is how the far side gets covered in act five.
* The trip out is the first long drive: the crawler's real debut as transport rather than a toy, and the
  first time a **magnetic storm** catches the player in the open with the link degrading.

Tanaka is a seismologist and he is the one who knows what is under the floor. He hands over the
**resonance damper**, and he is the first person to say the word *borer* out loud.

## Act III: Contract Seven

[The assay](DESIGN-assay.md), unchanged except at stage three. **The core** now means going below the borer
line, and the damper is what makes that survivable. A player who skipped act two can still do it — badly,
loudly, and with Halloran shouting — which is the rule the whole game keeps.

The act ends with the ship taking the payload and not coming down.

## Act IV: the Conference

The turn from *contract* to *rescue*. It starts the tick after `AssayState.STAGE_DONE`.

**The decline.** Every shelter starts a failure clock the moment the ship leaves. Scrubber output falls a
percent a day; the terminal at each site shows it; the radio calls change from small talk to symptoms.
Nobody dies of it. It is a graph going the wrong way in six places at once, and it is visible from the pod's
hub terminal, which is the point.

**The call.** Halloran asks everyone onto the band at once, and the player is asked to sit at the hub
terminal in the pod. The screen becomes a conference: eight tiles, four across and two down, with six live
faces on them and Marsh in the last corner with the company's logo already peeled off his sleeve. It is
letterboxed, the camera pushes into the screen and never cuts away, and it runs three or four minutes.

Eight tiles rather than six, because eight is the cast and because beat five needs three of them to break.
Reyes has a tile of her own even though she is speaking over Brandt's set, and Novak's is dark from the
first frame and says NO CARRIER, which is the scene's whole argument stated before anybody makes it. The
faces are the heads off the survivors' own skins rather than a second set of portraits, so the people on the
screen cannot drift from the people in the shelters.

Beats of the call, in order:

1. Halloran counts the room. Everyone is on. Marsh reads the last message the relay ever received, which is
   a schedule with no ship on it.
2. Okafor: the greenhouse has eleven days of margin. Sorensen: the shop can build one more of anything.
3. Tanaka says what the seismographs have been saying for a month, which is that the vent field is opening.
4. Brandt cuts in from the far side, badly, through the belt's static. Reyes is with him. Novak is not on the
   call and nobody knows why.
5. **A storm arrives mid-call** and takes the far side off the band. Three panels go to snow. It stays that
   way for the rest of the scene.
6. The plan, in Halloran's words: one crawler, one chassis, everybody home, and then we build the thing the
   company would never have sold us. Marsh, who has spent the whole game filing forms, offers the only useful
   thing he owns: the pad, the gantry, and the fabricator's licence key.

Out of it: the **mission board**, a terminal page listing every survivor, their state (not reached, reached,
aboard, home), their shelter's scrubber, and the one thing in the way of each. It is the campaign's checklist
and it stays live to the end. It appears on the hub terminal only, it is worked out fresh every time the
screen opens — every fact on it is already saved somewhere better — and the blocked column is the whole
value of the thing: six names with six states is a list, and six names with "the Rift" written beside three
of them is a plan.

Also out of it: a **span kit**, from Sorensen's one-more-of-anything, because the far side has to be a drive
by the end of the scene and not a topic.

## Act V: the Rift

The long act. Everything built so far gets used at once.

**The Rift** is a chasm across the valley floor, six to twelve blocks wide and thirty deep, running the length
of the map. The far side has never been reachable: the crawler's reachability grid stops at it and always has.
There are exactly three ways over, and two of them are wrong.

* **The bridge.** A **span kit** built at the fabricator lays a five-wide deck across a gap at a marked
  anchor, one course at a time, like the pad. Five wide because the hull is five wide. It can also be built
  by hand out of plating and takes about four hundred blocks, which is the joke.
* Beyond it, the **acid belt**: two biomes' worth of country downwind of the vent field where it rains
  something that eats machines. Uncovered metal corrodes. Solar collectors stop. A chassis in the open loses
  hull. The crawler loses charge and then loses hull, and a crawler that dies in the belt is a walk home.
* **Ceramic cladding**, from Brandt's pattern, is the crawler module that makes the belt survivable, and
  **acid coating** does the same for a chassis. Getting the pattern means reaching Brandt, and reaching
  Brandt means one dash into the belt with everything charged and nothing to spare. That dash is the act's
  first real set piece.

The three rescues, in the order the world allows:

1. **Brandt, behind the magma flow.** A vent has opened across the approach to Ceramic Row and the crawler
   cannot pass a lava channel. Cut it upstream, drop the wall, let it pond, and bridge the crust. He hands
   over the cladding pattern through the port before he will even discuss boarding, because he wants you to
   get home.
2. **Reyes, behind her own airlock.** Clinic Nine's outer door blew in a storm and its frame — the far face
   of the porch, not her own wall — is `breached_plating` from top to bottom. Plate it from outside, cycle
   it, and she walks aboard. She is a doctor and she asks about Novak before she asks about herself.
3. **Novak, at the bottom of the Rift.** His crawler went over the edge eleven days ago. The wreck is under
   an overhang on the chasm floor, in acid mist that takes a chassis apart in seconds and that a hull cannot
   reach. So the player does it **as a body**: park as close as the ledges allow, put on a **rebreather**,
   sixty seconds of your own air, down the scree, use Novak to pick him up, and carry him back up. Carrying
   halves your speed and the timer does not care. Reyes has to be aboard to keep him alive when you get
   there; without her the run is refused, out loud, by Halloran.

   This is the day-four breach on the Provender paid off: the same item, the same lungs, the same rule, on a
   planet instead of a ship.

The drive home has everyone aboard. The cabin has six passengers in a room built for two, the crawler is
overweight and slow, and it is the best scene in the game.

### What building it changed

**The narrows are searched for, not chosen.** `Rescue.crossing` sweeps rays from the pod towards each of the
three far people, finds where the Rift band is narrowest, and squares the answer up onto a cardinal, because
a deck can only be laid on one. It is a coordinate on the radio and nothing else: the player may cross
wherever they like, and four hundred blocks of plating laid by hand is still a crossing.

Three things it has to be told, all of them learned the hard way. The far three are placed one per compass
sector, so there is no single bearing to "the far side" and a search aimed only at Brandt looks the wrong way
down an empty horizon. Both lips have to be valley floor, or the noise happily reports a twenty block chasm
in the middle of a lake. And the near lip is a slope rather than an edge, so a survey that starts measuring
from the first block of it measures a shoulder and reports no crossing anywhere on the map.

**The Rift is flooded.** The chasm cuts thirty-four blocks below a valley floor that is itself one block above
sea level, so it fills, and the crossing is a bridge over water rather than over air. That reads perfectly
well from the deck. It changes the third rescue, which the design wrote as a climb down the scree: it is a
dive now. The rebreather covers it — it tops the lungs up as well as slowing the toxin, which is the name of
the thing and should always have been true — and Novak's wreck is laid on the real floor of the chasm in a
sealed pocket, which is exactly the overhang his log and Reyes' patient list both describe. Making the chasm
dry instead would mean turning off aquifers or moving sea level, which moves every terrain constant, and
`docs/DESIGN-campaign.md` says out loud that an old seed is not a promise after that. It is left as it is.

**Novak was in a ditch.** He is placed by looking for the Rift, and the Rift mask feathers out to nothing at
its edges, so "in the Rift" included a four-block scratch in the open floor. He is placed at the bottom of it
now, mask at one, thirty-six blocks down, which is what every line either he or Reyes has about the place
says it is.

**A passenger could be lost on the way home.** The cabin is a pocket dimension whose chunks are held only
while a player is standing in it, so somebody put aboard from a docking console outside the hull was in an
unloaded section by the time the hull got home, and the scan of the room found an empty room: the collar
locked, nobody stepped out, and the site stayed marked aboard for good. Who is aboard is read off the saved
state now, along with which hull they are riding in, and anybody the room cannot produce is put back
together at the door. The hull matters: without it a second crawler coming home takes the first one's
passengers out of a cabin they are still sitting in, and one person becomes two.

**Two things the act had to be told about being a game rather than a story.** The call is for whoever sat
down at the terminal, not for whoever the prologue named the protagonist — on a server those are different
people, and the wrong one was being put in a four-minute locked cinematic eight hundred blocks away while
the one who pressed the button saw nothing. And the span anchor is an ordinary block: it mines, it drops
itself, and it takes its bearing from whoever puts it back. Broken and replaced in the same hole while
standing on the deck it just laid, it faces the other way, so the survey is keyed on the bearing as well as
the position and a turned anchor starts again rather than laying the rest of a measured span across open air.

## Act VI: the Vote

Everyone is in the pod. The pod is too small. This is the cinematic where eight people decide where they are
going, and the player is the one who says it out loud.

Three destinations, each argued for by the people who want it:

| Choice | Argued by | What it costs | What the ending is |
| --- | --- | --- | --- |
| **Home** | Marsh, Sorensen | Nine months in the tubes and a debt that outlives you; the company gets to ask its questions first | You land where you left. Sallow becomes somebody else's contract number. The last shot is a form. |
| **Tarsis Junction** | Halloran, Reyes | No home, no company, work for passage, a station that has never heard of any of you | Eight people with jobs and no country. Halloran gets her room with a table in it. |
| **Kepler's Sill** | Okafor, Tanaka, Novak | Forty years asleep on a candidate nobody has surveyed, on the strength of Okafor's spectra | The last shot is a window with no planet in it yet. |

Every survivor has an opinion and a strength of it, and the strength depends on what you did for them across
the whole game: the seeds, the airlock, the errands, whether you got there before their scrubber went under
fifty percent. Brandt will not argue with anyone. Novak votes from a stretcher.

The player chooses. Nobody is overruled bitterly; they are eight people who have already agreed on the hard
part.

## Act VII: Ascent

**Build it, house them, feed them, leave.**

* **Housing.** Every rescued survivor needs a bunk with their name on it. `BunkBlock` exists; the expansion
  onto the module-two slab is where they go. Salvage is where the material comes from: take the chassis back
  to each rescued shelter with a wrench and strip it, which is the last use of every place you have been.
* **Feeding.** Eight people eat. The greenhouse, the farm plot, the galley and the tomato line from act one
  are the supply, and the colony consumes from it daily. Fall behind and the build slows, and people say so.
* **Power.** The ascent vehicle needs more energy than the whole colony's solar can make in a month, and the
  belt is where the power is: a **geothermal tap** capped onto a live geyser turns an eruption cycle into
  a real supply. Capping a geyser means being on it when it goes.
* **The build.** An `ascent_kit` at the gantry, then courses, like the pad: hull, tanks, the seat ring, the
  scaffold. Each course wants materials and hours and the crew's hands, and the mission board tracks it.

**The launch.** The scaffold goes up the side. Using the door at the top fades the player into the cabin, the
way the crawler's hatch does: a cylindrical room with a seat ring, a window band around it, and **a seat for
every person, with their name on it**. The empty ones stay empty. Ballast has one, low, by the heater.

Then: everyone talks, once, briefly. The goodbye is to the planet, and Halloran gives it. The player presses
the ignition. The windows show the valley, the mesa wall, the pad, the Rift, the belt, and then the curve of
Sallow going small, drawn the way the Provender's sky is drawn. The nav console asks for a destination and
the player dials in the one the vote chose. The card comes up.

Done.

## The map

Everything above is placed relative to terrain that comes out of the world seed, and two of the acts are
gates made of that terrain: act two is a long drive, and act five is on the other side of the Rift. A seed
that put the Rift somewhere useless, or that left no far side worth crossing to, would quietly take an act
out of the game. So the map is not left to chance.

`world/SeedSampler` builds a `NoiseConfig` for a candidate seed with no world behind it — two registries and
a number is all a seed is — and `world/SeedSearch` scores the layout that seed would produce, out of a
hundred, in eight components:

| Component | Out of | What it asks |
| --- | --- | --- |
| pod | 15 | Is there a site for the pod, and how much connected floor does it open onto |
| sites | 20 | Is every near-side site reachable and every far-side one not |
| drive | 15 | Drive length over crow flight, per site: 1.15 to 1.9. A straight line is a car park |
| rift | 20 | Is there a large piece of floor cut off by the Rift, 900 to 2200 blocks out |
| cross | 10 | Is the narrowest crossing into it something a bridge can span |
| belt | 10 | How much of that far side is downwind of the vent field |
| wall | 5 | How many sites got a cliff behind them rather than the flat fallback |
| var | 5 | Terrain variety along the drives |

`/surrogate terrain seeds <count> [from]` sweeps them in a run and prints a ranked table;
`python tools/seed_search.py [count] [from]` boots a headless server, drives it and writes
`build/seed_search.json`. A seed costs about a sixtieth of a second: four thousand of them take just over a
minute.

**The locked seed is 3878**, chosen from a sweep of 4096 at 98.9 out of 100: the pod opens onto eighty-one
thousand cells of floor, all six researchers placed as designed, drives averaging 1.47 times crow flight,
seventeen thousand cells of floor behind the Rift at 1895 blocks with a twenty-five block crossing into it
at x 956 z 244, and eighty-five percent of that far side in the belt. It lives in
`SurrogateConfig.lockedSeed`, the new-world screen offers it for a Toxic Wastes world unless the player
types their own, and `tools/smoke_test_server.py` pins it so every headless run is the same map (override
with `SURROGATE_SEED`).

**Re-run the sweep before changing any terrain constant in `tools/gen_data.py`.** The Rift's width, the
belt's edge and the cliff gain all move where everything lands, and an old seed is not a promise.

## The optional work

Eight errands, none of them on the critical path, all of them counted by act six. `Errand`, `ErrandState`
and `Errands` are the whole of it; the design note for the animals two of them need is
[DESIGN-fauna.md](DESIGN-fauna.md), and for the reward tier three of them unlock,
[DESIGN-survey.md](DESIGN-survey.md).

There is one more optional thing that is not an errand and is not on this table, because it does not ask for
anything and cannot be failed: **the flashback**, on the first or second night the player sleeps down here.
It is the only scene the player authors rather than watches, and the only one where something they made
crosses back out of a cutscene into the world. [DESIGN-flashback.md](DESIGN-flashback.md).

Nothing here asks the player to press accept. Each one is offered on the radio when its gate opens and
finishes when the world says it has, which means a player who wandered into finishing one without noticing
still gets the credit, because they did the thing.

| Errand | When | Who | What finishes it |
| --- | --- | --- | --- |
| **Housewarming** | Early | Okafor and Sorensen | Help them both, then sleep. See below. |
| **Something Warm** | Early | Sorensen | Cook something and hand it to him before it goes cold |
| **Okafor's Survey** | Mid | Okafor | A reading of all eight subjects on the table |
| **Ballast** | Mid | — | The cat has gone. Carry her home. |
| **Tanaka's Cable** | Mid | Tanaka | A vent has opened under her power run; cap it with a geothermal tap |
| **Outside Clinic Nine** | Late | Reyes | Carry the body somewhere that is not her window and raise a marker over it |
| **Ceramic Row** | Late | Brandt | Three corroded machines in the belt, put back with plates |
| **Brandt's Ark** | Late | Brandt | One of each living species, alive, in a crate |

### The housewarming

The one that pays for being neighbourly, and the only errand whose gate is another errand rather than an act.

Help Okafor and Sorensen both. The next time you are inside your own base you are told you are further past
tired than you noticed. Sleep. You wake up with the two of them standing in your pod, in person, helmets
under their arms — they drove over and they let themselves in, because your airlock has been keyed to both of
them since the day you got the port working.

They have been talking about you. What they have decided is that the orbital platform still has your second
module in a rack with your habitat's number stencilled on it, that it has been there four hundred days,
that nobody will send it down for one signature — and that three registered sites requesting the same
manifest line is a different question entirely.

Then they walk out and wait on the pad, and **the scene stops until you follow them**. They can cross fifty
metres of open ground because they own suits and you do not; the only way you get out there is the chair and
the chassis, which is the thing the whole game is about. It is the one errand that makes the player do it,
and it costs ninety seconds. If they are left standing out there long enough they give up on the audience and
tell you about it over the radio afterwards; the module lands either way.

`ModuleTwo` builds on the plated slab east of the pod that has been empty since the prologue: six bunks, a
table, lockers, a scrubber and collectors of its own, and a door cut through the pod's east wall. That slab
has been sitting there the whole game with Halloran's draft of what it was meant to be on the terminal
beside it.

Six bunks is not a coincidence. Act five brings home six people.

### Marsh's suit

The prologue has Marsh walk from Site Two to the pod and back — two hours outside — and Halloran says out
loud that his suit is rated for one. Both of those are true and neither is a mistake.

Contractor issue on this contract is the **Tern**: soft suit, one bottle, one hour and a fifteen minute
reserve you are not supposed to touch. Halloran has one. Sorensen has one. Okafor's is nine years old and has
been patched twice. Halloran assumed Marsh had one because it is the only kind of suit she has ever seen.

Company personnel travelling on inspection carry the **Kestrel**: sealed hardshell, regenerative scrubber on
a six-hour cycle rather than a bottle, so the limit is a cartridge and the cartridge recharges off any
powered rack. Rated six hours, hard ceiling nine.

Both suits were made in the same year in the same yard. The difference is that one man is insured as an asset
and everybody else here is insured as a schedule. It is the smallest concrete injustice on the planet and it
is where Marsh starts turning.

It is also the answer to act five. Novak is on the floor of the Rift and getting him up needs a *body* down
there breathing, not a chassis — and there is exactly one suit on Sallow that can spend that long outside.
Halloran's reply on the Site Two terminal says so, before anybody asks.

## What gates what

| Gate | Blocks | Opened by | From |
| --- | --- | --- | --- |
| Signal range | Reaching Tanaka (act II) | **Relay module**, relay masts | Sorensen's schematic, act I |
| Depth | Mining below the borer line (act III stage 3) | **Resonance damper** | Tanaka, act II |
| The Rift | The crawler reaching the far side | **Span kit** (or four hundred blocks of plating) | The conference, act IV |
| The belt | Staying in the acid rain | **Ceramic cladding**, **acid coating** | Brandt's pattern, act V |
| The lift | Building the ascent vehicle | **Geothermal tap** on a live geyser | Act VII |
| Storms | Nothing, ever. They only take | **Shielded uplink** softens them | Reyes, act V |

Every one of these is a soft gate with teeth. There is no invisible wall anywhere in the game: the player who
drives an uncladded crawler into the belt gets in, gets warned four times, and walks home.

## Checklist

Ticked on 2026-09-06 and checked against the tree, not against intent: a box is only crossed off where the
thing runs. The hazard layer that acts three and five hang on is its own note,
[DESIGN-hazards.md](DESIGN-hazards.md), and all four of its pieces are built.

Act I
- [x] Six survivors in the roster, Reyes and Novak added, with radio lines and terminal pages
- [x] `Research` director: three runs, opened and closed on the radio
- [x] Survey stake block and the four-biome objective
- [x] Sealed sample item and the acid-seep run
- [x] Errands: seeds to Okafor, plate on Sorensen's airlock, the three standing needs
- [ ] Greenhouse food output once the seeds are delivered — the errand is recorded and has no consequence
      yet, because what it feeds is act seven

Act II
- [x] Radio and link range as real numbers, shown on the HUD and the sonar
- [x] Relay module upgrade + relay mast block
- [x] Tanaka out of range until it is fitted; carrier-only signal before then
- [x] Resonance damper handed over

Act III
- [x] Borer line wired into the assay's core stage; Halloran's lines about it

Act IV
- [x] Shelter decline clocks and their terminal readouts
- [x] The conference cinematic from the hub terminal, with the storm cutting the far side
- [x] The mission board terminal page, on the hub, with the blocked column
- [x] `/surrogate rescue call|skip|plan|status|board`, so four minutes of scene can be looked at on demand

Act V
- [x] The Rift in the terrain, and the reachability grid proving the far side is cut off
- [x] The acid belt: biomes, rain, corrosion, cladding and coating
- [x] The belt shelter kit: sloped ceramic roof, gutters, panels under a canopy
- [x] The narrows: searched for off the noise, squared onto a cardinal, and read out on the radio
- [x] Span kit and the bridge courses: five wide, a course a use, eight deck plating a course
- [x] Magma flow across Brandt's approach, the wall upstream, and the basin it ponds into when it is cut
- [x] Reyes' blown airlock: four plates in the frame, and she refuses the collar out loud until they are in
- [x] Novak: the wreck on the real floor of the chasm, the mist that eats machines, the carry, and Halloran
      refusing the run out loud until Reyes is up
- [x] Why a body can be down there at all: Marsh's Kestrel, on the Site Two terminal, with Halloran's reply
      saying out loud that he is going to be the one who can
- [ ] The drive home with six aboard — they ride and they arrive, and nobody has written what they say on
      the way
- [ ] The descent is a dive rather than a climb, because the chasm is flooded (see above). Playable, and not
      what the act was written as; a dry Rift is a terrain change and a fresh seed sweep

Act VI
- [ ] Opinions weighted by what the player did — `ResearchState` already records which errands were done,
      which is what the weighting reads
- [ ] The vote cinematic and the three endings' setup

Act VII
- [ ] Bunks, names, housing state
- [ ] Colony food consumption
- [ ] Salvage: stripping a rescued shelter for material
- [x] Geothermal tap
- [ ] Ascent vehicle: kit, courses, scaffold
- [ ] The cabin: seat ring, name plates, empty seats
- [ ] Launch cinematic, the window, the nav console, three ending cards

The optional work
- [x] `Errand`, `ErrandState`, `Errands`: eight errands, offered on their gates, finished by watching the world
- [x] `/surrogate errand list|start|tp|done|reset|status` and `/surrogate fauna spawn|read`, so none of this
      has to be reached the long way to be looked at
- [x] Housewarming: the tiredness, the two chassis, the drop, and `ModuleTwo` on the slab
- [x] Something Warm, Ballast, Tanaka's Cable, Outside Clinic Nine, Ceramic Row
- [x] Okafor's survey: bio-sampler, analysis disk, eight subjects, the terminal page in her voice
- [x] Brandt's ark: the crate bay, six live species, and the count act seven's manifest reads
- [ ] Act six weighting off `ErrandState.done` — the mask is kept and correct and nothing reads it yet
- [ ] The ark's crates as real objects on the pad, and a seat each on the rocket — act seven

The animals ([DESIGN-fauna.md](DESIGN-fauna.md))
- [x] Trundle, slagback, tocker, lantern slug: entities, models, renderers, textures
- [x] Trundles and tockers on the biome spawner lists; slagbacks by geysers and slugs on cave ceilings placed
      by `Fauna`, because the vanilla spawner can aim at neither
- [x] The slagback's fold, its shadow, and the fact that standing on one is how most people meet it
- [x] The lantern slug dying of the fall rather than the hit

The survey tier ([DESIGN-survey.md](DESIGN-survey.md))
- [x] Survey station: the scan, the packet, and the wireframe screen
- [x] Beacons: the chain, the red and green lamp, and the surveyed area being the shape of where you walked
- [x] Long-range scanner: range as the square root of everything ever fed in
- [x] The whole-map moment when a network covers every shelter at once

The map
- [x] `SeedSampler`: score a candidate seed with no world behind it
- [x] `SeedSearch` and `/surrogate terrain seeds`, and `tools/seed_search.py` to drive them
- [x] A seed searched for, chosen and locked, and pinned in the headless tests
- [x] `/surrogate terrain scan` knows a gated site from a blocked one

The hazards
- [x] No monsters: empty spawner lists, no dungeons, and a server sweep behind them
- [x] Borers: the disturbance field, the wake, the chew with the crack overlay, the lunge, the damper
- [x] Magnetic storms: the warning, the link degrading and dropping, the radio, the sun, the sky
- [x] Geysers and the geothermal tap
- [x] Acid rain: the belt, corrosion, the chassis, the crawler, cladding and coating
- [x] `minecraft:canyon` out of the carvers, so the Rift is the only chasm and `Valleys` knows where it is
