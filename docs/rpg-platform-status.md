# Full platform implementation status

The objective remains every phase in the supplied implementation brief. This
file is an index of evidence and remaining work, not a reduced definition of done.
The original brief remains the acceptance checklist for each named subsystem.

| Phase | Current evidence | Remaining gate/work |
| --- | --- | --- |
| 0 Audit | Source/installed comparison, baseline builds, architecture checkpoint | Resolve installed inventory renderer/source discrepancy before deployment |
| 1 Kernel | Shared registries/conditions/actions/rewards/events, strict packs; unit/server checks | Full expression system and remaining shared action/condition adapters as consumers are implemented |
| 2 Progression | UUID persistence, overflow-safe XP, stats cache/attributes, wallets/reputation, negotiated deltas | Authenticated lifecycle/network checks; equipment/Curios/skill/buff modifiers; full mastery rules and remaining progression options |
| 3 Admin framework | Persistent drafts/revisions/rollback, permissions, audit, commands, editor shell; unit/server checks | Real client editor/transport verification; broader permission adapters and production retention tooling |
| 4 Monsters | Data-driven profiles/tiers/affixes, seeded assignment, derived attribute scaling, per-entity persistence, name display, hooks, commands; unit and dedicated-server checks including restart restore | Loot multiplier consumers (Phase 5), region/world-tier facts (Phase 9), boss phases (Phase 6), authenticated client verification, Fabric runtime verification |
| 5 Items/loot | Item/rarity/set/loot definitions, stack-carried data, level and rarity scaling, requirement sweep, Curios and set bonuses, seeded loot, mailbox recovery, monster loot multiplier consumed; unit and server checks | Durability/sockets/reforging, instant equip gating (currently a one-second sweep), Curios verified only through the reflective bridge |
| 6 Bosses | Boss definitions bound to monster profiles: descending phases with owner-scoped modifiers, enter/interval actions, summons, arena leash and reset, enrage, persisted contribution ledger, share-gated payouts; unit and server checks | Offline contributor payouts, authored region arenas (Phase 9), boss bars and presentation |
| 7 Quests | Kernel quests beside the legacy board system: staged objectives over kernel events, branches, DAILY/WEEKLY/COOLDOWN resets, world bounty limits, one transaction per mutation, receipted claims; unit and server checks | Timed/escort objectives, shared party progress, quest UI, retroactive claims |
| 8 Economy/trade | Merchant definitions with mixed currency/item costs, windowed world stock, per-player limits, reserve-then-commit purchase with rollback compensation, RPG stacks rejected as vanilla payment; unit and server checks | Merchant screen and villager binding, player-to-player trading, long-run duplication soak |
| 9 World | Existing party support only | Factions, indexed regions, world tiers/events, encounter director, persistence/budgets/load tests |
| 10 Editors | Reusable generic content draft editor plus the runtime console (`/rotas ui`): list-and-detail control of monsters, items, loot, bosses, quests and merchants over a bounded server snapshot, with per-operation permission checks; catalog round-trip and server-operation checks | Specialized authoring editors and simulators per backend, quantity controls, and real client verification of the screens themselves |
| 11 Hardening | Bounded unit tests and isolated Forge smoke | Soak, full pack, player/entity load, profiler/memory/packet analysis, corruption/recovery, duplication/exploit and compatibility gates |

Current evidence: 93 unit tests, including a check that every shipped sample pack
still compiles; Forge/Fabric builds; isolated dedicated Forge server checks for
drafts/apply/invalid/rollback/restart, player records, monster
assignment/scaling/storage/kill-reward/restart, item scaling and requirement
sweeps, deterministic loot and mailbox recovery, boss phases/reset/contributions,
quest stages/resets/bounties and merchant stock/limits/atomicity. Consult
`rpg-kernel.md`, `rpg-progression.md`, `rpg-admin.md`, `rpg-monsters.md`,
`rpg-items.md`, `rpg-bosses.md`, `rpg-quests-economy.md`, `rpg-ui.md` and `.rotas-workstate.md`.

Known limits must remain visible until verified: legacy weighted reward retries,
authenticated client gameplay, installed artifact/UI mismatch, Architectury's
development classpath worker shutdown issue, and the Fabric monster bridge, which
compiles and mirrors the Forge wiring but has no runtime evidence because the
smoke harness runs a dedicated Forge server. The new phases add their own visible
limits: item requirements are enforced on a one-second sweep, Curios support is a
reflective optional bridge, boss payouts skip offline contributors, and the console
screens have no automated client-side test. Full completion is not yet proven.
