# Housing UI Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let authorized administrators create and fully configure rental houses and global housing rules through themed RotasUtils screens.

**Architecture:** Extend the existing server-authoritative housing subsystem with revisioned administrative snapshots and bounded mutation actions. Add focused presentation models and `RotasScreen` interfaces that reuse the shared cream, copper, parchment, typography, controls, sounds, and opening transition while keeping monster zones untouched.

**Tech Stack:** Java 17 target, Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.22, Fabric Loader 0.19.3, JUnit 5, Minecraft NBT/networking, existing RotasUtils `Ui`/`PixelUi` components.

**Spec:** `docs/superpowers/specs/2026-09-15-house-ui-config-design.md`

## Global Constraints

- Housing must remain separate from `ZoneDef`, `ZoneService`, and the monster Zone Wand.
- The server owns permissions, House Wand selections, validation, persistence, revisions, audit records, and index rebuilds.
- Clients hold drafts and read-only snapshots; they never commit definitions, bounds, prices, or tiers locally.
- Occupied houses cannot be disabled or removed.
- Failed mutations are atomic and retain both persisted state and the client's typed draft.
- New screens use `RotasScreen`, `Ui`, `PixelUi`, `RotasButton`, existing sounds, and the shared opening transition.
- English and Thai keys ship together and remain readable at GUI scales 2 and 3.
- Existing rental defaults and monster-zone behavior do not change.
- This workspace currently has no Git metadata. Replace commit steps with a changed-file checkpoint unless Git becomes available.

---

### Task 1: Revisioned Housing Configuration

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/house/HouseConfig.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseDomainTest.java`

**Interfaces:**
- Consumes: existing `HouseConfig` values and `RotasData.houseConfig()` persistence.
- Produces: `HouseConfig(..., long revision)`, `HouseConfig.withRevision(long)`, and persisted configuration revisions that default to `0` for old saves.

- [ ] **Step 1: Write failing revision tests**

Add tests proving a nonzero revision survives `save()`/`load()`, old tags without `revision` load revision `0`, and negative revisions are rejected.

```java
HouseConfig revised = defaults.withRevision(7L);
assertEquals(7L, HouseConfig.load(revised.save()).revision());
assertEquals(0L, HouseConfig.load(oldTagWithoutRevision).revision());
assertThrows(IllegalArgumentException.class, () -> defaults.withRevision(-1L));
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `.\gradlew.bat :common:test --tests net.schwarz.rotasutils.house.HouseDomainTest --console=plain`

Expected: compilation fails because `withRevision`/`revision` do not exist.

- [ ] **Step 3: Add the minimal revision model and codec**

Add `long revision` as the final record component, validate it as non-negative, write `revision` to NBT, read missing values as zero, and keep `defaults()` at revision zero. Add `withRevision` without changing any economic value.

- [ ] **Step 4: Preserve revisions through `RotasData`**

Confirm every config replacement stores the complete revised object and calls `setDirty()`. Do not increment revisions in persistence code; authoritative mutation code in Task 3 owns increments.

- [ ] **Step 5: Run focused and full common tests**

Run: `.\gradlew.bat :common:test --tests net.schwarz.rotasutils.house.HouseDomainTest --console=plain`

Then: `.\gradlew.bat :common:test --console=plain`

- [ ] **Step 6: Record a checkpoint**

Record changed files and test counts. If Git becomes available, commit as `feat(housing): revision house configuration`.

---

