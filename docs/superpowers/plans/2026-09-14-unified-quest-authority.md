# Unified Quest Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two independently mutable quest runtimes with one `QuestDef`/`ActiveQuest` authority while preserving kernel pack content, existing player progress, commands, UI operations, rewards, and save compatibility.

**Architecture:** Kernel quest JSON remains a validated content-pack input, and its non-serializable compiled conditions remain in `ContentRegistry.Snapshot`. A new adapter projects each enabled kernel quest into a canonical `QuestDef`; canonical progression lives only in `ActiveQuest` and `PlayerProgress`. `QuestKernelService` becomes a compatibility facade over `QuestService`, and old `rpg.q.*` variables become repeat-safe migration input rather than live state.

**Tech Stack:** Java 17, Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.22, Fabric, JUnit 5, Minecraft NBT, existing RotasUtils content kernel and networking.

**Spec:** `docs/superpowers/specs/2026-09-14-unified-quest-authority-design.md`

## Global Constraints

- The logical server owns all quest state and reward decisions; clients send intent and render snapshots only.
- Existing `QuestDef`, `ActiveQuest`, player completion history, board/NPC flows, and ordinary quest UI must remain compatible.
- Kernel quest IDs map to their full `ContentId.value()` string; collisions fail publication instead of overwriting content.
- Old `rpg.q.*` values are retained until canonical state is saved and a migration marker is recorded.
- Migration never grants rewards and malformed state is retained with bounded diagnostics.
- One gameplay event may traverse canonical quest progression exactly once.
- No broader monster, merchant, item, stat, or content-rule kernel behavior changes.
- No new dependency is introduced.
- The workspace currently has no `.git` repository. Each task ends in a build/test checkpoint instead of a commit; do not initialize Git as part of this work.

---

## File Map

- Create `common/src/main/java/net/schwarz/rotasutils/quest/QuestStage.java`: serializable canonical stage metadata and branch descriptors.
- Create `common/src/main/java/net/schwarz/rotasutils/server/KernelQuestAdapter.java`: kernel-to-canonical projection, origin lookup, condition/reward bridge, and collision validation.
- Create `common/src/main/java/net/schwarz/rotasutils/server/QuestProgressMigration.java`: repeat-safe migration from old kernel variables.
- Modify `common/src/main/java/net/schwarz/rotasutils/quest/QuestDef.java`: persist stable objective keys, stages, reset/bounty metadata, and kernel origin.
- Modify `common/src/main/java/net/schwarz/rotasutils/quest/objective/Objective.java`: persist a stable key and kernel event matching metadata.
- Modify `common/src/main/java/net/schwarz/rotasutils/progress/ActiveQuest.java`: persist current stage and keyed progress alongside legacy arrays.
- Modify `common/src/main/java/net/schwarz/rotasutils/server/ObjectiveEngine.java`: evaluate only the canonical active stage and normalize kernel facts once.
- Modify `common/src/main/java/net/schwarz/rotasutils/server/QuestService.java`: canonical availability, stage transitions, claim receipts, reset windows, bounty limits, and reward bridge.
- Modify `common/src/main/java/net/schwarz/rotasutils/server/QuestKernelService.java`: delegate all reads/actions to canonical services.
- Modify `common/src/main/java/net/schwarz/rotasutils/server/RpgKernel.java`: publish projections and remove independent quest event mutation.
- Modify `common/src/main/java/net/schwarz/rotasutils/core/ContentRegistry.java`: expose source identity needed for collision checks without changing pack syntax.
- Modify `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`: reconcile kernel projections and expose migration diagnostics.
- Modify `common/src/main/java/net/schwarz/rotasutils/network/KernelUi.java`: snapshot canonical quest state through the compatibility facade.
- Modify `common/src/main/java/net/schwarz/rotasutils/command/QuestKernelCommands.java`: report delegated canonical results.
- Add focused tests under `common/src/test/java/net/schwarz/rotasutils/quest`, `core`, and `server`.

---

