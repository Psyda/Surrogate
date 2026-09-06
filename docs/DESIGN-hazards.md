# The four things on Sallow

Nothing on this planet wants to eat you. There is nothing alive on the surface at all, which is why the
vanilla monsters are gone: a zombie on the salt pans was always a joke the world was not in on. What is left
is weather, geology and one animal that has never seen the sky, and each of them takes something specific
away from the player.

| Hazard | Takes | Where | Counter |
| --- | --- | --- | --- |
| **Borers** | The deep, and the ore in it | Below y 8, anywhere | Resonance damper; go up |
| **Magnetic storms** | The link, the radio, the radar, the sun | Everywhere, on a schedule | Shielded uplink; be indoors; wait |
| **Geysers** | A chassis that stands in the wrong place | The vent field, on the tables and the flats | Read the cycle; cap it and it pays |
| **Acid rain** | Machines, slowly, and the crawler | The belt, past the Rift | Ceramic cladding; acid coating; a roof |

They are also the campaign's gates ([DESIGN-campaign.md](DESIGN-campaign.md)), which is the point of having
them: each one is a wall until somebody gives you the thing that makes it a nuisance instead.

## No monsters

Every biome in the wastes loses its `monster`, `ambient` and `underground_water_creature` spawner lists, and
a server-side sweep discards any hostile that arrives by some other route (a spawn egg, a dungeon under the
floor, another mod). The wastes are empty. The only things that move out there are chassis, the crawler, six
people in six sealed rooms, and what is under the rock.

`vanillaMonsters` in the config puts them back for anyone who wants them: the overworld's own spawner lists —
the monsters and the bats, at vanilla's own weights — are added to Sallow's biomes as they load, and the sweep
stands down. Biomes load once per world, so the flag takes effect on the next world load.

## Borers

They live in the rock and they have no eyes. They hunt vibration, and a mining drill is the loudest thing
that has ever happened to them.

**The line.** Below **y 8** they are awake. Above it they are not, ever, so the surface, the shelters and
every shallow ore are safe forever. The deep resource the company wants (`tellurium_ore`, below y −8) is
sixteen blocks inside their country, which is the entire reason act three is hard.

**Disturbance.** Every block broken below the line adds to a **disturbance field** on the world: an amount at
a position that decays over a couple of minutes. Drilling steadily in one place raises it fast; ten blocks
mined slowly, wandering, raise it slowly. Past a threshold a borer wakes forty to sixty blocks away, in solid
rock, and comes.

**Coming.** A borer moves *through* stone. It does not open a tunnel behind it: it **damages** the blocks it
passes, using the same crack overlay the player's own mining draws, and a block only breaks when it has taken
enough. One pass leaves a seam of cracked rock. A borer circling a spot, or three of them in the same place,
chews out a cavern — which is exactly the failure mode: you hear it working, you keep drilling, and then the
ceiling is gone and there are four of them in the room with you.

Blocks it breaks drop nothing. Obsidian and bedrock stop it. Hull plating slows it to a crawl, which makes a
plated mining shaft a real defence and a real cost.

**Contact.** A borer that reaches open air where the player is lunges once and goes back into the wall. A
chassis takes most of its hull; a body dies. Then it circles.

**Hearing it.** Long before that: a grinding through the stone that gets louder and directional, dust falling
off the ceiling, and a **seismic** bar on the pilot HUD that fills as one closes. The crawler's sonar shows
them as blips below the floor. There is always warning; the warning is the game.

**The damper.** Tanaka's **resonance damper** is a chassis module that plays a tone into the rock at the
frequency they steer by. Fitted, disturbance from your own drilling counts for a fraction, and a borer that
has already fixed on you loses the fix and wanders. It does not make you safe. It makes forty seconds of
drilling possible instead of eight.

A **damper beacon** block does the same for a fixed site with power behind it, which is how the act seven
tellurium field gets mined at all.

## Magnetic storms

Sallow has no field to speak of and its star is unkind. Every day or two, the sky goes wrong.

**The warning.** Ninety seconds. The pod's mast picks it up first: a rising `MAG` reading on the HUD, a
forecast line on the terminal, and Halloran on the radio telling you to get the chassis under something.

**The peak.** Fifteen to twenty minutes at full strength, then a long tail.