### Task 2: Pure Administrative Validation and Time Fields

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseAdminValidator.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseTimeFields.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseAdminValidatorTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/client/screen/admin/HouseTimeFieldsTest.java`

**Interfaces:**
- Consumes: `HouseDefinition`, `HouseConfig`, `HouseTier`, current house definitions, and proposed changes.
- Produces: `HouseAdminValidator.validateDefinition(...)`, `validateConfig(...)`, `canDisableOrRemove(...)`, and `HouseTimeFields.toMillis(days,hours,minutes)` with field-specific error strings.

- [ ] **Step 1: Write failing definition/config validation tests**

Cover invalid IDs, blank/long names, unknown tiers, overlapping bounds, occupied disable/remove, duplicate tier IDs, removal of referenced tiers, empty tier maps, invalid currency IDs, negative prices, reminder greater than or equal to the payment interval, and arithmetic overflow.

```java
assertEquals("House is occupied", HouseAdminValidator.canDisableOrRemove(activeTenancy));
assertTrue(HouseAdminValidator.validateConfig(candidate, houses).stream()
        .anyMatch(error -> error.field().equals("tiers.starter")));
```

- [ ] **Step 2: Run validator tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminValidatorTest' --console=plain`

Expected: compilation fails because `HouseAdminValidator` is missing.

- [ ] **Step 3: Implement pure validation**

Return immutable `Error(field, message)` values. Reuse `HouseDefinition`/`HouseConfig` constructors and `HouseRegistry.overlaps` where they already express the rule. Do not duplicate monster-zone validation or mutate inputs.

- [ ] **Step 4: Write failing time conversion tests**

```java
assertEquals(259_200_000L, HouseTimeFields.toMillis("3", "0", "0").millis());
assertFalse(HouseTimeFields.toMillis("999999999999", "0", "0").valid());
assertFalse(HouseTimeFields.toMillis("1", "-1", "0").valid());
```

- [ ] **Step 5: Run time tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseTimeFieldsTest' --console=plain`

Expected: compilation fails because `HouseTimeFields` is missing.

- [ ] **Step 6: Implement checked conversion**

Parse non-negative integral strings, use `Math.multiplyExact`/`Math.addExact`, return the offending field on failure, and add `fromMillis(long)` for pre-filling days/hours/minutes without losing remainders.

- [ ] **Step 7: Run both focused suites**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminValidatorTest' --tests '*HouseTimeFieldsTest' --console=plain`

- [ ] **Step 8: Record a checkpoint**

If Git becomes available, commit as `feat(housing): validate admin drafts and time fields`.

---

### Task 3: Server-Authoritative Housing Admin Actions

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseAdminService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/ServerActions.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/RotasNetwork.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseAdminServiceTest.java`

**Interfaces:**
- Consumes: administrator UUID/permission, current `RotasData`, sender-held House Wand selection, bounded request payload, and expected revisions.
- Produces: `create`, `edit`, `replaceBounds`, `remove`, and `saveConfig` operations returning `Action(success, message)`; mutation action names `house_create`, `house_edit`, `house_replace_bounds`, `house_remove`, and `house_save_config`.

- [ ] **Step 1: Write failing service tests**

Use real housing domain objects and a small fake data port. Cover permission denial, server-held selection use, successful selection clearing, invalid/overlapping selection retention, stale definition/config revisions, occupied disable/remove rejection, tier-reference rejection, atomic settings failure, revision increment, and audit emission.

```java
HouseAdminService.Action action = service.edit(actor, requestWithRevision(3L));
assertFalse(action.success());
assertEquals(original, store.house("cottage"));
```

- [ ] **Step 2: Run service tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminServiceTest' --console=plain`

Expected: compilation fails because `HouseAdminService` is missing.

- [ ] **Step 3: Implement the service boundary**

Keep request decoding separate from mutation. Define a narrow `Store` interface for current definitions, tenancies, config, wand selection, persistence, audit, and resync hooks. Build validated replacements first, then commit once. Increment definition/config revision exactly once on success.

- [ ] **Step 4: Add bounded server action decoding**

In `ServerActions`, reject unexpected tag types, strings beyond domain limits, tier lists beyond 64 entries, and missing required revisions before calling `HouseAdminService`. Recheck admin permission for every action even if the control is hidden client-side.

- [ ] **Step 5: Synchronize after successful mutations**

Mark `RotasData` dirty, rebuild affected housing indexes or billing schedules, append the audit entry, call the existing content synchronization path, and send specific feedback. On failure, send feedback only.

