# Plan: Character stat balance (percent STR, harsher diminishing returns)

Status: version 2 implemented 2026-09-15; **version 3 implemented 2026-09-18** - VIT health became percent (+0.5%/point) with defense halved to +0.12, INT became percent magic damage (+0.8%/point on the new `rotas:magic_power` channel), AGI evasion cut to +0.15%/point. Reason: at level 100 an all-in VIT build was worth x10 durability against STR's x2.76 damage, so every build was VIT. All four stats now land between x2.39 and x2.76, guarded by `everySeasonStatIsWorthAboutTheSameAtMaxLevel`. Worlds on version 2 refund those stats once on server start. `:common:test` green; in-game checks still pending.

## Summary

Season stats are overpowered because flat per-point bonuses (STR +1 damage, VIT +10 HP) sit on vanilla-scale gear and on mobs that scale only by percent.

The fix has two parts:
- **STR becomes a percent bonus** to attack damage.
- **Diminishing returns get harsher**, so splitting points beats dumping them all into one stat.

Existing worlds migrate automatically once and every player gets a free refund.

Owner decisions (2026-09-15):
- Target: at L100, stats are worth about as much as gear.
- Levers: flat → percent STR, and harsher diminishing returns.
- Migration: automatic re-apply with a refund.

## Context (from exploration)

**Where the numbers live**
- `stat/CharacterStat.season(rules)` defines the four season stats:
  - STR: `generic.attack_damage` +1.0 ADD
  - VIT: +10 `max_health` ADD, plus +0.5 logical defense
  - INT: +1.2 logical magic attack
  - AGI: +0.3% `attack_speed` MULTIPLY_BASE, plus +0.2% evasion (cap 40%)
- `server/CharacterStatService.apply` computes the bonus: `perPoint × SeasonMath.effectivePoints(points, step, drop, floor) × synergy(mainJob, stat)`, then adds transient attribute modifiers.
- `level/SeasonRules`: `startStatPoints=10`, `statPointsPerLevel=3`, `milestone 10/+5`, `diminishingStep=50`, `diminishingDrop=0.10`, `diminishingFloor=0.40`. Synergy goes up to ×1.3.
- `level/MobLevelConfig`: mobs gain +5% health and +3% damage per level.

**How big the problem is** (all points in one stat, ×1.3 synergy)

| Level | points | effective | STR today | VIT today | Mob scaling |
|---|---|---|---|---|---|
| 1 | 10 | 13 | +13 damage (diamond sword = 7) | +130 HP | ×1 |
| 10 | 42 | 55 | +55 damage | +546 HP | HP ×1.5, damage ×1.3 |
| 100 | 357 | 322 | +322 damage | +3220 HP, damage taken ×0.38 | HP ×6, damage ×4 |

**Persistence (important for migration)**
- Stat definitions are saved **in the world** (`RotasData.characterStats`, seeded once, guarded by `character_stats_seeded`). Changing `CharacterStat.season` only affects new worlds.
- `config/rotasutils/season.json` **overrides** the world copy at server start (`SeasonConfigFile.load`, called in `RotasEvents.onServerStarted` before `seedDefaults`). It already stores `diminishingStep: 50` and `diminishingFloor: 0.4`, so changing the Java defaults alone does nothing on existing servers.
- `CharacterStatService.applySeasonPreset` already does "refund everyone, then replace the definitions". It is exposed as `/rotas season apply_stats`. The migration can reuse it.

**Vanilla attribute math (1.20.1):** `v = base + ΣADD; v += v × ΣMULTIPLY_BASE; v *= Π(1 + MULTIPLY_TOTAL)`.
- `MULTIPLY_BASE` scales base plus weapon damage and stacks **additively** with other percent bonuses, which is less explosive than MULTIPLY_TOTAL.
- Epic Fight reads the resulting attack damage, so motion multipliers apply on top of it proportionally.

## System impact

- **Source of truth is unchanged.** Definitions stay in `RotasData`, tuning stays in `SeasonRules`/`season.json`, allocations stay in `RpgProfile`. No new runtime state.
- **Two one-shot migration markers are added:**
  - `SeasonRules.statBalanceVersion` (JSON) lets old `season.json` files move to the new diminishing defaults once, while hand-tuned values are kept.
  - `RotasData.characterStatsVersion` (NBT) lets the world re-apply the season preset once, but only if STR still matches the old shipped definition. Admin-edited stats are not touched.
- **Lifecycle:** migration runs in `onServerStarted`, before any player joins. On login, `apply()` rebuilds modifiers from the refunded profile. The refund is free, and the respec counter is not increased.
- **Players see:** all points returned once, STR showing `+0.8% attack` per point, and lower effective points past 25 in a stat.

## Approach

### 1. New tuning values

- **STR:** `generic.attack_damage`, **MULTIPLY_BASE 0.008/point**, `percent=true`.
- **Diminishing:** `diminishingStep 50 → 25`, `diminishingFloor 0.40 → 0.25`, drop stays 0.10.

