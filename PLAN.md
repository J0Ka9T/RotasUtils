# Plan: fix audit bugs, expose hidden config, cut admin-screen duplication

Status: **Phase 1 implemented 2026-09-20** (`./gradlew build` green, both loaders, all tests). Phases 2-4 still proposed. Supersedes the earlier houses plan (that work is done; recover its text from git if needed).

## Phase 1 — done

Fixed and compiling:
- `KernelUi.catalog(content, admin)` — monster profiles, raw definition JSON, boss internals and loot tables are now omitted for non-admin viewers. Player screens keep quests/merchants/items. Gating `kernel_refresh` alone would not have worked: `sync()` is also called after player-facing quest/shop/mail ops.
- `HouseProtectionService` — admin bypass, so ops can build in an unrented house.
- `MonsterService.relevel` — carries `rewarded` across a relevel; no more double payout.
- `ServerActions.copySettings` — now a `save()`/`load()` round trip instead of a hand-written setter list. The 5 dropped settings work, and a future field cannot be dropped.
- `MonsterForgeEvents.died` — `LivingDropsEvent` → `LivingDeathEvent`; Forge kill rewards now fire when the mob drops nothing.
- `ProgressService` — `CharacterStatService.apply` on level-up, so derived stats stop lagging a level behind.
- `LevelConfig` — XP setters reject non-finite values (plain `Math.max/min` passes NaN through) and `load()` goes through them, closing the unclamped save-packet path.
- `HouseBillingService` — null house no longer aborts the billing tick for every other house.
- `MonsterService.error` — evicts oldest instead of going permanently deaf after 64 distinct failures.
- Server-stop cleanup for `CombatStats`, `ZoneWandService`, `ZoneRuleService`, `NpcConversations`, `ObjectiveEngine`, `BoardService`, `WorldPicker`; `ZoneWandService.forget` on logout.
- `ZoneService.neutral` — falls back to the configured spawn level, not a hardcoded 1.
- `NpcConversations` — no `UUID.fromString("")` crash when previewing an unbound NPC draft.
- `WaystoneService.onPlaced` — respects the enabled toggle instead of burning a slot.
- `BoardConfigScreen.saveBoard` — honours its `action`, so "Save & View Board" opens the board.
- New `ConfigRoundTripTest` covering the settings round trip, the XP clamps and the summon/pet exclusion.

One audit finding was **wrong** and no change was made: `HorseService` already null-checks `getServer()` at the entry of `onDeath`.

Deliberately **not** done in Phase 1: tightening permission *tiers* (moving `save_level_config`, `admin_add_xp` etc. from admin to operator). That changes who can use existing workflows and is your call, not a bug fix.

## Summary

A four-way audit of the mod (mob levels, admin UI, combat/stats, whole-mod sweep) found ~90 provable defects. This plan fixes them in four phases, ordered so that correctness and access control land first, then the "everything is configurable from the in-game UI" goal, then the duplication that makes each new config field cost 6-7 files.

Owner decisions (2026-09-20): scope is mob levels + admin UI + combat/stats + whole-mod audit; "easier" means **the server admin edits everything in-game, no commands and no NBT**; bugs are **audited and fixed in the same pass**.

## Context (from exploration)

**Audit coverage.** Four parallel read-only audits: `level/` + `Monster*`, `client/screen/admin/**` + `network/`, `CombatStats` + `stat/`, and a sweep of house/quest/job/board/npc/waystone/skill/block/item/event/data/compat/mixin + both loader modules. Findings below were re-verified by hand in the source before being listed.

**Admin screen pattern.** The canonical one is `ServerSettingsScreen` — `extends SimpleFieldScreen`, declares rows via `collectFields(List<Field>)`, edits a draft loaded from `ClientState`, keeps an `editingBaseline`, and saves through `ConfigReviewScreen` (a 3-step stage/review/apply round trip with optimistic concurrency). 6 screens follow it fully. Against that: **5 hand-rolled `row(...)` helpers**, **26 files building `EditBox` directly**, **3 independent unsaved-changes mechanisms**, **3 independent number parsers**.

**Cost of one new config field today:** config class + `save()` + `load()` + `ClientState` decode + screen row + `validateValue` bounds + **`ServerActions.copySettings`** = 6-7 files. Forgetting the last one silently drops the setting (this already happened — see P1-3).

