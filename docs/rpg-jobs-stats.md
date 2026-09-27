# Jobs, race trees and character stats

## Choosing the skill system

Admin > Progression > **Skills and stats > Skill system** switches between:

- **RotasUtils trees** (default): the built-in skill trees, with job and race limits.
- **Pufferfish Skills**: Rotas levels send skill points to Pufferfish, and Pufferfish's own
  screen is the skill tree. Job and race limits do not apply.

## Jobs

1. Admin > Jobs > Open Job Manager > **New job**. Set its name, description, icon, colour and
   minimum level.
2. Admin > Skills: open a tree, then **Category settings > Jobs**, and add the job. Only players
   with that job see and use the tree.
3. Players pick a job from the main menu (**Job: ... (click to choose)**) or from the skill tree
   rail. The first pick is free. Later changes wait for the cooldown in Server Rules
   (`job_cooldown`, default one day). Skill points spent in trees the new job cannot use are
   refunded.

Admins can set a job without the cooldown from Player Records > **Set job**, or with
`/rotas job set <player> <job>` and `/rotas job clear <player>`.

## Sub-roles and artisans

A sub job's unlock table also decides who can make things (Admin > Progression > Season rules).

- **Craft, smelt and brew are sealed.** A result listed in any job's table can only be taken by a player whose
  sub job is that job at the unlock level, including level 1 rows (`lockStarterRows`). Hoppers under a furnace or
  brewing stand take sealed results only for the last player who opened it.
- **Mine, harvest and fish are thinned.** Without the role, each drop survives at `gatherDropChance` (0.35) with at
  most `gatherMaxCount` (1); sealed catches usually become `gatherFishFallback`. Rows at or below `gatherFreeLevel`
  (1) are free.
- **Artisan NPCs.** Give an NPC the **Crafts for a fee** role, pick the job it stands in for and the highest level it
  makes. Players bring the recipe's materials and pay `crafterFee[tier] × crafterPriceMultiplier`, up to
  `crafterDailyLimit` crafts a day. Artisans have no marker and pay no job EXP. Brewing and tag rows cannot be
  commissioned; sell those through the NPC's trades.

## Races (Origins)

With Origins installed, **Category settings > Races** lists every origin. A tree limited to
races is open to a player who holds any of them, on any origin layer. A tree limited to both
jobs and races needs both. Without Origins, race-limited trees stay closed.

## Character stats

Four fixed stats: STR (+1% attack), VIT (+1% max health), INT (+1% magic), AGI (+0.5% attack speed,
+0.2% dodge). Every point is worth the same; there are no diminishing returns or job multipliers.

- Players start with 3 points and earn 2 per level (201 at level 100). A stat holds at most 100 points, so a
  level 100 character fills two and has to choose.
- The Stats screen shows "now -> after" for each stat. Players plan with + and - (Shift adds 10), then press
  **Confirm**. Respec gives every point back for a flat price (`stats.respecCost`, default free).
- Every number lives under `stats` in `season.json` (Admin > Progression > Season rules > Stats). Stats are no
  longer created or edited one by one; the old Stat Manager screen is gone.
- Player Records > **Reset stats**, or `/rotas stats reset <player>`, refunds every point.

## Mob picker

Every entity field in the quest, NPC and content editors now opens a grid of live 3D mob
models. You can search by name or id, filter by mod and mob type, and see a rotating preview
with health and size. Click to select, or double-click to use a mob right away.
