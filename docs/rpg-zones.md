# Level zones and universal mob levels

Every mob in the world has a level, not just the ones an author gave a monster profile. **Where a
mob spawns decides how strong it is** - never the player standing next to it - so a dangerous area
stays dangerous and a starting meadow stays gentle. Authors who want bosses, tiers, affixes or loot
still write a monster profile, and a profile always wins over the zone.

## What a level does

- **Health and damage** grow with the level through the default per-level rates
  (`health_per_level`, `damage_per_level`).
- **Kill XP** starts from `max(base_xp + xp_per_level x (level - 1), threat rating x level bonus)`,
  stored on the mob when it is leveled, then adjusted at death (see *Kill XP* below). Paid once per
  entity through the existing receipt system.
- **The head plate** replaces the vanilla name tag (which showed through walls): a small pixel-art
  plate with a gem and the level coloured by `mob level - your level`, the mob's name and a thin health
  bar. Blocks hide it, it shows within 12 blocks (or up to 24 while the mob is hurt or aimed at), and
  it fades out with distance. The same mob reads green to a veteran and red to a new player.
- **The target frame** at the top of the screen, for the mob under your crosshair, shows a level crest,
  a difficulty ribbon (Trivial to Mythic), a health bar with a damage trail, the health numbers and the
  monster type.

## How a level is chosen

1. If a monster profile matches the mob, the profile decides everything as before.
2. Inside a **zone**, the highest-priority enabled zone supplies the band (equal priority: lowest
   zone id wins). A **safe** zone does not level new mobs at all.
3. Outside every zone, the **wilderness band** is
   `floor = spawn_level + floor(distance_from_spawn / level_per_blocks)` (capped at `max_level`;
   `level_per_blocks = 0` keeps the spawn level everywhere) up to `floor + band_spread`.
4. Within a zone's **edge blend** (`transition_blocks`) outside its shapes, the wilderness band is
   blended toward the zone band: the closer to the edge, the closer to the zone's levels. The zone's
   own minimum only applies once inside. Whole-dimension zones have no edge.
5. The level inside the band is a stable roll from the mob's UUID, weighted toward the middle but
   reaching both ends, so a `5-20` zone really has level 5 and level 20 mobs. The same mob always
   rolls the same level, including after a reload or a wand re-level.

Tamed animals, EasyNPC entities and the configurable exclude lists never level. A mob that walks
into a safe zone keeps the level it already has.

## Kill XP

At a confirmed kill, the stored XP is multiplied by:

- the **zone XP multiplier** where the mob died (not where the killer stood);
- a **challenge** factor from `mob level - player level`: 10% for trivial kills, 100% at even
  level, up to 175% for mobs 10+ levels above;
- a **repetition** factor: each recent kill of the same mob type costs 20%, down to a 20% floor.
  One remembered kill is forgiven per `repetition_window_ticks` (default 6000 = 5 minutes) of server
  play time since the last kill, so farming loses value but a type is never penalised forever. Old
  saves' permanent counters decay away on the next kill.

The total is capped at the configured maximum monster XP.

## Zones

A zone is a named level band for a dimension, made of any number of **areas** (max 16). A position
matches the highest-priority enabled zone whose dimension applies and whose areas contain it; a zone
with no areas covers the whole dimension.

| Setting | Meaning | Default |
| --- | --- | --- |
| Mob level from / to | band new mobs roll in | from the location |
| Danger | Safe, Normal, Dangerous, Deadly - how players should read it; Safe also stops leveling | Normal |
| Recommended player Lv | the player range the zone is meant for | the mob band |
| Priority | higher wins where zones overlap | parent + 1 when nested |
| Kill XP multiplier | 0 - 10 | 1 |
| Edge blend, blocks | 0 - 256; 0 is a hard edge | 16 |
| Entry lock | requirements a player must meet to enter; none is open | none |

| Shape | Fields | How it is drawn |
| --- | --- | --- |
| `sphere` | `x y z radius` | Wand in Sphere mode, or the editor's "Add area at a block" |
| `box` | `min_x min_y min_z max_x max_y max_z` | Wand in Box mode, or the editor's "Add a box" |
| `polygon` | `points[] min_y max_y` | Wand in Outline mode |
| *(none)* | - | "Clear shapes" makes the whole dimension the zone |

Zones persist in the world's `rotasutils_data.dat` and are pushed to administrators for the editor.
Zones saved before these settings existed load with the defaults above.

## The Zone Wand

The Zone Wand is an **administrator-only** item. It is listed only in the **RotasUtils Admin Tools**
creative tab together with the Admin Tool, NPC Wand and House Wand. That tab fills only for players
with operator permission (level 2; no video option needed) and refreshes when a player is opped or
deopped, so other players never see it. The server also hands the wand out
from the admin menu (`Level Zones -> Get the Zone Wand`) or with `/rotas zone wand`, and every use
re-checks administrator permission.

**Sneak + right-click the air** switches between three modes:

