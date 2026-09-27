# RPG monsters (Phase 4)

Monster profiles, tiers and affixes are ordinary kernel definitions. They load
from disk packs, from the admin draft workflow and from rollback exactly like
conditions, actions, rewards, rules and stats. Nothing is hardcoded per entity
type, and no vanilla mob is changed until a pack selects it.

## Definitions

`monster` profiles decide which entities are eligible, which level they receive,
how much XP a kill is worth, which tier weights and affixes may roll, which
attributes scale, the display name template and the optional kill reward.

```json
{
  "schema": 1,
  "id": "rotas:monster/undead",
  "kind": "monster",
  "body": {
    "priority": 10,
    "apply_existing": false,
    "selector": {"entity_tags": ["minecraft:skeletons"], "dimensions": ["minecraft:overworld"]},
    "level": {"strategy": "NEAREST_PLAYER", "min": 1, "max": 60, "offset": 2},
    "base_xp": 40,
    "xp_per_level": 6,
    "tiers": {"rotas:tier/normal": 20, "rotas:tier/elite": 3},
    "affixes": ["rotas:affix/swift"],
    "attributes": {"minecraft:generic.max_health": {"multiplier": 1, "per_level": 0.04}},
    "name": "{name} [{tier} {level}]",
    "reward": "rotas:reward/undead_kill"
  }
}
```

- `selector` filters by entities, `entity_tags`, namespaces, biomes, dimensions,
  spawn reasons, regions and an explicit `exclude_entities` list. An empty field
  matches everything; `exclude_entities` always wins.
- `priority` orders candidates; equal priorities fall back to ID order, so
  assignment is stable. `manual_only` keeps a profile out of automatic
  assignment while `/rotas monster assign` can still use it.
- `apply_existing` decides whether mobs that were already in the world may be
  adopted when they load. It defaults to false so a new pack does not retro-fit
  every stored mob.
- `level.strategy` is one of `FIXED`, `RANDOM`, `NEAREST_PLAYER`,
  `PARTY_AVERAGE`, `REGION`, `WORLD_TIER`, `DIMENSION` or `EXPRESSION`. The
  expression form uses the bounded numeric language (`clamp`, `floor`, `min`,
  `max`, `pow`, arithmetic) over facts such as `player.level`,
  `player.party_level`, `player.party_size`, `world.tier` and `monster.tier`.
  Missing or non-finite facts fail the assignment instead of guessing.
- Levels always clamp to `min`/`max`, so an offset or expression cannot escape
  the declared band.

`tier` definitions carry a label, rank, affix budget, XP and loot multipliers,
a boss flag and their own attribute scaling. `affix` definitions carry a weight,
minimum level and tier, tag/incompatibility sets, an optional selector and
condition, an XP multiplier, attribute scaling and up to five hooks.

```json
{
  "schema": 1,
  "id": "rotas:affix/swift",
  "kind": "affix",
  "body": {
    "weight": 4,
    "min_tier": 1,
    "tags": ["speed"],
    "incompatible": ["slow"],
    "xp_multiplier": 1.2,
    "attributes": {"minecraft:generic.movement_speed": {"multiplier": 1.25}},
    "hooks": [{"event": "HURT", "cooldown": 60, "actions": ["rotas:action/swift_burst"]}]
  }
}
```

Hook events are `SPAWN`, `HURT`, `ATTACK`, `INTERVAL` and `DEATH`. Each affix
declares at most one hook per event and at most eight action IDs per hook.
`INTERVAL` hooks schedule themselves between 20 and 1200 ticks. Every hook has a
cooldown recorded in the monster's own persisted runtime data, so a reload,
chunk unload or restart cannot replay it early.

## Assignment and scaling

When a mob loads, the service first restores persisted state. Otherwise it walks
the profile candidates for that entity type in priority order and assigns the
first match. The roll is seeded from the mob UUID, so the same mob produces the
same tier and affixes for the same content revision.

