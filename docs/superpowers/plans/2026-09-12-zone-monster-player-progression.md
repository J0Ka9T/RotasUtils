# Zone, Monster, and Player Progression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make world geography control monster danger while threat, participation, exploration, and encounter novelty determine server-authoritative player XP.

**Architecture:** Add pure calculation types for zone resolution and XP decisions, then persist their bounded inputs through the existing `RotasData`, `PlayerProgress`, and `MonsterState` stores. Connect them at existing spawn, death, player-tick, admin-screen, and sync boundaries without global entity scans or a second progression store.

**Tech Stack:** Java 17, Minecraft 1.20.1 Mojang mappings, Architectury 9.2.14, Forge 47.4.22, Fabric API 0.92.11, JUnit Jupiter 5.11.4.

**Spec:** `docs/superpowers/specs/2026-09-12-zone-monster-player-progression-design.md`

## Global Constraints

- Preserve existing zone IDs/shapes/bands and every player's XP, level, points, quests, and rewards.
- Server owns all gameplay decisions; clients receive presentation summaries only.
- Reuse `RotasData`, `PlayerProgress`, `MonsterState`, progression receipts, and existing network channels.
- No global entity scans and no unbounded player, contribution, discovery, or familiarity collections.
- Keep current Forge/Fabric Architectury structure and Java 17 release compatibility.
- Do not deploy into PROJECT-RC as part of this plan.

---

### Task 1: Pure Zone and Adventure XP Rules

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/level/AdventureXpConfig.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/level/AdventureXpMath.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/core/ZoneDef.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/ZoneService.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/AdventureXpMathTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/ZoneDefTest.java`

**Interfaces:**
- Produces: `ZoneDef.Danger`, zone metadata accessors, `AdventureXpConfig`, and pure bounded multiplier functions in `AdventureXpMath`.
- Produces: deterministic `ZoneService.select` ties ordered by zone ID and pure wilderness-level helpers.

- [x] Write failing tests for old NBT defaults, metadata round-trip, deterministic overlap ties, challenge bounds, and repetition decay.
- [x] Run the focused tests and confirm failures name missing metadata/math behavior.
- [x] Implement bounded configuration and pure math with finite-value guards and saturating arithmetic.
- [x] Run the focused tests and confirm zero failures.

### Task 2: Backward-Compatible Player and Monster Persistence

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/level/LevelConfig.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/progress/PlayerProgress.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/core/MonsterState.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/LevelConfigTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/PlayerProgressTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/MonsterStateTest.java`

**Interfaces:**
- Consumes: `AdventureXpConfig` from Task 1.
- Produces: `LevelConfig.adventureXp()`, bounded zone/distance/first-kill/familiarity records, and persisted monster base threat XP.

- [ ] Write failing migration and round-trip tests using literal legacy NBT fixtures.
- [ ] Run the three focused test classes and verify failures occur because the new state is absent.
- [ ] Add schema-default loading, defensive copies, hard limits, deterministic oldest-entry eviction, and private snapshot handling.
- [ ] Run focused tests and the full `:common:test` suite.

### Task 3: Threat-Based Monster Assignment and Rewards

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/server/MonsterThreat.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/MonsterService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/ZoneService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/event/RotasEvents.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/MonsterThreatTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/ZoneDefTest.java`

**Interfaces:**
- Consumes: zone region metadata, adventure XP math/config, existing `MonsterDefinitions`, live attributes, and `MonsterXpService` threat evidence.
- Produces: finite `MonsterThreat.score(...)`, location-first deterministic level selection, and persisted base threat XP used at death.

- [ ] Write failing tests proving stronger attributes/tier/affixes/boss status never reduce threat, extreme values remain finite, UUID variance is stable, and selected levels stay inside the location band.
- [ ] Run focused tests and confirm the intended failures.
- [ ] Implement threat calculation at assignment/relevel and remove nearest-player control from default location-based levels.
- [ ] Run focused tests and full common tests.

### Task 4: Contribution, Familiarity, Exploration, and Atomic XP

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/server/AdventureProgressService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/ProgressService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/MonsterService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/event/RotasEvents.java`
- Modify loader event bridges only where an existing hurt/death/movement hook must forward evidence.
- Test: `common/src/test/java/net/schwarz/rotasutils/core/AdventureProgressTest.java`

