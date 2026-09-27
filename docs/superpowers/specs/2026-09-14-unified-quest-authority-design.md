# Unified Quest Authority Design

## Goal

RotasUtils will have one server-authoritative quest runtime. Boards, NPC conversations, player screens, HUD, commands, kernel UI, events, persistence, requirements, and rewards will all read and mutate the same quest definition and the same per-player quest progress.

## Current problem

The established runtime uses `QuestDef`, `QuestService`, `ObjectiveEngine`, and `ActiveQuest`. The kernel runtime separately uses `QuestDefinitions.Quest`, `QuestKernelService`, and `rpg.q.*` variables. Both consume gameplay events and both can accept, complete, and reward quests. Sharing the outer `PlayerProgress` record does not make them consistent because their active state, completion state, limits, reset rules, and reward paths remain separate.

## Decision

The established `QuestService` runtime remains the gameplay authority because it already owns the complete player-facing flow and the documented save format. Kernel quest content remains supported, but `QuestKernelService` stops being an independent progress engine. It becomes a compatibility facade that resolves kernel content into canonical quest definitions and delegates every player mutation to the canonical runtime.

This is a staged migration. Existing worlds must remain playable at every stage, and old `rpg.q.*` progress is removed only after it has been converted successfully.

## Canonical model

### Definition

`QuestDef` becomes the only executable quest definition. It gains optional staged quest metadata rather than creating another top-level quest type:

- stable objective keys;
- one or more ordered stages;
- conditional branches between stages;
- stage rewards;
- reset window and global bounty limit metadata;
- an optional kernel content ID and source revision for traceability.

A normal existing quest is treated as one stage containing its current objective list. Existing serialized quests therefore require no content migration.

Kernel pack parsing may retain an intermediate parse-only record, but that record cannot be accepted, progressed, completed, claimed, displayed as live state, or rewarded. During content preparation it is compiled into a `QuestDef`. Compilation failure disables that content definition and reports a diagnostic before publication.

### Progress

`ActiveQuest` and the existing `PlayerProgress.activeQuests`, completion history, and cooldown maps are the only quest progress store. `ActiveQuest` gains canonical stage index and keyed objective progress while preserving its existing index-based data for backward compatibility. A migration helper maps old objective indices to stable keys when loading or first touching a quest.

Kernel `rpg.q.*` variables are read-only migration input. New gameplay writes never create or update them.

### Requirements and rewards

Acceptance always passes through `QuestService.blockedReason` and canonical board/NPC source validation. Kernel conditions used by quest packs are represented as canonical conditional requirements evaluated through `KernelPlayerContext`; they do not bypass established level, visibility, maximum-active, cooldown, party, or source rules.

Stage and final kernel reward IDs are represented by canonical reward adapters. Granting still happens through one `QuestService` transition, with receipts recorded before payout. Existing `RewardService` rewards and kernel `RewardEngine` rewards may have different implementations, but neither can independently change quest state.

## Runtime flow

1. A gameplay event enters `ObjectiveEngine` once.
2. `ObjectiveEngine` evaluates the active quest's current canonical stage.
3. Matching keyed objectives advance atomically on the server thread.
4. When a stage completes, the branch evaluator selects the next stage and stage rewards are receipted and granted.
5. When the final stage completes, the quest becomes ready for the existing turn-in flow or immediate claim according to its source policy.
6. Completion history, cooldown/reset state, bounty reservation, rewards, season rank points, audit records, and client synchronization are committed through the canonical service.
7. Every UI receives the same snapshot derived from canonical definitions and progress.

`RpgKernel.emit` continues to feed content rules and other kernel subsystems. It no longer calls an independent quest progression loop. Quest-related kernel events are normalized into the canonical `QuestEvent` envelope or passed to `ObjectiveEngine` through a single explicit adapter.

## Compatibility facade

`QuestKernelService` remains temporarily so existing commands and `KernelUi` packet names do not break. Its methods delegate as follows:

- `available` queries canonical published quests originating from kernel content;
- `accept` calls canonical acceptance with a defined source policy;
- `abandon` calls canonical abandonment;
- `claim` calls canonical turn-in/claim handling;
- `active`, `stage`, `progress`, and `completedAt` read canonical progress;
- `handle` is removed from the event path and later deprecated.

