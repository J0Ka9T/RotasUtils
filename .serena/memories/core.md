# RotasUtils core
- Architectury multi-loader Minecraft mod. Most gameplay/domain code lives in `common/src/main/java/net/schwarz/rotasutils`; Fabric/Forge modules are thin platform shims.
- Main common init: `Rotasutils.init()` wires monster environment before service construction, then registry, network, events, commands.
- Persistent world state is centralized in `RotasData` (quests/boards/NPCs/skills/jobs/stats/zones/houses/player progress/settings/history/counters).
- Newer data-driven RPG kernel lives in `RpgKernel` + `core/ContentRegistry`; it validates/prepares immutable content snapshots, revisions/history/drafts, monster/item/kernel-quest/merchant content, rules/actions/rewards.
- There are currently two live quest/content paths: legacy `QuestDef` + `QuestService` drives board/NPC/player quest UI; kernel `QuestDefinitions` + `QuestKernelService` is used by kernel console/UI/commands. Treat this as intentional-but-costly dual architecture unless consolidated.
- Runtime domain services are under `server/`; client UI/editor/HUD under `client/`; Mixins under `mixin/`; cross-mod integrations under `compat/`.
- For stack/build pins read `mem:tech_stack`. For commands read `mem:suggested_commands`. For code patterns read `mem:conventions`. For completion gates read `mem:task_completion`.