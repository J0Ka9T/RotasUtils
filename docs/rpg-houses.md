# Houses

A house is a box of blocks that players can rent. Only the owner and the house's members can break,
place or use blocks inside it.

## Making a house (administrators)

1. Get the House Wand from the RotasUtils Admin Tools creative tab or with `/rotas house wand`.
2. **Left-click** one corner block, then **right-click** the opposite corner block. Pick the corners
   diagonally: the house is every block between them, from the lower corner's height to the higher one's.
3. Chat shows the size and warns when the box overlaps another house or is larger than the limit
   (16,777,216 blocks). **Sneak + right-click the air** clears the selection.
4. Keep holding the wand and run `/rotas house create <id> <tier> [name]`, for example
   `/rotas house create riverside_1 starter Riverside Cottage`. The id is lowercase letters, digits,
   `_`, `.` or `-`; the name is optional and may contain spaces.

### No wand needed

On the create form, **Choose the area...** opens a screen that picks the area for you: a box round where you stand in four
sizes (Small 9x9x6, Medium 15x15x8, Large 25x25x10, Huge 41x41x14), a corner at your feet or at the block you are looking at,
and four nudges (wider, narrower, taller, deeper). The result is the same selection the wand makes - if you hold the House
Wand the corners are written into it, and the green outline and the wand's chat message read the same.

### Faster ways to make houses

- **Create screen** (Admin menu > Houses > Create): type only the display name - the id writes itself from it
  (`Riverside Cottage` becomes `riverside_cottage`, with `_2`, `_3`... if it is taken; type in the id box to
  override it). The tier button shows its prices. When you press Create the form clears for the next house in the row
  (`Riverside 1` suggests `Riverside 2`, in the same tier), and the new house's quick settings (guest doors, buttons,
  containers, prices, welcome line) open so you can set it up straight away.
- `/rotas house new <tier> <name...>` does the same from chat: no id to invent.

## Rental rules in one click

Admin menu > Houses > Rental Settings has a **Preset** button that fills in every rent rule from a ready-made answer
(you still read the values over and press Save); the same from chat:

| Preset | Rent | Reminder | Grace | Buyout | Members |
| --- | --- | --- | --- | --- | --- |
| Relaxed | 7 days | 2 days ahead | 3 days | x8 | 8 |
| Standard (the shipped default) | 3 days | 1 day ahead | 1 hour | x10 | 5 |
| Strict | 1 day | 6 hours ahead | 6 hours | x12 | 3, +5 slots at 500 |
| Monthly | 30 days | 3 days ahead | 2 days | x6 | 6 |

`/rotas house config` shows the current rules (and which preset they match); `/rotas house config preset <name>`
applies one. Presets never touch the currency or the tiers' prices.

## Seeing houses

While the House Wand is held, every house in this dimension within render distance is drawn as a
translucent box with bright edges. The edges also show faintly through blocks. Each box is labelled
`name  status  tier  size` and coloured by status:

| Colour | Status |
| --- | --- |
| Green | Available |
| Blue | Rented |
| Orange | Overdue |
| Purple | Owned (bought out) |
| Grey | Disabled |

The corners you picked are marked in yellow. While only corner 1 is set, the box follows the block
under your crosshair, so you can see the house before clicking the second corner. The preview turns
red, with the reason in its label, when it would overlap a house or is too large.

Houses are sent to every player with their name, tier, area and status, never their owner.

## Commands

- `/rotas house list` - every house with status, tier, size and position.
- `/rotas house here` - the house you are standing in.
- `/rotas house info <house>` - one house, including its member count.
- `/rotas house rent|pay|buyout <house>` - player rental actions.
- `/rotas house tp <house>` - go home: the middle of the house, on its highest floor with headroom (owner, members and administrators).
- `/rotas house members <house>` - the owner and the members by name (same people).
- `/rotas house wand` - get the House Wand (administrators).
- `/rotas house create <id> <tier> [name]` - create a house from the wand selection (administrators).
- `/rotas house remove <house>` - delete a house and its tenancy (administrators).

After a house is created, removed or rented, every online player's view updates immediately.

## What a house protects

Besides breaking, placing and using blocks (owner, members and administrators only; visitors get what the
house settings open to guests), a house also stands against:

- **Blasts** - TNT, creepers and the like destroy no block of a house and do not hurt its fixtures.
- **Fixtures** - item frames, paintings, armor stands, boats and minecarts inside a house cannot be hit or
  used by anyone who does not belong to it.
- **Pistons** - a piston may not push or pull blocks across a house's wall, in or out.
- **Buckets** - water and lava cannot be emptied into (or against) a house by outsiders.
- **Farmland** - trampling a house's farmland is cancelled for outsiders and for every animal or monster.
- **Mob griefing** - mobs do not carry off or break blocks inside a house.

Administrators are never stopped, so an unrented house can still be built and repaired.
