# Refinement (ตีบวก)

Any weapon and any piece of armour can be refined, vanilla or modded. The level
lives in a `RotasRefine` compound on the stack, so it survives chests, deaths,
trades and restarts, and it needs no kernel definition behind it.

A refined item shows its level in its own name (`+7 Iron Sword`), coloured by how
far it has come, and its tooltip says what the level is actually worth.

## The attempt

| Step | What happens |
|---|---|
| Target level at or below `safeLevel` | Always succeeds |
| Above it | `chances[target - 1]` decides, plus the enriched ore and the scrolls |
| Failure | What `onFail` says: `DOWNGRADE`, `RESET_TO_SAFE`, `BREAK` or `KEEP` |

Every attempt takes one ore and some gold. Weapons take `rotasutils:oridecon`,
armour takes `rotasutils:elunium`, and the enriched versions of both add
`enrichedBonus` to the chance in place of the plain ore.

## Scrolls

| Item | Effect |
|---|---|
| `rotasutils:protection_scroll` | A failure cannot break or lower the item |
| `rotasutils:blessing_scroll` | Adds `blessingBonus` to the chance |
| `rotasutils:certificate_scroll` | The attempt cannot fail |

All three are one use and are consumed only by an attempt that actually ran; an
attempt refused for a missing ore or missing gold spends nothing at all.

## What a level is worth

Levels inside the safe range pay `attackPerLevel` / `defensePerLevel`; levels
above it pay the `...PerOverLevel` rate, which is why a +10 is worth far more
than twice a +5.

- A weapon's refinement is flat attack damage, added in `CombatStats.modifyDamage`
  for the hit the weapon actually made - a melee swing or a shot from it. It lands
  before the PvP and level-parity multipliers, so it is scaled exactly like the
  weapon's own damage.
- Armour's refinement feeds the `rotas:defense` channel that Vitality already
  uses, read from the worn pieces at the moment of the hit, so a piece swapped
  mid-fight counts at once.

## Where a player refines

- `/rotas refine` opens the bench, `/rotas refine info` prints the next attempt's
  chance and cost, and `/rotas refine try [enriched] [protection] [blessing]`
  makes one attempt without the screen.
- The bench (`RefineScreen`) shows the item, the step, the chance, the cost, the
  material and what a failure would do, and lets the player switch each scroll on
  and off. It only ever asks: `RefineService` re-reads the stack, the ore, the
  scrolls and the wallet on the server before anything is spent.
- `/rotas refine set <level>` is the operator's way to place a level directly.

A success at or above `announceFrom` is broadcast to the whole server.

## Configuration

Everything is in `config/rotasutils/season.json` under `refine`, reloaded with
`/rotas season reload`:

```json
"refine": {
  "enabled": true,
  "maxLevel": 10,
  "safeLevel": 4,
  "chances": [1.0, 1.0, 1.0, 1.0, 0.6, 0.4, 0.4, 0.2, 0.2, 0.1],
  "onFail": "DOWNGRADE",
  "attackPerLevel": 1.0,
  "attackPerOverLevel": 1.5,
  "defensePerLevel": 0.8,
  "defensePerOverLevel": 1.2,
  "goldPerAttempt": 200,
  "goldGrowth": 1.6,
  "enrichedBonus": 0.15,
  "blessingBonus": 0.2,
  "announceFrom": 8
}
```

Hand-edited values are clamped on load, and an unknown `onFail` reads as
`DOWNGRADE` rather than failing the file.

## Where the materials come from

Nothing crafts them. They are drop-table entries in `drops.grades`:

- medium: ore at 4%
- rare: ore at 18%, protection scroll 3%, blessing scroll 5%
- epic: ore at 45%, enriched ore 8%, protection 12%, blessing 15%, and the
  certificate scroll at 1%

A server that wants them easier to find edits those lines; a server that wants
them from a boss only removes them from the lower grades.