**Config truth.** `season.json` (`SeasonRules`) is genuinely config-driven. Almost everything else — stat per-point values, monster XP threat weights, drop rolls, party radius — is Java literals.

## System impact

- **No new source of truth.** Server stays authoritative; `RotasData` keeps owning config; the client keeps editing drafts and re-validating server-side.
- **One change of ownership:** the mob level currently exists on the client *only as text parsed out of the mob's custom name*. That is why renaming the format blanks every existing plate. Phase 2 makes the level a real synced field, and the name becomes presentation.
- **Validation moves to one place.** Range rules currently live in per-screen `validateValue` overrides (only 3 screens have them). They move next to the config fields so client and server share them.
- **`copySettings` stops existing.** It is a hand-maintained field list that silently drops anything not added to it; replaced by routing through the setters the config class already has.

## Phase 1 — correctness and access control (do first, no UI work)

**Access control**
- `network/KernelUi.java:288` — `kernel_refresh` has no permission check and `KernelUi.handle` is dispatched at `ServerActions.java:62`, *before* the admin gate. Any player can request the full content catalog: monster profiles including raw definition JSON (`KernelUi.java:85`), loot tables, bosses, merchants. **Verified.** Gate it, and split the player-facing kernel ops from the admin-facing ones.
- `house/HouseProtectionService.java:10-16` — `canModify` allows only the owner or a member, with no operator/creative bypass. On an `AVAILABLE` house (`owner == null`) **nobody, including ops, can build inside it**. **Verified.** Add an admin bypass.
- `network/ServerActions.java:686` `save_level_config` — no operator check and `LevelConfig.load` straight from the client packet, bypassing every setter clamp. Same shape at `:730 save_job`, `:227 save_season`.
- No operator re-check on: `admin_set_level` (:810), `admin_add_xp` (:816), `admin_add_points` (:821), `admin_grant_clearance` (:826), `test_give_item` (:854, up to 2304 items), `test_spawn_mob` (:862), `test_teleport` (:873).
- `wallet_withdraw` (:75) takes an unclamped client int — a **non-admin** action.
- `command/HorseCommands.java:44` `horse whistle` grants an item with no permission and no cost.

**Silent data loss**
- `network/ServerActions.java:1461-1478` `copySettings` **omits 5 settings the UI shows and the config parses**: `waystonesEnabled`, `waystoneDiscoverCost`, `waystoneWarpCost`, `waystoneWarpCostPerThousandBlocks`, `monsterDropsEnabled`. Saving them from the admin UI does nothing. **Verified.**
- `server/MonsterService.java:266-269` `relevel` rebuilds `MonsterState` without copying `state.rewarded()`. A mob that already paid out, then re-leveled (zone wand or `/monster level`), **pays its kill/boss reward a second time**. **Verified.**
- `client/screen/admin/BoardConfigScreen.java:255-259` `saveBoard` ignores its `action` parameter, so "Save & View Board" never opens the board.
- `server/ProgressService.java:221-231` — level-up never calls `CharacterStatService.apply`, so derived stats and `CombatStats.Values` stay stale until the next login/respawn/job change.

**Loader divergence (Forge players get different behaviour)**
- Forge confirms monster death from `LivingDropsEvent` (`MonsterForgeEvents.java:61`), which **does not fire when the mob drops nothing** (`doMobLoot=false`, drops cancelled). Kernel kill rewards, quest KILL_ENTITY credit and combat XP are all skipped in that case. Fabric uses `AFTER_DEATH` and always fires. **Verified.** Move Forge to `LivingDeathEvent`.
- Forge feeds post-mitigation damage to monster HURT/ATTACK conditions, Fabric pre-mitigation — the same hit produces different `event.amount` per loader.
- Fabric infers "loaded from disk" from a scoreboard tag it sets itself, inverting its own documented `applyExisting` behaviour; Forge uses the authoritative `loadedFromDisk()`.
- The vanilla XP bar is cancelled on Forge but still drawn on Fabric.

