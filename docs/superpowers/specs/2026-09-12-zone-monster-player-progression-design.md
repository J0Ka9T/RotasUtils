# Zone, Monster, and Player Progression Design

## Objective

Make geography determine danger, make monster rewards reflect the creature actually fought, and
make player advancement reward exploration and meaningful encounters instead of repetitive farming.
The system must preserve existing worlds, remain server-authoritative, and support Minecraft 1.20.1
on both Forge and Fabric through the current Architectury structure.

## Player Experience

The world has readable danger. Authored zones communicate a recommended player range and enforce a
monster level band. Outside authored zones, the minimum monster level rises with horizontal distance
from the dimension spawn. Entering a dangerous area is a deliberate choice rather than an invisible
copy of the player's current power.

Player progression has four dependable paths:

1. discovering new zones and distance milestones;
2. completing quests;
3. defeating a monster type for the first time and fighting level-appropriate threats;
4. defeating bosses and elite monsters.

Repeated trivial kills lose most of their value. The system never removes earned XP or existing
levels, and it does not punish death by deleting progression.

## Zone Model

Extend each `ZoneDef` with optional gameplay metadata:

- `danger`: `SAFE`, `NORMAL`, `DANGEROUS`, or `DEADLY`;
- `recommended_min` and `recommended_max`, defaulting to the zone's monster band;
- `xp_multiplier`, bounded to `0.0..10.0`, default `1.0`;
- `safe`, default `false`; safe zones suppress default hostile leveling and default combat XP;
- `transition_blocks`, bounded to `0..256`, default `16`.

Existing zone records load with defaults, so old saves keep their identifiers, dimensions, shapes,
bands, priority, enabled state, and behavior. The metadata is stored in the same `RotasData` world
record; no second zone store is introduced.

`ZoneService` returns a richer immutable region result containing the selected zone, resolved
danger, recommended range, XP multiplier, distance, and transition information. Highest priority
still wins when zones overlap. Equal-priority ties remain deterministic by zone ID.

### Wilderness Difficulty

Each dimension uses the current spawn-level and blocks-per-level settings. The level floor is:

```text
spawn_level + floor(horizontal_distance_from_dimension_spawn / level_per_blocks)
```

The result is clamped to the configured maximum. When the ramp is disabled, wilderness uses the
spawn level. Authored zones override the wilderness band.

At an authored-zone boundary, `transition_blocks` blends the wilderness floor toward the zone band
only for automatic monster assignment. The zone's hard minimum and maximum remain authoritative
once inside. This avoids a sudden wall of power immediately outside a town while keeping location,
not player level, in control.

### Zone Feedback and Editing

The existing zone editor gains fields for danger, recommended range, XP multiplier, safe status,
and transition distance. It shows a compact preview with the resolved monster band and expected
reward multiplier. The Zone Wand action-bar hint shows zone name, danger, recommended player range,
and monster band. Existing world wireframes and picking behavior remain intact.

## Monster Level Selection

Automatic monsters use a location-first policy:

1. a matching authored monster profile selects its existing level strategy and band;
2. otherwise the containing zone supplies the band;
3. otherwise the wilderness distance ramp supplies the floor;
4. deterministic variance derived from monster UUID changes the selected level by at most 10% of
   the available band, clamped back into it;
5. nearby player level never raises or lowers the authoritative location floor.

For profiles that explicitly request player-relative behavior, the player-derived value remains
available but is clamped to the location/profile band. Existing manually assigned monsters retain
their stored level until an administrator explicitly relevels or clears them.

Safe zones skip default leveling of newly spawned hostile mobs. They do not delete, weaken, or
rewrite persistent monsters moved into the zone.

## Threat Rating

Add a pure `MonsterThreat` calculation used by default monsters and profile monsters. Inputs are
captured after Rotas-owned modifiers are derived:

- effective maximum health;
- attack damage;
- armor and armor toughness;
- movement speed, knockback resistance, and follow range with bounded influence;
- monster level;
- tier rank and configured tier multipliers;
- affix count and each affix's declared weight;
- boss status;
- profile base XP and explicit entity XP overrides.