### Task 1: Canonical staged definition and progress serialization

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/quest/QuestStage.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/quest/QuestDef.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/quest/objective/Objective.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/progress/ActiveQuest.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/quest/QuestStageSerializationTest.java`

**Interfaces:**
- Produces `QuestStage(String key, String label, List<String> objectiveKeys, List<Branch> branches, String rewardId)` with NBT `save()`/`load(CompoundTag)`.
- Produces `QuestStage.Branch(int targetStage, String conditionId)`; an empty condition ID means the default branch.
- Produces `QuestDef.stages()`, `QuestDef.kernelOrigin()`, `QuestDef.resetPolicy()`, `QuestDef.bountyLimit()`, and matching setters.
- Produces `Objective.key()`, `setKey(String)`, `kernelEvent()`, and `setKernelEvent(String, Map<String,String>)`.
- Produces `ActiveQuest.stage()`, `setStage(int)`, `keyedProgress()`, `progress(String)`, `setProgress(String,int)`, `isComplete(String)`, and `setComplete(String,boolean)`.

- [ ] **Step 1: Write serialization regressions**

Add tests that construct a two-stage quest with keys `intro/talk` and `hunt/kill`, save/load it, and assert exact preservation. Also load a legacy `QuestDef` and `ActiveQuest` tag with no new fields and assert stage `0`, generated objective keys `o0`, `o1`, and unchanged array progress.

```java
@Test void stagedQuestRoundTripsWithoutLosingLegacyFields() {
    QuestDef quest = new QuestDef("rotas:quest/hunt");
    Objective talk = new Objective(ObjectiveType.TALK_NPC);
    talk.setKey("intro/talk");
    Objective kill = new Objective(ObjectiveType.KILL_MOB);
    kill.setKey("hunt/kill");
    quest.objectives().addAll(List.of(talk, kill));
    quest.stages().add(new QuestStage("intro", "Speak", List.of("intro/talk"),
            List.of(new QuestStage.Branch(1, "")), "rotas:reward/intro"));
    quest.stages().add(new QuestStage("hunt", "Hunt", List.of("hunt/kill"), List.of(), ""));
    quest.setKernelOrigin("rotas:quest/hunt");
    QuestDef loaded = QuestDef.load(quest.save());
    assertEquals("rotas:quest/hunt", loaded.kernelOrigin());
    assertEquals(List.of("intro/talk"), loaded.stages().get(0).objectiveKeys());
}

@Test void legacyActiveQuestLoadsAsStageZeroAndKeepsArrayProgress() {
    ActiveQuest original = new ActiveQuest("legacy", 1, 2);
    original.setProgress(1, 7);
    ActiveQuest loaded = ActiveQuest.load(original.save());
    assertEquals(0, loaded.stage());
    assertEquals(7, loaded.progress(1));
}
```

- [ ] **Step 2: Run the focused test and confirm red state**

Run: `./gradlew.bat :common:test --tests "net.schwarz.rotasutils.quest.QuestStageSerializationTest"`

Expected: compilation fails because `QuestStage` and the new accessors do not exist.

- [ ] **Step 3: Implement bounded, backward-compatible NBT**

Implement immutable copies in `QuestStage`. Validate keys with `[a-z0-9_./-]{1,96}`, stage count `1..16`, objective keys unique per quest, branch targets within the stage list, bounty limit `0..1_000_000`, and reward/origin IDs no longer than 200 characters. Save new fields under `stages`, `kernel_origin`, `reset_policy`, `bounty_limit`, `objective_key`, `kernel_event`, `kernel_match`, `stage`, `key_progress`, and `key_complete`. When absent, preserve current behavior and synthesize `o<index>` only in accessors, without rewriting saves during load.

- [ ] **Step 4: Run focused and existing serialization tests**

Run: `./gradlew.bat :common:test --tests "*QuestStageSerializationTest" --tests "*QuestObjectiveReliabilityTest"`

Expected: PASS, including legacy array-based objectives.

- [ ] **Step 5: Record checkpoint**

Append the exact command and result to `.rotas-workstate.md`; do not claim runtime compatibility yet.

---

### Task 2: Compile kernel quests into collision-safe canonical projections

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/server/KernelQuestAdapter.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/core/ContentRegistry.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/server/KernelQuestAdapterTest.java`

**Interfaces:**
- Produces `KernelQuestAdapter.Projection(QuestDef quest, QuestDefinitions.Quest source)`.
- Produces `Map<String, Projection> KernelQuestAdapter.project(ContentRegistry.Snapshot, Collection<QuestDef>)`.
- Produces `Projection KernelQuestAdapter.find(String questId)` on the live adapter owned by `RpgKernel`.
- Produces `RotasData.reconcileKernelQuests(Map<String, Projection>)`, which publishes projections without silently overwriting independently authored world quests.

