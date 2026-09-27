# Suggested commands (Windows)
- Common unit tests: `gradlew.bat :common:test --no-daemon`
- Cross-loader build: `gradlew.bat :forge:build :fabric:build --no-daemon`
- Full verification used by project work log: `gradlew.bat :common:test :forge:build :fabric:build --no-daemon`
- Full Gradle build: `gradlew.bat build --no-daemon`
- Server smoke harness exists at `scripts/rpg-console-smoke.py`; it has historically been used for RPG console/kernel smoke verification, but newer live-client/runtime features are not necessarily covered.
- Do not run multiple Gradle invocations concurrently in this workspace; prior work notes report Loom cache-lock contention.
- Memory graph sanity check from project root: `serena memories check`.