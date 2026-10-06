# Epic Fight animation authoring (Zenith moveset)

Rig: `EpicFight Animation Rig.blend` (EF 20-bone biped). See the `epicfight-animation-realism` skill for rules and workflow. Moves are defined as keyed specs in `zenith.py`
(hand IK targets, blade/edge directions, spine angles, feet), solved per frame by `poser.py`, exported by `efanim.py`.

Preview (contact-sheet PNGs):
    blender -b "EpicFight Animation Rig.blend" --python zenith.py -- <outdir> preview [zenith_auto1 ...]
    python sheet.py <outdir> zenith_auto1

Export to the mod:
    blender -b "EpicFight Animation Rig.blend" --python zenith.py -- ../../forge/src/main/resources/assets/rotasutils/animmodels/animations/biped/combat export

Timings (antic/contact/recovery, blade-ring time) live in `forge/.../epicfight/ZenithMoveset.java`; keep them in sync with key frames (60 fps).

Check any clip (STRICT house standard + PARITY with vanilla EF):
    blender -b "EpicFight Animation Rig.blend" --python check.py -- ../../forge/src/main/resources/assets/rotasutils/animmodels/animations/biped/combat/zenith_auto1.json

`ef_envelope.json` is learned from Epic Fight 20.14.17; rebuild with `build_envelope.py -- <epicfight jar>` after an EF update.

## Capoeira moveset (unarmed, held item: Capoeira Wraps)

`capoeira.py` authors the ginga idle loop, four kicks (meia-lua de frente, martelo, bencao, armada), the rasteira dash and the Roda skill (rasteira into armada).

    blender -b "EpicFight Animation Rig.blend" --python capoeira.py -- <outdir> preview [move ...]   # STEP=3 VIEWS=side,quarter env vars; then COLS=5 python sheet.py <outdir> <move> side quarter
    blender -b "EpicFight Animation Rig.blend" --python capoeira.py -- <outdir> diag [move ...]      # where each foot really lands vs the key
    blender -b "EpicFight Animation Rig.blend" --python capoeira.py -- ../../forge/src/main/resources/assets/rotasutils/animmodels/animations/biped export
    blender -b "EpicFight Animation Rig.blend" --python check.py -- <clips> --config capoeira --loop capoeira_ginga
    blender -b "EpicFight Animation Rig.blend" --python lintdetail.py -- capoeira <clips> --loop capoeira_ginga
    blender -b "EpicFight Animation Rig.blend" --python refview.py -- <outdir> <any clip.json>      # look at a reference animator's kick

The solver shifts the pelvis over the supporting foot (`balance`), and the lint enforces human limits (`human_limits`: COM over the support foot, a turn >= 0.55 s, feet <= 16 blocks/s, jump physics). All seven clips pass STRICT; `lint_allow` only excuses crossing feet and the thigh envelope. Hit windows in `forge/.../epicfight/CapoeiraMoveset.java` follow the clips' key frames. Art: `python scripts/gen_capoeira_art.py`.

Capoeira specs use 60 Hz frame numbers; final IK/export uses 120 Hz without changing duration. Use absolute output paths: loading the rig can change Blender's working directory. For `check.py`, pass `--neutral <absolute combat/capoeira_auto1.json>` so recovery is compared with the attack entry guard. Set `CAPOEIRA_CONTACT_REPORT` to an absolute scratch JSON path during export, then run `continuity.py -- <clips> --fps 120 --contacts <report> --loop capoeira_ginga --report <audit.json>`. This checks exported interpolation, rotational steps, acceleration, authored contacts and loop seams. `review_export.py -- <absolute preview directory> <clips>` reimports every exported sample and renders continuous fixed-camera playback. Passing these checks does not establish runtime attack/idle transitions; verify the exact loaded Forge artifact in gameplay. The reusable `epicfight-animation-continuity` skill documents this workflow.
