# Monster Progression System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a persistent level 1-999 monster framework with natural level 1-100 generation, stable ranks/types, centralized scaling, custom monsters, contextual nameplates, affixes, and boss presentation.

**Architecture:** Evolve the current server-authoritative monster engine and migrate its stored schema. Keep gameplay calculation in common/server services and send only bounded presentation state to client renderers.

**Tech Stack:** Java 17, Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.22, Fabric, JUnit 5, NBT, existing Rotas kernel/config history.

**Spec:** `docs/superpowers/specs/2026-09-13-monster-progression-design.md`

## Global Constraints

- Absolute monster level is 1-999; natural generated level is 1-100.
- Preserve current monster assignments and authored definitions through migration.
- No per-tick global entity scans or full-state packet spam.
- Server owns gameplay state; client classes stay out of server paths.
- UI/file edits use the same validator and Review & Apply flow.
- PROJECT-RC deployment is excluded.

---

### Task 1: Schema 2 and Level Invariants

**Files:** `MonsterState.java`, `MonsterAssignmentSource.java`, `MonsterRank.java`, `MonsterType.java`, `MobLevelConfig.java`, state/config tests.

**Produces:** schema-2 persisted identity and `MonsterLevels.validateNatural/validateConfigured`.

- [ ] Write failing tests for 999 absolute cap, 100 natural cap, schema-1 migration, and neutral type/rank defaults.
- [ ] Run focused tests and confirm failures are caused by missing schema-2 behavior.
- [ ] Add enums, validation helpers, schema-2 save/load, and schema-1 migration.
- [ ] Run focused and full common tests.

### Task 2: Normal Generation and Central Scaling

**Files:** `MonsterLevelGenerator.java`, `MonsterScalingService.java`, `MonsterDefinitions.java`, `MonsterService.java`, `MobLevelConfig.java`, scaling tests.

**Produces:** one-shot normal level generation and ordered base/curve/rank/profile/affix calculation.

- [ ] Write failing deterministic generation and bounded curve tests.
- [ ] Add independently enabled difficulty/dimension/biome/distance/region factors.
- [ ] Add linear, soft-exponential, and bounded custom-curve evaluation.
- [ ] Route all seven scalable attributes through `MonsterScalingService`.
- [ ] Verify reapplication uses stable modifier IDs without stacking.

### Task 3: Configured Monsters and Validation

**Files:** `MonsterDefinitions.java`, `MonsterCatalog.java`, `MonsterContentValidator.java`, guided schema/editor resources, parser tests.

**Produces:** fixed/ranged level 1-999 custom definitions with type/rank/presentation/spawn rules.

- [ ] Write failing parser and invalid-reference tests.
- [ ] Extend profiles without breaking old JSON fixtures.
- [ ] Validate entity, rank, type, curves, multipliers, affixes, and spawn selectors.
- [ ] Add admin templates for elite, miniboss, boss, and world boss.
- [ ] Verify stale draft rejection and atomic apply remain unchanged.

### Task 4: Presentation Synchronization and Contextual Nameplates

**Files:** `MonsterPresentation.java`, `MonsterSync.java`, loader client bridges, `MonsterNameplateRenderer.java`, HUD config/editor, renderer math tests.

**Produces:** bounded change-only snapshots and compact/detailed client presentation.

- [ ] Write failing tests for danger bands, HP formatting, visibility/fade state, and packet bounds.
- [ ] Add target/damage tracking without entity-wide scans.
- [ ] Render distant name+level and detailed targeted/damaged health/type/rank/affixes.
- [ ] Add distance, wall visibility, fade, and detail configuration.
- [ ] Verify GUI scales, long names, and no server-side client classloading.

### Task 5: Affix Runtime and Damage Feedback

**Files:** existing affix hooks, `MonsterDamageFeedback.java`, client pool/renderer, affix tests.

**Produces:** typed built-in affix templates and a capped reusable damage-number pool.

- [ ] Write failing tests for incompatibility, deterministic rolls, cooldown persistence, and pool eviction.
- [ ] Add configured armored/berserker/swift/regenerator/vampiric/explosive/arcane/colossal/cursed/commander templates.
- [ ] Emit bounded damage presentation events only to tracking/relevant players.
- [ ] Verify no entity allocation per number and no double application.

### Task 6: Miniboss, Boss, and World-Boss HUD

**Files:** `BossService.java`, `MonsterBossHud.java`, boss sync/config, phase tests.

**Produces:** rank-aware boss HUD backed by existing persisted phase state and clean phase-change hooks.

- [ ] Write failing phase boundary, heal rollback, HUD selection, and contributor tests.
- [ ] Expose phase transitions without embedding abilities in level code.
- [ ] Render restrained miniboss/boss/world-boss variants with compact HP.
- [ ] Verify multiple bosses, dimension changes, logout, death, and unload cleanup.

### Task 7: Diagnostics, Migration, and Runtime Gates

**Files:** monster commands, validation reports, migration tests, smoke harness, documentation.

**Produces:** inspect/validate diagnostics and evidence for save/restart and loader builds.

- [ ] Add command output for source, rank, type, custom ID, curve, affixes, and effective attributes.
- [ ] Test legacy fixtures and corrupt/future data handling.
- [ ] Run `:common:test :forge:build :fabric:build`.
- [ ] Run isolated dedicated Forge initial/restart smoke.
- [ ] Run live Forge client presentation inspection; report Fabric/authenticated multiplayer gaps explicitly.
