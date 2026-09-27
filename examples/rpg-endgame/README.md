# Optional endgame sample (items, loot, boss, quest, merchant)

Copy `server/endgame` under `config/rotasutils/packs/server`, run `/rotas validate`
and `/rotas reload`. Nothing here applies automatically: the monster profile is
`manual_only`, so no world mob changes until an administrator assigns it.

What the pack adds:

- two rarities, three item profiles, a two-piece set and a loot table,
- a boss tier plus `rotas:boss/warden` with two phases, an arena, an enrage timer
  and contribution-shared rewards,
- `rotas:monster/warden`, which binds the boss and the loot table,
- `rotas:quest/daily_hunt`, a daily bounty capped at 20 claims per day,
- `rotas:merchant/town_smith` with a stocked, per-player-limited item trade and a
  mixed item-plus-currency trade.

Try it:

```
/rotas item give <player> rotas:item/warden_blade 20
/rotas loot preview rotas:loot/warden 20 3
/rotas monster assign <target> rotas:monster/warden
/rotas quests accept rotas:quest/daily_hunt
/rotas shop list rotas:merchant/town_smith
/rotas shop buy rotas:merchant/town_smith trade_up
```

The blade requires level 5; equipping it below that returns it within a second
with a message. Wearing both set pieces adds the four-point armour bonus, which
disappears as soon as the set is broken. Quest and merchant rewards pay in
`rotas:gold`, which the progression sample also uses.

See `docs/rpg-items.md`, `docs/rpg-bosses.md` and `docs/rpg-quests-economy.md`
for the field reference and the current limits.
