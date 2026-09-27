# NPC, quest and progression implementation

User specification: EasyNPC in-world editing; configurable appearance, movement, combat, dialogue, quest, gift and shop roles; branching quest-aware actions; collect/kill/reach/ordered-talk objectives; gift selectors/rewards/repeat limits; reliable persisted skills across many jobs; meaningful level gates; ready-to-play test world.

Architecture: retain RotasData/PlayerProgress and the existing quest, reward, merchant and skill services. EasyNPC owns entity appearance and native behavior; Rotas NPC definitions bind to entity UUIDs and own server-authoritative interactions. New content is JSON, never a code-only archetype. Keep Forge and Fabric common logic portable; verify the live world on Forge 1.20.1 first.

Execution uses writing-plans, subagent-driven-development, systematic-debugging, TDD, Minecraft Modder/UI and anti-slop guidance. No Git checkout exists; preserve a source backup instead of creating a worktree. Latest request explicitly authorizes a prepared in-game test environment. Do not overwrite an existing player world or deploy to an unrelated profile.

## Work ledger

- [ ] Skill reliability and multi-job progression: audit SkillService/PlayerProgress/SkillTreeScreen, reproduce faults, implement shared prerequisite checks, correct pool persistence/refunds, level gates/effect refresh and synchronization. Add regression tests and a focused report.
- [ ] EasyNPC integration: verify current primary upstream release for both 1.20.1 loaders, pin dev runtime dependencies including editor, inspect supported entity/config APIs, implement safe editor access and document exact spawn/behavior setup. No unverified reflection or invented NBT fields.
- [ ] NPC interaction content: validate bounded JSON dialog/gift definitions, preserve binding and permissions, add server-authoritative response sessions, gift item/tag/list checks, repeat receipts, rewards and visible failure text. Reuse existing journal/objective engine and data storage.
- [ ] Sample adventure: ship editable NPC/quest/job/progression configuration, install an isolated test world with EasyNPC guides, ordered talk/collect/kill/reach quests, gift examples, merchant and visible level gates. Make demo setup repeat-safe and OP-only.
- [ ] Verify: unit regressions, common/Forge/Fabric builds, server save/restart and rejected-action tests, then live client world and editor/dialog/quest/skill checks. Review all changes and record evidence/remaining limitations accurately.

Global invariants: UUID persistence, server-thread mutation, no arbitrary client-supplied commands/rewards, explicit content validation before apply, bounded counts/text/packets, no silent data loss, no duplicate gift payout, preserve Pixel UI/slot geometry. Default content changes only in the explicit demo environment.
