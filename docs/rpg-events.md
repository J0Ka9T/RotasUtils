# The event catalogue

Every moment this mod can see is a line in one list, and every line says what the
server does about it. Opened from **Admin menu → Advanced → Event catalogue**.

## What a line is

A rule is an **event type** plus a **filter**:

| Filter | Answers for |
|---|---|
| empty | everything of that type |
| `alexsmobs:*` | anything from that mod |
| `minecraft:zombie` | exactly that |

The most specific matching rule wins, so "kills pay 1x, Cataclysm bosses pay 3x"
is two lines that do not fight. Every type always keeps its empty-filter line, so
a type can never be left without an answer.

## What a rule can do

- **React or ignore.** Switching a rule off never stops the event - a kill still
  kills, a craft still crafts, a quest objective still ticks. It stops this mod
  paying and announcing for it. That separation is what makes the list safe.
- **Experience multiplier**, applied to the experience the event already pays.
  A type that pays none says so on its page instead of pretending.
- **Extra experience** and **gold**, per occurrence.
- **Announcement**: nobody, the player, or the whole server.
- **Cooldown**: seconds before the same player is paid for that rule again.

## The events

Twenty-six types in six groups: combat (kills, player deaths, spell damage),
world (break, place, use a block), crafting (craft, smelt, use, pick up, fish),
people (mobs, NPCs, dialogue, trade), progress (quests, levels, titles, places,
joining) and this mod's own (refine success and failure, card drops, monster
payouts).

They are not per-mod hooks. `KILL_ENTITY` is the same event whether the mob came
from vanilla, Alex's Mobs or Cataclysm, which is why a 700-mod pack is covered by
filtering a type down to a namespace rather than by shipping a hook per mod.

## Adding a rule for one mod

**New rule for one mod** lists the events on the left and, on the right, every
namespace in this pack that could be the subject of the chosen event - read from
the client's own registries. The list is therefore the pack that is actually
installed, not a list this mod guessed at. Pick a mod, or type an exact id.

## How it is wired

- `ObjectiveEngine.handle` is the single bridge: every objective event already
  passes through it with the entity, item or block it happened to, so the
  catalogue stays complete without a second set of call sites.
- `ProgressService` asks the catalogue for a multiplier before awarding any
  experience, using the anti-farm key - which is the id of the thing - as the
  subject. A rule can therefore retune one mod's mobs without touching the rest.
- This mod's own moments (refine, cards, titles, levels, monster payouts) report
  themselves where they happen.
- A rule that pays nothing and announces nothing never touches the cooldown map,
  so a per-hit event like spell damage costs nothing until someone configures it.

## Configuration

`config/rotasutils/season.json` under `events`, reloaded with
`/rotas season reload`:

```json
"events": {
  "enabled": true,
  "rules": [
    {"type": "KILL_ENTITY", "filter": "", "enabled": true, "xpMultiplier": 1.0,
     "xpFlat": 0, "gold": 0, "announce": "OFF", "cooldownSeconds": 0},
    {"type": "KILL_BOSS", "filter": "cataclysm:*", "enabled": true,
     "xpMultiplier": 3.0, "gold": 500, "announce": "SERVER", "cooldownSeconds": 0}
  ]
}
```

A rule naming an event this build does not have is dropped on load rather than
kept as a trap; an unreadable filter falls back to the plain entry.

## Commands

```
/rotas events                              list the rules that do something
/rotas events enable <true|false>
/rotas events add <TYPE> <filter>
/rotas events remove <TYPE> <filter>
/rotas events set <TYPE> <filter> <enabled> <xp_multiplier>
```