Result, all-in with ×1.3 synergy:

| Level | effective points (was) | STR bonus |
|---|---|---|
| 1 | 13 | +10% damage |
| 10 | 52 (55) | +42% |
| 50 | 162 (205) | +130% |
| 100 | 220 (322) | +176% (×2.76 vs mobs with ×6 HP) |

That lands on the "stats ≈ gear" target for STR.

### 2. `season.json` migration (`SeasonRules`)

- Add `public int statBalanceVersion = 2;` (the current value for fresh rules).
- In `fromJson`, parse to a `JsonObject` first. If the key is missing or below 2, treat the file as legacy:
  - If `diminishingStep == 50` and `diminishingFloor == 0.4` (untouched defaults), set 25 / 0.25.
  - Custom values stay as they are.
  - Set `statBalanceVersion = 2`.
- `SeasonConfigFile.reload` already writes the sanitized copy back, so the migration persists after one start.

### 3. World stat-definition migration (`CharacterStatService.seedDefaults`)

- `RotasData`: add `int characterStatsVersion` (saved as `character_stats_version`, missing → 0).
- Add `CharacterStat.SEASON_VERSION = 2`.
- New flow:
  - **Fresh world:** seed `season()` and set the version to 2.
  - **Already seeded, season active, version < 2:** if `rotas:str` still has attribute `generic.attack_damage`, operation ADD and perPoint 1.0 (the old shipped value), call `applySeasonPreset(data)` (refunds everyone and installs the new defs) and log the refund total with `data.audit`.
  - **Admin-edited STR:** leave it alone.
  - Either way, set the version to 2.
- Season off: set the version and do nothing else.

## Changes

- `common/.../stat/CharacterStat.java`: STR in `season()` becomes MULTIPLY_BASE 0.008 with percent on; add `SEASON_VERSION`; add a helper `isLegacySeasonStrength(CharacterStat)`; update the Javadoc numbers.
- `common/.../level/SeasonRules.java`: new defaults `diminishingStep=25`, `diminishingFloor=0.25`; add `statBalanceVersion`; legacy detection in `fromJson`; update the sanitize fallback values (`0.4` → `0.25`).
- `common/.../data/RotasData.java`: add the `characterStatsVersion` field, getter/setter, save and load.
- `common/.../server/CharacterStatService.java`: `seedDefaults` migration branch; update the class Javadoc ("every 25 points … never below 25%").
- `common/.../client/screen/admin/SeasonSettingsScreen.java`: no code change (the fields already exist); check that the labels still read correctly.
- `docs/` season/stats doc, if one describes the 50/40% rule: update the numbers.
- Tests:
  - `SeasonMathTest`: add `effectivePoints(357, 25, 0.1, 0.25) == 169.25` and `effectivePoints(42, 25, 0.1, 0.25) == 40.3`.
  - `SeasonRules` JSON test: a legacy file (no version, 50/0.4) migrates to 25/0.25; a legacy file with custom 40/0.5 is kept; a current file round-trips.
  - `JobsAndStatsTest`:
    - An old seeded world with legacy STR and allocated points → `seedDefaults` refunds the points, STR becomes MULTIPLY_BASE, version is 2.
    - A world with an admin-edited STR → untouched.
    - Running it a second time does nothing.

## Verification

- Automated: `./gradlew.bat :common:test :forge:build :fabric:build`.
- In game (Forge dev run, which already has a `season.json` with 50/0.4):
  1. Start the server. Log shows the refund audit; `season.json` now reads `diminishingStep: 25`, `diminishingFloor: 0.25`, `statBalanceVersion: 2`.
  2. Existing character: points are all unspent, and the Stats screen shows STR as `+0.8%`.
  3. Put 42 points in STR as a fighter with an iron sword (6 damage) → F3/attribute tooltip shows attack about 6 × 1.42 ≈ 8.5, not 60.
  4. Epic Fight combo damage scales proportionally (no flat +55 per hit).
  5. Restart the server → no second refund.
  6. `/rotas season apply_stats` still works manually.

## Risks and open question

- **VIT and INT are still overpowered (not in the chosen levers).** Even with the harsher diminishing returns, an L100 all-in VIT build gets **+2200 HP** and takes ×0.48 damage, and INT gets **+264 flat magic**. That misses the "stats ≈ gear" target. Recommended follow-up, same migration:
  - VIT +0.5 HP/point and +0.25 defense/point → L100 ≈ +110 HP, damage taken ×0.69.
  - INT +0.1 magic/point, or a percent spell power.
  Needs owner OK before it goes in.
- The refund is global and one-time; players must re-spend their points. A server announcement is suggested.
- MULTIPLY_BASE stacks additively with other percent attack-damage buffs from mods, so the numbers may need retuning if a mod adds a large percent bonus.