- [ ] **Step 6: Run focused tests and common tests**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminServiceTest' --console=plain`

Then: `.\gradlew.bat :common:test --console=plain`

- [ ] **Step 7: Record a checkpoint**

If Git becomes available, commit as `feat(housing): add authoritative admin actions`.

---

### Task 4: Admin-Only Housing Snapshots and Client State

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/ClientHouseAdminState.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/RotasNetwork.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/ClientState.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseAdminSnapshotTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/core/ClientResetTest.java`

**Interfaces:**
- Consumes: authenticated player, admin flag, full house definitions/config, tenancy status summaries, and that player's House Wand selection.
- Produces: admin-only `house_admin` NBT containing revisioned definitions, config/tiers, statuses, and sanitized selection summary; `ClientState.houseAdmin()` returns an immutable snapshot or `null`.

- [ ] **Step 1: Write failing privacy/bounds tests**

Assert non-admin content contains no `house_admin`, admin snapshots contain no owner/member UUIDs, collections and strings are bounded, selection contains only the requesting administrator's dimension/coordinates/size, and malformed snapshots fail closed to an empty state.

- [ ] **Step 2: Run snapshot tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminSnapshotTest' --console=plain`

Expected: failure because the admin snapshot type is missing.

- [ ] **Step 3: Implement snapshot encoding and decoding**

Reuse domain NBT codecs for definitions/config after enforcing outer collection bounds. Represent status as the enum name and selection as a dedicated immutable nested record. Do not expose tenancy identities.

- [ ] **Step 4: Integrate and reset client state**

Apply `house_admin` only when the server marks the client admin. Clear it on content without admin data and in `ClientState.reset()` so switching servers cannot retain privileged values.

- [ ] **Step 5: Run snapshot and reset tests**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminSnapshotTest' --tests net.schwarz.rotasutils.core.ClientResetTest --console=plain`

- [ ] **Step 6: Record a checkpoint**

If Git becomes available, commit as `feat(housing): sync safe admin snapshots`.

---

### Task 5: Housing Presentation Models and Layout

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseAdminPresentation.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseAdminLayout.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/client/screen/admin/HouseAdminPresentationTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/client/screen/admin/HouseAdminLayoutTest.java`

**Interfaces:**
- Consumes: `ClientHouseAdminState`, screen width/height, search text, and selected house/tier.
- Produces: sorted/filterable overview rows, semantic status colors, field-ready editor drafts, validation summaries, and non-overlapping rectangles for overview/editor/settings at small and normal design canvases.

- [ ] **Step 1: Write failing presentation tests**

Assert sorting by enabled/status/name, case-insensitive ID/name search, exact AVAILABLE/ACTIVE/OVERDUE/BOUGHT_OUT/DISABLED tags, selection summaries, and clear empty-state guidance.

- [ ] **Step 2: Run presentation tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminPresentationTest' --console=plain`

- [ ] **Step 3: Implement the presentation model**

Return immutable row and draft records. Use semantic `Ui` color constants through a small screen-layer mapping; keep the domain/presentation model free of Minecraft rendering calls where practical.

- [ ] **Step 4: Write failing layout tests**

Test the effective canvases used by GUI scales 2 and 3. Assert positive field/list heights, footer visibility, non-overlapping action/sidebar/content rectangles, and scroll activation for 64 tiers.

- [ ] **Step 5: Run layout tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminLayoutTest' --console=plain`

- [ ] **Step 6: Implement calculated layout**

Use `Ui.PAD`, `Ui.GAP`, bounded columns, and explicit footer/content rectangles. Avoid coordinates duplicated between rendering and hit testing.

- [ ] **Step 7: Run both suites**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminPresentationTest' --tests '*HouseAdminLayoutTest' --console=plain`

- [ ] **Step 8: Record a checkpoint**

If Git becomes available, commit as `feat(housing): model themed admin layouts`.

