# Optional monster sample

Copy `server/monsters` under `config/rotasutils/packs/server`, run `/rotas validate`
and `/rotas reload`. Nothing is installed automatically; without this pack no mob
is modified.

The pack adds two tiers, two mutually exclusive affixes and one profile for
overworld zombies and skeletons. Levels follow the nearest player between 1 and
60, elites roll roughly one in seven and receive a single affix. `apply_existing`
is false, so mobs that were already stored in the world keep their vanilla
behaviour; newly spawned ones are adopted.

Check the result with `/rotas monster inspect <target>`, or assign explicitly with
`/rotas monster assign <target> rotas:monster/undead 20`. `/rotas monster clear`
restores the entity, including its attributes and name.

Tier loot multipliers are stored but unused until the item and loot phase. See
`docs/rpg-monsters.md` for the definition fields and current limits.
