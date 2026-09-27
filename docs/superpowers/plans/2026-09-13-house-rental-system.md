# House Rental System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete server-authoritative rental-house system with a separate House Wand, protection, recurring payments, owner/member UI, signs, and a custom gold coin item/texture.

**Architecture:** Store validated house definitions and tenancy state inside the existing overworld `RotasData`, but keep all housing domain types and services separate from monster `ZoneDef` and `ZoneService`. Common code owns transitions, billing, commands, networking, UI, and portable protection decisions; Forge/Fabric adapters cover only events that Architectury cannot cancel consistently.

**Tech Stack:** Java 17, Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.22, Fabric, JUnit 5, Minecraft NBT/commands/networking, 16x16 PNG item art.

**Spec:** `docs/superpowers/specs/2026-09-13-house-rental-system-design.md`

## Global Constraints

- Housing must not use or mutate monster `ZoneDef`, `ZoneService`, or the monster Zone Wand.
- The server owns all state, prices, permissions, deadlines, and wallet mutations.
- Default currency is `rotas:gold`; default interval is 259200 seconds, reminder lead 86400 seconds, grace 3600 seconds, buyout multiplier 10, and base members 5.
- Visitors may cross open space but may not mutate or use protected house contents.
- Repossession clears tenancy only; it never removes blocks, block entities, entities, or items.
- Ownership transfer, selling, voluntary surrender, and member-funded payments are out of scope.
- Every external collection and NBT payload is bounded and malformed data fails closed.
- English and Thai text must be complete; UI must remain usable at GUI scales 2 and 3.
- Verify common tests plus Forge and Fabric builds; runtime claims require isolated-world evidence.

---

### Task 1: Housing Domain, Configuration, and Persistence

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseStatus.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseBounds.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseTier.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseDefinition.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseTenancy.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseConfig.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/data/RotasData.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HousePersistenceTest.java`

**Interfaces:**
- Produces: `HouseBounds.contains(String,int,int,int)`, `HouseDefinition.save()/load()`, `HouseTenancy.save()/load()`, `HouseConfig.save()/load()`, and bounded `RotasData.houses()/tenancies()/houseConfig()` accessors.

- [ ] Write failing tests that round-trip all four states, bounds, owner/member UUIDs, deadlines, purchased slots, revisions, tiers, and defaults; assert invalid dimensions, inverted/oversized bounds, negative money, excessive members, unknown state, and excess collection counts are rejected.
- [ ] Run `./gradlew.bat --no-daemon --console=plain :common:test --tests '*HousePersistenceTest'` and confirm the housing types are missing.
- [ ] Implement immutable/encapsulated domain types with explicit limits (`MAX_HOUSES`, `MAX_MEMBERS`, string lengths, coordinate/volume bounds) and versioned NBT codecs.
- [ ] Add `houses`, `house_tenancies`, and `house_config` sections to `RotasData.save/load`; old worlds load empty defaults and writes call `setDirty()`.
- [ ] Re-run the focused test and then `:common:test`.
- [ ] Commit when Git metadata is available: `feat(housing): add persistent house domain model`.

### Task 2: Registry, Spatial Lookup, and Validation

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseRegistry.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseValidation.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/server/Validation.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseRegistryTest.java`

**Interfaces:**
- Consumes: persisted `HouseDefinition` map.
- Produces: `HouseRegistry.rebuild(Collection<HouseDefinition>)`, `at(String dimension, BlockPos)`, `overlaps(HouseDefinition)`, and `HouseValidation.validate(RotasData)`.

- [ ] Write failing tests for containment edges, adjacent boxes, overlap rejection, different dimensions, disabled houses, duplicate sign bindings, missing tiers, and index rebuild consistency.
- [ ] Run the focused test and confirm failure.
- [ ] Implement a chunk-column index mapping dimension/chunk keys to bounded house-ID sets; exact bounds decide final containment.
- [ ] Connect housing issues to the existing validation report without changing monster-zone validation.
- [ ] Re-run focused and common tests.
- [ ] Commit when available: `feat(housing): index and validate house regions`.

