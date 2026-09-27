# Drops for mobs with no setup, and the drop filter

A monster with a Mob Setup pays through its profile, its tier and its rank. Every
other mob in a pack used to pay nothing at all, which made a server's own mobs
feel worthless next to the configured ones. `drops.plain` is the other half.

## What it covers

- A mob with no `MonsterState` of any kind: the kill goes through
  `DropService.onPlainMobKilled`.
- A mob that was levelled automatically but never given a rule of its own: if an
  administrator wrote a per-entity rule for it, that rule wins over its rank.

`hostileOnly` keeps it to mobs the game itself calls hostile; turning it off makes
animals pay too. `ignore` names the entities that never pay whatever else says -
villagers, wandering traders and the golems that belong to a town rather than a
fight.

A mob with no level band still has to scale its coins by something, so its own
maximum health stands in for a level.

## Per-entity rules

```json
"drops": {
  "plain": {
    "enabled": true,
    "hostileOnly": true,
    "rule": {"coinChance": 0.25, "coinMultiplier": 0.5, "lootChance": 0.02, "grades": ["common"], "items": []},
    "byEntity": {
      "minecraft:zombie": {
        "coinChance": 0.9,
        "coinMultiplier": 2.0,
        "lootChance": 0.5,
        "grades": ["medium"],
        "items": ["minecraft:rotten_flesh 1-3", "minecraft:iron_ingot 1 @0.05"]
      }
    },
    "ignore": ["minecraft:villager", "minecraft:wandering_trader", "minecraft:iron_golem"]
  }
}
```

`items` is the part that makes this worth having: item lines in the same format
the drop grades use (`"<item> <min>-<max> [@chance]"`), rolled on top of whatever
grade the rule names. That is how one entity type gets its own drop without a
content pack behind it.

## Commands

Every one of these writes `season.json` and takes effect at once.

```
/rotas drops                                  what the current rules are
/rotas drops enable <true|false>
/rotas drops hostile_only <true|false>
/rotas drops rates <coin_chance> <coin_multiplier> <loot_chance>
/rotas drops entity <entity> rates <coin_chance> <coin_multiplier> <loot_chance>
/rotas drops entity <entity> grade <grades...>
/rotas drops entity <entity> add_item <line>
/rotas drops entity <entity> clear_items
/rotas drops entity <entity> remove
/rotas drops ignore <entity> <true|false>
```

The master switch for every drop in this mod is still
`serverSettings.monsterDropsEnabled`; `drops.enabled` and `drops.plain.enabled`
sit under it.


## Switching ordinary drops off

`drops.plain` adds; `drops.filter` takes away. It is the part an administrator
uses to stop rotten flesh filling every bag, or to make one mob stop dropping one
item, without rewriting a loot table or shipping a data pack.

```json
"drops": {
  "filter": {
    "enabled": true,
    "blocked": ["minecraft:rotten_flesh"],
    "byEntity": {"minecraft:spider": ["minecraft:string"]}
  }
}
```

- `blocked` - items no mob ever drops.
- `byEntity` - items one kind of mob never drops.
- `enabled` - the master switch; turning it off keeps both lists but applies
  neither.

Lists are bounded (1024 global items, 512 entities, 128 items each), so one
mistake cannot grow without limit.

### How it is enforced

Every drop a dying mob makes goes through `Entity#spawnAtLocation`, whatever
produced it - vanilla loot, a data pack, another mod. `LivingEntityDropMarkMixin`
marks the window in which a mob is spilling its death drops and
`EntityDropFilterMixin` drops the blocked stack inside that window only, so an
item handed over for any other reason is untouched. This mod's own drop tables
are filtered too, so a blocked item cannot come back in through the rank grades.

Armour and held items a mob was wearing are outside the loot table and are not
filtered - that is also why they are not listed in the screen.

### The screens

**Drops per mob** (Admin menu -> Monsters -> Drops per mob) lists every mob in the
pack from the client's own registry, then asks the server for one mob's real
drops. The server gets them by rolling that mob's actual loot table three hundred
times against a throwaway copy of the mob (`VanillaDropScan`), so the chances
shown are the ones players meet and modded mobs need no special handling. Each
line can be switched off for that mob; a line already off everywhere says so.

**Every item** (Admin menu -> Monsters -> Every item) lists the whole item
registry with a search box and a "show only the ones off" filter, and each line
toggles that item for every mob at once.

Both write `season.json` immediately.

### Commands

```
/rotas drops filter <true|false>                 the master switch
/rotas drops block <item> <drops>                for every mob
/rotas drops block_for <entity> <item> <drops>   for one mob
```

`drops` is what the item should do: `true` means it drops normally again.
