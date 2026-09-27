# Plan: Ragnarok-style RPG expansion

Owner brief (2026-09-20): "add unique ฉายา system", "make that we can configure normal drop that drop
from each mobs that we didnt set", "add ตีบวก to weapon and armor. and scroll that will help about
this system too. and it very hard to find", "make all system relate to RPG or Ragnarok anything that
the best of it", "add many system that may useful to this mods".

Status: batches 1-4 implemented 2026-09-20 and green (`:common:test` 665/0/0, `:forge:build` and
`:fabric:build` successful). Batch 5 delivered as part of the others: screens, lang parity, creative
tab entries, textures and docs are all in. Still open: nothing has been seen in a running game - no
authenticated client session was run, so the screens, the name-plate title and the live drop rates
rest on unit and build evidence only. The NPC dialogue outcome for the refinement bench was left out
on purpose (it would mean touching the dialogue engine's fixed outcome enum); the bench is reached
from `/rotas refine` and from using any refine material instead.

## What is being added

| Batch | System | Thai name |
|---|---|---|
| 1 | Refinement + refine ores + scrolls | ตีบวก |
| 2 | Drops for mobs that have no Mob Setup | ดรอปปกติ |
| 3 | Titles, unique titles, unlock engine | ฉายา |
| 4 | Cards and sockets | การ์ด / รู |
| 5 | Screens, lang, creative tab, docs | - |

## Batch 1 - Refinement (ตีบวก)

**Where the level lives.** A `RotasRefine` compound on the stack (`{schema:1, level:int}`). It works on
any weapon or armour, vanilla or modded, so it does not depend on the kernel item catalogue.

**Numbers** live in `season.json` under `refine` (`SeasonRules.RefineRules`), reloaded with
`/rotas season reload`:

- `maxLevel` 10, `safeLevel` 4 (at or below it an attempt never fails).
- `chances` per target level, Ragnarok classic: +5 60%, +6 40%, +7 40%, +8 20%, +9 20%, +10 10%.
- `onFail` one of `DOWNGRADE` (default), `RESET_TO_SAFE`, `BREAK`, `KEEP`.
- `attackPerLevel` 1.0 and `attackPerOverLevel` 1.5 (levels above the safe level pay more), both flat
  damage added at hit time; `defensePerLevel` 0.8 and `defensePerOverLevel` 1.2 feeding the existing
  `rotas:defense` channel, so armour refinement uses the damage formula that already exists.
- `goldPerAttempt` and `goldGrowth`: the gold cost grows with the target level.
- `announceFrom` 8: a success at or above this level is announced to the whole server, the way a
  Ragnarok +10 is.

**Materials.** One ore per attempt, by category: weapons take `rotasutils:oridecon`, armour takes
`rotasutils:elunium`. The enriched versions replace them and add `enrichedBonus` (+15%) to the chance.

**Scrolls** (all rare, all one-use, all consumed only when they were actually used):

| Item | Effect |
|---|---|
| `protection_scroll` | A failed attempt cannot break or downgrade the item |
| `blessing_scroll` | +20% success chance on this attempt |
| `certificate_scroll` | The attempt cannot fail at all |

**How a player refines.** `/rotas refine` with the item in hand, the Refinement screen, or simply using
a refine ore or scroll. (The NPC dialogue outcome was dropped: it would mean extending the dialogue
engine's fixed outcome enum, which is not worth the blast radius for a second door to the same bench.) The server is authoritative in both paths: `RefineService`
re-reads the held stack, the materials and the wallet.

**Combat.** `CombatStats.modifyDamage` adds the attacker's main-hand refine attack for a direct melee
or projectile hit, and `EquipmentService` folds the worn armour's refine defence into the player's
`rotas:defense` value, so refinement rides the balance that already exists rather than a second one.

## Batch 2 - Drops for mobs with no setup

`SeasonRules.DropRules` gains `plain`:

- `enabled`, `coinChance`, `coinMultiplier`, `lootChance`, `grades`, `hostileOnly`.
- `byEntity`: entity id -> its own `RankDrop`, for "zombies pay more than chickens" without a full
  Mob Setup.
- `ignore`: entity ids that never pay (villagers, pets, boss-mod entities an admin handles elsewhere).

`RotasEvents.handleLivingDeath` calls `DropService.onPlainMobKilled` when the kill has no
`MonsterState`, so a mob nobody configured still pays coins and can roll a drop grade. Everything an
assigned monster already pays is untouched.

Admin surface: `/rotas drops` shows and edits the plain rules and writes `season.json` straight away.
A "Default drops" page inside the Mob Setup screen was planned and is **not** done; the commands and
the config file cover the same ground, so it stays a follow-up rather than a gap in the feature.

## Batch 3 - Titles (ฉายา)

- `title/TitleDef`: id, name, description, colour, rarity, `unique`, unlock condition, effects
  (the existing `CharacterStat.Effect`, so a title's bonus goes through the modifier pipeline that
  already exists).
- `RotasData` stores the definitions and, for a unique title, the one player who holds it. A unique
  title is claimed once: the first player to meet the condition keeps it and nobody else can earn it.
- `PlayerProgress` gains the earned set and the equipped title.
- `TitleService.check` runs on level up, kill, quest completion, boss kill and refine success; each
  condition kind reads a counter that already exists rather than adding new tracking.
- Display: the equipped title shows in the Character Hub, above the player's name plate and as a chat
  prefix. `/rotas title list|use|clear`, and `/rotas title grant|revoke` for an admin.

## Batch 4 - Cards and sockets

- Gear carries `RotasSockets {count, cards:[id]}`. Sockets are punched by `rotasutils:socket_punch`,
  one per punch, up to `maxSockets`.
- A card is one registered item (`rotasutils:card`) carrying the card id in NBT; the definitions live
  in `season.json` under `cards` with drop sources, effects and a rarity.
- A card in a socket contributes its effects through `EquipmentService`, the same path set bonuses use.
- Card drops hang off the drop rules, at a Ragnarok-like rate, from the monster that the card names.

## Batch 5 - Surface

Refinement screen, title picker in the Character Hub, creative tab entries, item textures and models,
`en_us`/`th_th` in parity, `docs/rpg-refine.md`, `docs/rpg-titles.md`, `docs/rpg-cards.md`.

## Gates

Every batch ends green on `gradlew :common:test :forge:build :fabric:build`, and the lang parity test
must stay green because every new message ships in both languages.