- [ ] **Step 1: Write projection and collision tests**

Test a two-stage kernel quest and assert full ID preservation, objective keys `s0/o0` and `s1/o0`, ordered canonical objectives, mapped repeat policy, cooldown, stage metadata, and kernel event facts. Add a collision test where an existing `QuestDef("rotas:quest/hunt")` has empty `kernelOrigin`; expect a diagnostic containing `Quest ID collision: rotas:quest/hunt` and no mutation of the existing definition.

```java
assertEquals("rotas:quest/hunt", projection.quest().id());
assertEquals("rotas:quest/hunt", projection.quest().kernelOrigin());
assertEquals("s0/o0", projection.quest().objectives().get(0).key());
assertThrows(IllegalArgumentException.class,
        () -> KernelQuestAdapter.project(snapshot, List.of(worldAuthoredQuest)));
```

- [ ] **Step 2: Run the adapter test and confirm red state**

Run: `./gradlew.bat :common:test --tests "*KernelQuestAdapterTest"`

Expected: compilation fails because `KernelQuestAdapter` is absent.

- [ ] **Step 3: Implement pure projection**

Map reset values exactly: `NONE + !repeatable -> NEVER`, `NONE + repeatable -> UNLIMITED`, `DAILY -> DAILY`, `WEEKLY -> WEEKLY`, and `COOLDOWN -> COOLDOWN`. Use `ObjectiveType.CUSTOM` for kernel events and store the exact event ID plus exact fact map on the objective. Flatten stage objectives into the canonical objective list while retaining stage membership by stable key. Keep compiled conditions and reward IDs in `Projection.source()`; do not serialize closures.

`RotasData.reconcileKernelQuests` must update only quests whose `kernelOrigin` equals the projection ID. It must not remove ordinary quests or overwrite an ID with another origin. Reconciliation marks data dirty only when serialized canonical projection content actually changes.

- [ ] **Step 4: Run adapter, registry, and definition tests**

Run: `./gradlew.bat :common:test --tests "*KernelQuestAdapterTest" --tests "*QuestDefinitionsTest"`

Expected: PASS with missing referenced rewards still rejected by `ContentRegistry`.

- [ ] **Step 5: Record checkpoint**

Append the focused test result and list of published projection IDs to `.rotas-workstate.md`.

---

### Task 3: Repeat-safe migration from `rpg.q.*` variables

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/server/QuestProgressMigration.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/progress/PlayerProgress.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/server/QuestProgressMigrationTest.java`

**Interfaces:**
- Produces `QuestProgressMigration.Result(boolean changed, boolean complete, List<String> diagnostics)`.
- Produces `Result QuestProgressMigration.migrate(PlayerProgress, Map<String, KernelQuestAdapter.Projection>, long nowSeconds)`.
- Produces `PlayerProgress.removeQuestVariables(Collection<String> keys)` with one dirty mark after validated removal.
- Migration marker format: `rpg.migration.quest.<16-char-id-hash>=<quest-version>`.

- [ ] **Step 1: Write migration regressions**

Cover active stage/progress, completed quest, pre-existing canonical state, malformed stage, unknown objective keys, and a second migration call. Assert migration creates no reward receipt, does not change XP/currency, and leaves malformed old keys intact.

```java
@Test void migrationIsRepeatSafeAndNeverRewards() {
    progress.questVariables().put(QuestDefinitions.key(id, "stage"), "1");
    progress.questVariables().put(QuestDefinitions.key(id, "p.o0"), "2");
    long xpBefore = progress.xp();
    var first = QuestProgressMigration.migrate(progress, projections, 1_700_000_000L);
    var second = QuestProgressMigration.migrate(progress, projections, 1_700_000_001L);
    assertTrue(first.changed());
    assertFalse(second.changed());
    assertEquals(xpBefore, progress.xp());
    assertNotNull(progress.active("rotas:quest/hunt"));
}
```

- [ ] **Step 2: Run migration test and confirm red state**

Run: `./gradlew.bat :common:test --tests "*QuestProgressMigrationTest"`

Expected: compilation fails because the migration service is absent.

- [ ] **Step 3: Implement validation-before-write migration**

Build the complete `ActiveQuest` in memory first. Reject invalid stage bounds, negative/non-numeric counts, objective counts above their configured requirement, and variables that cannot be mapped. If canonical state exists, record the marker but do not replace it. For a completed old quest, record canonical completion at the old `done` timestamp without invoking `QuestService.turnIn` or reward code. Remove only the exact validated old keys after the canonical record and marker exist.

Bound returned diagnostics to 64 entries and include player UUID, quest ID, and rejected key without dumping arbitrary values.

- [ ] **Step 4: Run migration and persistence tests**

Run: `./gradlew.bat :common:test --tests "*QuestProgressMigrationTest" --tests "*QuestStageSerializationTest"`

Expected: PASS; a save/load/save cycle retains canonical migrated state.

- [ ] **Step 5: Record checkpoint**

Append migration cases and results to `.rotas-workstate.md`. Old malformed keys must be explicitly listed as retained, not cleaned.

---

### Task 4: Make `QuestService` the sole transition authority

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/QuestService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/ObjectiveEngine.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/QuestInventory.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/server/QuestTransition.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/server/UnifiedQuestTransitionTest.java`

