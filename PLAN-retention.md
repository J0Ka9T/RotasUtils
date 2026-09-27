# Plan: reasons to come back tomorrow

Owner brief (2026-09-21): "อยากได้ระบบที่จะทำให้คนติดพันและใช้เวลากับเกมมากขึ้น".
Owner decisions: rotating daily missions with a track, a season pass track on the existing rank
points, and a mining site where a node can be worked but then goes on cooldown. Pressure setting:
**medium** - a missed day loses the day's progress, but nothing is time-limited beyond the day, and
no reward is ever destroyed. Vertical slice first.

Written as a separate file because `PLAN.md` still holds an unfinished plan (its phases 2-4).

**Status (2026-09-21): all three phases implemented, plus five more systems the owner asked for in the
same session** - luck-driven loot, kill combos, elite/champion monsters, the monster book and salvage.
`:common:test` green, `:forge:build` and `:fabric:build` green. Player-facing reference:
`docs/rpg-journey-and-farming.md`. Nothing has been exercised in a running game yet.

### Where the build differs from the plan below

- Daily and season share one page, `JourneyScreen` (tabs TODAY / SEASON), and one command class,
  `JourneyCommands` (`/rotas daily`, `/rotas pass`) - not a separate `DailyScreen` / `DailyCommands`.
- Mining sites live in `RotasData.miningSites` (`mine/MiningSite`), not on zones, and are enforced in
  the Architectury block-break hook in `RotasEvents`, not in `BlockDropsMixin`. A site is a set of
  registered blocks, which needs no zone at all; the payout waits one tick and only happens when the
  block is really gone.
- The single reference is `docs/rpg-journey-and-farming.md`, covering all eight systems.

## Summary

Three loops, built in order, each on top of machinery that already exists: a **session goal** (today's
missions plus a track), a **season arc** (claimable tiers on the rank points already being earned),
and a **place worth returning to** (mining nodes that deplete and come back).

Phase 1 is the vertical slice and is the only phase specified to the file level here.

## Context from the code

What already exists and must be reused rather than rebuilt:

- **Board rotation is already daily.** `BoardConfig.Rotation.DAILY` (86400s) with `rotationSlots`,
  and `BoardService.resolveRotation` persists the chosen set on the board with `lastRotation` /
  `rotationSeed`. Today's missions therefore already have a source of truth - one that is
  server-wide and survives restarts. Nothing new should decide "which missions are today's".
- **Quests already repeat daily.** `QuestDef.Repeat.DAILY`, cooldowns in `PlayerProgress`
  (`questCooldowns`, `lastCompletedAt`), and `QuestService.turnIn` is a single chokepoint.
- **A day already rolls.** `SeasonService.today()` (`LocalDate.toEpochDay`) and the private
  `rollDay` pattern that clears `rpg.*` day variables on `PlayerProgress`.
- **Rank points already accumulate.** `PlayerProgress.rankPoints`, `SeasonService.awardRank`,
  `SeasonRules.rankThresholds` / `rankPerks`, `SeasonMath.rankFor`. What is missing is a *track*:
  tiers a player can claim, not just a letter they are given.
- **Rewards already have a shape.** The drop grades in `season.json` use item lines
  (`"minecraft:diamond 1-3 @0.5"`), parsed by `DropLoot.parse`, delivered by `LootService.deliver`
  with a mailbox fallback. A track tier should pay in exactly that language.
- **The event catalogue exists.** `EventService.fire` already reports quest completions, so the
  track can hang off it rather than adding call sites.
- `ZoneEncounterSchedule` / `ZoneEncounterService` already do "spawn, die, respawn after N seconds
  when a player is near" for mobs. Phase 3's nodes are the same shape for blocks.

## System impact

| | Before | After |
|---|---|---|
| Which missions are today's | `BoardConfig.rotatedSelection` | unchanged - phase 1 reads it |
| How far a player got today | nowhere | `PlayerProgress` day variables (`rpg.daily.*`), cleared on day roll |
| Which track tiers are claimed | nowhere | daily: a bitmask in a day variable; season: `claimedRewards` keys |
| What a tier pays | nowhere | `season.json`, item lines like the drop grades |
| Where a player sees it | walk to a board | a screen in the main menu, plus one line on the first join of a day |

No new store, no new persistence format, no second source of truth for "today".

## Phase 1 (the slice) - Today's missions and the daily track

**The loop:** log in, see three to five missions picked for the day, finish some, pass 1 / 3 / 5
completions to claim escalating rewards, come back tomorrow for a fresh set.

