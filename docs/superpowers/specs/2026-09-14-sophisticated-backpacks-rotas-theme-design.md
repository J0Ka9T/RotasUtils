# Sophisticated Backpacks RotasUtils Theme Design

## Scope
Retheme Sophisticated Backpacks `1.20.1-3.24.57.1908` with Sophisticated Core `1.20.1-1.3.54.2027` to match RotasUtils main dark brown/copper UI while preserving all storage, slot, upgrade, sorting, search, scrolling, settings, drag, shift-click, tooltip and packet behavior.

## Architecture
Sophisticated Backpacks delegates most of its GUI chrome to seven shared Sophisticated Core atlases. RotasUtils will override those resources under `assets/sophisticatedcore/textures/gui` so original screen classes and interaction geometry remain untouched. Because Sophisticated Core hard-codes vanilla dark GUI text (`0x404040`) in storage/settings titles and default labels, three optional client-only `@Pseudo` Mixins will change only that color constant to RotasUtils cream.

This intentionally themes shared Sophisticated Core storage chrome. Other Sophisticated-family storage screens using the same Core atlases may inherit the theme; item/block textures and gameplay are not changed.

## Versions
- Minecraft `1.20.1`
- Forge `47.4.22`
- Sophisticated Backpacks `1.20.1-3.24.57.1908`
- Sophisticated Core `1.20.1-1.3.54.2027`
- RotasUtils remains buildable and launchable when Sophisticated mods are absent.

## Visual language
- Base: `#221A12`
- Surface: `#2C2218`
- Raised: `#382C1F`
- Border: `#6B5334`
- Separator: `#463726`
- Recessed: `#1A140E`
- Copper: `#C98B3D`
- Bright copper: `#E3A857`
- Accent wash: `#4C3A20`
- Primary text: `#EDDFC0`
- Muted text: `#B6A17C`
- Faint text: `#8B7A5C`

## Resource contract
Override exactly these Sophisticated Core GUI atlases, preserving each upstream `256x256` canvas, UV layout and alpha boundaries:
- `gui_controls.png`
- `icons.png`
- `slots_background.png`
- `storage_background_12.png`
- `storage_background_12_wider.png`
- `storage_background_9.png`
- `storage_background_9_wider.png`

Background and slot atlases use full neutral remapping into the Rotas palette. Control chrome maps neutral surfaces to dark brown and warm selected/active chrome to copper. `icons.png` preserves saturated semantic colors while converting low-saturation glyphs to cream/muted tones so they remain visible on dark controls.

## Text compatibility
Sophisticated Core hard-codes `4210752` (`0x404040`) in `StorageScreenBase`, `SettingsScreen`, and default `Label` constructors. Resource replacement cannot change these draw colors.

Add optional client Mixins:
- `SophisticatedStorageScreenMixin`
- `SophisticatedSettingsScreenMixin`
- `SophisticatedLabelMixin`

They must use `@Pseudo`, target classes by string only, keep `require = 0`, replace only `0x404040` with `0xEDDFC0`, and never cancel rendering or alter input, slots, menu state, networking, geometry, strings or localization. Named dev selectors and the pinned 1.20.1 production selector may both be listed where the inherited Minecraft method is remapped.

## Functional preservation
Do not replace `BackpackScreen`, `BackpackSettingsScreen`, `StorageScreenBase`, menus, slots, widgets, packets, upgrade containers, inventory scrolling, sorting, search, settings logic, drag handling or quick-move logic.

## Failure behavior
If Sophisticated Core is absent, the `@Pseudo` Mixins must skip cleanly and the resource overrides remain harmless. If future Sophisticated versions change atlas UVs or targeted methods/constants, compatibility must be reviewed rather than broadening the Mixins.

## Testing
Automated tests verify all seven resources exist, decode, remain 256x256, contain visible pixels, contain Rotas dark surfaces plus copper/cream pixels, and the pure text-color helper only remaps `0x404040`. A source/config regression test verifies all three optional Mixins are registered and use `@Pseudo`/`require=0`.

Full verification: `gradlew.bat :common:test :forge:build :fabric:build --no-daemon`.

Runtime acceptance on Forge verifies normal backpack screen, upgrade side panel, settings screen, sorting, search, scrolling, upgrade controls, tooltips, shift-click/drag and GUI scales 1/2/3/Auto. No further Mixin is allowed without a concrete runtime mismatch.
