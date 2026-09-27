# RPG bosses (Phase 6)

A boss is a `boss` definition bound to a monster profile through its `boss`
field. Everything a boss needs at runtime lives in the monster's own persisted
runtime data, so a chunk unload, a reload or a restart restores the same phase
and the same contribution ledger instead of a fresh fight.

## Definition

```json
{
  "schema": 1,
  "id": "rotas:boss/warden",
  "kind": "boss",
  "body": {
    "label": "The Warden",
    "arena_radius": 48,
    "leash": true,
    "reset_seconds": 30,
    "enrage_seconds": 300,
    "enrage_attributes": {"minecraft:generic.attack_damage": {"multiplier": 2}},
    "minimum_share": 0.05,
    "reward": "rotas:reward/warden",
    "loot": "rotas:loot/warden",
    "phases": [
      {"threshold": 1, "label": "The Warden wakes"},
      {"threshold": 0.6, "label": "The Warden roars", "attributes": {"minecraft:generic.armor": {"add": 8}},
       "on_enter": ["rotas:action/warden_slam"], "interval": 100, "on_interval": ["rotas:action/warden_pulse"],
       "summon": "rotas:monster/warden_add", "summon_count": 2}
    ]
  }
}
```

- Phases descend by health threshold and the first must start at full health.
  The phase is chosen from the current health fraction, so a boss healed above a
  threshold steps back to the earlier phase and its modifiers are replaced
  rather than stacked.
- `on_enter` runs once per entry; `interval` plus `on_interval` runs on a
  persisted schedule while the phase is current. Both use ordinary kernel
  actions, staged and committed through the monster transaction.
- `summon` spawns the named monster profile, which is assigned like any other
  monster and therefore carries its own tier, affixes and scaling.
- Phase and enrage attributes are applied as owner-scoped transient modifiers.
  Each owner replaces its own previous work, so nothing double-applies.

## Arena, reset and enrage

The encounter records its origin when it begins. With `leash` on, a boss that
leaves `arena_radius` is teleported back. When no living, non-spectator player
has been inside the radius for `reset_seconds`, the encounter resets: full
health, phase zero, phase modifiers removed, ledger cleared, boss returned to the
origin. Enrage applies its attributes once the timer expires and is cleared by
the same reset.

## Contributions and payouts

Damage dealt by players is recorded per player in the ledger, bounded to 32
contributors so a raid cannot grow the entity tag without limit. On a confirmed
death the shares are normalised and every contributor at or above
`minimum_share` receives the boss reward and, if declared, a roll of the boss
loot table at the monster's level and loot multiplier. Both use the boss UUID as
the occurrence, so a duplicate death event or a retried payout pays nothing
twice. Contributors who are offline at the moment of death are skipped.

## Commands

`/rotas monster inspect <target>` reports the boss definition, the current phase
and the number of recorded contributors alongside the monster fields.

## Current limits

- Offline contributors are skipped rather than queued; a mail-based payout for
  offline raiders is not implemented.
- The arena is a radius around the spawn origin, not an authored region; region
  shapes arrive with the world phase.
- Boss bars, music and cinematic presentation are not part of this phase.
