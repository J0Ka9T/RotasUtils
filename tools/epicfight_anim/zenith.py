"""Zenith moveset for Epic Fight, authored at 60 fps on the EF biped rig.

auto1  Crescent Fall     - diagonal forehand cut, high right to low left, with a lunge step
auto2  Horizon Sweep     - flat backhand sweep, left to right, full torso wind
auto3  Zenith Ascension  - crouch, leaping 360 spin slash, overhead slam that releases a ring of blades

Every move starts in Epic Fight's own idle stance (READY, measured from vanilla idle.json) and steps
back into it at the end, shifted by the move's root motion, so EF's blend in and out never flips a bone.
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import efanim as E
import poser as P

READY = P.idle_key() or dict(
    root=(0, 0, 0, 0, 0, 0), spin=(0, 0, 0), torso=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0),
    handR=(0.35, -0.18, 0.84), poleR=(-0.46, -0.89, 0.05), blade=(0.46, 0.89, -0.02), edge=(-0.05, 0.05, 1),
    handL=(-0.35, 0.24, 0.81), poleL=(-0.46, -0.89, 0.03),
    footR=(0.177, -0.042, 0.011), footL=(-0.125, 0.168, 0.011))


def k(f, base=None, **ch):
    d = dict(base or {}); d.update(ch); d['f'] = f; return d


def settle(f, dy, spin=0):
    """READY after the move's root motion `dy`: feet step back under the body into the idle stance."""
    fr, fl = READY['footR'], READY['footL']
    r = READY['root']
    return k(f, READY, root=(r[0], r[1] + dy, r[2]) + tuple(r[3:]), spin=(spin, 0, 0),
             footR=(fr[0], fr[1] + dy, fr[2]), footL=(fl[0], fl[1] + dy, fl[2]))


def recover(f, dy, spin=0, **ch):
    """Halfway home: upper body back near READY while the stance still holds."""
    base = dict(READY)
    base.update(root=(0, dy, -0.05, 0, 4, 0), spin=(spin, 0, 0), chest=(0, 2, 0))
    base.update(ch)
    return k(f, base)


AUTO1 = [
    k(0, READY),
    # wind-up: coil right, weight on the back foot, blade cocked behind the head
    k(10, root=(0, -0.04, -0.08, -25, -4, 0), torso=(-10, 0, 0), chest=(-20, -4, 0), head=(45, 0, 0),
      handR=(0.6, 0.02, 1.78), poleR=(0.7, -0.4, -0.3), blade=(-0.3, -0.55, 0.78), edge=(0.25, 0.65, 0.45),
      handL=(-0.3, 0.4, 1.15), poleL=(-0.6, -0.3, -0.6),
      footR=(0.2, -0.1, 0.011)),
    # mid-cut: hips lead, blade crosses the front on the diagonal, front foot steps in
    k(14, root=(0, 0.12, -0.1, 0, 10, 0), torso=(0, 5, 0), chest=(0, 5, 0), head=(0, -8, 0),
      handR=(0.14, 0.6, 1.3), poleR=(0.7, -0.3, -0.5), blade=(-0.2, 0.95, 0.25), edge=(-0.6, 0.05, -0.8),
      handL=(-0.4, 0.2, 1.05), poleL=(-0.5, -0.4, -0.7),
      footR=(0.2, -0.12, 0.011), footL=(-0.18, 0.3, 0.011)),
    # follow-through: low left, weight over the front foot
    k(18, root=(0, 0.3, -0.15, 15, 18, 0), torso=(15, 5, 0), chest=(25, 8, 0), head=(-45, -15, 0),
      handR=(-0.33, 0.5, 0.8), poleR=(0.3, -0.2, -0.9), blade=(-0.7, -0.25, -0.5), edge=(-0.45, 0.85, -0.2),
      handL=(-0.48, -0.25, 1.02), poleL=(-0.3, -0.3, -0.9),
      footR=(0.2, -0.14, 0.011), footL=(-0.2, 0.38, 0.011)),
    k(26, root=(0, 0.3, -0.12, 12, 12, 0), chest=(22, 6, 0), head=(-38, -10, 0),
      handR=(-0.28, 0.45, 0.85), blade=(-0.6, -0.2, -0.6), edge=(-0.5, 0.85, 0.2)),
    # recover around the front (never back through the belly), blade raised to guard
    recover(33, 0.3, torso=(3, 0, 0), head=(-10, 0, 0),
            handR=(0.34, 0.42, 0.98), poleR=(0.5, -0.5, -0.7), blade=(0.35, 0.9, 0.25), edge=(-0.1, -0.25, 1),
            footR=(0.2, -0.14, 0.011), footL=(-0.2, 0.38, 0.011)),
    settle(42, 0.3),
]

