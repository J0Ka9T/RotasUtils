# RPG progression foundation (Phase 2)

This batch extends the existing UUID-owned PlayerProgress in RotasData's overworld
SavedData. It does not introduce another capability or change inventory UI.
Player data version 1 migrates to version 2 on load; the nested RPG section has
schema 1. Unknown fields survive disk round trips but are excluded from client
snapshots. Unsupported future versions and malformed wallets fail explicitly.

## Profile and XP

Existing level, XP, total XP, skill points, quests, party and preferences remain
in the same record. New fields hold allocated logical stats, unspent stat points,
integer currency balances, signed reputation and mastery XP. Each new map is
limited to 256 namespaced IDs. Currency balances range from 0 to 9 quadrillion;
reputation ranges from -1 trillion to +1 trillion. Mastery definitions, XP source
rules and mastery rewards remain later work; this batch provides their storage.

The UUID record survives death, respawn, entity clone and dimension changes;
these transitions do not clear currencies, reputation or allocations. Existing
death/quest behavior still applies. The server remains authoritative.

XP addition and cumulative curves saturate instead of overflowing. Zero XP
multipliers now award zero, fixing the previous minimum-one-XP behavior. NaN,
infinite and negative multipliers fail validation. Every crossed level still
runs the existing level-up hooks, including Pufferfish compatibility. XP beyond
the level cap leaves zero current XP; total XP retains the saturating award.
These legacy external hooks are not an atomic kernel reward transaction.

## Data-driven stats

Use the existing pack loader and reload commands described in [rpg-kernel.md](rpg-kernel.md).
An opt-in stat definition is:

```json
{
  "schema": 1,
  "id": "rotas:stat/vitality",
  "kind": "stat",
  "body": {
    "base": 0,
    "per_level": 0,
    "min": 0,
    "max": 100,
    "attribute": "minecraft:generic.max_health",
    "attribute_scale": 2,
    "operation": "ADD"
  }
}
```

`base`, `per_level`, `min` and `max` are required finite numbers. The initial
value is base + per_level * (level - 1) + allocated points. Optional `attribute`
maps the final logical value to a transient Minecraft attribute modifier;
`attribute_scale` defaults to 1. Operations are ADD, MULTIPLY_BASE and
MULTIPLY_TOTAL. Unknown attribute IDs reject the candidate before publication.
Omit `attribute` to define a logical stat without changing Minecraft attributes.

The pure modifier pipeline supports additive, summed base-multiplier and
compounded total-multiplier terms before clamping. Live equipment, Curios, buff
and skill modifier providers are not connected yet. Live values currently use
base, level and allocations. Cache keys include entity/profile identity, profile
revision, level and content hash. Reloads and respawn refresh values; logout and
shutdown remove only this system's owned modifiers. Lower maximum health clamps
current health; increasing maximum health does not heal automatically.

- `/rotas stats`: inspect your final values and unspent points.
- `/rotas stats allocate <stat-id> <points>`: spend 1-10000 owned points on an
  enabled known stat. The server checks the balance.
- `/rotas stats points_per_level <0-1000>`: OP 2; persisted and audited.
  Defaults to 0, so this batch grants no new default stat points.

No stat definitions or sample rewards are installed automatically.

## Wallet and reputation transactions

New action bodies use the shared ActionEngine:

```json
{"type":"currency","id":"rotas:gold","amount":25}
```

`reputation` uses the same fields. Amounts are integers from -1000000 to 1000000.
`{"type":"stat_points","amount":2}` grants 1-10000 points. Compose these into
existing reward definitions; staged balances, allocations, variables, unlocks
and the reward receipt commit together into the same player record. Insufficient
funds, overflow, staging failure or a conflicting profile mutation prevents that
commit. Retry the same server-issued occurrence to obtain ALREADY_CLAIMED.

Typed reputation takes precedence over legacy `reputation.<faction>` quest
variables. The first typed transaction starts with the legacy value; subsequent
legacy reputation rewards write the typed value if it exists.

New numeric condition facts are `player.stat_points`, `currency.<id>`,
`reputation.<id>`, `mastery.<id>` and `stat.<id>`. RotasApi adds server-thread
read methods for stats, currency and reputation. Clients cannot submit arbitrary
wallet mutations or reward receipts. The old RewardService's physical effects
and weighted retry behavior do not gain transactional guarantees from this work.

## Progress networking

Clients advertise the versioned chunk channel through Architectury, or negotiate
using a two-byte version/operation request. Existing progress packets also carry
a version hint. Updated clients can initiate negotiation without receiving a
legacy snapshot first. Old peers retain the existing full-snapshot protocol.

Negotiated snapshots have a connection epoch, monotonic revision and exact base
revision. Only changed top-level fields and removed keys are sent; unchanged
snapshots emit nothing. Missing baselines request a full snapshot; duplicate and
older frames are ignored. Profile application retains the existing ClientState,
screen refresh and level-up effects. Disconnect clears client assembly state.

Chunks are at most 32 KiB with a 1 MiB assembled cap, ordered indices and a
10-second assembly deadline. A single transfer is retained at a time. Server
control requests are limited to one per player every 2 seconds. Legacy snapshots
are capped at 512 KiB; oversized snapshots are withheld with an administrator
diagnostic, preserving the authoritative profile. Updated peers can negotiate a
chunked resync; profiles above the assembled cap need administrator review.
Existing content-definition packets are outside this progress-only protocol.

## Validation and remaining gate

`gradlew :common:test :forge:build :fabric:build` passes 41 tests. These cover
multi-level gain, caps, overflow, invalid XP, v1 migration, private fields, stat
math, insufficient funds, conflicts, receipt persistence, delta application,
replay, resync and chunk bounds. A one-field XP delta is under 256 bytes for a
test profile whose full frame exceeds 20 KiB; this is a payload test, not a live
multiplayer bandwidth benchmark.

`scripts/rpg-console-smoke.py` runs an isolated Java 17 Forge server with a
development-only test mod. It checks real attribute application/cache cleanup,
multi-level callbacks, FakePlayer death clone, dimension-independent storage,
permissions, durable wallet receipts and world restart. The test mod is excluded
from production jars. Architectury's known development worker shutdown issue is
handled by checking server/kernel thread termination before cleaning up only the
test JVM; this is not a verified clean production JVM exit.

Authenticated login/logout, actual death/respawn/dimension travel, client packet
exchange and full PROJECT-RC gameplay remain unverified. The Phase 2 runtime gate
is therefore still open. Later platform phases and production soak/load testing
are not complete. The installed JAR remains untouched because its inventory
renderer differs from the source baseline.
