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
- `/rotas house wand` - get the House Wand (administrators).
- `/rotas house create <id> <tier> [name]` - create a house from the wand selection (administrators).
- `/rotas house remove <house>` - delete a house and its tenancy (administrators).

After a house is created, removed or rented, every online player's view updates immediately.
