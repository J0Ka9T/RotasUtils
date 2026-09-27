# Cero Metralleta Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Cero Metralleta''s 200 synchronized projectile entities with one server-authoritative barrage controller, deterministic two-shot-per-tick ballistics, batched damage, and client-only shot VFX while preserving the five-second barrage identity.

**Architecture:** `ExoCeroMuzzleEntity` remains the only gameplay entity. Pure schedule/spread helpers make timing testable; the controller traces two shots per firing tick, batches victim damage, and sends one compact nearby-player FX packet containing the two visual segments. A client FX manager renders short-lived Cero lances while the existing muzzle renderer is simplified and the old bolt entity path is removed or left registered-but-unused only if loader compatibility requires it.

**Tech Stack:** Minecraft 1.20.1, Architectury 9.2.14, Fabric Loader 0.19.3, Fabric API 0.92.11+1.20.1, Forge 47.4.22, Java 17 target, Gradle 8.12.1.

**Spec:** `docs/superpowers/specs/2026-09-26-cero-metralleta-rework-design.md`

## Global Constraints

- Preserve shift + left-click input and server-side revalidation.
- Preserve 200 shots over 100 firing ticks, exactly 2 logical shots per tick.
- Preserve ally/pet sparing, block stopping, direct damage, splash damage, fire, aim sweeping, and blue-white identity.
- Add no dependency and no new Mixin.
- A victim receives at most one Cero `hurt()` call per server tick per barrage.
- Cero cannot overlap beam/lance or another Cero controller.
- No `ExoCeroEntity` instance may be created by a barrage after the rework.

## Review Focus

- Owner disconnect/death/weapon swap during barrage must terminate cleanly without stale FX or damage.
- Near-vertical aim must produce a stable orthonormal spread basis without NaN vectors.
- A block impact before an entity must prevent hitting the entity behind the block.
- Multiple splash/direct contributions to one victim in a tick must preserve total damage while producing one hurt call.
- Multiplayer observers inside visual range see the same shot endpoints, while players outside range receive no Cero FX traffic.

---

### Task 1: Pure barrage schedule, spread, and overlap guards

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/entity/CeroBallistics.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/entity/ExoCeroMuzzleEntity.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/entity/ExoBeamEntity.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/entity/ExoCeroTest.java`

**Interfaces:**
- Produces: `CeroBallistics.shotsForStep(int)`, `CeroBallistics.direction(Vec3,int,long)`, `CeroBallistics.muzzleSide(int)`, `ExoBeamEntity.hasActiveBeam(ServerLevel, LivingEntity)`, and controller helpers for first logical step.
- Consumes: existing `ExoBeamEntity.spared` behavior.

- [ ] Write failing tests for first logical step = 2 shots, 100 steps = 200, deterministic spread, vertical aim finite vectors, old range floor, and overlap guard helper shape.
- [ ] Run focused common tests and verify RED.
- [ ] Implement pure helpers and shared active-beam query.
- [ ] Run focused common tests and verify GREEN.

### Task 2: Server controller, batched damage, and compact Cero FX transport

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/entity/CeroFx.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/entity/CeroDamageBatch.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/entity/ExoCeroMuzzleEntity.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/network/ClientNetworkHandlers.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/item/ExoDisintegratorItem.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/entity/ExoCeroTest.java`
- Test: `common/src/test/java/net/schwarz/rotasutils/entity/CeroDamageBatchTest.java`

**Interfaces:**
- Consumes: Task 1 schedule/spread helpers and active-beam query.
- Produces: one authoritative two-shot trace step, per-tick victim accumulation, and `CeroFx` S2C packet with two segments.

- [ ] Write failing accumulator tests for repeated direct contributions and direct+splash folding.
- [ ] Run focused tests and verify RED.
- [ ] Implement damage accumulator independent of Minecraft damage application.
- [ ] Replace projectile spawning in the controller with two traces/tick, impact collection, one damage apply/victim, and one FX send/tick.
- [ ] Enforce mutual exclusion in both Cero start and beam/lance channel paths.
- [ ] Register the Cero FX receiver.
- [ ] Run common tests and verify GREEN.

### Task 3: Client-only shot rendering and lower-overdraw muzzle

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/render/CeroFxRenderer.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/RotasClient.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/client/render/ExoCeroMuzzleRenderer.java`
- Modify or delete after verification: `common/src/main/java/net/schwarz/rotasutils/client/render/ExoCeroRenderer.java`
- Modify: `common/src/main/java/net/schwarz/rotasutils/registry/RotasRegistry.java`
- Modify or delete after verification: `common/src/main/java/net/schwarz/rotasutils/entity/ExoCeroEntity.java`

**Interfaces:**
- Consumes: `CeroFx.Sink` packet delivery from Task 2.
- Produces: short-lived client-side shot visuals and revised muzzle rendering.

- [ ] Implement client FX sink storage with bounded lifetime/capacity.
- [ ] Render each shot as short white/azure/haze layered lances plus impact flare.
- [ ] Reduce muzzle halo/ring segment counts and sizes; correct opening quake condition.
- [ ] Remove old projectile renderer/entity registration if both loaders compile; otherwise retain registration unused and document the compatibility ruling.
- [ ] Run common tests, Fabric compile, Forge compile, and full build.

### Task 4: Final verification and review

**Files:** all touched files.

- [ ] Re-run `:common:test --rerun-tasks`.
- [ ] Re-run `:fabric:compileJava :forge:compileJava --rerun-tasks`.
- [ ] Run full `build --rerun-tasks`.
- [ ] Search the source tree for runtime `ExoCeroEntity.launch`/construction references and confirm the barrage path has none.
- [ ] Review the final diff manually because this workspace has no Git repository/subagent review range.

