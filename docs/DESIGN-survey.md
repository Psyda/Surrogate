# The survey tier

Three blocks that answer one question — *what is out there* — and answer it with an instrument rather than a
map screen.

This is reward-tier kit. Nothing in the campaign is gated behind it. It exists because by act four the player
has walked a lot of valley without ever seeing the shape of it, and because the map is a locked, designed
thing (see [DESIGN-campaign.md](DESIGN-campaign.md), "The map") that deserves to be looked at.

## Survey Station

A table with a glass top. Lean on it and it draws the ground for a few hundred metres in every direction, in
light, on a lattice you can turn and zoom.

It is deliberately not a map. The scan is a heightmap on an eight-block lattice — 129 by 129 samples, a
kilometre square — projected, tilted, and drawn as line segments between neighbouring samples. Eight blocks
between samples means a mesa comes out as a stack of terraces and a narrow chasm can vanish between two
columns. Those artefacts are the instrument, not a bug in it, and the picture is honest about what a radar
sweep of a surface can actually tell you.

Glowing dots mark what the network can see: your habitat, every survivor's shelter, the assay pad, and every
beacon holding the picture up. A beacon that is not connected shows red, on the table and on itself.

The whole picture is built server-side when somebody right-clicks the table and sent in one packet. It is
large for a packet and trivial for a thing that happens once a minute at most, and the alternative was a
screen that fills itself in while the player watches.

## Survey Beacon

A metre of pole with a lamp on it, pushed into the dirt. It has to be planted on solid ground — you carry it
out there and find somewhere to put it, and that walk is the mechanic.

**Green** means it is talking to the station, directly or through another beacon, and the ground around it is
on the table. **Red** means it is out there on its own doing nothing.

There is no menu and no pairing. The light is the whole interface and the fix for a red one is always to
plant another between it and the last green one.

The network is a breadth-first walk from the station: a beacon joins when it is within link range of
something already in. The surveyed area is the union of the station's disc and every linked beacon's disc, so
**the shape of what you have surveyed is visibly the shape of where you have walked.** That is the point of
the whole thing.

## Long-Range Scanner

A pillar with a hole in the side that takes power. The more power has ever gone into it, the further the
table beside it can see.

Range goes as the square root of total charge, so the first two hundred metres are cheap and the last two
hundred are a project. It is a sunk cost rather than a fuel tank: one that runs dry keeps the range it has
already paid for and simply stops climbing. That is the intended endgame shape — somebody with a geothermal
field and nothing left to spend it on pours it into this until the whole map is on the table.

Right-click it and it tells you what it has taken and what another lump would buy.

When a network covers every shelter on Sallow at once, everyone standing at the table is told so. It is not a
data-pack advancement — there is no criterion anybody could write for "the union of these discs contains all
seven of these points" — so it is a message, a sound, and a set of who has already had it.

## Numbers

| | Default | Why |
| --- | --- | --- |
| `surveyStationRadius` | 160 | A generous room's worth of valley, and no more |
| `surveyBeaconRadius` | 140 | Slightly less than the table, so beacons feel like extensions rather than replacements |
| `surveyBeaconLinkRange` | 220 | Longer than either radius, so a chain can bridge a gap it cannot see across |
| `surveyBeaconChainMax` | 8 | Bounds the chunk scan that finds beacons at all, rather than being a rule |
| `longRangeScannerBlocksPerRoot` | 2.0 | Blocks of range per √unit |
| `longRangeScannerMaxRadius` | 4000 | Comfortably past the furthest shelter the seed places |
| `longRangeScannerBuffer` | 20000 | The lump it takes power in, and the unit its readout quotes |

## Cost

The station wants two data racks, a robot core and reinforced glass; the pillar wants an antenna mast, a data
rack and a core. Beacons are cheap on purpose and come two at a time, because the errand is walking them out,
not affording them.

## Implementation

- `survey/SurveyNetwork` — the breadth-first walk, and the discs it produces. Recomputed on demand rather
  than cached: there are never many beacons, and a cache would only ever be wrong at the moment somebody
  moved one, which is the only time anybody looks.
- `survey/SurveyScan` — heightmap sampling and the light list.
- `survey/SurveyAdvancement` — the whole-map moment.
- `block/SurveyStationBlock`, `SurveyBeaconBlock`, `LongRangeScannerBlock` and their entities.
- `network/SurveyPayloads` — the picture, server to client, in one packet.
- `client/gui/SurveyScreen` — the projection, the Bresenham lattice, the dots.

A beacon checks whether it is linked every five seconds, staggered by position hash so a row planted in one
trip does not all check on the same tick.
