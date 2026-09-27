# RPG console and the screen design system

Two things changed here: the shared visual language the screens are built from, and the addition of
one console screen that can reach every runtime system the kernel owns.

## Design system

The console palette lives in `client/screen/RotasTheme` and the drawing helpers in
`client/screen/Ui`. Four rules hold it together, and each one answers a problem the previous
translucent theme had:

1. **Panels are opaque, and dark.** Body text used to sit on a translucent surface over whatever
   terrain was behind the player, so contrast changed as the camera moved. Only the full-screen
   scrim is translucent now, and it carries no text. The surfaces are dark tooled leather
   (`PANEL #221A12` → `SURFACE #2C2218` → `SURFACE_HIGH #382C1F`) with cream text, because the pale
   parchment console glared over a night-time world and washed out beside the dark HUD.
   Contrast targets: `TEXT` on `SURFACE` about 11:1, `TEXT_MUTED` about 6:1, `TEXT_FAINT` about
   3.5:1, so the muted steps stay readable at GUI scale 1.
2. **One accent, reserved.** Copper `#C98B3D` marks the selected row and the single primary action on
   a screen. Nothing decorative uses it, so an accent-coloured pixel always answers "where am I" or
   "what is the main action here".
3. **Small radii, no drop shadows.** Minecraft's font sits on a pixel grid; large corner radii and
   soft shadows turn a 22px row into mush. Depth comes from three surface steps
   (`PANEL` → `SURFACE` → `SURFACE_HIGH`) and 1px separators.
4. **A 4px spacing grid.** `Ui.PAD` (12), `Ui.GAP` (4) and `Ui.ROW` (22) are the only spacing values
   the console screens use, so unrelated screens still line up.

Buttons come in three console tiers that are distinguishable at a glance: `PRIMARY` (filled accent,
dark label — white on copper fails contrast at this size), `DEFAULT` (surface fill, hairline border)
and `NAVIGATION` (reads as a list entry, with a 2px accent marker when selected). `DANGER` is the
destructive variant. The wooden `BOARD` styles are unchanged: the quest board is a physical object in
the fiction, so parchment-on-wood is its content rather than decoration.

The widget is named `RotasButton` now. It was `GlassButton`, and it no longer draws glass.

The quest board keeps its own palette in `Ui` (the `PARCHMENT_*`, `WOOD_*` and `INK_*` tokens). The
paper is aged rather than bleached - mid-tone stock carrying dark ink - so it reads as a physical
object beside the dark console without becoming the brightest thing on screen. The `INK_*` tokens are
deliberately independent of the console text tokens, which are light: the two palettes are complete
on their own and neither can quietly drag the other out of contrast.



## Character hub

The inventory hub is a paperdoll, not a dial. Equipment sits in two aligned columns beside the
model - armour left, hands right - and accessories continue those columns outward, filled top down
in the order Curios reports them. `RotasInventoryRenderer.layout` reserves both column pairs before
it sizes the model, so a narrow pane shrinks the model instead of sliding slots over it, and the
same method stays pure geometry (`RotasInventoryLayoutTest` runs it without a client).

The earlier version placed slots on an ellipse whose angles moved with the pane size, so no slot had
a stable home and the accessory wings read as scatter. Two hairline rails and one caption per column
replaced the hexagon and its six tethers, which implied a relationship between slots that does not
exist.

Slots carry their own theme (`HUB_SLOTS`) rather than the window theme: a slot reads as a hole
punched in a surface, so it needs a lighter fill and a brighter edge than the panel it sits on.
Sharing the window theme is what turned the equipment ring into a field of barely-visible squares.

## Quest tracker

`client/hud/QuestTrackerHud` draws the tracked contract on the right edge, a quarter of the way down:
name and rank, up to five objectives with counters and hairline progress bars, and a footer carrying
the deadline or a "ready to turn in" line. It answers "what am I doing" without opening a screen.

Which quest it follows is a client-side view preference (`client/ClientQuestTracker`), persisted in
`config/rotasutils-tracked-quest.txt`. Accepting a quest tracks it; the Track button on the contract
page toggles it; with no explicit choice the tracker falls back to the accepted quest that is
furthest along, so the panel is useful before a player has ever pressed Track. The server is not
involved, because nothing about progression depends on what a player is looking at.


## The combat HUD

The survival HUD speaks the same four-rule language as the screens. `client/hud/HudLayout`
owns the geometry as a pure record so the arithmetic is tested (`HudLayoutTest`) without a
client, and `RotasHudRenderer` draws from it, locked to GUI scale 3 so the HUD never resizes
with the player's scale setting:

