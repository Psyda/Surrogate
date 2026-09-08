# The flashback: the night you packed

Three nights, a few days apart, starting the first or second time the player sleeps on the planet. It is the
only part of the game the player writes rather than watches, and the only one where something they made
crosses back out of a cutscene into the world.

## The shape of it

You go to bed in the pod and you are not in the pod. You are standing at the end of a corridor with three
doors at the far end of it, each standing open, each with its name on a sign over it — HOME, THE OFFICE, THE
BAR — and each with a few lit blocks of the place behind it showing through.

> Where was I?

You walk through one, and that is where you were — not remembered, *decided*, four hundred days late, by
somebody who was not paying attention at the time. Which is how anyone's last night before a four year
contract actually gets stored. Behind the door is the whole place.

It happens more than once. After the first night it comes back every three nights until all three doors have
been used, and **the ones you have already gone through are bricked up**, with the sign still over them.
Three branches and one visit would be two thirds of a scene nobody ever reads, and letting the player simply
pick again would mean the first answer never meant anything.

## The evening, at the player's pace

Every place is a room you are sitting in, with something small to do, somebody in it who asks you two
questions, and then a stretch of the evening that goes by without you. The rhythm is the same three times and
the furniture is different, and the exposition is in the furniture: the post on the kitchen table, the
contract on the screen, what the television says about you while you are eating.

**Home.** You are on the couch. The set is on the news; there is a pizza box on the coffee table; the dog is
wandering about the kitchen. *Eat something.* When you have, somebody is in the kitchen doorway — a figure
with no face — and they ask *how are we going to get through this?* and then *and after?* Then they go up,
and you hear them on the landing. *Watch the news:* the anchor covers the Provender leaving Ceres orbit with
twelve pilots aboard, the four-year contracts, the balances discharged on arrival; the advert after it sells
the same thing more warmly and gives the boarding time. Then the screen goes black and comes back and it is
later: the lights are off, the set is on the credits of something, the clock says twenty to two, the box is
lighter by three slices, and their mug is on the arm of the couch. You get up, with the noise a couch makes
about that. *Go up.* The case is on the bed, open, half done. Pack, and walk out of the front door.

**The office.** Your desk at eleven at night, the contract on the screen, everybody else's screen dark.
*Read the contract* — page eleven of fourteen, clause nine. Somebody comes over from two desks along, asks
how long you have been there and why you are signing, and goes to the lift. Later: the panels have gone off
a bank at a time, every monitor is dark, the printer has finished with you. The box is by your chair. The
lift doors open when it is time.

**The bar.** A stool at the counter, a pint with your name on it, three darts in your hand and the board
where it always was, the set up in the corner on the same news. *Throw a dart, or drink up.* The barman
comes down the counter with a cloth, asks who is drinking with you and whether you have anything to say
before you go, and puts the stools up. Later: one light left on, over the door. Your case is by it, because
you came straight from the port office.

The small errands (eating, reading, throwing) wait two minutes and then the evening carries on without them.
The questions wait four. Packing waits fourteen, and a few minutes in, the car outside sounds its horn.

## Somebody in the room

Each place has one person in it — `Crew.FIGURE`, `Crew.COWORKER`, `Crew.BARMAN` — drawn as a flat figure
with no face and no name tag. That is not a shortcut. A person you are inventing four hundred days after the
fact does not have features, and giving them some would be the game telling the player who it was.

They ask two questions each, through the game's only dialogue screen (`ChoiceScreen`, driven by
`CinematicPayloads.Choice` and a `Beat.Ask` that holds the script until somebody picks; the number keys
answer too). It cannot be closed with escape, because somebody asked you a question. A question left long
enough answers itself with the first option, on the principle that walking away from one is also an answer.
Every answer is filed in `FlashbackState.answers` under `<place>.<n>`, and **the second answer is also the
reason you were there** — `FlashbackState.reason` — because "and after?", "why sign it?" and "anything to say
before you go?" are the same question asked three ways.

## The suitcase, and everything else