---

### Task 6: Themed Housing Overview, Create, and Edit Screens

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseManagerScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseCreateScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseEditScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseRemoveConfirmScreen.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/AdminMenuScreen.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/client/screen/admin/HouseScreenRoutingTest.java`

**Interfaces:**
- Consumes: Task 4 snapshots, Task 5 presentation/layout models, and Task 3 action names.
- Produces: `AdminMenuScreen.Section.HOUSES`, searchable overview, create/edit forms, themed confirmation, and bounded NBT requests containing typed values plus expected revisions.

- [ ] **Step 1: Write failing routing/payload tests**

Test a pure routing/payload helper rather than source text. Assert overview actions route to wand/create/settings/edit, create packets omit coordinates, edit packets include the expected definition revision, replace-bounds packets omit coordinates, and remove requires a confirmed second screen action.

- [ ] **Step 2: Run routing tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseScreenRoutingTest' --console=plain`

- [ ] **Step 3: Add the Housing admin section**

Add one sidebar section using the same navigation button, list row, search, tag, heading, and empty-state helpers as other Admin screens. Include “Get House Wand,” “Create from selection,” and “Rental settings” actions.

- [ ] **Step 4: Implement the create screen**

Use labelled `EditBox` controls for ID/name, a tier picker using existing list controls, and read-only selection panels. Disable the primary submit button when no complete selection snapshot exists or local fields are invalid; the server remains authoritative.

- [ ] **Step 5: Implement the editor**

Preserve typed values across rebuild/resync. Render definition and tenancy status separately. Save sends name/tier/enabled/revision. Replace Bounds sends house ID/revision only. An occupied snapshot disables the off/remove controls with a visible reason.

- [ ] **Step 6: Implement themed removal confirmation**

Show house identity and the exact effect: definition and empty tenancy records are removed, world blocks/items remain. Confirm sends the house ID and expected revision; cancel returns to the editor without losing its draft.

- [ ] **Step 7: Apply shared visual language**

Use `RotasScreen`, `Ui.window`, `Ui.modernPanel`, `Ui.rowCard`, `Ui.tag`, `Ui.sectionHeading`, `Ui.readableComponent`, `RotasButton` styles, `Sfx`, and the inherited open transition. Add no independent palette or font.

- [ ] **Step 8: Run routing, presentation, and layout tests**

Run: `.\gradlew.bat :common:test --tests '*HouseScreenRoutingTest' --tests '*HouseAdminPresentationTest' --tests '*HouseAdminLayoutTest' --console=plain`

- [ ] **Step 9: Record a checkpoint**

If Git becomes available, commit as `feat(housing): add themed house administration screens`.

---

### Task 7: Themed Rental Settings and Tier Editor

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseSettingsScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseTierEditScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseConfigDraft.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/client/screen/admin/HouseConfigDraftTest.java`

**Interfaces:**
- Consumes: revisioned `HouseConfig`, Task 2 time conversion/validation, house tier references, and `house_save_config`.
- Produces: lossless local draft editing, tier add/edit/remove controls, review summary, and a bounded save payload with `base_revision`.

- [ ] **Step 1: Write failing draft tests**

Assert config-to-draft-to-payload round trips every value, invalid field text remains in the draft, tier edits preserve insertion order, duplicate/removal errors attach to the correct tier, and a failed/stale response does not discard typed values.

- [ ] **Step 2: Run draft tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseConfigDraftTest' --console=plain`

- [ ] **Step 3: Implement the draft model**

Store raw strings for editable numeric fields and immutable saved baselines for change summaries. `toPayload()` succeeds only after Task 2 conversion and validation and includes every setting, ordered tiers, and `base_revision`.

- [ ] **Step 4: Implement global settings screen**

Group Currency, Billing, Membership, and Tiers into clear sections. Use a scroll panel for tier rows and narrow displays. Show a concise changed-fields summary above Save; use primary styling only for Save.