Attribute scaling is derived once at assignment: profile, tier and affix layers
compose into a bounded multiplier plus addition per attribute, stored on the
entity. Applying them uses transient modifiers with mod-owned UUIDs, so clearing
a monster restores the entity exactly and no modifier is duplicated on rejoin.
Health keeps its ratio when the maximum changes, and the derived attributes are
re-applied from storage alone — a restored monster does not need its profile to
still exist.

XP is `(base_xp + xp_per_level * (level - 1))` multiplied by the tier and by
each affix multiplier, capped at 1e9. A monster kill awards that amount through
the normal XP source pipeline with a per-entity receipt, so a kill cannot be
paid twice. The profile reward, when present, is granted through the shared
reward engine with the monster UUID as occurrence.

## Persistence

State lives with the entity, not in a side table: Forge stores it in a
serializable capability, Fabric in a bounded, compressed scoreboard tag. Both
implementations reject oversized, truncated or malformed payloads and both keep
the health ratio across the max-health change on load. Unknown or future state
schemas fail explicitly instead of loading half a monster.

## Loader differences

Forge reports the spawn reason and whether an entity came from disk directly.
Fabric has no such loader event, so the Fabric bridge records natural spawn
reasons from the shared spawn check and marks mobs it has already seen with a
persistent `rotasutils.seen` scoreboard tag. Mobs that predate the mod are
therefore treated as fresh once, and Fabric hooks pre-mitigation damage where
Forge hooks the applied damage.

## Mob Setup (easy editor)

Admin > Monsters > **Mob Setup (easy)** (also on the overview) shows every
monster profile as a card with a live model of its first mob, its level rule,
health multiplier and base XP. Click a card to edit it later; **+ New mob
setup** picks a mob from the picture browser and opens the editor with safe
defaults (`MobSetupForm.create`: follows the nearest player's level 1-100,
+3% health per level, 20 base XP, +2 XP per level, name `{name} [Lv {level}]`).

`MobSetupEditScreen` has four pages: **Mobs** (which mobs, display name),
**Level** (fixed, random range or follows the nearest player), **Strength**
(health and damage multiplier and per-level growth, armor, speed, elite
chance) and **Rewards** (XP, XP per level, loot table, apply to existing mobs,
manual only). A side preview shows the resulting values at a chosen level.

`MobSetupForm` edits only these fields and keeps everything else in the body
(affixes, rewards, bosses, other selectors), so the advanced JSON editor stays
usable on the same profile. Elite chance maps to `rotas:tier/normal` and
`rotas:tier/elite` weights; profiles with other tiers are left alone.

**Save** sends `mob_setup_save`; `server/MobSetupService` needs EDIT and APPLY,
refuses while the admin has an unfinished studio draft, adds the two standard
tier definitions when they are missing, validates and publishes in one step,
and discards its draft on failure. **Delete** sends `mob_setup_delete` the same
way. The monster catalog sent to clients now carries each profile's body so
cards can open without another request.

## Commands

- `/rotas monster inspect <target>`: profile, level, tier, affixes and XP.
- `/rotas monster assign <target> <profile> [level]`: manual assignment; the
  level override must stay inside the profile band.
- `/rotas monster clear <target>`: remove state, modifiers and generated name.

Inspection needs the VIEW capability; assignment and clearing need EDIT and are
audited like other administrative mutations.

## Current limits

- Tier loot multipliers are persisted but no loot system consumes them yet;
  drop tables, rarities and item levels are Phase 5.
- Region and world-tier facts come from the environment provider, which still
  returns defaults until Phase 9 supplies regions and world tiers.
- Boss phases, arenas and contribution tracking are Phase 6; the tier `boss`
  flag only marks the kill as a boss kill for XP and quest events today.
- Affix hooks act on the monster and its current target within 32 blocks. They
  cannot unlock player content; player-facing grants belong to rewards.
