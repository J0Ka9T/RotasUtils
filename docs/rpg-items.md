# RPG items and loot (Phase 5)

Items, rarities, sets and loot tables are kernel definitions like everything else:
they load from disk packs, from admin drafts and from rollback, and they are
validated against the live registries before they can be published.

## Definitions

`rarity` carries a label, chat colour, rank and a modifier multiplier that scales
every modifier on an item rolled at that rarity.

`item` describes one profile:

```json
{
  "schema": 1,
  "id": "rotas:item/warden_blade",
  "kind": "item",
  "body": {
    "item": "minecraft:iron_sword",
    "slot": "MAINHAND",
    "min_level": 1,
    "max_level": 60,
    "rarities": {"rotas:rarity/common": 9, "rotas:rarity/rare": 1},
    "modifiers": [{"attribute": "minecraft:generic.attack_damage", "base": 2, "per_level": 0.5}],
    "requirement": {"min_level": 10, "stats": {"rotas:stat/might": 5}},
    "set": "rotas:set/warden",
    "name": "{rarity} {name} Lv{level}",
    "lore": ["Forged in the smoke"],
    "curios_slot": "ring"
  }
}
```

- The item level is clamped into `min_level`..`max_level`, whatever level the
  source (a monster, a merchant, a command) asks for.
- Modifiers scale as `base + per_level * (level - 1)`, multiplied by the rarity
  multiplier, and are summed per attribute and operation so one item can never
  carry two modifiers for the same pair.
- `slot` gives the item ordinary vanilla equipment modifiers. `curios_slot`
  marks a Curios item; those modifiers are applied by the equipment service
  instead, because Curios slots have no vanilla equivalent.
- Everything the runtime needs lives on the stack in a `RotasItem` tag: profile,
  rarity, item level and set. A stack keeps working after its definition is
  removed; it simply stops being requirement-gated.

`set` lists its pieces and the bonuses earned at piece counts. Membership must
agree in both directions — a profile that names a set the set does not list is
rejected at validation, and an item cannot belong to two sets.

`loot` is a weighted table:

```json
{
  "schema": 1,
  "id": "rotas:loot/undead",
  "kind": "loot",
  "body": {
    "min_rolls": 1,
    "max_rolls": 2,
    "entries": [
      {"weight": 8, "item": "minecraft:bone", "min_count": 1, "max_count": 3},
      {"weight": 1, "profile": "rotas:item/warden_blade"}
    ]
  }
}
```

Each entry produces either a vanilla item or an RPG profile, never both. Entries
whose condition fails are removed from the pool before the first draw, so a
failing condition never silently wastes a roll. A roll produces at most 64
stacks whatever the multiplier.

## Determinism and recovery

Every roll is seeded. The reward engine seeds each transaction with its receipt,
so the same grant retried after a rejected write produces exactly the same items.
`/rotas loot preview` uses a stable seed too, which makes an administrator's
preview reproducible rather than indicative.

Rewards are staged inside the player-record transaction and delivered only after
the record write succeeds. Anything that does not fit in the inventory goes to a
bounded per-player mailbox (128 stacks) instead of dropping on the floor;
`/rotas loot recover` delivers it later, and a still-full inventory puts the
stacks straight back rather than destroying them.

## Monsters and loot

A monster profile may name a `loot` table. On a confirmed kill the table is
rolled with the monster level and the tier's loot multiplier, under a receipt
keyed by the monster UUID, so a duplicate death event pays nothing extra. The
multiplier scales the rolled count and is still bounded by the 64-stack ceiling.

## Requirements, sets and Curios

The equipment service re-checks every player once per second:

- an equipped item whose requirement is unmet is removed and returned to the
  inventory (or the mailbox), with a message naming the missing requirement;
  the main hand is left alone so a player is never disarmed mid-swing,
- set bonuses are counted across armour, off-hand and every Curios slot, then
  applied as transient, mod-owned attribute modifiers,
- Curios item modifiers are applied the same way, since vanilla does not apply
  stack modifiers in a Curios slot.

All of those modifiers are removed on logout, content reload and shutdown, so no
owned modifier can survive its cause.

## Commands

- `/rotas item inspect`: profile, rarity, level and set of the held item.
- `/rotas item give <player> <profile> [level]` (EDIT, audited).
- `/rotas loot preview <table> [level] [multiplier]` (VIEW).
- `/rotas loot recover`: deliver pending mailbox stacks.

## Current limits

- Requirements are enforced on a one-second sweep, not at the instant of
  equipping; a player can hold an item they do not qualify for for up to a tick
  window before it is returned.
- Curios integration is reflective and optional. Without Curios installed the
  Curios paths are inert and only vanilla slots participate.
- Item durability, sockets, reforging and player trading are not part of this
  phase.
