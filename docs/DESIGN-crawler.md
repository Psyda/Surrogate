# Design: mesa valleys, base towers and the crawler

Status: every step of the order of work below is built and checked headlessly. Steps 1 to 5 and 7 landed
2026-09-05; step 6's remainder — beds, food and water for the people who come home — is act seven of
[DESIGN-campaign.md](DESIGN-campaign.md) and is tracked on that note's checklist instead of here. This note
records the direction agreed on 2026-09-05 so the surface work (Site Two, the module-two slab, the docking
collar) points the right way. See "What exists" at the end for the code.

Two things have changed under it since: the valley floors are now cut by **the Rift**, a continuous chasm in
the noise that the reachability grid refuses to cross (which is what makes act five a gate rather than a
long drive), and `minecraft:canyon` is out of the biomes' carvers, because it was cutting real chasms across
drives that `/surrogate terrain scan` called drivable. See [DESIGN-hazards.md](DESIGN-hazards.md) for the
rest of what is out there now.

## The idea

Sallow's surface becomes wide, flat valleys between mesas: long clear runs that a slow, heavy vehicle can
cross. Each landed base is a tower with a docking station at its foot. Nobody starts with a vehicle; the
crawler is built from resources over the first weeks, then backed onto the base's collar, and from then on the
player can walk from the pod into the crawler and back without an airlock cycle. The crawler is a sealed
room that moves: the cat can ride in it, chests and machines and a bed can be bolted inside, and the same
collar lets it dock at a survivor's shelter or Site Two, which is how people get rescued and how the colony
eventually gathers in one place.

## World generation

* The Toxic Wastes preset gets its own noise settings: low, flat valley floors with long wavelengths, and
  mesa tables that rise sharply from them. Rivers of acid in the lowest channels; salt pans on the flats.
* Valley floors should be crossable for hundreds of blocks without a two-block step. The crawler's pathing
  treats a one-block step as a ramp and a two-block step as a wall.
* Sites (the starter pod, Site Two, the survivor shelters) are placed on valley floors near a mesa wall so
  the towers read against the cliff, and so a crawler can reach every one of them without climbing.
* Verification: a headless flatness scan (sample surface heights along straight lines between sites; count
  steps of two or more) before any vehicle work starts.

## The base tower

* The existing pod is the ground floor. A tower is the pod plus stacked modules: a mast with the relay
  dish (the company's numbers go up it), a lookout with glass on all sides, and the docking collar at the
  foot, which the starter pod already has as a sign and a glass ring on its west wall.
* Docking station: a three-wide, three-high frame on the collar side with a sealed door in the middle. The
  door only opens when a crawler reports itself docked.

## The crawler

Two parts, so the outside and the inside are different things:

1. **The hull** is an entity: a big, slow, indestructible body the player drives. It has a position, a heading,
   a speed and a turn rate. It cannot be broken by the player and does not take block damage; it can run out
   of charge and stop. Model: a boxy tracked hull, two segments, a docking ring at the back.
2. **The interior** is a room in a pocket dimension (one 16x16 chunk per crawler, like the ship uses the
   transit dimension). Walking into the crawler puts the player in that room; the windows are a sky renderer
   that shows the outside as seen from the hull (camera follows the hull's position and heading, like the ship's
   sky follows the week). The player can place blocks inside; the room is sealed and scrubbed by its own
   life support; the cat rides in the room.

   The atmosphere system already handles sealed volumes, so the interior is just another sealed room with a
   scrubber; the hull carries the power.

* **Boarding**: a door on the hull's side. From outside, using it puts the player inside (dimension change
  with a fade). From inside, the door puts them out beside the hull. Docked, the hull door and the base's
  collar door become the same door: the player walks pod to crawler with no fade, by teleporting between the
  two rooms at matching offsets.
* **Driving**: the driver's seat at the front. Slow turn radius (the hull turns like a tank: heading changes
  a few degrees a second, the body follows a path, not a pivot). Speed under walking pace on sand, slower on
  a ramp. The HUD shows heading, speed, charge, and a dock indicator.
