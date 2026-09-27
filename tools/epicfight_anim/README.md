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