### Task 3: Atomic Tenancy and Economy Service

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseActionResult.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseService.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseServiceTest.java`

**Interfaces:**
- Produces: `rent`, `payOverdue`, `buyout`, `addMember`, `removeMember`, `purchaseMemberSlot`, and `repossesIfDue`; every method accepts expected revision and returns `HouseActionResult(success,messageKey,newRevision)`.

- [ ] Write failing tests for every state transition, insufficient funds, stale revisions, duplicate requests, aggregate overdue payments, buyout price (`deposit * multiplier + overdue charge`), member limits, UUID identity, and two callers racing for one available house.
- [ ] Run the focused test and confirm failure.
- [ ] Implement validation-before-mutation and a single server-thread commit boundary using `PlayerRecordTransaction`; create stable action receipts before exposing network entry points.
- [ ] Ensure all arithmetic uses checked operations and all failed paths leave wallet and tenancy unchanged.
- [ ] Re-run focused and common tests.
- [ ] Commit when available: `feat(housing): add atomic rental transactions`.

### Task 4: Billing, Restart Catch-up, and Notifications

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseBillingService.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseNotice.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/event/RotasEvents.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseBillingServiceTest.java`

**Interfaces:**
- Produces: `rebuild(RotasData,long now)`, `tick(MinecraftServer,RotasData,long now,int budget)`, and `onPlayerLogin(ServerPlayer,RotasData)`.

- [ ] Write failing clock-controlled tests for 24-hour reminder, exact due boundary, successful debit, overdue entry, one-hour grace, immediate startup repossession after expired grace, aggregate catch-up, clock rollback, idempotent notices, and bounded work batches.
- [ ] Run the focused test and confirm failure.
- [ ] Implement a priority queue keyed by deadline and tenancy revision; stale queue entries are ignored.
- [ ] Invoke billing once per second from the existing coarse tick and deliver bounded persisted notices on login.
- [ ] Re-run focused and common tests.
- [ ] Commit when available: `feat(housing): process recurring rent safely`.

### Task 5: Dedicated House Wand and Admin Commands

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/item/HouseWandItem.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseWandService.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/command/HouseCommands.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/registry/RotasRegistry.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/command/RotasCommands.java`
- Create: `common/src/main/resources/assets/rotasutils/models/item/house_wand.json`
- Create: `common/src/main/resources/assets/rotasutils/textures/item/house_wand.png`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseWandServiceTest.java`

**Interfaces:**
- Produces: item NBT keys under `RotasHouseWand`, exact two-corner selection, and `/rotas house wand|create|edit|enable|disable|remove|inspect|validate`.

- [ ] Write failing tests for first/second corner, dimension mismatch, clearing, permission denial, volume/overlap rejection, and successful creation.
- [ ] Run the focused test and confirm failure.
- [ ] Implement the separate registered wand, tooltip, selection state, admin checks, commands, audit entries, and item model.
- [ ] Create a restrained copper-and-teal 16x16 wand texture consistent with existing RotasUtils assets; inspect at nearest-neighbor scale for silhouette and transparency.
- [ ] Re-run focused/common tests and both resource-processing/build tasks.
- [ ] Commit when available: `feat(housing): add independent house authoring wand`.

### Task 6: Protection and Loader Coverage

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseProtectionService.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/event/RotasEvents.java`
- Create or modify: `forge/src/main/java/net/schwarz/rotasutils/forge/HouseForgeEvents.java`
- Create or modify: `fabric/src/main/java/net/schwarz/rotasutils/fabric/HouseFabricEvents.java`
- Modify loader initialization files to register adapters.
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseProtectionServiceTest.java`

**Interfaces:**
- Produces: pure `canBreak`, `canPlace`, `canUse`, `canAccessContainer`, `canMovePiston`, `canFlowFluid`, and `canExplode` decisions consumed by event adapters.

- [ ] Write failing table-driven tests for owner/member/visitor/available/disabled/bought-out cases and exact boundary positions.
- [ ] Run the focused test and confirm failure.
- [ ] Implement pure decisions and common break/place/right-click cancellation; never cancel movement.
- [ ] Add only the loader hooks required for buckets/fluids, explosions, pistons, and entity containers, preserving conservative external-mutation defaults.
- [ ] Re-run common tests and compile both loaders.
- [ ] Commit when available: `feat(housing): enforce cross-loader house protection`.

### Task 7: Sign Binding, Network Actions, and Player Commands

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/house/HouseSignService.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/network/HouseNetwork.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/RotasNetwork.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/ClientNetworkHandlers.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/command/HouseCommands.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/event/RotasEvents.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseSignServiceTest.java`

**Interfaces:**
- Produces: bounded house snapshots, revision-bearing action packets, `/rotas house list|info|rent|pay|buyout|members|member|manage|bind-sign`, and authoritative sign lookup by dimension/position.

- [ ] Write failing tests for sign bind/use/invalidation, stale sign blocks, bounded snapshots, invalid packet fields, stale revisions, and command authorization.
- [ ] Run the focused test and confirm failure.
- [ ] Implement sign authority independent of editable sign text; validate every network action again on the server thread.
- [ ] Add command suggestions and useful translated failures without leaking disabled/admin-only house data.
- [ ] Re-run focused/common tests.
- [ ] Commit when available: `feat(housing): expose safe player and sign actions`.

### Task 8: House Screens and Admin Workflow

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/player/HouseScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseManagerScreen.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/HouseEditScreen.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/ClientNetworkHandlers.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/screen/admin/AdminMenuScreen.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseSnapshotTest.java`