**State leaks / cleanup**
- Never cleared on server stop: `ZoneWandService.ACTIVE/RADIUS` (no cleanup path at all — a stale zone id from a previous world is reused by the wand), `ZoneRuleService.KEPT` (`forget` has zero callers; a stale entry makes `getExperienceReward` return 0 on later unrelated deaths), `NpcConversations.SESSIONS`, `ObjectiveEngine.LAST_REJECTION`, `BoardService.RANDOM_PICKS`, `WorldPicker.PENDING`, `CombatStats.VALUES`, `MobSpawnDirector` catalog caches.
- `MonsterService.errors` is a 64-entry set cleared only at shutdown — after 64 distinct failures **all** monster errors are silently swallowed for the rest of the server's life. `bosses` caps at 256 with no signal; boss #257 never ticks.
- `MonsterService.forget` removes `bosses`/`ownedScales` by UUID unconditionally while guarding `loaded`/`pending` by instance identity — a dimension change wipes the surviving instance's boss tracking.

**Null / crash paths**
- `house/HouseBillingService.java:17` — unchecked `data.house(id)`; one missing house NPEs the tick and **all** houses stop being billed.
- `server/NpcConversations.java:220` — `UUID.fromString` on an empty string when an admin previews an unbound NPC draft.
- `server/horse/HorseService.java:521` — nullable `entity.getServer()`.
- `ZoneService.neutral()` hardcodes `region.min/max = 1`, so any transient failure levels the mob at **1 permanently**, ignoring `spawnLevel`.

## Phase 2 (mob system) — done 2026-09-20

Remaining mob-level bugs fixed:
- `MonsterService.forget` — boss tracking and owned-scale records are only dropped for the live instance, so a dimension change no longer kills the surviving mob's boss ticking.
- `MonsterFabricEvents` — the "seen" tag is now written for every mob, not just ones that had a profile at the time. Fabric's `apply_existing` now behaves as documented instead of inverted.
- `MonsterForgeEvents.damaged` — `LivingDamageEvent` → `LivingHurtEvent`, so the `event.amount` fact matches Fabric's pre-mitigation number and ability thresholds fire the same on both loaders.
- `MobSpawnDirector.clearCaches()` on server stop; the catalogue no longer outlives the world.
- `MobLevelConfig.load` **replaced** the merge for exclusion lists: removing a default exclusion (e.g. `minecraft:villager`) used to come back on the next load. Found while wiring the UI, not by the audit.

Now configurable, all editable in Admin → Level System → Mob Levels:
- Excluded mobs — multi-select mob picker, plus one row per exclusion to remove it.
- Excluded mod namespaces — the `easy_npc`/`easynpc`/`easy_npc_bundle` list was hardcoded; now data, and removable.
- Excluded entity tags — listed and removable.
- "Skip summons and golems" (the MISC rule) as an explicit toggle instead of invisible behaviour.
- **Monster XP threat weights** — new `MonsterXpWeights` (health, damage, armour, toughness, speed + its baseline, knockback resistance, and the three big-health tiers with their multipliers). These decide every mob's XP and were literals in `MonsterXpService`. Persisted in `LevelConfig`, clamped through setters, NaN-proof.
- Party radius for monster scaling now reads `partyNearbyRadius` instead of a hardcoded 64, matching the party/quest/XP-share code.

Removed: `MobLevelConfig.offset`, dead config read by nothing.

Tests: `ConfigRoundTripTest` extended — exclusion removal survives a save, namespace exclusions work, XP weights drive the score and reject NaN. Full `./gradlew build` green (638 tests, both loaders).

**Left alone deliberately:** `MonsterState.load` clamps persisted natural levels at 100 rather than re-clamping to the current `maxLevel`. Re-clamping would silently rewrite the level of every already-spawned mob when an admin lowers the ceiling. That is a design call, not a bug — say the word if you want lowering `maxLevel` to retroactively nerf existing mobs.

## Phase 2 (rest) — make it configurable from the UI (the "easier" goal)

**Config that exists but no admin can reach** (neither UI nor command — NBT-only today):
- `MobLevelConfig.excludeEntities` and `excludeTags` — mutators have zero production callers. Add an editor screen (entity picker + tag entry) so the 4 hardcoded defaults stop being the only possible values.
- Every `MobCategory` except the literal `"MONSTER"` — `toggleCategory` is only ever called with that one string.
- The hardcoded namespace deny-list `easy_npc|easynpc|easy_npc_bundle` and the MISC skip rule (both in `shouldLevel`) become visible, editable entries rather than invisible behaviour.
- Delete `MobLevelConfig.offset` — dead config, read by no production code, its own Javadoc admits it.

