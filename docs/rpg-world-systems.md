# Nemeses, world events and weapon memory

Three systems that make the world remember what players do in it. Every number
lives in `config/rotasutils/season.json` (`nemesis`, `worldEvents`,
`weaponMemory`) and reloads with `/rotas season reload`.

## 1. Nemeses (`nemesis`)

A hostile mob that kills a player may **rise** (`riseChance`, default 50%). It
gets a name picked from how it killed - `melee`, `ranged`, `magic`, `fire`,
`explosion` or `other` epithets - `levelsPerRank` more levels, tougher health
and damage (`healthPerRank`, `damagePerRank`), fire resistance, and it stops
despawning. The plate reads `กรัค ผู้บดกระดูก ★ [Lv 12]`.

- **It remembers.** Every further player it kills ranks it up (to `maxRank`)
  and becomes the one it hunts. A player it has killed who comes within
  `tauntRadius` blocks hears from it, at most every `tauntCooldownSeconds`.
- **It comes back.** If the body is lost - it burned, drowned, exploded, or the
  chunk was left behind - the nemesis waits `ambushCooldownMinutes`, then has
  `ambushChancePerMinute` to find the player it last killed (same dimension, not
  in a safe zone) and appear `ambushMin/MaxDistance` blocks away. A body left in
  an old chunk is refused when it loads, so there is never two of it.
- **Only a player ends it.** The slayer gets `goldPerRank` and
  `rankPointsPerRank` times its rank, a drop grade from `gradeByRank`, and a
  **trophy**: a new `trophyItem` (a `trophyRangedItem` for an archer) named after
  it, refined to its rank up to the refine safe level, with its history on the
  lore. The trophy is never a copy of what the mob held - that may be a player's
  own gear - and the mob's own equipment drops by vanilla rules. The victim
  slaying their own nemesis is **revenge** and pays `revengeMultiplier` times as
  much. The server hears about it either way.
- A nemesis nobody has met for `forgetAfterDays` is forgotten. At most
  `maxActive` exist on the server and `maxPerPlayer` hunt one player, and one
  player's deaths raise at most one nemesis per `riseCooldownMinutes`, so dying
  on purpose is not a bounty farm.
- Bosses, minibosses, pets, quest mobs and mobs someone else named never rise.

Titles: `ผู้ล้างแค้น` (1 slain), `นักล่าศัตรูคู่แค้น` (10, +2% attack) and the
unique `ฝันร้ายของเหล่าอสูร` (first to 25). They are installed once into
existing worlds as the `nemesis_v1` batch; a deleted one stays deleted.

```
/rotas nemesis                  the nemeses that killed you, and a rumour of where each one is
/rotas nemesis list             (op) every nemesis, loaded or waiting
/rotas nemesis summon <id>      (op) bring one to you, to test it
/rotas nemesis remove <id>      (op) end one without a payout
```

## 2. World events (`worldEvents`)

Every `intervalMinutes`, with `startChance`, while fewer than `maxActive` run,
an event starts in a random enabled, non-safe zone with shapes (and hostile
spawning not switched off) in a dimension where someone is playing. With no such
zone it goes into the wilderness, `wildMin/MaxDistance` blocks from a random
player, as a circle of `wildRadius`. Everyone is told what it is, where, and
which way it is from them; players inside see a boss bar with the goal and the
time left.

While an event runs, the kind's numbers apply **inside** it:

| Field | Effect |
| --- | --- |
| `mobLevelBonus`, `mobHealth`, `mobDamage` | mobs levelled there spawn stronger |
| `eliteMultiplier` | multiplies elite and champion chances |
| `xpMultiplier` | kill experience, by where the mob died |
| `lootMultiplier`, `coinMultiplier` | drop-grade chance and coins |
| `healingMultiplier` | player healing |
| `oreBonusChance` | an ore broken there drops its drops once more |
| `playerEffects` | e.g. `"minecraft:haste 1"`, kept on players inside |
| `spawns`, `waveSize`, `waveSeconds`, `maxAlive` | waves brought around players inside |
| `goal` (`KILL`/`MINE`/`NONE`), `goalCount`, `minContribution`, `reward` | the shared goal |

Meeting the goal pays `reward` (gold, experience, rank points, item lines) to
every online player who did at least `minContribution` of it, and ends the
event. Otherwise it ends when its time runs out; a `nightOnly` kind also ends at
dawn. Wave mobs count as wild for elite promotion and do not burn in daylight.

Shipped kinds: `overrun` (ฝูงอสูรบุก), `blessed` (แดนศักดิ์สิทธิ์),
`corrupted` (แดนมลทิน), `treasure_storm` (พายุสมบัติ), `rich_ore`
(สายแร่อุดม), `cursed_night` (ค่ำคืนต้องสาป). Add more by adding entries.

```
/rotas worldevent                        running events and which way they are
/rotas worldevent types                  (op) the configured kinds
/rotas worldevent start [type] [here]    (op) start one, optionally around you
/rotas worldevent stop <id>              (op) end one without a payout
```

## 3. Weapon memory (`weaponMemory`)

The weapon that makes a kill - a melee swing, or a projectile fired by the
player holding it - counts it, on the item. Each of `milestones` (50 / 250 /
1000 / 5000) is a rank: ผ่านเลือด, ผ่านศึก, ลือนาม, ในตำนาน. Each rank adds
`damagePerRank` damage against monsters, and from `favoredFromRank` the weapon
hits its **favoured prey** - the kind it killed most - `favoredBonus` harder. It
remembers at most 8 kinds and forgets the least-killed first. A rank at or above
`announceFrom` is announced to the server. The tooltip shows kills, bosses,
nemeses, the next rank and the bonus. Never counts in PvP.

## How they feed each other

```
death by a mob ──► nemesis ──► ambush ──► slay ──► trophy weapon ──► weapon memory
world event ──► waves + elites ──► kills ──► goal ──► reward, rank points
                                   └──────► weapon memory, bestiary, combo
```

## Not yet seen in a running game

Everything is unit-tested and builds on both loaders, but nothing here has been
exercised in a live client or server yet: the nemesis plate and ambush placement,
boss bars, wave spawning and the stale-body refusal on Fabric and Forge are the
first things to watch.