The facade returns accurate results. In particular, successful abandonment returns a success result rather than `NOT_ACTIVE`.

## Content identity and conflicts

Canonical quest IDs are strings. A kernel `ContentId` maps to its full value without lossy shortening. Publication rejects a kernel quest when its mapped ID collides with an independently authored world quest unless both definitions carry the same source identity and revision. Silent overwrite is forbidden.

Only compiled and published canonical definitions appear in boards, NPC selectors, player screens, HUD, kernel UI, or commands.

## Existing-world migration

Migration is versioned, server-side, repeat-safe, and performed per player after canonical kernel definitions are available.

For each kernel-origin quest:

1. If canonical progress already exists, it wins and old variables remain untouched until cleanup eligibility is recorded.
2. Otherwise, read `stage`, `done`, and keyed `p.*` values from the old `rpg.q.*` namespace.
3. Validate the stage and objective keys against the compiled definition.
4. Create the equivalent canonical active or completed state.
5. Copy cooldown/reset information where it can be represented exactly.
6. Record a migration marker containing the content ID and definition revision.
7. Save canonical state successfully before deleting old keys.

Malformed or unmappable state is retained and logged with a bounded diagnostic. It is never silently discarded and never pays rewards during migration. Migration itself cannot grant stage or final rewards.

## Failure and transaction rules

- All quest mutations occur on the Minecraft server thread.
- Client packets contain intent only; the server revalidates quest ID, source, permissions, state, and limits.
- Duplicate or replayed completion/claim requests cannot pay twice.
- A failed bounty reservation does not consume the player's claim.
- A reward failure keeps a recoverable receipt/state instead of reverting to an exploitable pre-completion state.
- Content reload does not reinterpret active progress silently. Incompatible revisions use the existing objective migration policy and surface diagnostics.
- Missing kernel content leaves migrated canonical state visible but unavailable for new acceptance until the definition returns.

## UI behavior

The existing board, NPC, player quest, and HUD interfaces remain the primary player experience. The kernel quest section becomes another view of the same quests, not a separate quest screen with separate state. Labels may identify pack-sourced quests, but actions and status must match every other UI.

No client-side class owns or mutates quest progress.

## Rollout sequence

1. Add pure conversion and identity validation from parsed kernel quests to canonical definitions.
2. Extend canonical definitions/progress for stable objective keys, stages, branches, reset windows, bounty limits, and reward adapters.
3. Add versioned, repeat-safe old-state migration with tests for partial and malformed data.
4. Replace `QuestKernelService` mutation logic with canonical delegation.
5. Route kernel event facts through `ObjectiveEngine` exactly once and remove the second quest loop.
6. Change `KernelUi` and commands to read the canonical snapshot.
7. After migration coverage and live save/restart verification, stop writing and then prune migrated `rpg.q.*` keys.

Each step must compile and keep existing ordinary quests usable. Cleanup is last, not part of the initial cutover.

## Verification

Automated regression coverage must prove:

- an ordinary one-stage quest behaves exactly as before;
- a kernel-origin quest appears in the established board/player query path;
- the same quest cannot be independently accepted in both APIs;
- one event increments an objective once, not twice;
- abandon, completion, turn-in/claim, cooldown, repeat windows, and active limits agree across APIs;
- branch selection and stage rewards occur once;
- final rewards and season rank points occur once;
- bounty-limit failure is atomic;
- migration is repeat-safe and never grants rewards;
- malformed legacy variables are retained and diagnosed;
- save/reload preserves migrated progress;
- stale client packets cannot mutate invalid state.

Required project gates are `:common:test`, `:forge:build`, and `:fabric:build`. Runtime verification must include a Forge dedicated-server save/restart test and a real client check covering board, NPC, HUD/player quest screen, and kernel UI views of the same quest.

## Out of scope

- Replacing the broader RPG kernel used by monsters, equipment, merchants, stats, or content rules.
- Redesigning the visual style of quest screens.
- Deleting compatibility commands or packet operation names during the first migration release.
- Automatically guessing conversions for unsupported conditions or rewards; unsupported content fails validation with a precise diagnostic.

## Completion criteria

The refactor is complete only when there is one executable quest definition per ID, one canonical progress record per player and quest, one event-processing path, one reward/claim transition, and all UIs report identical state. Old kernel quest variables may remain only as inert migration backups during the compatibility window.
