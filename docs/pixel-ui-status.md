# Pixel UI and navigation refinement — 2026-09-09

- Shared screen frames, buttons, scrollbars, badges and inventory overlay frames now use integer-aligned stepped edges and offset shadows through `PixelUi`.
- Retained the current dark leather/copper palette, screen layout and inventory hitboxes. Primary copper buttons use dark ink for contrast. Shared text scaling rounds down to whole GUI pixels to avoid expanding existing text bounds.
- Player tabs expose explanatory tooltips, including their full purpose when a narrow tab label is truncated. The overview explains accepting/tracking quests and opening character information. English and Thai strings are supplied.
- Admin section names and draft workflow instructions use concrete actions. Content checks no longer claim a clean setup merely because the cached issue list is empty.
- Backup: `backup/pixel-ui-*.zip` created before edits. No installed profile was modified.

Validation: `gradlew :common:test :forge:build :fabric:build --console=plain` passed; 122 existing tests, zero failures/errors. EN/TH keys and Unicode content checked. Log: `build/pixel-ui-build.log`.

Remaining visual gate: real in-game player/admin screens, inventory and HUD at GUI scales 2 and 3. The existing project checkpoint prohibits GUI automation; no visual or authenticated gameplay verification is claimed. This change refines presentation and navigation, not the unfinished platform phases recorded elsewhere.
