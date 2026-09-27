# Easy NPC development runtime and adapter

Minecraft 1.20.1, Forge 47.4.22 and Fabric Loader 0.19.3 use Easy NPC **7.12.1**. Both loader Gradle files load Core and Config UI for `runClient` / `runServer`; these remain optional development dependencies and are not bundled into RotasUtils. Do not put duplicate Easy NPC jars in `dev-mods`.

| Loader | Core Modrinth version | Config UI version | Minimum upstream loader |
|---|---|---|---|
| Forge | `b2koOKsW` | `swcRILIX` | Forge 47.4.10 |
| Fabric | `WBGtJwAc` | `isvZOPEP` | Loader 0.18.3; Fabric API 0.92.9+1.20.1 |

Versions were checked against Modrinth release metadata and actual downloaded jar manifests on 2026-09-10. The `easy-npc` project now distributes a small dependency bundle; direct Core and Config UI pins avoid relying on unpinned bundle dependencies.

Forge Config UI 7.12.1 has an upstream packaging error: its manifest references `easy_npc.forge.mixins.json`, a resource present only in Core. Architectury Loom rejects this during remapping. `forge/build.gradle` creates a local derived runtime jar under `forge/build/easy-npc`, keyed by the original SHA-256, removing only that duplicate manifest field. Core's real mixin config remains enabled. The original downloaded jar is untouched. An archive comparison verified every other entry is byte-identical; original Config UI SHA-1 is `e9cd504114a48303e6520ed643f20c62976274a8`. Review/remove the workaround on an upstream version change. `gradlew help` successfully configured and remapped both dependencies after this fix.

## In-world editing

Give an administrator `/give @s easy_npc_config_ui:easy_npc_wand`. Right-click an Easy NPC with the wand to open Config UI; shift-right-click rotates it toward the player. Upstream checks access when opening configuration. `/easy_npc configure <npc>` is another supported entry point, with upstream target/access parsing. The humanoid entity ID is `easy_npc:humanoid`.

Rotas' interaction hook calls `EasyNpcCompat.isEditorInteraction` before talk objectives or dialogue. Easy NPC tool interactions pass through; the upstream mod owns tool access and behavior, so the wand keeps working even on a Rotas-bound NPC. A pending world-selection click with the RotasUtils admin tool also passes through, so the picker resolves instead of opening a bound NPC's dialogue. Rotas dialogue and quest branching use Rotas JSON and server state. `stateFor` is a Java helper only: it does not register an Easy NPC dialogue condition.

## NPC Wand (the normal way to make and edit NPCs)

Admins get the wand from Admin > NPCs > **Get the NPC Wand** (or `/give @s rotasutils:npc_wand`).

- Right-click any mob, whether a villager, an Easy NPC humanoid or a modded mob. If it is not an NPC yet, it becomes one: it is named from its custom name or entity id and placed on that mob, and the editor opens.
- Right-click an existing NPC to open its editor.
- Shift + right-click an NPC to test talking to it.
- Right-click the air to open the NPC list.
- While you hold the wand, the action bar says what the next click will do.

The click is handled in `RotasEvents.onInteractEntity` before the mob's own interaction, so a villager trade screen or an Easy NPC dialog does not open on top of the editor. The Easy NPC wand still goes to Easy NPC.