**Pressure setting (medium), stated exactly:** the count resets at local midnight. An unclaimed tier
is lost with the day, so the track is worth claiming the day it is earned; nothing already claimed is
ever taken back, and no tier is limited to a time of day.

### Files

- `common/src/main/java/net/schwarz/rotasutils/core/DailyTrack.java` **(new, pure)** - tier maths:
  which tiers are reached at N completions, which are claimable given a claim bitmask, the next tier,
  and seconds to the next reset. No Minecraft types, so every promise is unit-tested.
- `common/src/main/java/net/schwarz/rotasutils/level/SeasonRules.java` - new `DailyRules daily`:
  `enabled`, `boardId` (which board is "today's missions"), `tiers[]` (`completions`, `gold`, `xp`,
  `items[]`), `announceOnJoin`. Clamped in `sanitize()` like every other block.
- `common/src/main/java/net/schwarz/rotasutils/server/DailyService.java` **(new)** - the day roll for
  its own variables, `completedToday`, `claim(tier)`, and `missionsToday(player)` which delegates to
  `BoardService.visibleQuests` for the configured board. Claims pay through `LootService.deliver`
  and the wallet, and are audited.
- `common/src/main/java/net/schwarz/rotasutils/server/QuestService.java` - after
  `progress.recordCompletion(...)`, one call into `DailyService.onQuestCompleted`, which counts the
  turn-in only when the quest is in today's set or repeats daily.
- `common/src/main/java/net/schwarz/rotasutils/event/RotasEvents.java` - on join, roll the day and,
  if `announceOnJoin`, send one line naming how many missions are waiting. One line, once a day.
- `common/src/main/java/net/schwarz/rotasutils/network/RotasNetwork.java` - `openDaily(player)` and a
  `daily` block in the progress sync (count, claimed mask, tier definitions, seconds to reset).
- `common/src/main/java/net/schwarz/rotasutils/network/ServerActions.java` - `open_daily`,
  `daily_claim` (server re-checks the count and the mask; the client only asks).
- `common/src/main/java/net/schwarz/rotasutils/client/screen/player/DailyScreen.java` **(new)** -
  today's missions with their state, the track with claim buttons, and the reset countdown.
- `common/src/main/java/net/schwarz/rotasutils/client/screen/player/MainMenuScreen.java` - one row:
  "Today - 2/5 missions, 1 reward waiting".
- `common/src/main/java/net/schwarz/rotasutils/client/screen/ScreenRouter.java` - route `daily`.
- `common/src/main/java/net/schwarz/rotasutils/command/DailyCommands.java` **(new)** -
  `/rotas daily` (see it), `/rotas daily claim <tier>`, `/rotas daily reset <player>` (operator).
- `common/src/test/java/net/schwarz/rotasutils/core/DailyTrackTest.java` **(new)**.
- Lang keys in both `en_us` and `th_th`; `docs/rpg-daily.md`.

### Lifecycle

Join → roll the day (clears yesterday's count and mask) → sync. Turn in a mission → count up → sync.
Claim → server validates tier reached and not already claimed → pays → sets the bit → sync. Midnight
→ next read rolls the day. Server restart mid-day → the count is in the player record, the mission
set is on the board; both survive. Mailbox catches a claim that does not fit in the bag.

## Phase 2 - Season track

Tiers keyed to rank points in `season.json`, claimed once per season, stored as `season:<tier>` in the
existing `claimedRewards`. Rank points already come from quests, bosses and repeatables, so the track
needs no new earning path - only tiers, a claim action and a screen. The daily track feeds it: a
completed day is worth rank points.

## Phase 3 - Mining sites

A node is a block inside a zone that pays a configured drop when mined, is replaced by its depleted
block, and returns after a cooldown - the block equivalent of `ZoneEncounterService`. Per-site daily
quota optional. Enforced in the existing `BlockDropsMixin` path, with the node state persisted on the
zone rather than in a new store.

## Verification

- `./gradlew :common:test :forge:build :fabric:build` green, lang parity green.
- `DailyTrackTest`: tiers at 0/1/3/5 completions, a claimed mask blocking a second claim, a day roll
  clearing both, tiers out of order in a hand-edited file, and "no tier is claimable before it is
  reached".
- By hand in a world: finish a daily mission, watch the count rise, claim a tier, relog and confirm
  the claim held; set the system clock forward a day and confirm the reset.
- Edge cases: no board configured (the screen says so instead of erroring), a board with no quests,
  a full inventory on claim (mailbox), two claims in the same tick (the mask is checked server-side),
  and a season that is off (`SeasonService.active` false) - the daily track still works, since it is
  quest-driven rather than season-driven.
