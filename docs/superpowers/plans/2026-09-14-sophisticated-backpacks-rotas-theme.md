# Sophisticated Backpacks RotasUtils Theme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retheme Sophisticated Backpacks `1.20.1-3.24.57.1908` / Sophisticated Core `1.20.1-1.3.54.2027` to RotasUtils dark brown/copper UI without changing gameplay or interaction geometry.

**Architecture:** Override the seven shared Sophisticated Core GUI atlases and preserve their UV/alpha contract. Add three optional `@Pseudo` client Mixins that only replace Sophisticated Core hard-coded default text color `0x404040` with Rotas cream. No screen/menu/widget/network replacement.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.4.22, Architectury Loom 1.14, Sponge Mixin 0.8, JUnit 5, Python 3 + Pillow.

**Spec:** `docs/superpowers/specs/2026-09-14-sophisticated-backpacks-rotas-theme-design.md`

## Global Constraints
- Target Sophisticated Backpacks `1.20.1-3.24.57.1908` and Sophisticated Core `1.20.1-1.3.54.2027`.
- Keep Sophisticated mods optional; no direct runtime class references.
- Preserve all slot/widget/menu/input/network geometry and behavior.
- Override only the seven Sophisticated Core GUI atlases.
- Every output atlas stays exactly `256x256` and preserves source alpha per pixel.
- Text hooks may only map `0x404040` to `0xEDDFC0`.
- No additional Mixins without concrete runtime evidence.

---

### Task 1: Lock and generate the Sophisticated Core atlas theme

**Files:**
- Create: `common/src/test/java/net/schwarz/rotasutils/client/theme/SophisticatedThemeResourcesTest.java`
- Create: `scripts/theme_sophisticated_core.py`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/gui_controls.png`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/icons.png`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/slots_background.png`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/storage_background_12.png`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/storage_background_12_wider.png`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/storage_background_9.png`
- Create: `common/src/main/resources/assets/sophisticatedcore/textures/gui/storage_background_9_wider.png`

**Interfaces:**
- Consumes exact upstream atlases from `forge/dev-mods/sophisticatedcore-1.20.1-1.3.54.2027.jar`.
- Produces seven same-size, same-alpha override PNGs under the Sophisticated Core namespace.

- [ ] Write `SophisticatedThemeResourcesTest` asserting all seven classpath resources exist, decode with ImageIO, are `256x256`, have visible pixels, and collectively contain >1000 dark Rotas pixels, >20 copper pixels and >20 cream/muted icon pixels.
- [ ] Run `gradlew.bat :common:test --tests net.schwarz.rotasutils.client.theme.SophisticatedThemeResourcesTest --no-daemon`; verify RED because resources are missing.
- [ ] Implement `scripts/theme_sophisticated_core.py`: copy source alpha unchanged; full neutral luminance remap for storage/slot atlases; neutral chrome + warm selected-state copper mapping for `gui_controls.png`; preserve saturated semantic icon colors while mapping low-saturation icon glyphs to cream/muted tones in `icons.png`. Use `get_flattened_data()`.
- [ ] Run `python scripts\theme_sophisticated_core.py forge\dev-mods\sophisticatedcore-1.20.1-1.3.54.2027.jar common\src\main\resources`.
- [ ] Run the focused resource test; verify GREEN.
- [ ] Compare each generated image to its source with a Python check: size exactly `(256,256)` and alpha byte identical for every pixel.

---

### Task 2: Theme hard-coded Sophisticated Core text without a hard dependency

**Files:**
- Create: `common/src/main/java/net/schwarz/rotasutils/client/theme/SophisticatedTheme.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/mixin/client/compat/SophisticatedStorageScreenMixin.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/mixin/client/compat/SophisticatedSettingsScreenMixin.java`
- Create: `common/src/main/java/net/schwarz/rotasutils/mixin/client/compat/SophisticatedLabelMixin.java`
- Modify: `common/src/main/resources/rotasutils.mixins.json`
- Create: `common/src/test/java/net/schwarz/rotasutils/client/theme/SophisticatedThemeTest.java`
- Create: `common/src/test/java/net/schwarz/rotasutils/client/theme/SophisticatedThemeMixinSourceTest.java`

**Interfaces:**
- `SophisticatedTheme.remapDefaultTextColor(int color)` returns `0xEDDFC0` only for `0x404040`, otherwise returns input unchanged.
- Mixins target Sophisticated classes via string names and `@Pseudo`, with `require=0`.

- [ ] Write `SophisticatedThemeTest` asserting `0x404040 -> 0xEDDFC0` and semantic/non-default colors remain unchanged; run focused test and verify RED because helper is missing.
- [ ] Add minimal `SophisticatedTheme` helper and rerun; verify GREEN.
- [ ] Write `SophisticatedThemeMixinSourceTest` that reads the three source files and mixin JSON, requiring the three config entries plus `@Pseudo`, `remap = false`, `require = 0`, target class strings, and no imports/references to `net.p3pp3rf1y` types other than target strings. Run and verify RED because mixins are absent.
- [ ] Implement storage Mixin with `@ModifyConstant` for constant `4210752` in `renderStorageTitle`, named `renderLabels`, and pinned 1.20.1 production selector `m_280003_`; `require=0`.
- [ ] Implement settings Mixin similarly for `renderSettingsTitle`, `renderLabels`, `m_280003_`; `require=0`.
- [ ] Implement label Mixin on `<init>` so default Label constructor constant `4210752` becomes Rotas cream; `require=0`.
- [ ] Register the three classes in the client mixin list and rerun focused tests.
- [ ] Run `gradlew.bat :common:compileJava --no-daemon` to verify optional Mixins compile without Sophisticated on common compile classpath.

---

### Task 3: Full build and packaging verification

**Files:** no new production files expected.

- [ ] Run `gradlew.bat :common:test :forge:build :fabric:build --no-daemon`; require exit 0 and zero test failures.
- [ ] Inspect newest Forge and Fabric JARs; require exactly seven `assets/sophisticatedcore/textures/gui/*.png` override paths.
- [ ] Inspect both JARs for the three Mixin classes and updated `rotasutils.mixins.json`.
- [ ] Search main Java for direct typed imports/usages of `net.p3pp3rf1y`; require none. Target class names inside `@Mixin(targets=...)` strings are allowed.

---

### Task 4: Forge runtime acceptance

**Files:** no source change unless a concrete mismatch is observed.

- [ ] Launch Forge `runClient` with the three Sophisticated dev mods already under `forge/dev-mods`; verify lifecycle reaches main menu/world without Mixin apply errors.
- [ ] Open backpack and verify dark frame, recessed dark slots, copper selected/active controls, cream titles/labels, readable icons, and unchanged visible hitbox alignment.
- [ ] Verify sorting, search, inventory scrolling, upgrade side panel, upgrade enable/disable, settings, tooltips, drag and shift-click.
- [ ] Verify GUI scales 1/2/3/Auto where practical.
- [ ] If a visual mismatch remains, record exact screen/element/state and make a separate bounded render-only follow-up. Do not replace Sophisticated screen classes.