* The **uplink** degrades: rolling static across the pilot view, the picture tearing, colour going. Past the
  threshold the link drops and the player wakes in the chair with the chassis standing wherever it was.
* The **radio** goes to noise. Bearings scatter, survivor calls do not come, and any scripted line that would
  have played over the band is lost — including, once, on purpose, in act four.
* The **sonar and radar** fill with ghosts.
* **Solar collectors** make almost nothing, so a base runs on its buffer and a base with no buffer goes
  stale. This is the storm's real teeth for anybody who thought two panels were enough.
* At the peak, a chassis in the open takes small, repeated electrical damage.

**Shelter.** A sealed room, a docked crawler or a charging dock all shield the link completely. The rule the
player learns is: when the mast calls it, park.

**The counter.** Reyes' **shielded uplink** halves the degradation and doubles the time before a drop. A
**storm mast** at the base turns ninety seconds of warning into six minutes, which is the difference between
running for home and choosing where to be.

## Geysers

The vent field is the reason the belt exists. `vent` blocks (the dead fumaroles) already dot the ash and the
flats; a **geyser** is one that still works.

**The cycle.** Ninety seconds, visible the whole way: a quiet vent, then steam at the lip, then a rumble and
shaking ground for four seconds, then eight seconds of a scalding column ten blocks high, then quiet again.
A player who watches one cycle can walk past it forever. A player who does not loses a chassis.

**In the column.** Heat and acid: heavy damage to a chassis, instant to a body, and everything is thrown
upward. Around the vent, the eruption lays down fresh **sulfur crust**, so a geyser field is a renewable
sulfur mine for anyone willing to work to a clock.

**The tap.** In act seven a **geothermal tap** is capped onto a live geyser — a placement that has to happen
in the quiet part of the cycle, and that fails loudly if it does not. Capped, it produces power on the order
of a hundred solar collectors, which is what an ascent vehicle costs.

## Acid rain and the belt

Downwind of the vent field, on the far side of the Rift, what falls out of the sky is not water.

**Where.** The **belt** is a region of the map, not a weather state: a low-frequency mask read from the same
noise the terrain comes from, so `Valleys` can answer "is this column in the belt" in microseconds and
without loading a chunk, the same way it answers everything else. Inside it, two biomes of its own; outside
it, no acid rain ever falls.

**When.** The belt has its own weather clock, unrelated to vanilla rain: roughly a third of the time, in
bouts of five to fifteen minutes.

**What it does.** Only to machines, and only to machines with the sky above them.

* Solar collectors, conduits, docks, life support units, terminals, masts: each accumulates **corrosion**
  while exposed and rained on. Corroded, output falls; fully corroded, the block breaks and drops a
  damaged remnant. A repair kit or a hull plate puts it back.
* A **chassis** in the open loses hull steadily, and grime instantly.
* The **crawler** loses charge four times as fast and then loses hull. An uncladded crawler has about eight
  minutes in a downpour before it stops, and where it stops is where it stays.

**A roof is the answer.** Anything with a solid block above it is untouched. The rule is one sentence long
and it is the only building lesson the belt teaches, which is why the shelters over there are the shape they
are: sloped ceramic roofs, gutters, panels under a canopy, no bare metal anywhere. Brandt built the first one
and **ceramic cladding** is his pattern.

**Cladding and coating.** Cladding is a crawler module — the first crawler module, and the reason the crawler
needs a module system at all. Coating is a chassis upgrade. Neither makes the belt pleasant; both make it
survivable, which is the difference between a dash and living there.

## Tunables

Everything above is a config field, in one block, so a play test can move any of it:

```
"borerDepthY": 8, "borerDisturbancePerBlock": 12, "borerWakeThreshold": 240,
"borerDecayPerSecond": 3, "borerSpeed": 0.06, "borerBlockDamagePerTick": ...,
"stormIntervalTicks": 36000, "stormWarningTicks": 1800, "stormPeakTicks": 18000,
"stormLinkDropAt": 0.75, "stormSolarFactor": 0.05,
"geyserCycleTicks": 1800, "geyserEruptTicks": 160, "geyserHeight": 10,
"acidRainFraction": 0.33, "acidCorrosionPerSecond": ..., "acidCrawlerDrainMultiplier": 4,
"vanillaMonsters": false
```
