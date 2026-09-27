# Tech stack
- Minecraft 1.20.1 only.
- Java 17 source/target.
- Gradle 8.12.1 wrapper.
- Architectury Plugin 3.4.x; Architectury API 9.2.14; Architectury Loom 1.14-SNAPSHOT/beta line.
- Forge 1.20.1-47.4.22; Fabric Loader 0.19.3; Fabric API 0.92.11+1.20.1.
- JUnit Jupiter 5.11.4 in common tests.
- LenlorUI is a sibling project dependency pinned by `lenlorui_version` and `lenlorui_project` in `gradle.properties`; expected sibling project `../LenlorUI-Rust`.
- Optional integrations include Curios (Forge implementation via `@ExpectPlatform`), Easy NPC, Pufferfish Skills, Origins, MonsterRoll, and SWEM horse support (Forge/runtime reflection).
- Mixins are required and target Java 17; common `rotasutils.mixins.json` has `defaultRequire: 1`.