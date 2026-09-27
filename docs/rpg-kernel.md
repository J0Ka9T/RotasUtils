# RPG kernel, schema 1

This is the Phase 1 foundation. It extends RotasUtils' existing player records,
requirements, lifecycle bridge and commands. Legacy quest/board/skill definitions
stay in SavedData and are not converted or overwritten by pack reloads.

Phase 2 adds stats, wallets, reputation and progress delta networking; see
[rpg-progression.md](rpg-progression.md). Phase 3 adds persistent revisions,
draft editing and rollback; see [rpg-admin.md](rpg-admin.md). Remaining full-platform
work is tracked in [rpg-platform-status.md](rpg-platform-status.md).

## Packs

Put UTF-8 `.json` definitions in `config/rotasutils/packs/<layer>/<pack>/...`.
Layers, lowest to highest: `core`, `server`, `season`, `event`, `hotfix`.
One JSON object per file. Paths are diagnostic labels; the explicit namespaced
`id` is the identity. IDs are unique across all kinds. Equal-priority duplicates,
kind-changing overrides, unknown fields and unsupported schema versions fail
validation. Higher-priority overrides retain their source/layer in diagnostics.

```json
{
  "schema": 1,
  "id": "rotas:action/welcome",
  "kind": "action",
  "body": {"type": "set_variable", "key": "rpg.welcomed", "value": "yes"}
}
```

`enabled` defaults to true. Disabled definitions can omit `body`; their identity
remains inspectable, but enabled definitions cannot reference them. Optional
`requires` is an array of definition IDs. Cycles and missing dependencies fail
the candidate as a whole. Files on disk are never rewritten by the loader.
Unknown future schemas require an explicit migration; no lossy fallback occurs.

Kinds:

- `condition`: `body` is a condition tree.
- `action`: `body` is one validated staged action.
- `reward`: `body` has `actions` (array of action IDs) and optional `condition`.
- `rule`: `body` has `event`, `scope` (`once` or `occurrence`), `reward` ID and
  optional `condition`. Rules are indexed by event, not scanned every tick.
- `stat`: bounded logical stat formula and optional attribute mapping; see progression docs.

See `examples/rpg-packs/server/welcome` for a working, opt-in login example.
Copy the example's `server` directory under `config/rotasutils/packs` to enable it.

## Conditions and actions

Condition forms:

- `{"type":"always"}`
- `{"type":"ref","id":"rotas:condition/eligible"}`
- `{"type":"number","name":"player.level","op":"ge","value":10}`;
  operators `eq`, `ne`, `ge`, `gt`, `le`, `lt`.
- `{"type":"text","name":"player.dimension","value":"minecraft:overworld"}`.
- `{"type":"and","children":[...]}`; also `all`, `or`, `any`, `not` (one child),
  `at_least` (also needs `count`). Empty groups are rejected.
- `{"type":"requirement","name":"MIN_LEVEL","params":{"level":"10"}}`.
  Adapts the existing RequirementChecker. Supported names: MIN_LEVEL, MAX_LEVEL,
  HAS_ITEM, DIMENSION, PERMISSION, PRESTIGE. Params use strings with strict type
  validation. PERMISSION uses OP `op_level`; named nodes require a future adapter.
  `consume:true` is rejected because condition evaluation cannot consume items.

Facts: `player.level`, `player.xp`, `player.total_xp`, `player.skill_points`,
`player.dimension`, `player.biome`; `rpg.*` player variables; `event.*` facts supplied
by the event producer. Unavailable/non-finite facts fail the rule, even under NOT.

Staged actions: `set_variable` (`key`, string `value`), `add_variable` (`key`, integer
`amount`, checked 64-bit arithmetic), `unlock` (namespaced quest `id`). Variables
are restricted to `rpg.*` so content cannot modify existing preference keys.
Unlocks reuse PlayerProgress.unlockedQuests; they do not create quest definitions.

## Transactions and persistence

Kernel rewards stage all actions against copies of the existing player's variables,
unlocks and receipts. Validation/staging failure changes none of them. One
server-thread commit publishes state and receipt together to the SAME SavedData
player record. No new player store or capability is introduced. Existing profile
identity, XP, party, quests and settings remain intact.

Receipt identity includes reward ID and a server-issued occurrence, scoped to the
player UUID. Rule `once` uses a stable rule identity; `occurrence` uses the emitted
event identity. Retries must reuse the same occurrence. Per-kill events use entity
UUID; ordinary repeatable interactions use a fresh event UUID. No client packet
can submit a kernel action, event or receipt. Console reward commands require OP 2.

