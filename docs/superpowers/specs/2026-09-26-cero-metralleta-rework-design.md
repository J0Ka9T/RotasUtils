# Cero Metralleta Rework Design

Date: 2026-09-26
Project: RotasUtils
Target: Minecraft 1.20.1, Architectury 9.2.14, Fabric 0.19.3 / Forge 47.4.22, Java 17
Status: Awaiting written-spec review before implementation

## Intent

Rebuild Cero Metralleta so it still feels like a five-second, forty-shots-per-second spiritual-energy barrage, while removing the current 200 synchronized projectile entities and the combat/render amplification they cause.

The rework must preserve server authority, the existing shift + left-click input, the Disintegrator cooldown model, ally/pet protection, block collision, direct damage, splash damage, fire application, aim sweeping, and the blue-white Cero identity.

## Current Problems

- 200 ExoCeroEntity instances are spawned per barrage.
- Around 88 bolt entities can coexist at steady state because each bolt lives 44 ticks.
- Every live bolt raycasts and performs an entity query every server tick.
- Every bolt uses updateInterval(1), creating high multiplayer tracking traffic.
- Every impact can perform an additional splash entity query.
- Damage repeatedly clears invulnerabilityTime, multiplying combat-mod hooks.
- The first firing tick is off by one because tickCount is used after super.tick().
- The strongest opening camera quake checks tickCount == 0 after super.tick(), so it is unreachable.
- A Cero request does not explicitly exclude an already-running beam or lance.
- Thirteen-block additive tails intentionally merge the barrage into a continuous beam.
- The muzzle uses very large halos, lens streaks, and 40-segment shock rings, causing unnecessary additive overdraw.

## Chosen Architecture

Keep one ExoCeroMuzzleEntity as the authoritative five-second barrage controller. Remove ExoCeroEntity from the active firing path and replace each projectile entity with a logical hitscan shot computed when its lane fires.

Each server tick during the volley:
1. Resolve the owner''s current eye position and aim.
2. Generate exactly two deterministic Cero shot directions.
3. Raycast each shot against blocks and living entities.
4. Collect direct and splash contributions into a per-tick victim accumulator.
5. Apply at most one damage call per victim for that tick.
6. Send one compact S2C visual packet containing both shot paths to nearby players.
7. Let the client render the two shots as short-lived Cero lances without spawning gameplay entities.

This drops the steady-state Cero simulation from roughly 88 synchronized entities plus their per-tick collision work to one controller entity, two authoritative shot traces per tick, one batched damage application per victim, and one small VFX packet per tick.

## Ballistics

The barrage remains 200 shots over 100 firing ticks with two lanes per tick.

Shot spread remains deterministic and evenly distributed around the current aim using the golden-angle pattern. Random jitter is replaced by deterministic hash jitter derived from the shot index and controller seed so the server and tests are reproducible.

The effective maximum range will remain equivalent to the old projectile envelope: SPEED * LIFE = 158.4 blocks unless implementation constraints reveal a compatibility reason to clamp slightly lower.

Entity collision chooses the nearest valid living target along the shot segment. Blocks stop the shot first. Armor stands, the owner, spectators, dead entities, pets, and protected allies remain excluded using the same sparing rules as ExoBeamEntity.

## Damage Model

Direct and splash damage values remain numerically equivalent to the current implementation unless a test proves the old implementation applies a different total because of the tick bug.

For every firing tick, contributions are accumulated per victim:
- direct contribution: flat damage plus max-health bite;
- splash contribution: reduced flat damage plus reduced max-health bite;
- fire: refresh to five seconds if any contribution damages the victim.

A victim receives one hurt() call for the total Cero contribution generated in that tick. invulnerableTime is cleared once immediately before that call so the barrage preserves rapid damage without issuing multiple combat events for the same victim in the same tick.

Splash lookup remains centered on each impact point, but duplicates are folded into the same per-tick accumulator.

## Networking

Add a dedicated Cero VFX packet using the existing RiftFx-style Architectury S2C pattern.

One packet represents one barrage tick and contains:
- owner entity id;
- first shot index;
- two start positions or one shared muzzle origin;
- two end positions;
- impact flags needed for client impact flashes.

Packets are sent only to players in the same level within a bounded visual range. No Cero projectile movement packets are sent because there are no networked bolt entities.

The ExoCeroMuzzleEntity remains synchronized so remote clients can render the muzzle and follow the owner without introducing a second controller protocol.

