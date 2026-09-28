# Systems added 2026-09 (breeding, service NPCs, titles, level/XP, admin tools)

Every number below lives in `season.json` and shows in **Admin > Season rules** with a Thai name and hint.
From a console or RCON use `/rotas season find|get|set|reset|changed` (paths tab-complete).

## Horse breeding (`horse.*`)

- At a stable NPC, tab **ผสมพันธุ์**: pick your horse, then a partner (your own, or another player's stud).
- Fee = `breedBaseCost + older lineage × breedCostPerLineage` (+ stud fee). The foal waits unborn in a stable
  slot for `gestationMinutes`; traits, coat and levels are revealed at birth.
- Foals inherit `breedLevelInheritance` of the parents' trained levels, each parent trait at
  `breedTraitInheritChance`, and mutate a new trait at `breedMutationChance`. Two parents with 2+ traits each can
  produce the mythic **Starborn** (`starbornChance`, announced server-wide).
- Parents, children, siblings and half-siblings cannot breed. Each horse breeds `breedsPerHorse` times
  (+`fertileBonusBreeds` with Fertile) with a `breedCooldownMinutes` rest.
- **Stud service**: "รับเป็นพ่อพันธุ์" lists a horse for other players; the fee goes to the owner (market fee
  applies unless the horse has Showstopper).
- 14 traits: combat (Warhorse, Ironhide, Valiant, Windrunner, Stalwart), economy (Golden Blood, Showstopper,
  Prized Line, Fertile), life (Forager, Trailblazer, Surefoot, Peddler), mythic (Starborn). Drawn horses roll
  traits by rarity (`gachaTraits`); horses from before this update get theirs once, the first time they are seen.

## Service NPCs (`npcServices.*`)

`/rotas npc quick <role> [name]` places a ready villager NPC; refine it with the NPC wand/editor.

| Role | Does |
|---|---|
| Blacksmith | repair hand/all, refine, salvage, sockets |
| Enchanter | disenchant into a book (curses stay), runes, sockets |
| Alchemist | drink a brew on the spot (`brews`) |
| Innkeeper | rest: heal, feed, cleanse, **Rested** EXP buff; set respawn |
| Priest | free daily prayer, cleanse, blessing, lift curse |
| Fortune teller | EXP fortune for one activity (or a bad omen) + a true rumour |
| Banker | deposit coins, withdraw steps |
| Bounty master | daily contract board (same for everyone), nemesis wanted posters |
| Guard | area danger, nearest waystone, events, nemesis reports |
| Trainer | stats, skill tree, job, respec |
| Cartographer | waystone travel, nearest unfound waystone, bestiary |
| Collector | buys materials; daily picks pay `collectorBonus`×; gives trading EXP |
| Auctioneer | player auction house (own save file, fee, expiry, claim box) |

## Titles (`titles.*`)

- Every title earned pays `rarityGold`/`rarityXp` once and adds `rarityPoints` collection points.
- `collection` tiers are permanent bonuses for total points, whichever title is worn.
- New conditions: BOUNTY, BREED, BESTIARY, WAYSTONE, TITLE_COUNT, DEATH, TRADE; 14 new titles seed once as batch
  `expansion_v2`. Epic+ titles are announced; worn legendary titles glow (`legendaryAura`).

## Level and EXP (`milestones.*`, `exploration.*`)

- Exploration EXP (scaled by level): first entry to each zone, new waystone, first kill of a monster kind,
  vanilla advancements by frame, every `travelBlocks` travelled.
- Variety: mixing fighting / gathering / crafting / exploring / questing / trading within
  `varietyWindowMinutes` adds `varietyBonus` per extra kind, up to `varietyMax`.
- Milestones every `every` levels pay gold and stat points; `bigLevels` are announced and pay more.
- Timed buffs (Rested, fortunes) and variety stack with horse traits and events on every EXP source.

## Admin tools

- Season editor: search matches hints; **เฉพาะที่แก้** lists every value changed from default; presets
  (ง่าย/ปกติ/ยาก) for breeding, NPC services, milestones and exploration.
- `/rotas season changed` prints the same list in chat.