The editor (`NpcEditorScreen`) has four pages: **Who**, **What they do** (role cards: just talks, gives quests, opens a quest board, runs a shop), **What they say** (six plain lines, plus the advanced branching editor) and **Rules** (level, distance, the mob's own menu, marker, stand still, can be hurt, name above head). A live model of the mob is on the left. **Save** applies at once through `save_npc`. **Place on a mob**/**Move to another mob** and **Detach from mob** apply immediately, like the binding described below.

### What each role does on right-click

`NpcService.onInteract` routes by role, so the role cards now change behaviour, not just a label:

- **Just talks**: dialogue only. Quests and the shop are not offered (`offers()` is empty for this role).
- **Gives quests**: dialogue with the quest offers, as before.
- **Opens a quest board**: the board browser opens right away.
- **Runs a shop**: the shop opens right away.

### Simple shop

A shop NPC holds up to 32 trades (`NpcDef.Trade`: pay item, optional second pay item, result item; NBT keys `trades`/`pay`/`pay2`/`get`). In the editor, **+ Add trade** opens `TradeEditScreen`, where each item is picked from the item browser by picture and counts use -/+ (Shift = 10). Click a trade to change it; "remove" deletes it. The trades open as the vanilla villager trading screen through `server/NpcMerchant`, which implements `Merchant` for any mob; trades never run out. A content-pack merchant (`merchantId`) is still supported as the advanced option and is used only when the NPC has no simple trades.

### Mob options

`NpcService.applyEntityOptions` runs on bind and on every save: the mob is made persistent, **Stand still** turns its AI off, **Can be hurt: No** makes it invulnerable, **Name above head** sets a visible custom name, and an Easy NPC behaviour from the branching conversation is applied only when the NPC does not stand still. Before this pass none of these reached the mob.

## Binding an entity to a character

In the NPC editor, Bind To Entity closes the editor and asks for a world click with the RotasUtils admin tool. The click resolves server-side (`WorldPicker.resolveEntity`), and the binding - uuid, entity type and dimension - is applied to the live character immediately through `NpcService.bind`, audited, and released from any other NPC bound to the same entity. It survives a closed editor, a restart, or an admin who never reaches the Save/review flow. Release binding works the same way and is just as immediate. Name, dialogue lines, quests and role edits still travel through Save -> Save Draft -> Review Changes -> Apply. "Take over the click" is on by default, so a bound NPC answers with the Rotas dialogue and suppresses the entity's own interaction, including Easy NPC dialog actions configured in the upstream editor.

Immediate changes stay consistent with the review flow: binding and releasing refresh every client's synced content (so a reopened editor never carries a stale baseline into Save), and any live change to a character - bind, release, quest assignment, deletion, direct save - discards a staged draft for that character, because the draft's recorded base can no longer match live values. `ConfigHistory.stage` also re-bases on the current live snapshot on every stage, and the review screen offers Discard Draft whenever a draft is retained, so an admin can always recover instead of being stuck on Apply.

## Supported adapter

`createDemoNpc(ServerLevel, Vec3, UUID owner, String name)` creates, finalizes and adds a persistent humanoid guide. It returns the added entity, or null if the dependency/type is missing or the world rejects the entity. It must run on the server thread after caller permission checks.

`configureBehavior(Entity, String movement, String combat, UUID owner)` accepts the following lowercase options. It saves the existing entity NBT, replaces the native objective list and optionally owner UUID, then loads that state. Existing appearance, equipment and other NPC configuration survive. It stops the old path and clears the target. A follow configuration without an owner is rejected. The caller owns permissions and persistence of its chosen Rotas configuration.

| Rotas value | Native Easy NPC objectives |
|---|---|
| stationary | LOOK_AT_PLAYER, LOOK_AT_RESET |
| wander | Above plus RANDOM_STROLL |
| follow | Above plus FOLLOW_OWNER |
| patrol | Look-only native objectives; Rotas owns the saved waypoint route and navigation ticks |
| passive | No attack or retaliation objectives |
| defensive | HURT_BY_TARGET and MELEE_ATTACK |
| hostile | Defensive objectives plus ATTACK_PLAYER_WITHOUT_OWNER |

Movement and combat are combined. Stationary means no idle roaming; defensive/hostile combat can chase a target. Passive does not make an NPC invulnerable. Easy NPC 7.12.1 has no native PATROL objective.

Native objective NBT uses `ObjectiveData:{ObjectiveDataSet:[{Type:"RANDOM_STROLL",Prio:11}]}`. Owner is the vanilla UUID-int-array `Owner` field. The adapter uses data version `EasyNPCVersion:3`, `VariantType:"STEVE"` and `SkinData:{Type:"DEFAULT"}` for new guides. Existing NPC objectives are intentionally replaced when applying a Rotas behavior preset.

Skin configuration stays in the upstream editor: DEFAULT, PLAYER_SKIN, CUSTOM, RESOURCE_LOCATION, SECURE_REMOTE_URL and INSECURE_REMOTE_URL are native types. SkinDataEntry serializes `Type`, `Name`, `URL`, `UUID`, `Texture`, `DisableLayers` and `Timestamp` as applicable. Prefer editor controls because custom texture caching and player-skin resolution are managed upstream.

## Primary evidence and validation

- [Core releases](https://modrinth.com/mod/easy-npc-core/versions), [Config UI releases](https://modrinth.com/mod/easy-npc-config-ui/versions).
- [Upstream source snapshot](https://github.com/MarkusBordihn/BOs-Easy-NPC/tree/fa5a3a2b5955153fc513bc4daab0c90639f4ea3d), 1.20.1 branch: `core/Common/.../entity/easynpc/data/OwnerDataCapable.java`, `ObjectiveDataCapable.java`; `data/objective/ObjectiveDataEntry.java`, `ObjectiveType.java`; `data/skin/SkinDataEntry.java`; `api/npc/raw/NPCRawTemplate.java`; `config-ui/Common/.../item/configuration/EasyNPCWandItem.java`.
- Upstream humanoid base preset: `core/Common/src/main/resources/data/easy_npc/api/preset/base/humanoid.npc.snbt`.

Configuration/remapping success is verified. Final common/loader compilation and actual world/editor/behavior/save-restart checks are recorded in the main NPC progression task report; this document does not claim runtime behavior solely from source inspection.