AUTO2 = [
    k(0, READY),
    # gather: sword brought in front of the body on its way across
    k(4, root=(0, -0.02, -0.05, 18, 2, 0), chest=(10, 0, 0), head=(-25, 0, 0),
      handR=(0.14, 0.46, 1.02), poleR=(0.5, -0.5, -0.7), blade=(-0.2, 0.9, 0.35), edge=(-0.9, -0.2, 0.1),
      handL=(-0.42, 0.12, 0.95), poleL=(-0.4, -0.5, -0.7)),
    # wind-up: sword drawn across to the left, torso wound, off hand out for balance
    k(10, root=(0, -0.04, -0.1, 40, 2, -4), torso=(10, 0, 0), chest=(25, 0, 0), head=(-62, 0, 0),
      handR=(-0.3, 0.2, 1.3), poleR=(0.2, -0.35, -0.9), blade=(-0.55, -0.8, 0.12), edge=(-0.8, 0.55, 0),
      handL=(-0.5, -0.02, 1.05), poleL=(-0.4, -0.5, -0.7),
      footL=(-0.24, 0.12, 0.011)),
    k(15, root=(0, 0.1, -0.12, 5, 8, 0), torso=(0, 0, 0), chest=(0, 4, 0), head=(-5, -5, 0),
      handR=(0.1, 0.63, 1.3), poleR=(0.5, -0.4, -0.8), blade=(0.05, 1, 0.05), edge=(1, -0.05, 0),
      handL=(-0.35, 0.05, 1.05), poleL=(-0.4, -0.4, -0.8),
      footL=(-0.16, 0.3, 0.011)),
    # follow-through: carried out to the right, kept inside the shoulder's real range
    k(19, root=(0, 0.22, -0.12, -20, 5, 4), torso=(-6, 0, 0), chest=(-14, 3, 0), head=(40, -5, 0),
      handR=(0.6, 0.42, 1.2), poleR=(0.2, -0.6, -0.8), blade=(0.8, -0.45, 0.1), edge=(-0.45, -0.88, 0),
      handL=(-0.42, -0.2, 1.0), poleL=(-0.3, -0.3, -0.9),
      footR=(0.22, -0.06, 0.011), footL=(-0.14, 0.34, 0.011)),
    k(26, root=(0, 0.22, -0.1, -18, 4, 3), chest=(-12, 2, 0), head=(36, -3, 0),
      handR=(0.55, 0.36, 1.1), blade=(0.65, -0.6, -0.3), edge=(-0.66, -0.66, 0.1)),
    recover(33, 0.22, head=(10, 0, 0), footR=(0.22, -0.06, 0.011), footL=(-0.14, 0.34, 0.011)),
    settle(41, 0.22),
]

SPIN_ARM = dict(handR=(0.8, 0.2, 1.36), poleR=(0.3, -0.5, -0.8), blade=(0.9, 0.42, 0.02), edge=(-0.42, 0.9, 0),
                handL=(-0.72, 0.08, 1.3), poleL=(-0.3, -0.5, -0.8), torso=(0, 0, 0), chest=(0, 0, 0), head=(0, 0, 0))