- **Vitals panel** pinned to the top-left corner: LV and clearance header, gold XP rail, then
  blood, hunger, ARMOR and the situational bars with right-aligned values, growing downward.
  Moving the vitals to the top keeps the bottom-left lane open for vanilla chat, so a burst of
  messages can never paint over the bars. The value column is reserved in the layout, so every
  row's bar shares one width and one right edge — a hurt "7/20" never shrinks the bar under a
  full "20/20" row. Absorption and saturation are two-pixel sub-lines along the bar bottoms,
  blood/hunger carry 20% notches, bars ease toward their targets and low health pulses. AIR
  appears while diving, MOUNT and JUMP while riding. The bars and labels float straight over
  the world with their own drop shadows and ink edges rather than a background slab, so bright
  terrain or a dark cave never gets a slab of UI pasted over it.
- **One block, four values.** With no panel to hold it together, the clearance rank is what
  makes level, rank, blood and hunger read as one instrument: its colour runs down a two-pixel
  spine on the block's leading edge and fills the badge that carries the rank letter on the
  header row, which is the same colour the quest tracker puts on *its* leading edge and the
  board puts on a contract's rank. The XP rail and every resource row are drawn by one shared
  bar helper, so they share corner radius, ink edge, gradient and gloss, and the resource hues
  are held at one saturation and value (terracotta blood, honey hunger, muted steel armour) so
  the set reads as designed rather than assembled. On a narrow panel the rank word drops first
  and the bare badge only if even that would touch the level.
- **Hotbar** centred on the bottom edge: 18px slots in one translucent shell, the offhand
  module attached to its left. The selected slot carries the copper accent, and its underline
  refills with the attack cooldown and reads fully lit once the swing is ready.
- **Item-name pill** centred above the hotbar when the selection changes: the hover name in
  its rarity colour with the count appended, fading with vanilla highlight timing.

The quest tracker hangs on the top-right edge (see above), so the classic MMO reading order
holds: vitals top-left, contract top-right, inventory bottom-centre.

Vanilla centred survival overlays are cancelled on both platforms (the `GuiHudMixin`
cancellations plus Forge `MOUNT_HEALTH`, `JUMP_BAR` and `ITEM_NAME` overlay events) so
nothing orphaned floats at the bottom of the screen. Spectators keep vanilla spectator UI,
and creative shows the hotbar and pill without the vitals panel, matching the survival-only
gate on both loaders.

## The console

`/rotas ui [section]` opens the console; `/rotas ui quests` opens it on a section directly. The
player menu has an "RPG Console" button — the menu's one filled-accent primary action — and the
admin dashboard lists it beside the Content Studio. The menu's geometry (tab row, list panel,
status strip, footer) is computed from one set of helpers on the 4px grid, so widgets, panels
and text share the same margins instead of drifting by a pixel or two per pass.

Layout is a section rail, a list, and a detail pane, because every section is "choose one of these,
then act on it". The actions along the bottom belong to the selected row and change with it, so the
screen never shows a button that cannot do anything. Sections:

| Section | Shows | Actions |
| --- | --- | --- |
| Character | Level, total XP, unspent stat points, wallet, pending rewards | Collect pending stacks |
| Quests | Every quest you can see, its stages and your counters | Accept, abandon, claim |
| Merchants | Each merchant's trades, cost, remaining stock, your limit | Buy the selected trade |
| Items | Item profiles: base item, level band, requirement, slot, set | Give yourself one (operator) |
| Loot | Tables, roll ranges, and the last preview | Roll a preview (operator) |
| Monsters | Profiles, level bands, tiers, affixes, boss and loot links | Assign, clear, inspect (target) |
| Bosses | Phases, arena radius, enrage, minimum payout share | Read-only |

Monster operations act on the mob you are looking at, within 24 blocks, so no screen asks anyone to
type a UUID. Selecting a target is aiming at it.

## How it stays honest

The client never decides anything. `network/KernelUi` builds a bounded snapshot of live content plus
the player's own state and pushes it after login and after every operation. The screen sends back an
operation name and the ID it selected; the server re-checks the same permissions and services the
commands use, then answers with the feedback line the screen shows. A modified client that sends
`kernel_item_give` without the EDIT capability is refused exactly as the command would be.

Snapshot bounds: at most 256 entries per kind and 32 trades per merchant. A larger pack is listed up
to the cap and the screen says so rather than pretending the list is complete.

## Copy

Empty states name the reason and the next step: "No kernel quests are defined yet. Add a quest
definition in the Content Studio, or install the endgame sample pack." Feedback text is the server's
wording, not a client guess, so the screen cannot claim a trade succeeded when it did not.

## Current limits

- The console reads a snapshot, so a value can be one action stale until the next push; every
  operation triggers a fresh snapshot, and the Refresh action forces one.
- The older admin editors (quest creator, board config, skill editor) inherit the new tokens and
  button styles but keep their existing layouts.
- Buying is one unit at a time; bulk purchase belongs with a quantity control this screen does not
  have yet.
- Real client verification is still open: the layout math and the server operations are tested, but
  no automated GUI test drives the screen.
