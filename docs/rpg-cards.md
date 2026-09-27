# Cards and sockets (การ์ด / รู)

A card is the thinnest drop in the game and the one that changes a build. It goes
into a socket on a weapon or a piece of armour and stays there until it is prised
back out.

## The item

Every card is the same registered item, `rotasutils:card`; which card it is lives
in its NBT (`RotasCard.id`). A server can therefore add a card by editing
`season.json` - no new item id, no resource pack, no restart beyond a reload.

Because a stack only carries an id, the names, colours and bonuses travel to
clients with the content snapshot and are held in `CardIndex`, which both sides
read. A card a client has not been told about shows its id rather than nothing.

## Sockets

Sockets live in a `RotasSockets` compound on the stack: how many there are, and
which cards are in them. They are punched with `rotasutils:socket_punch`, one
socket per punch, up to `maxSockets`.

Both tools open the same page (`SocketScreen`): using a card offers to set it,
using a punch offers to open a socket, and the row list is the player's own gear
with its sockets drawn as `[*]` and `[ ]`.

## What a card may do

A card only ever moves a plain Minecraft attribute, from a fixed list:

```
attack_damage  attack_speed  max_health  armor
armor_toughness  movement_speed  knockback_resistance  luck
```

One line is `"<attribute> <amount> [add|percent]"`, at most four lines per card.
A line naming anything else - including the mod's own `rotas:defense` channel -
is refused when the card is read, so a card can never quietly become a second
stat system. A mistyped line is skipped; the rest of the card still works.

The bonuses are applied by `EquipmentService` with the set bonuses, from the worn
armour and the weapon in hand. That pass normally runs once a second, but a
changed main hand refreshes it the same tick, so a card in a weapon counts from
the swing it is swung with.

## Configuration

```json
"cards": {
  "enabled": true,
  "maxSockets": 4,
  "dropChance": 0.0005,
  "entries": {
    "rotas:blaze": {
      "name": "การ์ดเบลซ",
      "source": "minecraft:blaze",
      "fits": "WEAPON",
      "chance": 0.0004,
      "color": -2053044,
      "effects": ["minecraft:generic.attack_damage 0.06 percent"]
    }
  }
}
```

- `source` names the entity that leaves the card; empty means any monster can.
- `fits` is `WEAPON`, `ARMOR` or `ANY`.
- `chance` below zero falls back to `dropChance`.

One kill leaves at most one card, and a card drop is announced to the server.

## Commands

```
/rotas card                 list every configured card
/rotas card sockets         the sockets of the item in your hand
/rotas card give <player> <id>     (operator)
/rotas card remove <socket>        (operator) prise a card back out
/rotas card set_sockets <count>    (operator)
```

## The shipped set

Nine cards, from a 0.08% zombie card to the Ender Dragon card at 5% of a dragon
kill. They are deliberately thin: a card should be the story of a season, not a
crafting step.