## Client VFX

Replace ExoCeroRenderer''s long entity trail with a lightweight client-side Cero shot manager and renderer.

Each packet creates two visual shots that live only a few frames. Each shot renders:
- a narrow white-hot core;
- a brighter azure body;
- a softer blue outer haze;
- a slight deterministic curve/noise offset or segmented taper so it reads as spiritual plasma instead of a flat line;
- a compact impact flare when the server reports an impact.

The shot lifetime is short enough that individual Ceros remain readable at forty shots per second rather than merging into one permanent beam.

The muzzle keeps the existing blue-white identity but is rebuilt for lower overdraw:
- smaller layered core;
- four to six orbiting charge motes instead of eight heavy halos;
- restrained lens streak;
- lower-segment shock rings;
- opening burst stronger than sustain;
- periodic recoil pulses tied to the actual firing cadence;
- fade after the final shot.

The opening camera quake is triggered on the first reachable client tick or explicitly from the start event. Recoil quakes remain periodic but smaller.

## Mutual Exclusion

Cero may start only when:
- the player is sneaking;
- the Disintegrator is in the main hand;
- the item is off cooldown;
- the player is not actively using the Disintegrator;
- no ExoBeamEntity owned by that player is already active;
- no other ExoCeroMuzzleEntity owned by that player is active.

The ordinary beam/lance channel path also treats the Disintegrator cooldown/controller state as authoritative, preventing overlap in either direction.

The existing MouseHandlerClickMixin remains because common Architectury APIs do not provide an equally reliable cross-loader hook for consuming raw left-click before vanilla attack/mining behavior. No additional Mixin is introduced.

## Code Shape

Expected production changes:
- ExoCeroMuzzleEntity: controller, firing schedule, hit traces, damage batching, VFX sends.
- New CeroBallistics helper: deterministic spread, shot schedule, hit math that can be unit tested without a client.
- New CeroFx transport/sink similar to RiftFx.
- New client Cero FX manager/renderer for short-lived visual shots.
- ExoCeroMuzzleRenderer: lower-overdraw muzzle and corrected opening pulse.
- ExoBeamEntity: expose a narrow owner-active query used for mutual exclusion.
- ClientNetworkHandlers: register Cero VFX receiver.
- RotasClient: install/register client Cero FX rendering.
- RotasRegistry: remove EXO_CERO registration after no runtime references remain.
- ExoCeroEntity and ExoCeroRenderer: delete after the replacement is verified.
- ExoCeroTest and new focused tests: schedule, deterministic spread, range, and batching invariants.

## Test Strategy

Use TDD for behavior changes.

Required RED -> GREEN regression coverage:
- actual first firing step produces exactly two shots, not four;
- 100 firing steps produce exactly 200 shots;
- opening pulse uses a reachable first-tick condition;
- deterministic spread returns the same direction for the same seed/index and alternates muzzle sides correctly;
- range remains at least the old useful envelope;
- two direct contributions to one victim in one tick collapse into one accumulated damage value;
- direct plus splash contributions to one victim collapse into one application;
- barrage-active and beam-active state cannot overlap through the shared guard.

Verification after implementation:
- :common:test --rerun-tasks
- :fabric:compileJava --rerun-tasks
- :forge:compileJava --rerun-tasks
- full build if the targeted tasks pass
- inspect warnings for new cross-loader client annotation or networking issues

## Performance Acceptance Criteria

A single Cero barrage must not create ExoCeroEntity instances.

Server work during sustain is bounded to two shot traces per tick plus splash queries only when those shots actually impact.

Each victim receives no more than one Cero hurt() call per server tick from one barrage controller.

Nearby clients receive no projectile entity tracking stream; Cero shot visuals are driven by the compact VFX packet.

The client renderer must avoid a 13-block trail per bolt and avoid the current giant always-on additive muzzle layers.

## Compatibility and Maintenance

The change stays inside Architectury/common APIs already used by RotasUtils and does not add a dependency.

The raw-click Mixin is retained and remains the main version-sensitive hook. Ballistics, damage batching, and the VFX packet are plain Java/common code and should survive minor loader updates better than additional Mixins.

Because EXO_CERO is noSave(), removing the old runtime entity type does not strand saved projectile entities in worlds. If registry removal causes a loader/datapack compatibility issue during implementation, keep the type registered but unused and record that as a compatibility ruling rather than reintroducing projectile spawning.