* **The rear docking seat**: a second seat facing a terminal at the back. It shows a schematic (the collar
  outline, the crawler's ring, an offset and an angle) and a grainy rear camera, not the world. Docking is
  done from here, in reverse, watching the numbers converge: like docking a submarine. The forward seat cannot
  dock. Coupling succeeds when offset is under half a block and angle under ten degrees; a clunk, the doors
  unlock, the collar sign updates.
* **Charge**: a big battery charged from the base's dock while coupled, or slowly from panels on the roof.
* **Persistence**: the hull entity saves heading, charge, interior dimension id and dock state. Interiors
  survive the hull being far away because the pocket dimension keeps them.

## What it changes for the story

* The colony is not one room bolted to a pod: it is the room the crew build on the module-two slab **and** the
  crawler that lets them visit each other. Halloran's line about a table with four chairs stays; Marsh's walk
  on day three is what the crawler makes unnecessary.
* Rescues become trips: build the crawler, cross the valley, dock at a shelter, walk the survivor into the
  crawler, bring them home. The seat count in the eventual ship home is the count of people who made that
  trip.

## Order of work

1. World gen: noise settings for valleys and mesas; the flatness scan; re-site the pods on valley floors. **Done.**
2. The tower: docking station frame and the collar door on the starter pod and Site Two. **Done** (frame, door,
   apron and a placeholder mast; no lookout module yet).
3. The hull entity: model, driving, slow turns, charge, no damage. **Done.**
4. The interior dimension and boarding; the window renderer. **Done**: the cabin is a room in the
   `surrogate:crawler_cabin` pocket, boarded through the hull or the coupled collar door; the porthole looks
   onto the ground ahead of the hull, painted in block for block.
5. Docking from the rear seat; the schematic terminal. **Done**: the rear camera with a reticle, zooming in
   as the ring closes; a range beep that quickens and goes steady inside the window; W backs the hull up, a
   click locks, S pulls away; a clunk for a click off the collar or for sliding back out of the window.
6. The cat, chests and beds inside; survivors boarding. **Done**: the cat comes along, the room is the
   crew's to build in, the chassis bay deploys and recalls; a survivor boards when the crawler couples to
   their shelter's collar and steps into the pod when it couples at home. Beds, food and water for them
   moved to act seven of [DESIGN-campaign.md](DESIGN-campaign.md), where housing and feeding eight people is
   an objective rather than a nicety.
7. The researcher. **Done**, and now six of them: the first shelter is within a chassis walk of the pod,
   every shelter has a chassis port (a chassis talks to the room through a terminal, nobody opens a door)
   and the researcher hands the crawler blueprint over it; the kit recipe needs the blueprint on the bench.
   The radar on the sonar shows bases within two hundred blocks.
8. Modules. **Started**: the hull carries a module bitmask and its first fitting, the ceramic cladding that
   makes the acid belt survivable. Everything the crawler needs in act five hangs off that.

## What exists

* **Terrain.** `tools/gen_data.py` (terrain section) writes five noises and the `surrogate:mesa/*` density
  functions: a corridor along the zero contour of a long noise plus basins where a second dips are the floors
  (y 64, rolling a block or two), everything else is table (26 blocks up, sharp across about six blocks).
  Seas fill the deepest basins, rivers the lowest channels. Caves keep vanilla shapes but cannot open within
  fourteen blocks of the surface. The router's `continents` is the mesa mask and `depth` the river mask, so
  the biomes (tables ash, floors salt pans in basins and desert or grove in corridors, acid in the water) and
  the code read the terrain from the seed.
* **Reading the terrain.** `world.Valleys` classifies any column without loading a chunk (sea, river, floor,
  cliff, mesa), flood-fills the floor a crawler can reach from the pod on an 8 block grid with a 5 block margin
  from cliffs and banks, and picks sites: the pod goes to the nearest clear floor that opens onto a square
  kilometre of reachable floor (a flat pocket walled in by cliffs or a river is refused), with the west
  approach flat for the apron and a wall to the north, east or south (`HabitatBuilder.onJoin`); Site Two and
  the shelters go to reachable floor with the same west approach and a wall behind them when there is one
  (`SiteTwo.choose`, `SurvivorManager.createSites`).
* **Checking it.** `/surrogate terrain scan` follows the reachability grid's drive from the pod to every site
  through the chunk generator and counts steps of two or more, acid columns and columns off the floor;
  `/surrogate terrain map [radius] [step]` paints `terrain_map.png` in the run directory (sites as crosses,
  drives in blue, unreachable floor grey); `/surrogate terrain here` classifies where you stand.
  `python tools/terrain_scan.py` runs all of it headlessly and copies the map to `build/`.
* **The collar.** `block.DockDoorBlock` (`surrogate:dock_door`) is an airlock door with a `docked` state that
  refuses hands and redstone until a crawler couples; `HabitatBuilder.dockingStation` puts it in the middle of
  a 3x3 reinforced glass frame in every pod's west wall (`DOCK_COLLAR`, at z = 3 so it opens onto floor, not
  the farm), levels the apron west of it (`APRON_MIN..APRON_MAX`) and stands the mast on the roof.