- [ ] **Step 5: Implement tier editor**

Allow a stable tier ID on creation and editable deposit/maintenance prices. Existing tier IDs remain read-only to avoid silently breaking references. Remove returns to settings only after reference validation; no server request occurs until global Save.

- [ ] **Step 6: Preserve drafts through child screens and resync**

Pass one `HouseConfigDraft` between settings and tier editors. On successful server synchronization, replace the baseline and rebuild. On failure or stale revision, keep raw inputs and show server feedback.

- [ ] **Step 7: Run draft, time, and validator tests**

Run: `.\gradlew.bat :common:test --tests '*HouseConfigDraftTest' --tests '*HouseTimeFieldsTest' --tests '*HouseAdminValidatorTest' --console=plain`

- [ ] **Step 8: Record a checkpoint**

If Git becomes available, commit as `feat(housing): add themed rental settings UI`.

---

### Task 8: Localization, Documentation, and Final Verification

**Files:**
- Modify: `common/src/main/resources/assets/rotasutils/lang/en_us.json`
- Modify: `common/src/main/resources/assets/rotasutils/lang/th_th.json`
- Modify: `docs/rpg-houses.md`
- Create: `common/src/test/java/net/schwarz/rotasutils/house/HouseAdminTranslationTest.java`

**Interfaces:**
- Consumes: every new housing screen, action, status, validation, and confirmation key.
- Produces: complete English/Thai UI copy, updated administrator instructions, passing full builds, and an explicit runtime evidence record.

- [ ] **Step 1: Write failing translation parity tests**

Enumerate required housing-admin keys by functional group and assert both locale files contain nonblank values with matching placeholder counts.

- [ ] **Step 2: Run translation tests and verify RED**

Run: `.\gradlew.bat :common:test --tests '*HouseAdminTranslationTest' --console=plain`

Expected: missing-key assertions for the new screens/actions.

- [ ] **Step 3: Add concise English and natural Thai text**

Cover overview headings, empty states, fields, tier actions, selection states, save/remove confirmations, stale revisions, permission failures, overlap/volume errors, occupied protections, and success feedback. Use the existing readable font path; do not force bold or `minecraft:uniform`.

- [ ] **Step 4: Update housing documentation**

Document Admin UI navigation, creation from selection, editing/replacing bounds, occupied safeguards, global settings, tier reference rules, commands as fallback, and the separation from monster zones.

- [ ] **Step 5: Run the complete automated gate**

Run: `.\gradlew.bat :common:test :forge:build :fabric:build --console=plain`

Expected: exit code 0, zero test failures/errors, and current Forge/Fabric production JARs.

- [ ] **Step 6: Inspect generated artifacts and test reports**

Sum `common/build/test-results/test/TEST-*.xml`, report tests/failures/errors, and confirm current timestamps for `forge/build/libs/RotasUtils-forge-1.0.2.jar` and `fabric/build/libs/RotasUtils-fabric-1.0.2.jar`.

- [ ] **Step 7: Perform isolated Forge visual/gameplay validation**

At GUI scales 2 and 3: open Housing, Create House, House Editor, Remove Confirmation, Rental Settings, and Tier Editor; verify no overlap/truncation, the opening transition, Thai/English readability, draft retention, creation from wand selection, bounds replacement, restart persistence, occupied disable/remove rejection, and unchanged monster-zone behavior.

- [ ] **Step 8: Perform isolated Fabric boundary validation**

Repeat creation, edit, persistence, permissions, and House Wand selection behavior on Fabric. Inspect current logs for missing translations, rendering exceptions, packet errors, and housing save/load failures.

- [ ] **Step 9: Report exact evidence and remaining gaps**

Separate automated results from Forge/Fabric live proof. Do not claim visual or cross-loader completion for any runtime step that was not observed.

- [ ] **Step 10: Record final checkpoint**

List every changed file and artifact hash. If Git becomes available, commit as `feat(housing): configure houses through themed UI`.