The formula uses capped logarithmic or square-root terms for large attributes so extreme modded
boss values do not overflow or dominate linearly. Every intermediate and final value must be finite,
nonnegative, saturating, and bounded by the existing configured maximum monster XP. Explicit entity
overrides remain the final base before situational reward modifiers.

The assigned `MonsterState` persists the computed base threat XP. Reloading a monster therefore
does not change its value merely because configuration or another mod changed later. Explicit
releveling recalculates the level-dependent portion while preserving profile, tier, affixes, reward,
and runtime state.

## Combat XP Award

The server computes final combat XP at confirmed death:

```text
base threat XP
* zone XP multiplier
* level challenge multiplier
* contribution share
* repetition multiplier
```

All multipliers are bounded independently and the final amount is clamped to the configured maximum.

### Challenge Multiplier

- monsters far below the player's level taper toward 10% reward;
- monsters near the player's level grant approximately 100%;
- monsters above the player's level rise gradually to a 175% cap;
- bosses may apply the existing boss multiplier after threat calculation, without bypassing the
  global XP maximum.

### Contribution

Track bounded per-player combat contribution for assigned monsters using damage dealt, damage
received from the monster, and support credited through existing event seams where available.
Eligibility requires meaningful participation and proximity at death. Party members receive a
bounded shared portion only when eligible; merely standing nearby grants nothing. Total distributed
XP cannot exceed the encounter's configured party budget.

The first implementation credits direct damage reliably and leaves unsupported third-party healing
events uncredited rather than guessing. Contribution entries expire with the monster and remain
bounded by the existing boss-ledger limits.

### Repetition Control

Store a bounded, server-side kill-familiarity map in `PlayerProgress`, keyed by entity/profile
identity. Recent repeated kills reduce combat XP in steps down to a 20% floor. Familiarity decays by
server play time and is cleared gradually, not by wall-clock time while the server is offline.
Bosses, quest-required kills, and first kills never fall below their explicit protected floor.

The existing per-entity XP receipt remains the final anti-duplicate gate. Repetition state updates
and XP award commit together so reconnects or duplicated death callbacks cannot apply one without
the other.

## Exploration and First-Kill Progression

`PlayerProgress` gains bounded server-only discovery records:

- discovered zone IDs;
- reached wilderness distance milestones per dimension;
- first defeated monster/profile IDs;
- repetition familiarity entries.

Entering an enabled non-safe zone for the first time grants a configurable discovery award. Distance
milestones grant once per dimension at fixed configurable intervals. First kills grant a bounded
bonus based on threat. Each award uses the existing receipt/transaction system and is emitted through
the existing progression event flow. Client snapshots receive only summary counts and the information
needed for player-facing progress; anti-farm timestamps and receipts remain private.

## Player Level Progression

Keep the existing overflow-safe level curve, UUID-owned `PlayerProgress`, stat points, skill points,
Pufferfish integration, rank clearances, rewards, delta sync, and vanilla-level compatibility mirror.

Add an `AdventureXpConfig` under `LevelConfig`:

- enable flags and multipliers for combat, quests, zone discoveries, distance milestones, first
  kills, and bosses;
- challenge multiplier thresholds and caps;
- repetition window, decay rate, protected floor, and map limit;
- discovery XP, milestone block interval, and milestone XP;
- first-kill multiplier and maximum;
- party contribution threshold, radius, and total reward budget.

Defaults favor adventure progression while remaining conservative for existing saves. Missing
configuration loads these defaults. Existing customized level curves and XP source settings are not
overwritten. Increment the progression data version only for serialized semantic changes and provide
an explicit migration test.

Level-up feedback explains what was earned: new level, stat points, skill points, rank clearance,
and level rewards. The player menu adds an adventure summary showing current zone danger, next
wilderness milestone, discoveries, first kills, recent repetition penalty when active, and the next
level reward. It does not expose private receipt identifiers.

## Administration and Diagnostics

Extend the existing Level Manager rather than adding a parallel admin screen. Add an Adventure XP
section with human-readable controls and reset-to-default actions. Add previews:

- expected wilderness level at a distance;
- expected monster level for a zone and player level;
- threat XP for supplied attributes/tier/affixes;
- final XP across representative level differences and repetition counts;
- cumulative XP and estimated encounters for selected player levels.

