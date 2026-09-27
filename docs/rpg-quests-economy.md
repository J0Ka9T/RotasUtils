# RPG quests and economy (Phases 7 and 8)

Kernel quests and merchants are content definitions. They sit beside the existing
board quest system rather than replacing it: the legacy quest, party and
objective services are untouched.

## Quests

```json
{
  "schema": 1,
  "id": "rotas:quest/daily_hunt",
  "kind": "quest",
  "body": {
    "label": "Daily Hunt",
    "reset": "DAILY",
    "bounty_limit": 20,
    "reward": "rotas:reward/daily_hunt",
    "requirement": {"type": "MIN_LEVEL", "params": {"level": "5"}},
    "stages": [
      {"label": "Cull the undead",
       "objectives": [{"event": "rotas:monster_defeated", "count": 10,
                       "match": {"event.monster_profile": "rotas:monster/undead"}}],
       "reward": "rotas:reward/hunt_stage",
       "branches": [{"stage": 2, "condition": {"type": "MIN_LEVEL", "params": {"level": "20"}}}]},
      {"objectives": [{"event": "rotas:item_obtained", "count": 1}]},
      {"objectives": [{"event": "rotas:monster_defeated", "count": 1}]}
    ]
  }
}
```

- Objectives observe the same kernel events content rules do; there is no second
  event pipeline. An objective advances when the event type matches, every
  declared fact matches exactly, and its condition passes.
- A stage completes when all of its objectives are full. Its reward is granted,
  then branches are evaluated in order — the first passing branch chooses the
  next stage, otherwise the quest steps to the following stage or completes.
- `reset` is `NONE`, `DAILY`, `WEEKLY` or `COOLDOWN` with `cooldown_seconds`.
  A resetting quest must be repeatable; a one-shot quest never returns.
- `bounty_limit` caps how many players may claim the quest per reset window,
  world-wide. A full bounty refuses the claim and pays nothing.

Quest state lives in the existing bounded player variable namespace, so accept,
advance, claim and abandon each run as one player-record transaction and inherit
the conflict checks, receipts and persistence the progression phase proved. The
completion reward is receipted per reset window, so a second claim in the same
window pays nothing. Active quests per player are capped at 32.

Commands: `/rotas quests`, `/rotas quests available`, `/rotas quests accept <id>`,
`/rotas quests abandon <id>`, `/rotas quests claim <id>`.

## Merchants

```json
{
  "schema": 1,
  "id": "rotas:merchant/town_smith",
  "kind": "merchant",
  "body": {
    "label": "Town Smith",
    "trades": [
      {"key": "blade", "profile": "rotas:item/warden_blade", "item_level": 20,
       "stock": 4, "restock_seconds": 86400, "per_player_limit": 1,
       "costs": [{"currency": "rotas:gold", "amount": 250}]},
      {"key": "trade_up", "item": "minecraft:diamond", "count": 1,
       "costs": [{"item": "minecraft:iron_ingot", "amount": 4}, {"currency": "rotas:gold", "amount": 5}]}
    ]
  }
}
```

A trade produces exactly one of a vanilla item or an RPG item profile, and costs
mix currencies and vanilla items. `stock` is world-wide for the current restock
window; `per_player_limit` is per player per window.

### Purchase ordering

A purchase is deliberately ordered so a failure cannot pay twice or destroy
items:

1. requirement, trade condition, per-player limit, wallet and inventory are all
   verified first;
2. world stock is reserved, so two purchases in the same tick cannot oversell;
3. cost items are removed from the inventory;
4. one transaction debits the currency, writes the per-player counter and stages
   the result item, and commits;
5. if that transaction is rejected, the removed items are returned (to the
   inventory or the mailbox) and the stock reservation is released.

RPG stacks never satisfy a plain vanilla item cost, so a levelled item cannot be
spent as raw material.

Commands: `/rotas shop list <merchant>`, `/rotas shop buy <merchant> <trade> [count]`.

## NPCs

An NPC in RotasUtils is a role bolted onto an entity that already exists in the world, matched by
uuid, not an entity the mod spawns. That is what makes every NPC mod work the same way: a villager,
an armour stand, an Easy NPC character and a modded quest giver are all bound with the same admin
tool click, and breaking or replacing the entity loses the binding rather than the configuration.

- **Model** — `npc/NpcDef`: name, title, icon, role, bound entity, the quests it hands out and takes
  back, an optional board and merchant, a required level, reach, and one spoken line per situation.
  `npc/NpcState` is the resolved answer for one player; its ordering is what makes an NPC show its
  most advanced marker when it carries several quests.
- **Server** — `server/NpcService` owns lookup, state and the interaction. It re-checks reach and
  level, resolves the state, and pushes the dialogue screen; `captureInteraction` decides whether the
  entity's own menu (villager trades, for one) is suppressed. A configured NPC counts as a quest
  source in its own right, so it satisfies the "quests must be accepted from a board" server rule the
  way a board does - every other check still applies.
- **Client** — `client/screen/player/NpcDialogueScreen` shows the line for the state the server
  resolved and only the actions the server said are available; `client/NpcMarkerRenderer` draws the
  `!` / `?` over the bound entity through a mixin on the shared `EntityRenderer`, from client-side
  progress, as a display-only mirror.
- **Authoring** — NPCs in the admin menu, `NpcConfigScreen` for one character, and the quest
  creator's board step lists the NPCs that hand a quest out. Binding uses the same world-selection
  round trip as boards and positions, so an admin never types a uuid. NPCs travel through the same
  staged configuration pipeline as quests and boards (`npc/<id>` domains), and `Validation` reports
  unbound drafts, missing quests, unpublished quests and two NPCs bound to one entity.

Easy NPC remains a soft dependency. `compat/EasyNpcCompat` answers the state question for its
dialogue conditions, delegating to `NpcService` for bound NPCs and falling back to the quests that
name an entity in a talk or deliver objective for everything else.

## Current limits

- Bounty and stock counters live in one bounded world store (4096 keys). Windows
  roll over by time; stale keys are pruned on demand, not on a schedule.
- Merchants are commands, content and the kernel console only. An NPC with the merchant role
  opens that console rather than a per-merchant trading screen, and there is still no
  player-to-player trading.
- Quest objectives count events; timed objectives, escort objectives and shared
  party progress are not part of this phase.
- Claims are per reset window; retroactive claims for missed windows are not
  tracked.
