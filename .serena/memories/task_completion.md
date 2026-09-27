# Task completion gates
- For common/domain changes run `gradlew.bat :common:test --no-daemon`.
- For any gameplay, mixin, resource, compat, networking, or loader-facing change also run `gradlew.bat :forge:build :fabric:build --no-daemon`.
- Preferred final gate: `gradlew.bat :common:test :forge:build :fabric:build --no-daemon` and require BUILD SUCCESSFUL with zero test failures/errors.
- Inspect warnings; current known baseline includes benign `unknown enum constant EnvType.CLIENT` notes during cross-loader compilation and Gradle deprecation debt. New warnings should not be accepted silently.
- Changes involving Mixins, Easy NPC, Curios, SWEM/reflection, rendering/UI, disconnect lifecycle, or world events need live loader verification when practical; unit/build success alone does not prove runtime hooks.
- For persistence/schema changes, include round-trip/safety tests and migration coverage. For networking/admin changes, include malformed/stale/permission/rate-limit tests.
- For per-tick/per-player/per-entity code, verify bounded work and avoid chunk/world scans.
- Do not claim a runtime feature verified unless it was actually exercised in a client/dedicated-server smoke or equivalent integration run.