The one load-bearing object in the dream. **Whatever is in it when you walk out is in it when you wake up**,
in the same case on the floor beside your bed (a cardboard box, from the office). It is a block of its own
(`SuitcaseBlock`, eighteen slots, and it knows when it is lying on a bed so it sits on the mattress rather
than floating over it), and every case or box anywhere in the place is emptied on the way out, so a player who
moved it has still packed it.

Everything else in the place comes off in your hands. The couch, the fridge, the television, the bills, the
calendar, the dartboard, the neon sign. Nobody says so. The house is real enough to take apart and not real
enough to miss anything.

**The dog** is a tamed wolf with a collar, and the carrier by the kitchen door is a block with a block entity:
click it with the dog nearby and the dog is in it; pick the carrier up and the dog comes with it, as a
component on the item (`ModComponents.PET_DATA`, copied by the loot table); put the carrier in the case and
the dog wakes up on Sallow. Click the carrier again, anywhere, and he is out. Nobody is going to say out loud
that this is allowed.

The player's own inventory is **put away for the duration and given back on waking**, and the stash lives on
the save (`FlashbackState.stashed`), not on the running scene. A server that stops in the middle of a dream
must not be a server that ate somebody's inventory, and `/surrogate flashback reset` hands one back before it
clears anything. The suitcase is emptied on the way out rather than copied, so nothing is in two places.

## The furniture

Fifty-odd blocks of Earth, 2189, under "Earth, 2189" in `ModBlocks`, all registered with `house()` (no tool
required — `prop()` sets `requiresTool`, and a mug that drops nothing by hand is the scene not working) and
painted by `tools/gen_house.py`, with their models in `tools/gen_house_data.py`. The kinds of thing there are:

| kind | class | examples |
| --- | --- | --- |
| things you sit on | `ChairBlock` (a `SeatEntity` under you) | kitchen chair, office chair, bar stool |
| things in a run | `ConnectedPropBlock` (single / left / middle / right) | the couch, the bar counter, the bath |
| containers | `HouseContainerBlock` + `HouseContainerBlockEntity` | suitcase, cardboard box, fridge, wardrobe, bedside cabinet, filing cabinet |
| things to read | `ReadableBlock` → `DocumentPayload` → `DocumentScreen` | the bills, the calendar, the printout, the contract on the `MonitorBlock` |
| lights | `HouseLightBlock`, `LightSwitchBlock` | pendant lamps, ceiling panels, and the switch that works the ones in the room |
| the set | `TelevisionBlock` (off / news / credits / static) | the one in the living room and the one over the bar |
| the rest | `PizzaBoxBlock`, `PintGlassBlock`, `DartboardBlock` + `DartEntity`, `DogCarrierBlock`, `WallClockBlock` | |

`py -3.13 tools/render_models.py --sheet <names>` draws any of them isometrically to `run/showcase/`, which
is how they were checked before the game was. One trap in it: the renderer shows north faces mirrored
relative to the game, so text that reads backwards on the sheet reads forwards in the room.

## The sound of the place

Seventeen effects recorded with ElevenLabs by `tools/gen_sfx.py` (forty credits a second, tracked by a
manifest so a changed prompt re-records and an unchanged one does not): cars going past, footsteps and a door
upstairs, the couch creaking, the channel changing, the credits, the news sting, a dart in cork, glasses, the
fluorescent hum, the pub murmur, the lift, the printer, the pizza box, the light switch. `Flashback.ambience`
plays them off two clocks — a loop for the hum or the murmur, and a random one-shot — and changes what it
plays once the evening has moved on. The television's five lines are voiced (Daniel for the news, Brian for
the advert, both premade, both through a small-speaker filter) via `gen_voices.py --render television advert`.

## Why it is built the way it is

**Vanilla shells, mod furniture.** The walls are wallpaper and the floors are oak planks and glass panes,
because the player has been looking at hull plating for four hundred days. The furniture had to be the mod's
own: a house needs a couch and a fridge and a dog, and vanilla has none of them.

**Its own dimension, not the transit void.** That one is space: its biome is tagged toxic, so standing in it
kills you. The dream is `natural: false` so nothing in it counts as a night's sleep or a spawn point, and the
air is ordinary air — the whole point of the place being that four years ago you did not need a suit.

**Three sites, far apart.** The house, the office and the bar are each seventy blocks from the corridor and
from each other, so nothing shares a wall and nothing from a previous night is standing in this one.