- **Sphere** - each block click adds a sphere of the working radius. Good for arenas and caves.
- **Box** - click two opposite corners. Corners less than 8 blocks apart vertically are read as a
  floor plan and the box spans the whole build height; otherwise it is exactly the clicked volume.
- **Outline** - click the perimeter point by point, then **sneak-click** the last point to finish (or
  click the first point's block or one next to it). The outline spans the whole build height. Good
  for valleys, coastlines and towns. Shapes smaller than 16 blocks across get a misclick warning.

While a box or outline is unfinished it is previewed in light yellow up to the block under the crosshair,
and the action bar says what to click next. **Right-click the air** cancels it. Nothing is saved
until the shape is complete, so a half-traced shape never becomes a whole-dimension zone. The mode
and unfinished points live on the wand itself.

Finished shapes go to the **working zone**: the zone last opened in the editor, or the zone the wand
started. With no working zone in this dimension, the first finished shape **starts a new zone**
named after the biome, with its band taken from the level of that spot and, when drawn inside
another zone, a priority one higher so the nested area wins.

- **Right-click a mob** - re-levels it to where it stands, with the same roll a fresh spawn gets.
- **Right-click the air** (no unfinished shape) - opens the zone manager, or cancels a pending
  world pick.

While the wand is held, **every zone border within render distance** is shown on the ground. The
working zone (stored on the wand, so it is the one your clicks grow) is bright; other zones are
dimmer:

- a **3-block translucent curtain** stands on the terrain along the border, fading upward, and is
  hidden by hills like real blocks;
- a bright **ground line** runs along its foot, with a faint copy drawn through terrain so a border
  behind a hill is still findable;
- each zone has **its own colour**: zones that touch or overlap, such as a zone inside another, never share
  one, and the same zones always get the same colours (the label still names the danger);
- a **floating label** at the border point nearest you reads `Name  Lv a-b  Danger`.

Spheres and exact-height boxes are 3D volumes (arenas, caves), so they also keep a wireframe. Borders
in unloaded chunks or beyond render distance are not drawn.

## Locking a zone behind a quest

A zone can carry an **entry lock**: a list of requirements that a player must meet before they may
enter. The lock uses the same requirements as quests, boards and skill nodes - Quest Completed,
Minimum Level, Has Item, Faction, Reputation, Advancement and the rest - so "come back after the
main quest unlocks this area" is one Quest Completed rule.

- Enforcement is server side and runs after each player tick: a player inside a gated zone they do
  not pass is dismounted (a horse or boat cannot carry them through) and pushed back to the last
  position where they were allowed, or, when they logged in or the lock was added while already
  inside, to the nearest point outside the zone. A whole-dimension gate has no local exit, so it
  sends the player to the overworld spawn.
- The action bar names the first requirement still missing - `You cannot enter <zone> yet. Still
  needed: <requirement>` - with a spark and a shield sound, at most once every two seconds while a
  player keeps pushing at the border.
- Requirement results are reused for one second per player and zone, so a crowd at a border does
  not re-run quest and item checks every tick. Saving an edited zone takes effect immediately; a
  player who just finished the unlocking quest may enter within a second.
- Requirements marked **advisory** are shown to players but never block, exactly as in a quest.
- Administrators (permission level 2) are never pushed, so a locked build stays reachable.
  `/rotas zone gate test` switches that off for yourself until you run it again or log out, so you
  can walk into your own lock as a player would.
- `/rotas zone gate check <zone> [player]` lists every requirement of the lock with pass, fail or
  advisory for that player.
- The lock is edited in the zone editor's right column (**Entry lock**), independently of the
  level band and shapes, and saves with the rest of the zone. Each row shows the requirement type
  and its fields (for example `quest=rotas:quest/main_3, times=1`).

Legacy zones, and worlds saved before this feature, load with no entry lock and stay fully open.

## Zones inside zones

Draw a zone inside another and the inner one wins wherever they overlap (the wand gives it the parent's
priority + 1). The inner zone then decides everything at that spot: level band, rules, titles, effects,
movement and which mobs belong there.

A zone drawn inside another starts **separate** ("Keep outside mobs out", zone editor > Mobs):

- only Mob Setups scoped to this zone apply to mobs spawning inside it; global setups and setups of the
  surrounding zone do not;
- hostile mobs spawn naturally only when one of this zone's setups names them, so vanilla monsters and
  the outer zone's mobs stay outside;
- passive animals, spawners, eggs, commands, extra spawns and spawn points are never blocked.

Switch it off and the inner zone shares the outside mobs again, with its own setups winning inside it.
Zones made before this feature, and zones not drawn inside another, start shared.

## Zone types

Zone editor > **Type** fills in a preset. Every value it sets stays editable, and nothing saves until
**Save**.

| Type | Sets |
| --- | --- |
| Custom | nothing (your own settings) |
| Town | Safe (new mobs not leveled), hostile spawning off, outside mobs shared |
| Dungeon | Dangerous, outside mobs kept out, PvP off, no elytra, no ender pearls, entry title = zone name |
| Boss arena | Deadly, outside mobs kept out, hostile spawning off (spawn points still work), no elytra, flight or pearls, entry title |
| PvP arena | PvP on, keep inventory, Rotas XP loss 0, hostile spawning off |

## Rules

Zone editor > **Rules**. Each rule is *Global* (this zone changes nothing) or set here; a higher-priority
zone inside can override it again. Rules are applied in play:

- **PvP** - off cancels damage between players when either stands where PvP is off. On cannot turn PvP on
  if the server has it off.
- **Hostile mob spawning** - off stops natural and world-generation spawns of hostile mobs and extra
  spawns of hostile Mob Setups.
- **Damage taken / dealt** - multipliers (0-100, 1 = normal) for player victims and player attackers.
- **Healing** - multiplier for player healing.
- **Keep inventory** - players dying here keep items and vanilla experience, like the game rule. Mods that
  drop their own slots through their own death events may still drop those.
- **Rotas XP lost on death** - percentage of the player's progress into their current level.

A stored respawn target is shown by `/rotas zone here` but not applied.

## Boss and miniboss spawn points

Zone editor > **Bosses**. A point is a block position, a Mob Setup, a kind, a respawn time (10-86400 s)
and a wake radius (16-128 blocks). Up to 16 per zone.

- The point spawns the first mob its Mob Setup names, with that setup's level, strength, affixes and
  rewards. A setup with a `boss` definition also gets phases, enrage and contribution rewards.
- Only one mob per point is alive. It spawns when a player comes within the wake radius and the cooldown
  is over, and only while the point's chunk is loaded.
- A point mob that leaves its zone is pulled back to the point. When no player has been inside the zone
  for 30 s, it heals to full and returns to the point.
- **Boss** points show a boss bar with the mob's name and health to players inside the zone. **Miniboss**
  mobs glow.
- After the mob dies the point waits its respawn time. State (the living mob, the cooldown) is saved with
  the world, so a restart neither duplicates a boss nor skips its wait.
- Removing a point, or its zone, removes its mob. *Respawn now* (or the command) clears a cooldown.

## Titles, effects and movement

Zone editor > **Titles, effects & movement**:

- **Titles** - a title and subtitle on screen when entering (with an optional sound id) and a title when
  leaving. Walking from one zone straight into another shows the new zone's entry title. Checked every
  half second, so a very fast crossing can skip one.
- **Effects** - up to 8 potion effects (levels 1-5) kept on players inside, ambient and without particles.
  They fade a few seconds after leaving and never replace a stronger potion.
- **Movement** - no elytra (gliding stops), no flying (survival flight is switched off), no ender pearls (a
  pearl can neither land inside nor be thrown from inside).

Administrators are not affected by movement rules unless `/rotas zone gate test` is on. Titles and effects
apply to everyone.

## Admin surface

- **Level Zones** in the admin menu (In the World group): a list of zones with band, danger,
  shapes and priority; "New zone here" (starts with a sphere of the wand radius around you, band
  from your location); "Get the Zone Wand".
- **Zone editor**: name, mob band, recommended player range, priority, XP multiplier, edge blend,
  danger, on/off, and the shape list with per-shape remove. A preview line shows what the saved zone
  will do. Unsaved typing survives toggles and shape changes.
- **Mob Levels** tab in the Level Manager: enable, hostiles only, nameplate options, spawn level,
  level per N blocks, wilderness level spread, max level, per-level health/damage and XP.
- **Commands**:
  - `/rotas zone list` - every zone with dimension, band, danger, XP multiplier, priority and shapes.
  - `/rotas zone here` - zone or wilderness at your position, edge blend, and the band new mobs roll.
  - `/rotas zone gate check <zone> [player]` - every entry requirement with pass/fail for a player.
  - `/rotas zone gate test` - hold yourself (an administrator) to entry locks until toggled off.
  - `/rotas zone points <zone>` - spawn points with alive / ready / seconds until the mob returns.
  - `/rotas zone points <zone> respawn <point>` - clear a point's cooldown.
  - `/rotas zone wand [radius]` - get the wand, optionally setting its sphere radius.
  - `/rotas zone remove <zone>` - delete a zone.
  - `/rotas monster level <mob> <level>` - re-level one mob, keeping its profile, tier and affixes.

## Global settings

Saved in the level config (`mob_level`): `enabled`, `spawn_level`, `level_per_blocks`,
`band_spread` (default 3), `max_level`, `health_per_level`, `damage_per_level`, `base_xp`,
`xp_per_level`, `name_visible`, `name_format`, `color_by_delta`, and the category/entity/tag exclude
lists. `offset` is still read and saved for old configs but no longer affects default leveling.
Adventure XP tuning (`adventure_xp`) adds `repetition_window_ticks`.

## Current limits

- Zone content packs are not wired yet: zones are authored in-game (wand/editor) and persist in
  world data. A `zones` JSON content kind is the intended follow-up.
- Outlines and flat boxes always span the full build height.
- Per-viewer colour reads the level back out of the configured name template; a name another mod
  replaces is left untinted.
- Mobs leveled before this change keep their stored level until re-leveled with the wand or
  `/rotas monster level`.