**Interfaces:**
- Produces `QuestTransition` pure helpers: `activeObjectiveIndexes(QuestDef, ActiveQuest)`, `nextStage(QuestDef, ActiveQuest, KernelQuestAdapter.Projection, KernelContext)`, and `resetWindow(QuestDef,long)`.
- Adds `QuestService.acceptKernel(ServerPlayer, RotasData, String)` and `QuestService.claim(ServerPlayer, RotasData, String)` as server-validated wrappers used only by compatibility callers.
- Adds package-private `QuestService.advanceStage(...)` and receipt keys `quest|stage|<id>|<stage>|<completion>` and `quest|final|<id>|<window>`.

- [ ] **Step 1: Write pure transition and double-event tests**

Test that only current-stage objective indices are active, a matching branch wins, a missing condition uses the sequential next stage, a final stage sets `turnInReady`, and replaying the same occurrence does not advance or reward twice. Test reset windows and bounty reservation release on failed reward preparation.

```java
assertEquals(List.of(0), QuestTransition.activeObjectiveIndexes(quest, active));
active.setStage(1);
assertEquals(List.of(1), QuestTransition.activeObjectiveIndexes(quest, active));
assertEquals(2, QuestTransition.nextStage(quest, active, projection, context));
```

- [ ] **Step 2: Run unified transition tests and confirm red state**

Run: `./gradlew.bat :common:test --tests "*UnifiedQuestTransitionTest"`

Expected: compilation fails because `QuestTransition` and canonical kernel entry points are absent.

- [ ] **Step 3: Implement current-stage filtering and keyed progress**

Keep existing indexed objective behavior for one-stage quests. For staged quests, `ObjectiveEngine.handle` walks only `QuestTransition.activeObjectiveIndexes`. It updates both stable keyed state and legacy arrays so current clients remain readable during the transition. `QuestInventory` likewise sees only active-stage deliver/collect objectives.

For `ObjectiveType.CUSTOM`, match `Objective.kernelEvent()` and every stored fact exactly. Pass kernel facts in a new immutable field on `QuestEvent`; do not read global mutable event state.

- [ ] **Step 4: Implement canonical stage and final reward transitions**

Move stage selection, stage reward receipt, completion readiness, reset/cooldown checks, bounty reservation, final receipt, season rank points, audit, dirty marking, and sync under `QuestService`. Existing non-kernel turn-in behavior must remain byte-for-byte equivalent where possible. Never call reward code from migration.

If a kernel reward grant fails after its receipt/state write, retain the receipt plus a bounded failure record for administrative retry; do not restore an active stage that could replay the reward.

- [ ] **Step 5: Run all quest-focused common tests**

Run: `./gradlew.bat :common:test --tests "*Quest*Test" --tests "*DialogueObjectiveTest"`

Expected: PASS, including established collect/deliver reliability.

- [ ] **Step 6: Record checkpoint**

Append the test count and exact result to `.rotas-workstate.md`.

---