* **The hull.** `entity.CrawlerEntity` (`surrogate:crawler`, 5 wide, 3 high, drawn 8 long) is a plain entity
  the server drives from `setControls(throttle, steer)`: a rider in the front seat sets them from the movement
  keys each tick, the helm inside will later. It accelerates over two seconds to `crawlerSpeed` (0.14 blocks a
  tick), turns only while moving, at up to `crawlerTurnDegreesPerTick`, steps up one block and stops at two,
  cannot be damaged, drains `crawlerDrivePerTick` while driven and trickles charge from its roof in daylight.
  A power cell used on it charges it. `CrawlerHud` shows heading, speed, charge and the coupling state to the
  rider. `item.CrawlerKitItem` (crafted from hull plating, servo motors and an iron block) assembles one on
  level ground. Nobody rides the outside any more.
* **The cameras.** `crawler.CrawlerCamera` sends everyone aboard a colour heightmap of the ground around
  the hull (72x72 blocks, read like the map item, biased ahead) twice a second; `client.crawler.PortholeRenderer`
  draws it as a height field from the hull's eye into a 192x120 buffer and paints that on the porthole glass
  through a barrel-distorted, vignetted, flickering grid, and the same field from the ring on the back is
  the rear camera panel the docking console shows. `CrawlerSonar` feeds the scope on the right. The old
  block-painted diorama survives behind `crawlerPorthole` (off: it was the lag).
* **The cabin.** `crawler.CrawlerInterior` (with `CrawlerRoom`, `CrawlerInteriors`, `CrawlerDocking`,
  `CrawlerDimension`): using the hull, or the collar door while a hull is coupled, fades the player into the
  hull's room in the `surrogate:crawler_cabin` dimension (one per crawler, 64 blocks apart, built on first
  boarding, chunks held while anyone is aboard; the hull's chunks are held too, so it can be driven from
  another dimension). The room is 5x9x3 plating: helm under a 3x2 porthole at the front, hatch and docking
  console at the back, scrubber fed from the hull's charge, a dive chair, and the chassis bay. Using the helm
  or console stands the player at it and their movement keys go to the hull (client `CrawlerClientState`
  sends `CrawlerPayloads.Control`; the server reads a fake player's keys directly); sneak steps away. The
  porthole view is a diorama: the ground ahead of the hull, 49 wide and 40 deep, copied into the void in the
  hull's frame whenever it moves two blocks or turns ten degrees. At the docking console W backs the hull
  up at a third of speed (a tenth inside the window), A and D swing the ring the way the rear camera shows,
  and a click (the use key, or using the console again) couples when the ring is within half a block and ten
  degrees of a collar; S pulls away and uncouples. The console beeps from `CrawlerClientState`: forty ticks
  apart ten blocks out, six at the window, steady inside it; `CrawlerDocking` clunks (`crawler.dock_error`)
  for a click off the window or for sliding back out of it and plays `crawler.dock_lock` into the cabin on
  coupling. The rear panel zooms from 70 to 30 degrees as the ring closes and draws the collar as a yellow
  frame to centre in its reticle. The porthole is one block of glass over the helm; its buffer is square. The hatch
  leads through the collar into the pod when coupled, else out beside the hull; the cat follows if it is
  near and not sitting. The bay assembles a chassis beside the hull and keys the chair to it; a piloted
  chassis using the hull is packed back into the bay and its pilot wakes in the chair. `DockDoorBlock` never
  swings, so the pod never breathes the outside. `/surrogate crawler spawn|dock|undock|charge|board|leave|seat helm|seat dock|stand` (`entity.CrawlerCommand`) is the test stand-in
  for building and docking: `dock` backs a charged hull onto the starter pod's collar, marks both docked and
  unlocks the collar door; `dockat home|<n>` moves the hull onto the home collar or shelter n's and couples
  it there, which runs the boarding and the homecoming. `python tools/smoke_test_crawler.py` checks the
  collar door and the dock command, drives and turns from the helm, backs onto the collar and clicks to
  couple, deploys from the bay, dives from the chair, then couples at the first shelter (the survivor boards),
  hands a chassis the blueprint at the port and couples at home (the survivor steps into the pod).
* **The rescue.** `survivor.SurvivorShelter` gives every shelter a collar in its west wall (`COLLAR`, with
  an apron levelled to the west) and a chassis port in its south wall (`PORT`, `block.ChassisPortBlock`):
  a piloted chassis using the port gets the survivor's terminal (`terminal.surrogate.shelter_<key>`) and, at
  the first shelter, the `crawler_blueprint` item, which the kit recipe needs and hands back
  (`item.CrawlerBlueprintItem`). `SurvivorManager.board` moves the survivor into the cabin (`CrawlerRoom.PASSENGER`)
  when a hull couples at their collar and `disembark` puts everyone aboard into the pod when it couples at
  home, marking the site rescued. The first site is placed at `firstSurvivorMinDistance..MaxDistance` (160
  to 280 blocks) so a chassis can walk there. `CrawlerSonar` adds radar blips for every base within 200 blocks.
