# Fauna

Four animals. None of them is a threat in the way the hazards are threats; they exist so that the wastes are
somewhere rather than nowhere, and so that the empty monster lists read as a fact about the planet instead of
as a missing feature.

The rule they are all built to: **nothing on Sallow hunts you.** One of them will hurt you and it is entirely
your own fault when it does.

## The four

| | Where | What it does | Reading it | Crating it |
| --- | --- | --- | --- | --- |
| **Trundle** | Desert, dunes, pans, grove — off the biome spawner lists | Sleeps. Wakes when you get within six blocks, rolls away, settles again after twenty seconds of quiet | Walk up to a sleeping one | Any time |
| **Slagback** | Geyser fields, placed by `Fauna` because the spawner cannot see a geyser | Folded flat, indistinguishable from basalt. Hit it or stand on it and it unfolds, hits back for fifteen seconds, then folds wherever it ended up | Get close to something that objects | Only while folded |
| **Tocker** | Desert, flats, pans, grove | Walks towards the brightest thing it can see and sits down two blocks from it. Answers any note played near it, half a second late, at the wrong pitch | Trivially; it comes to you | Any time |
| **Lantern slug** | Cave ceilings under y 40, hung by `Fauna` because a heightmap has no roofs on it | Watches. Four lights, no movement, twenty-four blocks of tracking | Reach up to one | Only while it is still on the ceiling |

**The trundle rolls** because the model is a ball spun by distance travelled, which is the cheapest honest
animation in the mod: one float, no keyframes, and it desynchronises from the tuft on top in a way that reads
as effort.

**The slagback casts no shadow while it is folded.** A boulder-shaped shadow on open basalt is the exact tell
that would give the trick away at range, and a real boulder would not have one either.

**The lantern slug has one hit point and dies of the fall, not of the hit.** Any damage at all detaches it;
it comes down over about a second and is dead when it lands. There is no version of hitting one that works
out, the damage is dealt by the world rather than by whoever swung, and Okafor's note about them on the
analysis disk is the shortest one on the table and is not about biology.

## Where they come from

All four are placed by `Fauna`'s own sweep, every three seconds, near players, under a local population cap.
None of them are on the biome spawner lists. Trundles and tockers were, once, and that is how a player came
home from an idle hour to a few hundred of them: nothing of ours despawns, and vanilla does not count a mob
that cannot despawn towards its creature cap, so the biome spawner never believed it had enough. Now the
sweep counts. A trundle or a tocker turns up on open ground in its own biomes, between sixteen and forty
eight blocks from the player, no more than two of each in that radius. Three animals in sight is company.

- A **trundle** lives in the desert, the dunes, the pans and the grove.
- A **tocker** lives in the desert, the flats, the pans and the grove.

The other two need a place the sweep has to look for:

- A **slagback** needs to be beside a geyser, and the vanilla spawner has no idea where those are. They
  come as a group of two to five per geyser, placed once, and a geyser that has any is left alone.
- A **lantern slug** needs a cave ceiling. The spawner works downwards from a heightmap, and a cave roof is
  not on any heightmap, so the sweep walks up from a random point underground until it finds air with rock
  over it.

Nothing here despawns. There are few enough of them that walking back to where you saw one should find it,
and a survey that erased its own subjects between visits would be unplayable.

`Surrogate.fauna` turns the whole layer off. That is a legitimate way to play and it makes two of the errands
unfinishable, which the errand board says out loud rather than hiding.

## What they are for

Two errands, at opposite ends of the game.

**Okafor's survey** (mid). She hands over a **bio-sampler** for a chassis bay and an **analysis disk** for
the hub, and wants a reading of everything alive on the planet. That turns out to be eight subjects: the four
animals, a borer, the seep, the crust, and the cat. Filed readings appear as a SURVEY page on any hub
terminal with the disk in it, written in her voice, with a blank line under everything you have not found.

Two of the eight are jokes at the player's expense. The borer will not hold still, so the reading has to be
taken off a live one mid-pass. The cat is off-world and is on the table because you asked.

**Brandt's ark** (late). One of each, alive, in a crate, before the rocket goes. He has watched these animals
through a window for eleven years and he is not going to be the last person who ever sees one. Six live
species, six crates, and a slot on the manifest for each.

The two share one button. A chassis with both bays fitted takes a reading first — because a specimen you have
not read is one you want to read before you shut it in a box — and crates it on the second touch.

## Testing

```bash
py -3.13 tools/smoke_test_fauna.py
```

In game:

- `/surrogate fauna spawn <species> [count]` puts them in front of you. A slug is hung two and a half blocks
  up rather than dropped on the floor, because one on the floor is a dead one.
- `/surrogate fauna read` files every reading and installs the disk, for looking at the terminal page without
  doing the survey.
- `/surrogate errand start survey` / `start ark` opens either one immediately and hands over its kit.

## Tunables

`Surrogate.fauna` is the only config for the animals themselves. Everything else — how far a trundle stirs,
how long a slagback stays cross, how bright a thing a tocker will walk to — is a constant in its own class,
because none of it is a difficulty knob and all of it is characterisation.