### Task 5: Convert `QuestKernelService` into a canonical facade

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/QuestKernelService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/RpgKernel.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/server/QuestKernelFacadeTest.java`

**Interfaces:**
- Keeps public methods `available`, `accept`, `abandon`, `claim`, `active`, `stage`, `progress`, `completedAt`, and `diagnostics` for compatibility.
- Removes `QuestKernelService.handle` from every production call path.
- Adds `RpgKernel.questAdapter()` returning the live `KernelQuestAdapter`.

- [ ] **Step 1: Write facade consistency tests**

Use a test seam around canonical operations to assert `accept`, `abandon`, and `claim` delegate once and map results correctly. Assert successful abandonment returns `Result.ABANDONED`; add `ABANDONED` to the enum and UI success mapping. Assert facade reads canonical `ActiveQuest`, keyed progress, and completion history even when old `rpg.q.*` keys disagree.

- [ ] **Step 2: Run facade test and confirm red state**

Run: `./gradlew.bat :common:test --tests "*QuestKernelFacadeTest"`

Expected: FAIL because the current service reads and writes `questVariables` and successful abandon returns `NOT_ACTIVE`.

- [ ] **Step 3: Replace independent mutations with delegation**

Construct the facade with `RpgKernel` and a small package-private canonical operations interface so tests do not need a full `MinecraftServer`. Production operations call `QuestService`. Read methods resolve only kernel-origin canonical quests from the adapter. Delete the independent transaction/reward/counter logic after tests prove delegation.

- [ ] **Step 4: Reconcile and migrate during kernel publication/login**

After `RpgKernel.apply` accepts a prepared snapshot, project and reconcile definitions before syncing players. Migrate every loaded player record, then sync online players. On login, run migration for that player before `KernelUi.sync`. A failed migration logs bounded diagnostics but does not prevent login or remove old variables.

- [ ] **Step 5: Remove the second event loop**

Delete the `kernel.quests().handle(...)` call from `RpgKernel.emit`. Keep event-bus content rules unchanged. Ensure `RpgKernel.objectiveEvent` forwards normalized facts to the canonical `ObjectiveEngine` adapter only when the source event did not already originate there; use an explicit origin flag, not recursion detection.

- [ ] **Step 6: Run facade and event regression tests**

Run: `./gradlew.bat :common:test --tests "*QuestKernelFacadeTest" --tests "*UnifiedQuestTransitionTest" --tests "*QuestObjectiveReliabilityTest"`

Expected: PASS and one occurrence produces one objective increment.

- [ ] **Step 7: Record checkpoint**

Run `rg -n "quests\(\)\.handle|QuestKernelService.*handle" common/src/main/java` and record zero production event-path matches.

---

### Task 6: Unify kernel UI and command state

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/KernelUi.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/command/QuestKernelCommands.java`
- Modify: `common/src/main/resources/assets/rotasutils/lang/en_us.json`
- Modify: `common/src/main/resources/assets/rotasutils/lang/th_th.json`
- Modify: `common/src/test/java/net/schwarz/rotasutils/core/KernelUiCatalogTest.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/core/QuestAuthoritySurfaceTest.java`

**Interfaces:**
- Kernel UI packet operation names remain `accept`, `abandon`, and `claim`.
- Kernel UI quest entries remain compatible but derive `stage`, `available`, `claimable`, `completed`, and objective progress from canonical state.
- `QuestAuthoritySurfaceTest` statically guards against production reads/writes of `QuestDefinitions.key(...stage|done|p.*)` outside `QuestProgressMigration`.

- [ ] **Step 1: Extend UI snapshot tests**

Assert one canonical quest produces identical active/completed state when read through the player quest snapshot and `KernelUi`. Assert `ABANDONED` is successful and localized in both languages. Preserve existing catalog schema fields so current screens continue to render.

- [ ] **Step 2: Add the architectural guard test**

Walk `common/src/main/java` with `Files.walk`, ignore `QuestProgressMigration.java` and test sources, and fail if source contains `QuestDefinitions.key(` combined with `"stage"`, `"done"`, or `"p."`. Also fail on `.quests().handle(`. This prevents the second store/event loop from returning unnoticed.

- [ ] **Step 3: Run UI/authority tests and confirm red state**

Run: `./gradlew.bat :common:test --tests "*KernelUiCatalogTest" --tests "*QuestAuthoritySurfaceTest"`

Expected: FAIL until remaining old-state access and event mutation are removed.

- [ ] **Step 4: Update UI, command mapping, and translations**

Map facade results without duplicating gameplay checks. Retain packet IDs and command syntax. Change user-visible wording from “kernel quests” to “quests” where it describes player state; keep “kernel” only in administrator diagnostics. Preserve English/Thai key parity.

- [ ] **Step 5: Run UI, localization, and authority gates**

Run: `./gradlew.bat :common:test --tests "*KernelUiCatalogTest" --tests "*QuestAuthoritySurfaceTest" --tests "*Localization*Test"`