**Command-only features that need a UI path:** `/monster level` (the UI has inspect/assign/clear but no way to set a level), house `rent`/`pay`/`buyout` (the whole player housing economy), `season reload`/`check`/`pacing`/`drops`/`rank`, `zone gate check`/`test`, `horse top`, `item inspect`, `loot preview`/`recover`, `points_per_level`.

**Hard-coded tunables to move into config** (highest-value first, all currently Java literals):
- `MonsterXpService.java:60-65` threat weights (health `0.35`, damage `4.0`, armor `1.7`, toughness `3.5`, speed, knockback) and the tier thresholds at `:69-77`. These decide every mob's XP payout and cannot be tuned at all.
- `MonsterThreat.java:18-23` armor/toughness clamps, offense/level/affix/boss weights.
- `stat/CharacterStat.java:281-296` per-point stat values (STR `0.008`, VIT `0.005`/`0.12`, INT `0.008`, AGI `0.003`/`0.0015`).
- `MonsterService.java:305,314` party radius hardcoded to 64 blocks while `SeasonRules.partyRadius` (default 40) governs XP sharing — two different party radii in one system.
- `DropService.java:91-92` coin roll `1 + nextInt(2 + level/4)`.

**Level as real state.** Sync the mob's level as a field instead of having the client regex it out of the custom name. Fixes: changing the name format blanking every existing plate; a client that hasn't received the config sync parsing with the wrong template; mobs renamed by another mod being invisible to every client-side level reader; `{name}` substitution corrupting a mob whose own name contains `{level}`.

## Phase 3 — one way to build an admin screen

Convert the hand-rolled screens onto `SimpleFieldScreen` + `ConfigReviewScreen`, starting with `LevelManagerScreen` (it is the one this work touches most). Targets, worst first: `ZoneEditScreen` (own parser that **silently swallows bad input and ships the fallback as if typed**), `LevelManagerScreen`, `QuestCreatorScreen`, `SkillNodeEditorScreen`, `SkillCategorySettingsScreen`, `SeasonSettingsScreen`.

Also fix, once, in the shared layer:
- `editingBaseline` is set at draft creation and never refreshed after a successful apply → every saved draft still warns "unsaved changes", and the stale baseline is re-sent as the concurrency token.
- `if (draft == null)` guards mean an open editor never picks up someone else's saved changes; 14 screens have no `onDataRefreshed` override at all, so `refreshCurrent()` is a no-op for them.
- `ScreenRouter.pendingPickScreen` is a **single static slot**, cleared on delivery and never restored if the player cancels the world pick — abandoning a pick loses the entire editor draft.
- Range validation moves next to the config fields; `FieldInput.validate` checks parseability only, and only 3 screens declare bounds.

## Phase 4 — deferred (needs its own decisions)

Not in this plan, listed so they are not lost: skill modifiers being *permanent* while stat modifiers are *transient* (`SkillService` vs `CharacterStatService:377`); the two-sources-of-truth evasion cap; `@Inject` vs `@ModifyVariable` ordering in `LivingEntityCombatMixin` being undefined; the stale `CharacterStat.season` Javadoc and stale `PLAN-stat-balance.md` numbers.

## Verification

- `./gradlew build` — compiles both loaders; `JobsAndStatsTest` and `MobLevelConfigTest` must stay green. Note `MobLevelConfigTest` is currently the *only* caller of the exclude-list mutators, so Phase 2 must not break it.
- New unit tests: `relevel` preserves `rewarded`; `copySettings`-equivalent round-trips every field (a reflection test so a new field can never be dropped again); `shouldLevel` for summons/pets/MISC.
- In game, per loader (Forge and Fabric both, since the divergences above are loader-specific):
  - Kill a profiled monster with `doMobLoot=false` on Forge → reward must still pay.
  - Re-level a mob that already died-and-paid → must not pay twice.
  - Toggle waystone settings in the UI, reconnect → must persist.
  - Break a block in an unrented house as an operator → must be allowed.
  - Send `kernel_refresh` as a non-admin → must be refused.
  - Change the mob name format with mobs loaded → existing plates must survive.