**Interfaces:**
- Consumes: `AdventureXpMath`, `PlayerProgress` bounded records, confirmed monster death, existing receipts/transactions.
- Produces: one atomic award decision per eligible participant plus first-kill, zone-discovery, and distance-milestone awards.

- [ ] Write failing tests for trivial-kill taper, dangerous-kill bonus, repetition decay, first-kill protection, contribution eligibility, party budget, duplicate receipts, milestone uniqueness, and restart round-trip.
- [ ] Run the focused class and confirm failures.
- [ ] Implement bounded contribution recording and player movement sampling after meaningful block movement at a fixed interval.
- [ ] Commit familiarity/discovery changes and XP through the existing server transaction/receipt path.
- [ ] Run focused and full common tests.

### Task 5: Admin and Player Feedback

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/ZoneEditScreen.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/LevelManagerScreen.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/screen/player/MainMenuScreen.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/RotasClient.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/ServerActions.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/command/ZoneCommands.java`
- Modify: `common/src/main/resources/assets/rotasutils/lang/en_us.json`
- Modify: `common/src/main/resources/assets/rotasutils/lang/th_th.json`
- Test: existing layout, protocol, localization parity, and zone tests.

**Interfaces:**
- Consumes: synchronized zone/adventure configuration and private-safe player summaries.
- Produces: metadata editing, projected region/threat/XP diagnostics, action-bar danger feedback, and player adventure summary.

- [ ] Write failing protocol/layout/localization tests for every new editable field and summary row.
- [ ] Run focused tests and verify missing controls/keys cause the failures.
- [ ] Add bounded server actions and localized controls using the existing screen and permission patterns.
- [ ] Run focused tests at the existing small/large layout fixtures and language parity suite.

### Task 6: Documentation and Dedicated-Server Regression Harness

**Files:**
- Modify: `docs/rpg-zones.md`
- Modify: `docs/rpg-progression.md`
- Modify: `docs/rpg-platform-status.md`
- Modify: `scripts/smoke/java/net/schwarz/rotasutils/smoke/MonsterSmoke.java` or the current equivalent discovered in the smoke source set.
- Modify: `scripts/rpg-console-smoke.py`

**Interfaces:**
- Consumes: all production behavior from Tasks 1-5.
- Produces: restart-persistent, console-visible evidence for distance levels, threat XP, repetition, contribution, discoveries, and migration.

- [ ] Add smoke assertions that fail against the old behavior and use isolated test players/entities.
- [ ] Run `python scripts/rpg-console-smoke.py` and confirm the expected assertion failure.
- [ ] Update documentation with exact formulas, defaults, privacy, commands, and admin workflow.
- [ ] Run the smoke through initial server start and restart and retain its output under `build/`.

### Task 7: Full Verification and Live Client Gate

**Files:**
- Update test/build evidence only; do not change behavior unless a failing gate identifies a defect and receives its own regression test.

**Interfaces:**
- Consumes: complete implementation.
- Produces: build artifacts and an explicit verified/unverified matrix.

- [ ] Run `gradlew :common:test :forge:build :fabric:build` and inspect exit code and JUnit failure counts.
- [ ] Run the dedicated Forge smoke twice through its restart workflow and inspect its success markers.
- [ ] Launch the Forge dev client and verify mod initialization and both client mixins load without RotasUtils errors.
- [ ] In a disposable world, verify Zone Wand feedback, sphere/box/polygon outlines, zone danger fields, monster nameplates, XP messages, first-kill/discovery feedback, and player summary at GUI scales 2 and 3.
- [ ] Report Fabric runtime and authenticated multiplayer as unverified unless they were actually executed.

## Execution Notes

This workspace has no Git repository, so the plan's normal per-task commits cannot be created.
Before each task, make a targeted backup or preserve a reversible patch; never overwrite the installed
PROJECT-RC JAR. Update the checkboxes as each test-first batch completes.