Expected: PASS with equal English/Thai key sets.

- [ ] **Step 6: Record checkpoint**

Append the static authority guard result and translation counts to `.rotas-workstate.md`.

---

### Task 7: Full build and isolated server migration verification

**Files:**
- Modify: `docs/quest-objective-reliability.md`
- Modify: `docs/rpg-quests-economy.md`
- Modify: `docs/rpg-platform-status.md`
- Modify: `.rotas-workstate.md`
- Add only if absent: `scripts/quest-authority-smoke.ps1`

**Interfaces:**
- The smoke script accepts explicit `-ServerDirectory`, `-WorldName`, and `-TimeoutSeconds`; it must reject a target outside the supplied server directory and never touch a normal player world.

- [ ] **Step 1: Run the complete common suite**

Run: `./gradlew.bat :common:test`

Expected: BUILD SUCCESSFUL with zero failures and errors. Record the actual test count from XML, not an estimate.

- [ ] **Step 2: Build both loaders**

Run: `./gradlew.bat :forge:build :fabric:build`

Expected: BUILD SUCCESSFUL and both JARs present under their module `build/libs` directories.

- [ ] **Step 3: Inspect packaged class/resource boundaries**

Confirm the common code has no new client imports, language files are packaged, and both artifacts contain `QuestProgressMigration`, `KernelQuestAdapter`, and updated quest model classes. Compare SHA-256 values only for reporting; Forge and Fabric artifacts are expected to differ.

- [ ] **Step 4: Prepare an isolated Forge server world**

Use a dedicated temporary directory created beneath `build/quest-authority-smoke`, copy only the newly built Forge artifact and required test dependencies, accept the EULA only in that disposable directory, and start with Java 17. Seed one ordinary quest plus one kernel-origin quest and an old `rpg.q.*` active-state fixture through the existing supported admin/config workflow.

- [ ] **Step 5: Verify server behavior and restart persistence**

Verify from logs/commands that the migrated quest is active once, its old validated keys are no longer live state, one event increments once, abandon reports success, completion/claim rewards once, and a second claim is rejected. Stop cleanly, restart the same disposable world, and verify canonical progress/completion persists without a second migration or reward.

- [ ] **Step 6: Update architecture documentation**

Document `QuestDef`/`ActiveQuest` as the sole executable authority, kernel quests as compiled projections, the migration marker/key retention rules, exact operator diagnostics, and the compatibility window. Remove statements that say the two runtimes intentionally run beside each other.

- [ ] **Step 7: Record server evidence**

Append exact Java version, Forge version, artifact path/SHA, commands, world path, relevant log lines, and shutdown/restart outcome to `.rotas-workstate.md`. Clearly label client UI as unverified until Task 8.

---

### Task 8: Live client end-to-end consistency check

**Files:**
- Modify only if a defect is found: the smallest relevant screen/network file
- Modify: `.rotas-workstate.md`

**Interfaces:**
- No new interface is planned; this task validates the production paths.

- [ ] **Step 1: Start the Forge client with the built code**

Run: `./gradlew.bat :forge:runClient`

Expected: main menu renders and the integrated server opens without quest migration exceptions.

- [ ] **Step 2: Exercise one shared quest through every surface**

In a disposable world, expose the same kernel-origin canonical quest on a board and NPC. Accept it once, then inspect board browser, NPC conversation, HUD tracker, player quest detail, `/rotas quests`, and kernel quest UI. Every surface must show the same accepted state and progress.

- [ ] **Step 3: Verify mutation consistency**

Trigger one objective event and confirm one increment everywhere. Abandon and reaccept through different compatible surfaces. Complete and claim once, then confirm every surface reports completion/cooldown and a replayed packet/command cannot reward again.

- [ ] **Step 4: Verify GUI and language safety**

Open the relevant screens at GUI scales 2 and 3 in English and Thai. Check that full namespaced IDs or revised labels do not overflow and that no separate stale kernel quest entry appears.

- [ ] **Step 5: Re-run build gates after any runtime fix**

If this task changes code, rerun `./gradlew.bat :common:test :forge:build :fabric:build`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Final evidence checkpoint**

Record observed UI surfaces, GUI scales, language, save/restart result, reward count, remaining compatibility data, and any untested multiplayer edge in `.rotas-workstate.md`. Completion may be claimed only when the static authority guard, common tests, both loader builds, isolated server restart, and live client consistency check all pass.
