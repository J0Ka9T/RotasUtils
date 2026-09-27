# Quest objective reliability

The existing world-saved `QuestDef` / `ActiveQuest` path remains authoritative. The NPC demo loader can construct these definitions from JSON and publish them through `RotasData`; no second quest progress store is required.

## Content API

- Create `QuestDef(id)`, set name/description, `setPublished(true)`, and add objectives with `quest.objectives().add(objective)`.
- Set a hard level gate with `quest.setRequiredLevel(level)`. `QuestService.blockedReason` returns both the requirement and the current player level. Recommended level remains advisory.
- Collect: `new Objective(COLLECT_ITEM)` with `params().put("item", "minecraft:iron_ingot")`, `amount=8`, `consume=true`. `item_tag` may select a loaded item tag instead. Empty `item_tag` uses `item`.
- Kill: `new Objective(KILL_MOB)` with `entity="minecraft:zombie"`, `amount=3`.
- Reach: `new Objective(REACH_LOCATION)` with `pos="100 64 100"`, `radius=8`, `dimension="minecraft:overworld"`. Position uses the existing spherical area matcher; the radius boundary is exclusive.
- Ordered talk: `quest.setObjectiveMode(SEQUENTIAL)`; two `TALK_NPC` objectives with `setStep(0)` and `setStep(1)`. Set `npc_uuid` to the bound entity UUID for exact identity, or `npc_name` for a configured name. Different step numbers are required; equal steps run together. A single event uses an unlock snapshot and cannot advance a step that was locked when that event began.
- Give each objective a readable `setDescription(...)`. The detail screen shows current counts, a current unlocked required objective, and readiness.

## Inventory and turn-in

`ObjectiveEngine.refreshInventory(player, data)` should run on the existing once-per-second server tick. Acceptance and turn-in also call it. It counts the main inventory and offhand. Already-carried items count, and dropping/spending items reduces progress and revokes readiness. Raw pickup events still reach the RPG kernel but do not permanently credit legacy collection counters.

`QuestService.turnIn` refreshes inventory, checks required completion, then plans all consumable collection costs before modifying any stack. Duplicate item costs cannot spend one stack twice. A failed plan consumes nothing; a successful plan removes exactly the reserved quantities. Tag and item-detail matching use the same matcher as objective events. Non-consuming collection objectives only require possession.

Legacy collection objectives inside alternative groups still lack a persisted winning-branch identity. The conservative debit requires the configured collection clauses; use ordinary collection objectives in the demo. This branch format needs a separate migration before claiming mixed collect/noncollect alternative turn-ins.

## Evidence

`QuestObjectiveReliabilityTest`: six behavioral failures reproduced before implementation; then all six passed in `:common:test --configure-on-demand --offline --tests '*QuestObjectiveReliabilityTest'`. Coverage: dropping items revokes completion, duplicate costs are atomic, exact consumption and insufficient retry, sequential collection gating, malformed item ID rejection, malformed tag rejection. Log: `build/quest-reliability-test.log`.

These are bootstrapped Java regressions and common compilation evidence. The live Forge test world must still verify pickup/drop HUD refresh, NPC A/B order, kill attribution, dimension/radius matching, turn-in and save/restart persistence.