This gives exactly-once profile mutations for retained receipts and normal saves/
restarts. SavedData writes follow the existing autosave lifecycle; this is not an
fsync-backed external transaction journal. Commands, physical items, XP with
external level-up hooks and arbitrary world mutations are NOT yet transactional
kernel actions. Wallet, reputation and stat-point actions now share the profile
transaction. The legacy RewardService's general effects do not inherit this
guarantee. Its weighted/retry paths need later hardening.

Bounds per profile: 4096 variable keys, 4096 unlocks, 65536 total reward receipts.
At the cap, new claims fail closed; receipts are never silently evicted. Future
retention/epoch policies must preserve replay protection before increasing use.

## Commands and lifecycle

- `/rotas validate`: existing legacy validation plus asynchronous pack validation;
  available from console, retaining existing player validation UI.
- `/rotas reload`: parse/compile on a single bounded loader worker, then publish
  on the server thread. Failed candidates preserve the exact active snapshot.
- `/rotas debug`: revision, content hash, counts and bounded diagnostics.
- `/rotas debug content <id>`: effective kind, enabled state, layer and source.
- `/rotas debug reward <player> <reward-id> <occurrence>`: OP-only test grant;
  repeat the same arguments to verify ALREADY_CLAIMED. Recorded in existing audit.

Startup initializes one kernel per server; shutdown detaches its rule listeners
and stops its loader executor. Reloads never replace addon subscriptions. Revision
numbers and effective definitions now persist with world-owned history. Startup
restores the saved active revision; explicit reload imports disk packs. Authorized
admins can request bounded definition pages through the versioned editor channel.
Player client snapshots omit server reward receipts and `rpg.*` variables so
retained receipts do not inflate existing profile packets. Disk saves retain both.

Existing Architectury events feed the bus (no added Mixins): player login/logout/
respawn, level gains and normalized ObjectiveEngine events. Core kill/craft/obtain/
use/quest completion aliases are `rotas:entity_killed`, `rotas:item_crafted`,
`rotas:item_obtained`, `rotas:item_used`, `rotas:quest_completed`; remaining existing
EventKind names are lowercase under `rotas:`. Raw damage/spawn/boss/world events
will be added alongside their later systems. Death callbacks retain the existing
Architectury event cancellation semantics; no new post-death guarantee is claimed.

`api/RotasApi` exposes content inspection, event subscriptions/emission, levels and
kernel reward grants on the server thread. Pure ConditionEngine/ActionEngine
constructors accept immutable compiler-adapter maps for testability and extension.
Addons register namespaced types through `api/KernelAdapters.condition/action`
during mod initialization. Registrations freeze at first server startup. Compilers
run on the reload worker and must only inspect definitions; condition evaluation
runs on the server thread. Action adapters may ONLY stage operations through the
supplied transaction, never issue external effects or mutate world/player state
directly. Duplicate types and late registration are rejected.

## Validation limits

Loader budgets: 4096 definitions, 256 KiB/file, 16 MiB total, 8192 filesystem entries,
bounded JSON nesting, at most 128 actions/reward, 256 rules/event. Symlinks are
rejected. Duplicate JSON keys and lenient JSON syntax fail explicitly.

Build gate: `gradlew :common:test :forge:build :fabric:build --console=plain`.
Tests include pure kernel behavior and real compressed-NBT persistence. Dedicated
server smoke evidence and outstanding production/modpack checks are tracked in
`.rotas-workstate.md`. No GUI automation is used.

Phase 1 verification on 2026-09-05: 25 tests passed; common tests and Forge/Fabric builds passed.
The loopback Forge 47.4.22 console smoke loaded four definitions, exercised
validation/rejected reload/recovery, saved and restarted the world successfully.
Architectury transformer 5.2.91 leaks non-daemon development classpath workers:
its ReadClasspathProviderImpl calls awaitTermination without shutdown. The test
harness checks that the Minecraft server thread and Rotas loader thread have
terminated, records a thread dump, and cleans up its own lingering test JVM.
This is a development-runtime limitation, not a claimed clean production JVM exit.

The built Forge artifact has not replaced PROJECT-RC's installed JAR: its
inventory renderer bytecode differs from this workspace's baseline. Resolve that
source/artifact difference before deployment so existing UI work is preserved.