**The furniture comes off in your hands; the house does not.** Nothing in `#surrogate:dream_fixed` can be
touched — the floor, the walls, the stairs, the doors, the windows, the lamps set into the ceiling — because
a room you can dismantle is a room you can dismantle a wall of, and behind every wall in there is a flat
generator's worth of nothing. `DreamRules.clearEntities` also empties the dimension of everything that is not a
player on the way in and the way out, so last night's dog is not wandering through this one.

**The monologue cannot be pressed through.** Sneak advances a line everywhere else in the game; here it does
not, because the whole scene is its narration. Holding the skip key still works and still ends the night with
the player in their own bed holding their own things.

**The dialogue is drawn after the blur, once.** Vanilla applies its blur inside `Screen.renderBackground`, and
`Screen.render` calls it before the widgets. The first version of `ChoiceScreen` painted its panel in
`render` and then called `super.render`, which blurred the panel and painted buttons over it — the
unreadable dialogue in the first playtest. Everything is drawn in `renderBackground` now. The other large
thing on that screen was the figure's floating name; `CrewEntityRenderer` no longer labels anybody faceless.

## Where it lives

| | |
| --- | --- |
| `flashback/Flashback.java` | the `Director`: the script, the three evenings, going under and waking up, the ambience |
| `flashback/DreamBuilder.java` | the corridor, the house, the office, the bar, and what "later" does to each |
| `flashback/DreamPlace.java` | the three answers, who is in each room, and the three reasons each allows |
| `flashback/FlashbackState.java` | what was decided, the bed to return to, and the stashed inventory |
| `flashback/DreamDimension.java` | `data/surrogate/dimension/dream.json`, and the void it names |
| `flashback/DreamRules.java` | what the dream will not let you break, the floor you cannot fall through, and the cleanup |
| `client/flashback/DreamDimensionEffects.java` | no sky, no sun, and fog at arm's length |
| `client/gui/ChoiceScreen.java` | the question, the answers, and no way out but through |
| `client/gui/DocumentScreen.java` | a letter, a calendar, a page on a screen |
| `entity/SeatEntity.java`, `entity/DartEntity.java` | sitting down; a dart in flight |
| `tools/gen_house.py`, `tools/gen_house_data.py`, `tools/gen_sfx.py` | the textures, the models, the recordings |

Gated by `SurrogateConfig.flashback`, and by `HabitatState.landedDay`, which is set when the pod is built
rather than only by the prologue.

## Testing it without playing to it

- `/surrogate flashback start [home|work|bar]|skip|answer <n>|aim <player>|status|reset` — `start` with a
  place goes straight through that door; `answer` picks one of the options on the table through the same
  entry point the button uses; `aim` says what a player's crosshair ray lands on, from the server's side;
  `status` prints the script's current label and what has been done tonight, which is how the smoke test
  paces itself
- A Carpet fake player's click goes to the first *entity* its ray crosses, silently. The smoke test learned
  this the slow way: a clicker put down two blocks west of the dog carrier is standing where the figure
  still is, and clicks the figure. Stand clickers where nobody else is, and sit the dog down out of the line.
- `py -3.13 tools/smoke_test_flashback.py` — boots a server and walks one night at home through: the
  corridor, the door, the couch, the pizza, the two questions, the dog into the carrier and the carrier into
  the case, the evening moving on, the porch opening, and the two things that could cost a player something
  real: the stash going away and coming back, and the packed things existing in exactly one place afterwards
- `py -3.13 tools/check_assets.py` — every blockstate, model and texture edge, in a second
- `py -3.13 tools/dev_client.py --no-transit --run "surrogate flashback start@200"` — the real thing, in a
  client, from the last smoke-test world

## Not done

- The office and the bar are furnished and scripted but have not had the same number of eyes on them as the
  house; the smoke test walks the house.
- Nothing reads the answers back yet. Act VI weighs what the player did, and this is the one thing in the
  game they authored outright, so it belongs on that scale — and the crew should be able to allude to it.
- The dog on Sallow is a wolf with a name. He does not need feeding and does not mind the air, which is
  either a mercy or a gap.