**Interfaces:**
- Consumes: bounded snapshots and server actions from Task 7.
- Produces: player Rent/Pay/Buy out/Manage flows and admin list/edit/review/apply flows.

- [ ] Write failing snapshot/presentation-model tests for each state, owner/member/visitor action availability, countdown formatting, confirmations, empty/error/loading states, and stale refresh.
- [ ] Run the focused test and confirm failure.
- [ ] Implement screens with existing `Ui`, `RotasTheme`, `RotasScreen`, and scroll primitives; client state remains presentation-only.
- [ ] Add confirmation for every charge and review/apply for destructive admin edits; refresh after each server response.
- [ ] Verify layout bounds programmatically at GUI scales 2 and 3, then run common tests.
- [ ] Commit when available: `feat(housing): add rental and administration screens`.

### Task 9: Gold Coin Item and Pixel Texture

**Files:**
- Modify: `common/src/main/java/net/schwarz/rotasutils/registry/RotasRegistry.java`
- Create: `common/src/main/resources/assets/rotasutils/models/item/gold_coin.json`
- Create: `common/src/main/resources/assets/rotasutils/textures/item/gold_coin.png`
- Modify: `common/src/main/resources/assets/rotasutils/lang/en_us.json`
- Modify: `common/src/main/resources/assets/rotasutils/lang/th_th.json`
- Test: `common/src/test/java/net/schwarz/rotasutils/registry/RegistryContractTest.java`

**Interfaces:**
- Produces: registered `rotasutils:gold_coin` display item and a reusable `rotas:gold` currency icon mapping; possession of the item does not replace or duplicate the authoritative wallet balance.

- [ ] Write a failing registry/resource contract test asserting the item supplier, model, translation keys, 16x16 RGBA PNG, nonempty alpha, limited gold palette, and transparent corners.
- [ ] Run the focused test and confirm failure.
- [ ] Register the item and add it to the RotasUtils creative tab; keep it non-consumable and economically inert.
- [ ] Draw an original 16x16 coin with a strong circular silhouette, dark bronze outline, warm gold ramp, top-left highlight, lower-right shadow, and a small `R`/rune center mark.
- [ ] Inspect the PNG at native and nearest-neighbor enlarged scale; ensure no interpolation, stray alpha, or muddy outline.
- [ ] Connect the item icon to house/economy UI without treating inventory stacks as wallet currency.
- [ ] Re-run focused tests and both loader builds.
- [ ] Commit when available: `feat(assets): add Rotas gold coin`.

### Task 10: Localization, Documentation, and End-to-End Verification

**Files:**
- Modify: `common/src/main/resources/assets/rotasutils/lang/en_us.json`
- Modify: `common/src/main/resources/assets/rotasutils/lang/th_th.json`
- Create: `docs/house-rental.md`
- Modify: `docs/rpg-platform-status.md`
- Test: `common/src/test/java/net/schwarz/rotasutils/house/HouseTranslationTest.java`

**Interfaces:**
- Consumes: all housing surfaces.
- Produces: complete operator/player documentation and verified artifacts.

- [ ] Write a failing translation parity test for every housing item, command, status, validation issue, notice, button, confirmation, and failure key.
- [ ] Run the focused test and confirm failure.
- [ ] Add concise natural English and Thai strings; document configuration, wand workflow, commands, lifecycle, protection, recovery/re-rent semantics, and rollback/backup guidance.
- [ ] Run `./gradlew.bat --no-daemon --console=plain :common:test :forge:build :fabric:build` and record exact results.
- [ ] Launch an isolated Forge world and exercise wand creation, sign binding, rent, member access, visitor denial, open-door passage, payment, overdue, repossession, rerent, buyout, and restart persistence at GUI scales 2 and 3.
- [ ] Launch an isolated Fabric world and repeat loader-sensitive protection, sign, persistence, and payment checks.
- [ ] Inspect current logs for housing exceptions, rejected mixins/events, missing textures/models/translations, and save/load errors.
- [ ] Update status docs with only demonstrated evidence and list any remaining runtime gaps explicitly.
- [ ] Commit when available: `docs(housing): document and verify rental system`.
