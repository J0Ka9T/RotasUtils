# Optional progression sample

Copy `server/progression` under `config/rotasutils/packs/server`, run `/rotas validate`
and `/rotas reload`. No automatic login reward is installed by this sample.

An OP can grant `/rotas debug reward <player> rotas:reward/starter starter-v1`.
Repeating that occurrence grants nothing twice. The player can inspect `/rotas stats`
and use `/rotas stats allocate rotas:stat/vitality 2`, adding 4 maximum-health points.
The 25 gold remain in the wallet; merchant spending is a later phase.

The existing inventory screen is unchanged. See `docs/rpg-progression.md` for limits.
