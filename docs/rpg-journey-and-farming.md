# Journey and farming

Eight systems that give a player a reason to log in today, a reason to keep
playing this season, and a reason to farm one more hour. Every number lives in
`config/rotasutils/season.json` and reloads with `/rotas season reload`.

## 1. Today's missions and the daily track (`daily`)

Which missions are today's is the daily board's business - boards already rotate
once a day and keep the pick on the board itself. `daily.boardId` names that
board; with no board set, every quest that repeats daily counts.

Each mission finished today climbs the track. Rungs pay gold, experience, rank
points and item lines, once each, and the count resets at local midnight -
an unclaimed rung goes with the day, a claimed one is never taken back.

- Page: **Main menu → Journey → Today**. A mission row opens the ordinary quest
  page, so accepting and turning in work exactly as they do at a board.
- First join of a day: one chat line naming how many missions are waiting.
- `/rotas daily`, `/rotas daily claim <step>`, `/rotas daily reset <player>`.

## 2. The season track (`seasonTrack`)

Rungs on the rank points a player already earns from quests, bosses, the daily
track, marked monsters and the monster book. A claim is recorded in the player's
claimed rewards as `season:<seasonId>:<points>`, so reordering rungs never pays one
twice and a new `seasonId` starts every claim over.

- Page: **Journey → Season**. `/rotas pass`, `/rotas pass claim <step>`.

## 3. Mining sites (`/rotas mine`)

A site is a set of ore blocks that pay extra when worked, then run dry. Mining a
full node drops the ore as normal - the player's own tool and enchantments apply -
and adds the site's gold, loot lines and experience. The node turns into its
depleted block and comes back after `respawn` seconds; a spent node cannot be
broken, and says how long is left.

Payment waits one tick and only happens if the block is really gone, so another
mod's claim protection cancelling the break never pays out. Operators in creative
walk through a site without spending or earning.

```
/rotas mine create <site>                     here, in this dimension
/rotas mine add <site>                        the block you look at
/rotas mine scan <site> <radius> <block>      every such block within the radius
/rotas mine respawn <site> <seconds>
/rotas mine reward <site> <gold_min> <gold_max> <xp>
/rotas mine loot <site> add <line> | clear
/rotas mine depleted <site> <block>
/rotas mine limit <site> <per_day>            0 means no limit
/rotas mine refill <site> | unadd <site> | delete <site> | list
```

## 4. Luck (`farming.luck*`)

The player's own `generic.luck` attribute - raised by cards, potions and gear -
multiplies loot-grade and card chances, up to `luckBonusCap` (default: at most
double). `/rotas luck` shows what it is worth right now.

## 5. Kill combo (`farming.combo*`)

Hostile kills with no more than `comboWindowSeconds` between them build a chain.
From the second kill it adds experience (`comboXpPerKill`) and loot chance
(`comboLootPerKill`), growing until `comboCap`. The chain lives only in memory:
it is the next few seconds, not progress. Party members share the kill's
experience but not the killer's streak.

## 6. Elite and champion monsters (`farming.elite*`)

A naturally spawned hostile mob may be promoted when it is levelled: champion
first (`championChance`), then elite (`eliteChance`). Promoted mobs are tougher
(`*Health`, `*Damage`), wear their mark on the name plate, pay more experience,
and drop on their rank's better rules - which already exist for `ELITE` and
`CHAMPION`. A champion glows and is announced to players within 64 blocks. Both
pay rank points. Spawners, eggs and commands never promote, so an elite can be met
in the wild but never farmed from a cage.

## 7. The monster book (`bestiary`)

Every kind of hostile monster a player kills gets a page. Each rung of kills
(`tiers`, default 10/50/200/1000) is worth `damagePerTier` more damage against that
kind and `lootPerTier` more loot from it, and pays `rankPointsPerTier` once. From
rung `revealDropsAt` on, the page lists what the monster drops, rolled from its
real loot table.

- Page: **Main menu → Monster Book**, or `/rotas book [entity]`.
- The book is server-side (at most 512 kinds); the page is sent when opened.

## 8. Salvage (`salvage`)

Breaks a weapon or a piece of armour into gold and refine ore, priced from what
the item is: its attack or armour, its enchantment levels, its refinement (worth
more the higher it went) and its cards. Part of the ore sunk into refining it
comes back (`refineRefund`), and cards set in it are returned.

- Page: **Main menu → Salvage gear**, or `/rotas salvage`. Only the main
  inventory is offered, never a worn piece, and breaking needs a selection and a
  second button press.

## How they feed each other

```
missions ──► daily track ──► rank points ──► season track
elite kills ─────────────────► rank points
monster book rungs ──────────► rank points, damage, loot
luck + combo + book ─────────► loot-grade chance ──► refine ore, scrolls
cards ───────────────────────► luck ──► more cards
salvage ─────────────────────► refine ore ──► refinement
```