Commands gain read-only diagnostics for the player's current region and a targeted monster's stored
level, threat XP, zone multiplier, challenge multiplier, contribution eligibility, repetition
multiplier, and projected award. Mutating commands retain existing permission checks and audit.

## Authority, Persistence, and Networking

- The server owns zone selection, monster assignment, threat, contribution, XP, discoveries, level,
  points, ranks, and rewards.
- Clients render synchronized summaries and send only existing bounded admin/player requests.
- All client requests are revalidated for actor, permission, target, dimension, distance, and stale
  revision as applicable.
- Zone and level configuration stay in `RotasData`; player additions stay in the existing UUID-keyed
  `PlayerProgress`; monster additions stay in `MonsterState` or its bounded runtime map.
- Unknown future schemas fail explicitly. Existing saves load through defaults and migration without
  resetting progression.
- Disconnect, death, dimension change, chunk unload, server restart, duplicated packets, and
  duplicated death callbacks retain deterministic behavior.

## Performance Bounds

- No global entity or player scans.
- Zone lookup remains event-driven at spawn, entry sampling, explicit inspection, or bounded player
  movement checkpoints.
- Cache ordered zone candidates by dimension and invalidate only when zones change.
- Contribution maps, discoveries, first kills, and familiarity maps have hard entry limits and
  deterministic eviction.
- Threat is computed on assignment/relevel, not every tick or frame.
- Player region checks run at a bounded interval and only after meaningful block movement.
- Rendering remains client-only and does not allocate geometry collections per frame.

## Delivery Sequence

1. Pure rule layer: zone resolution, transition math, deterministic monster variance, threat formula,
   challenge/repetition/contribution math.
2. Backward-compatible configuration and persistence migrations.
3. Monster assignment and death-award integration.
4. Exploration, first-kill, and distance-milestone progression.
5. Admin editing, previews, diagnostics, player summaries, and localization.
6. Forge/Fabric integration verification and dedicated-server exploit/restart checks.
7. Live client inspection of zone feedback, monster nameplates, progression feedback, and affected
   screens at practical GUI scales.

Each sequence is a green, independently reviewable batch. No installed PROJECT-RC artifact is
replaced until all required gates pass and deployment is separately requested.

## Verification

Automated tests must cover:

- old zone/config/player/monster NBT migration;
- overlap priority and deterministic ties;
- wilderness floors, caps, disabled ramps, and transition boundaries;
- deterministic level variance and band clamping;
- threat monotonicity, finite bounds, explicit overrides, bosses, tiers, and affixes;
- challenge and repetition floors/caps;
- contribution eligibility, party budget, disconnect, distance, duplicate death, and zero damage;
- zone discovery, dimension milestones, first kills, receipt replay, eviction, and restart persistence;
- multi-level gains, level cap, point grants, clearance, level rewards, and privacy snapshots;
- malformed configuration and future schema rejection.

Required project gates:

```text
gradlew :common:test :forge:build :fabric:build
python scripts/rpg-console-smoke.py
```

The dedicated Forge smoke must create zones, verify distance and transition levels, assign vanilla
and modded monsters, compare projected and awarded XP, exercise repetition and contribution, save,
restart, and verify persistence. Fabric must compile and package; runtime parity remains explicitly
unverified unless a Fabric runtime smoke is added and executed.

Live verification must use a disposable world and confirm the real Zone Wand, nameplates, action-bar
feedback, player menu, admin editors, and multiplayer-relevant reward messages. Build success alone
does not prove these visuals or authenticated flows.

## Out of Scope

- resetting existing player levels or replacing the configured level curve;
- client-authoritative combat or progression;
- procedurally creating authored zones across the world;
- guessing contribution from unsupported third-party healing events;
- changing item levels, quest definitions, skill-tree content, or boss mechanics except where they
  consume the improved progression calculations;
- deploying into the installed PROJECT-RC pack without a separate request.

## Definition of Done

The feature is done when old saves migrate without progression loss; geography controls automatic
monster levels; threat and participation determine bounded XP; exploration, first kills, quests,
bosses, and meaningful combat advance players; repetitive trivial farming visibly loses value; admin
previews explain the rules; common tests and both loader builds pass; the dedicated Forge smoke passes
before and after restart; and remaining Fabric or live-client gaps are stated rather than implied.