AUTO3 = [
    k(0, READY),
    # crouch: coil right, sword low behind, weight sinks
    k(10, root=(0, 0, -0.22, -30, 22, 0), torso=(-10, 5, 0), chest=(-15, 5, 0), head=(45, -20, 0),
      handR=(0.46, -0.34, 0.8), poleR=(0.6, -0.2, -0.7), blade=(0.3, -0.9, -0.3), edge=(0, 0.3, 0.95),
      handL=(-0.25, 0.38, 0.95), poleL=(-0.5, -0.3, -0.7),
      footR=(0.22, -0.05, 0.011), footL=(-0.22, 0.12, 0.011)),
    # take-off, arm flung out: the spin begins
    k(16, SPIN_ARM, root=(0, 0.1, 0.35, 0, -4, 0), spin=(0, 0, 0),
      footR=(0.14, 0.02, 0.5), footL=(-0.14, 0.2, 0.55)),
    k(19, SPIN_ARM, root=(0, 0.13, 0.48, 0, -2, 0), spin=(90, 0, 0), footR=(0.14, 0.05, 0.62), footL=(-0.14, 0.22, 0.66)),
    k(22, SPIN_ARM, root=(0, 0.16, 0.55, 0, 0, 0), spin=(180, 0, 0), footR=(0.14, 0.06, 0.7), footL=(-0.14, 0.24, 0.72)),
    k(25, SPIN_ARM, root=(0, 0.19, 0.52, 0, 0, 0), spin=(270, 0, 0), footR=(0.14, 0.08, 0.66), footL=(-0.14, 0.26, 0.68)),
    k(28, SPIN_ARM, root=(0, 0.22, 0.48, 0, -4, 0), spin=(360, 0, 0), footR=(0.14, 0.1, 0.58), footL=(-0.14, 0.28, 0.6)),
    # apex: two-hand grip above and in front of the head, back arched over three joints
    k(32, root=(0, 0.26, 0.45, 0, -12, 0), spin=(360, 0, 0), torso=(0, -5, 0), chest=(0, -10, 0), head=(0, 10, 0),
      handR=(0.2, 0.26, 1.9), poleR=(0.8, -0.2, 0.1), blade=(0.05, -0.7, 0.7), edge=(0, 0.7, 0.7),
      handL=(-0.04, 0.32, 1.84), poleL=(-0.8, -0.2, 0.1),
      footR=(0.14, 0.14, 0.4), footL=(-0.14, 0.3, 0.45)),
    # arc: both hands travel forward over the head (not past the face) as the blade comes up and over
    k(34, root=(0, 0.3, 0.38, 0, -4, 0), torso=(0, 0, 0), chest=(0, -3, 0), head=(0, 4, 0),
      handR=(0.18, 0.48, 1.82), poleR=(0.75, -0.2, -0.1), blade=(0.02, 0.05, 1), edge=(0, 1, -0.05),
      handL=(-0.12, 0.6, 1.7), poleL=(-0.7, -0.35, -0.1)),
    k(37, root=(0, 0.38, 0.2, 0, 10, 0), torso=(0, 5, 0), chest=(0, 5, 0), head=(0, -5, 0),
      handR=(0.14, 0.62, 1.55), poleR=(0.6, -0.2, -0.3), blade=(0, 1, 0.12), edge=(0, 0.12, -1),
      handL=(-0.1, 0.72, 1.4), poleL=(-0.6, -0.2, -0.3),
      footR=(0.18, 0.2, 0.2), footL=(-0.16, 0.5, 0.2)),
    # slam: two-handed into the ground, landing absorbed in the knees
    k(41, root=(0, 0.45, -0.16, 0, 28, 0), torso=(0, 6, 0), chest=(0, 8, 0), head=(0, -30, 0),
      handR=(0.06, 0.98, 0.66), poleR=(0.5, -0.2, -0.8), blade=(0, 0.82, -0.57), edge=(0, -0.57, -0.82),
      handL=(-0.1, 0.9, 0.74), poleL=(-0.5, -0.2, -0.8),
      footR=(0.2, 0.22, 0.011), footL=(-0.18, 0.62, 0.011)),
    k(49, root=(0, 0.45, -0.19, 0, 30, 0), chest=(0, 9, 0), head=(0, -32, 0),
      handR=(0.06, 0.95, 0.62), handL=(-0.1, 0.88, 0.7), blade=(0, 0.8, -0.6), edge=(0, -0.6, -0.8)),
    recover(56, 0.45, spin=360, head=(0, -5, 0),
            handR=(0.34, 0.42, 0.98), poleR=(0.5, -0.5, -0.7), blade=(0.35, 0.9, 0.25), edge=(-0.1, -0.25, 1),
            footR=(0.2, 0.22, 0.011), footL=(-0.18, 0.62, 0.011)),
    settle(64, 0.45, spin=360),
]

MOVES = {'zenith_auto1': AUTO1, 'zenith_auto2': AUTO2, 'zenith_auto3': AUTO3}

if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    outdir = argv[0]; mode = argv[1] if len(argv) > 1 else 'preview'
    E.build_body(); E.build_sword('-Z', length=P.REALISM['blade_len'], color=(0.35, 1.0, 0.55, 1))
    bpy.context.scene.render.fps = 60
    for name, keys in MOVES.items():
        if len(argv) > 2 and name not in argv[2:]:
            continue
        P.bake(name, [dict(x) for x in keys])
        end = keys[-1]['f']
        if mode == 'export':
            E.export_json(f'{outdir}/{name}.json', 0, end)
        else:
            E.render_frames(outdir, list(range(0, end + 1, 3)), views=('quarter', 'front'), prefix=name, res=300)
