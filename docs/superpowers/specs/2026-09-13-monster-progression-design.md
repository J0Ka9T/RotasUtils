# Monster Progression System Design

## Goal

Evolve the existing RotasUtils monster engine into a persistent level 1-999 RPG framework while keeping naturally generated monsters within level 1-100 and preserving existing authored content and saved entities.

## Architecture

The existing `MonsterService`, `MonsterState`, kernel definitions, loader storage, spawn events, affix hooks, and boss service remain authoritative. Schema 2 adds explicit assignment source, stable rank, monster type, custom ID, and presentation data. A dedicated generator owns normal spawn levels; a scaling service combines base, curve, rank, profile, and affix layers once when assignment changes.

Clients receive bounded presentation snapshots only when relevant state changes. A client-only renderer handles compact distant labels, detailed targeted/damaged plates, and rank-aware boss presentation without putting gameplay rules in render code.

## Compatibility

- Legacy assigned monsters migrate to schema 2 without rerolling level, attributes, affixes, XP, rewards, or boss runtime.
- Legacy levels above 999 are clamped only during migration and diagnosed.
- Existing synthetic automatic profiles become `NATURAL`; authored profiles become `CONFIGURED`.
- Existing tiers map by ID/label to stable ranks, with `NORMAL` fallback.
- Missing optional fields use neutral defaults.
- Forge capability and Fabric scoreboard storage retain their current bounded persistence mechanisms.

## Invariants

- Absolute level range is 1-999.
- Natural generation is always capped at the configured normal cap, itself limited to 100.
- Only configured/manual assignments may exceed level 100.
- Rank and level remain independent.
- Entity assignment occurs on spawn/load/config mutation, never every tick.
- The server owns levels, ranks, types, affixes, stats, phases, and damage events.
- Client rendering is distance/frustum bounded and never scans all entities.

## Configuration

`MobLevelConfig` gains normal/absolute limits, generation factor toggles, curve parameters, rank multipliers, danger thresholds, and presentation distances/fades. Kernel monster profiles gain source-compatible type, stable rank override, curve override, fixed/ranged levels, and presentation options. Both file and UI paths continue using the shared revisioned Review & Apply service.

## Presentation

Normal distant mobs show name and level only. Targeted or recently damaged mobs show a thin health bar, compact HP, type, rank, and bounded affix list. Damage feedback is a capped client-side pool. Miniboss/boss/world-boss state feeds a separate HUD presenter and existing `BossService` phase state.

## Verification

Each phase uses test-first changes, common tests, Forge/Fabric builds, persistence round trips, and dedicated Forge smoke where applicable. Live-client visual claims require an actual client inspection and are reported separately from static/build evidence.
