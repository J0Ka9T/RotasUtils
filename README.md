# RotasUtils

Quest boards, danger ranks, player levels and skill trees as one RPG progression framework.

* Minecraft **1.20.1** (exactly — the published metadata refuses other versions on purpose, because
  the mod ships version-specific Mixins)
* Architectury: `common` + `fabric` + `forge` modules
* Forge 47.x, Fabric Loader 0.19.3 / Fabric API 0.92.x, Java 17

## Building

```bash
# from the RotasUtils directory
./gradlew build          # common tests + both loader jars
```

Outputs:

| Artifact | Path |
| --- | --- |
| Fabric mod | `fabric/build/libs/RotasUtils-fabric-<version>.jar` |
| Forge mod | `forge/build/libs/RotasUtils-forge-<version>.jar` |

Per-module targets:

```bash
./gradlew :common:test        # common unit tests
./gradlew :fabric:build       # Fabric jar
./gradlew :forge:build        # Forge jar
```

### External prerequisite: LenlorUI

RotasUtils renders its UI through **LenlorUI**, which is maintained as a **sibling source project**
(`../LenlorUI-Rust`). LenlorUI is not published to a Maven repository, so it must be built once
before RotasUtils can compile:

```bash
cd ../LenlorUI-Rust
./gradlew build
```

The version and location are pinned in `gradle.properties`:

```properties
lenlorui_version=0.3.0
lenlorui_project=LenlorUI-Rust
```

`build.gradle` resolves each module's jar through a single `lenloruiArtifact(...)` helper
(`common/build/devlibs/LenlorUI-common-<version>-dev.jar`,
`fabric|forge/build/libs/LenlorUI-<platform>-<version>.jar`). Resolution is lazy, so tasks that do
not need LenlorUI still run, and a missing artifact fails with the exact expected path and the build
command above instead of a bare "file not found".

Bumping LenlorUI is therefore a one-line change to `lenlorui_version`, and the module jars,
Forge/Fabric metadata ranges and the runtime classpath all follow it.

> The remaining external prerequisite is the sibling checkout itself. If LenlorUI is ever published
> to a Maven repository (or vendored as a Git submodule), the helper is the only thing that needs to
> change — `dependencies { }` in the three modules can then reference plain coordinates.

## Optional integrations

* **Curios (Forge)** — optional. When present, the Character Hub routes every accessory
  equip/unequip through Curios' own rules (Curse of Binding, `ICurio.canUnequip`,
  `CurioUnequipEvent`), and only returns the item after Curios removed it. Fabric has no Curios and
  the bridge is a no-op there.
* **Pufferfish Skills** — optional; can host skill points instead of the built-in trees.
* **Easy NPC** — optional developer dependency used by the NPC editor.

## Scope notes

The vanilla experience subsystem is intentionally untouched in this codebase's current state:
`PlayerExperienceMixin`, `ExperienceOrbMixin`, RPG XP gain, vanilla XP suppression, enchanted-level
mirroring, Mending and enchanting behaviour are all left as-is pending a separate redesign.
