# Titles (ฉายา)

A title is the record of something a character did. It is earned, never bought,
and a **unique** title belongs to exactly one player on the whole server: the
first to meet its condition keeps it, and everyone who arrives later is told they
were too late.

## Where it lives

- Definitions are world data (`RotasData.titles`), seeded once per world from
  `TitleService.defaults()` and guarded by `titles_seeded`, so a deleted title
  stays deleted.
- Who holds each unique title is world data too (`title_owners`), and the claim
  is decided on the server thread in `RotasData.claimUniqueTitle`.
- What a player earned and what they wear lives in their `PlayerProgress`
  (`titles`, `active_title`), so it migrates and syncs with the rest of a record.

## Conditions

| Condition | Read from |
|---|---|
| `LEVEL` | The character's level |
| `KILL_ENTITY` | Kills of `target`: one entity id, a comma-separated list (any of them counts, tallied together) or `namespace:*` for a whole mod |
| `KILL_ANY` | Monsters killed |
| `KILL_BOSS` | Bosses killed |
| `QUEST` | Completions of the quest named by `target` |
| `QUEST_COUNT` | Quest completions of any kind |
| `REFINE` | The best refine level this character ever reached |
| `GOLD` | The season currency held at once |
| `MANUAL` | Only `/rotas title grant` |

The tallies live in the player's variable map behind the `rpg.title.` prefix, so
they are server-side only and never reach a client. **Only the entity types some
title actually asks about are counted**, which is what keeps the map from growing
with the entity list of a large modpack.

Checks run where the act happens: a kill, a level-up, a quest turn-in and a
successful refine each call into `TitleService`.

## Bonuses

A title may carry up to four `CharacterStat.Effect`s. They are applied by
`CharacterStatService.apply` alongside the character stats, which means they are
transient, rebuilt on login and on every change, and a title taken off never
leaves a modifier behind.

Every shipped title carries a bonus (`TitleBuffs`): the amounts are the common-rarity base and are multiplied by
rarity when worn. Titles that had none got one that fits the deed (fighters get attack or crit, levelling gets health
or regen, makers get defense or luck), existing bonuses were raised x1.5 and rare titles and above carry two. The
upgrade runs once per world (`buffs_v1`) and only on titles that still equal what the mod shipped.

`rotas:dragon_slayer` needs a dragon of the Saints Dragons mod (Raevyx, Stegonaut, Cindervane, Varasuchus, Ignivorus,
Volitans or Nulljaw). The Ender Dragon has its own title, `rotas:ender_dragon_bane`. Both changes are one-time
migrations (`dragons_v1`) and leave an edited title alone.

## Display

- Chat, death messages and `/msg` come from `Player#getDisplayName`, which the
  `ServerPlayerTitleMixin` decorates on the server side.
- The name plate above a player's head is drawn from the client's own copy of
  that player, so the worn titles of everyone online ride along with the content
  snapshot and `PlayerTitles` feeds the plate.
- The player's own page is `TitleScreen`, reached from the main menu. It lists
  every earned title and every visible unearned one with its progress; hidden
  titles appear only once they are earned.

## Commands

```
/rotas title              list what you have and how far you are from the rest
/rotas title use <id>     wear one you have earned
/rotas title clear        take it off
/rotas title grant <player> <id>    (operator)
/rotas title revoke <player> <id>   (operator, releases a unique title)
```

## The starter set

Fourteen titles ship with a new world, from `มือใหม่หัดเดิน` at level 5 to
`ตำนานที่ยังมีลมหายใจ` at level 100. Two of them are unique and are the server's
own history:

- `rotas:first_century` - the first character to reach level 100.
- `rotas:grandmaster_smith` - the first player on the server to refine anything
  to +10.
