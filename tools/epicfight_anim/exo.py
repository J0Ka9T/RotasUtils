"""ExoElectric Disintegrator moveset for Epic Fight (a heavy two-handed beam gun), 60 fps, EF biped rig.

Living (composite layers over EF's own idle/walk/run legs):
  exo_hold        low ready: gun angled across the body, off hand on the foregrip      (arms mask)
  exo_hold_run    port arms: gun diagonal across the chest while running               (arms mask)
  exo_aim_*       shouldered aim, mid / up / down / lying, blended by look pitch       (root_upper_joints)
Attacks (full body, authored facing +Y; start and end in the hold stance on EF's idle body):
  exo_auto1       Stock Jab          - reverse the gun and drive the buttstock forward
  exo_auto2       Barrel Sweep       - two-handed club swing, left to right
  exo_auto3       Point-Blank        - step in, hip-fire discharge, recoil kicks the gun up
  exo_overdrive   innate skill       - brace, shoulder, charge, Annihilation Lance fires, recoil slides you back

Gun on the hand joint (from EF's item render: Tool_R x rotX(-90) x the model's display transform):
barrel = Tool_R local +Y, top rail = local -Z, grip ~ at the joint, stock butt (0,-0.36,-0.29), muzzle (0,0.5,-0.29),
foregrip (0,0.22,-0.18). In spec terms: edge = barrel direction, blade = top-of-gun direction.
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
from mathutils import Vector, Matrix
import efanim as E
import poser as P

P.REALISM.update(
    item_segment=((0, -0.36, -0.29), (0, 0.5, -0.29)),   # stock butt -> muzzle
    foregrip=(0, 0.22, -0.18),
    wrist_envelope=False,
    two_handed=True,
    rear_grip=(0, 0.06, -0.22),   # receiver: the off hand's hold for near-vertical aims
)
BUTT = Vector((0, -0.36, -0.29))
IDLE_YAW = 0.0
_ready = P.idle_key()
IDLE_YAW = _ready['root'][3] if _ready else -31.3


def rz(v, deg):
    return tuple(Matrix.Rotation(math.radians(deg), 3, 'Z') @ Vector(v))


def gun(barrel, top=(0, 0, 1)):
    """Spec channels for the gun: barrel direction and (approximate) top-of-gun direction."""
    b = Vector(barrel).normalized(); t = Vector(top)
    t = (t - b * b.dot(t)).normalized()
    return dict(edge=tuple(b), blade=tuple(t))


def gun_frame(barrel, top):
    g = gun(barrel, top)
    return P.frame_from(Vector((0, 0, -1)), Vector((0, 1, 0)), Vector(g['blade']), Vector(g['edge']))


def shoulder(butt, barrel, top=(0, 0, 1)):
    """Hand target that puts the stock butt at `butt` with the barrel along `barrel`."""
    R = gun_frame(barrel, top)
    hand = Vector(butt) - R @ BUTT
    return dict(handR=tuple(round(x, 3) for x in hand), **gun(barrel, top))


def chest(d):
    """Stance arms are authored facing the pelvis and solved with bodyframe=1, so they stay in the
    hands whichever way the body is turned (EF's idle pelvis faces IDLE_YAW)."""
    return dict(d)


def k(f, base=None, **ch):
    d = dict(base or {}); d.update(ch); d['f'] = f; return d


def w(f, base=None, **ch):
    """Attack key: arm channels in the world frame (the strike goes where it is aimed)."""
    return k(f, base, bodyframe=(0, 0, 0), **ch)


# ---- stances ------------------------------------------------------------------------------
HOLD_ARMS = chest(dict(handR=(0.1, 0.25, 1.05), poleR=(0.45, -0.4, -0.8), handL=(-0.15, 0.45, 0.95),
                       poleL=(-0.55, -0.2, -0.8), **gun((-0.6, 0.7, -0.38), (0, 0.3, 1))))
READY = dict(P.idle_key() or {}, support=(1, 0, 0), bodyframe=(1, 0, 0), **HOLD_ARMS)


def at(f, dy=0.0, **ch):
    """Hold stance on the idle body after `dy` root motion (feet under the body)."""
    r = READY['root']; fr, fl = READY['footR'], READY['footL']
    return k(f, READY, root=(r[0], r[1] + dy, r[2]) + tuple(r[3:]),
             footR=(fr[0], fr[1] + dy, fr[2]), footL=(fl[0], fl[1] + dy, fl[2]), **ch)


HOLD = [
    at(0),
    k(60, READY, **chest(dict(handR=(0.1, 0.25, 1.062), **gun((-0.6, 0.71, -0.35), (0, 0.3, 1))))),  # breath in
    at(120),
]

RUN_ARMS = chest(dict(handR=(0.22, 0.16, 1.0), poleR=(0.5, -0.3, -0.8), handL=(-0.2, 0.3, 1.35),
                      poleL=(-0.6, -0.1, -0.7), **gun((-0.5, 0.28, 0.82), (0, 1, 0))))
HOLD_RUN = [k(0, READY, **RUN_ARMS), k(20, READY, **dict(RUN_ARMS, handR=(0.22, 0.16, 1.015))),
            k(40, READY, **RUN_ARMS)]

# aim: bladed stance, stock in the right shoulder pocket, barrel on the look direction
AIM_BODY = dict(root=(0, 0, -0.02, -45, 2, 0), torso=(0, 2, 0), chest=(0, 2, 0), head=(45, 0, 0),
                footR=(0.2, -0.1, 0.011), footL=(-0.14, 0.2, 0.011), support=(1, 0, 0), bodyframe=(0, 0, 0),
                poleR=(0.7, -0.3, -0.6), poleL=(-0.5, -0.1, -0.85), handL=(-0.1, 0.6, 1.3))


def aim(barrel, top, butt, **body):
    d = dict(READY); d.update(AIM_BODY); d.update(body); d.update(shoulder(butt, barrel, top))
    return [k(0, d), k(26, d)]


AIM_MID = aim((0, 1, 0), (0, 0, 1), (0.2, -0.12, 1.42))
AIM_UP = aim((0, 0.12, 1), (0, -1, 0.12), (0.2, -0.16, 1.48), grip2=P.REALISM['rear_grip'],
             root=(0, -0.03, -0.02, -45, -12, 0), torso=(0, -6, 0), chest=(0, -12, 0), head=(45, -40, 0))
AIM_DOWN = aim((0, 0.25, -1), (0, 1, 0.25), (0.14, 0.08, 1.28), grip2=P.REALISM['rear_grip'],
               root=(0, 0.02, -0.05, -45, 18, 0), torso=(0, 12, 0), chest=(0, 14, 0), head=(45, 40, 0))
AIM_LYING = AIM_MID

# ---- attacks --------------------------------------------------------------------------------
AUTO1 = [  # Stock Jab: flip the gun back over the shoulder, drive the butt forward
    at(0),
    w(8, root=(0, -0.05, -0.07, -35, -3, 0), torso=(-5, 0, 0), chest=(-10, -3, 0), head=(20, 0, 0),
      **(dict(handR=(0.34, -0.02, 1.22), poleR=(0.7, -0.2, -0.5), **gun((0.2, -0.6, 0.78), (0, -0.8, -0.6)),
                   handL=(-0.42, 0.28, 1.12), poleL=(-0.6, -0.3, -0.6))), support=(0, 0, 0),
      footR=(0.2, -0.08, 0.011)),
    w(12, root=(0, 0.22, -0.1, 5, 12, 0), torso=(5, 4, 0), chest=(10, 6, 0), head=(-18, -8, 0),
      **(dict(handR=(0.14, 0.5, 1.22), poleR=(0.6, -0.3, -0.6), **gun((0.12, -0.55, 0.83), (0, -0.85, -0.55)),
                   handL=(-0.45, -0.1, 1.05), poleL=(-0.4, -0.3, -0.85))), support=(0, 0, 0),
      footR=(0.2, -0.1, 0.011), footL=(-0.16, 0.36, 0.011)),
    w(19, root=(0, 0.25, -0.1, 5, 10, 0), torso=(4, 3, 0), chest=(8, 5, 0), head=(-16, -6, 0),
      **(dict(handR=(0.16, 0.44, 1.2), handL=(-0.44, -0.08, 1.05), **gun((0.14, -0.5, 0.85), (0, -0.85, -0.5)))),
      support=(0, 0, 0)),
    # the right hand brings the gun back to low ready first, so the foregrip comes to the left hand
    w(25, root=(0, 0.25, -0.08, -22, 6, 0), torso=(0, 2, 0), chest=(0, 3, 0), head=(18, -4, 0),
      **(dict(handR=(0.16, 0.34, 1.0), poleR=(0.45, -0.4, -0.8), **gun((-0.55, 0.72, -0.4), (0, 0.3, 1)),
              handL=(-0.3, 0.42, 1.0), poleL=(-0.55, -0.25, -0.8))), support=(0, 0, 0)),
    w(29, support=(1, 0, 0)),
    k(34, READY, root=(0, 0.25, -0.05, -28, 4, 0), chest=(2, 2, 0),
      footR=(0.2, -0.1, 0.011), footL=(-0.16, 0.36, 0.011)),
    at(43, 0.25),
]

AUTO2 = [  # Barrel Sweep: two-handed club swing, left to right
    at(0),
    w(9, root=(0, -0.04, -0.09, 34, 2, -4), torso=(8, 0, 0), chest=(18, 0, 0), head=(-50, 0, 0),
      **(dict(handR=(-0.18, 0.02, 1.12), poleR=(0.3, -0.4, -0.85), **gun((-0.85, -0.45, 0.2), (0, 0, 1)),
                   poleL=(-0.4, -0.5, -0.7))),
      footL=(-0.2, 0.12, 0.011)),
    w(14, root=(0, 0.1, -0.12, 5, 8, 0), torso=(0, 2, 0), chest=(0, 4, 0), head=(-10, -5, 0),
      **(dict(handR=(0.2, 0.52, 1.15), poleR=(0.5, -0.4, -0.8), **gun((0.15, 1, 0.05), (0, 0, 1)))),
      footL=(-0.16, 0.3, 0.011)),
    w(18, root=(0, 0.2, -0.12, -40, 5, 4), torso=(-6, 0, 0), chest=(-14, 3, 0), head=(40, -5, 0),
      **(dict(handR=(0.36, 0.5, 1.26), poleR=(0.3, -0.6, -0.75), **gun((0.88, 0.0, 0.1), (0, 0, 1)))),
      footR=(0.22, -0.06, 0.011), footL=(-0.14, 0.34, 0.011)),
    w(25, root=(0, 0.2, -0.1, -36, 4, 3), chest=(-12, 2, 0), head=(34, -3, 0),
      **(dict(handR=(0.35, 0.47, 1.2), **gun((0.85, -0.1, -0.15), (0, 0, 1))))),
    w(28, root=(0, 0.2, -0.08, -28, 4, 2), chest=(-6, 2, 0), head=(22, -2, 0),
      **(dict(handR=(0.26, 0.5, 1.2), **gun((0.65, 0.4, -0.2), (0, 0, 1))))),
    k(33, READY, root=(0, 0.2, -0.05, -30, 4, 0), chest=(0, 2, 0),
      footR=(0.22, -0.06, 0.011), footL=(-0.14, 0.34, 0.011)),
    at(41, 0.2),
]

AUTO3 = [  # Point-Blank: step in, hip-fire, recoil
    at(0),
    w(10, root=(0, -0.02, -0.12, -20, 10, 0), torso=(0, 4, 0), chest=(0, 4, 0), head=(-5, -10, 0),
      **(dict(handR=(0.32, 0.16, 1.02), poleR=(0.5, -0.4, -0.75), **gun((0, 1, -0.15), (0, 0.15, 1)),
                   poleL=(-0.5, -0.3, -0.8))),
      footR=(0.2, -0.08, 0.011)),
    w(16, root=(0, 0.32, -0.14, 0, 14, 0), torso=(0, 5, 0), chest=(0, 6, 0), head=(-25, -12, 0),
      **(dict(handR=(0.28, 0.52, 1.06), poleR=(0.5, -0.4, -0.75), **gun((-0.15, 1, 0), (0, 0, 1)))),
      footL=(-0.18, 0.42, 0.011)),
    # recoil: the muzzle kicks up and the body rocks back
    w(21, root=(0, 0.27, -0.1, 0, 2, 0), torso=(0, -3, 0), chest=(0, -6, 0), head=(-25, 4, 0),
      **(dict(handR=(0.28, 0.44, 1.16), **gun((-0.14, 0.75, 0.66), (0, -0.66, 0.75)))),
      footL=(-0.18, 0.42, 0.011)),
    w(30, root=(0, 0.3, -0.1, -5, 6, 0), torso=(0, 2, 0), chest=(0, 2, 0), head=(-20, -4, 0),
      **(dict(handR=(0.3, 0.54, 1.1), **gun((-0.1, 1, 0.2), (0, -0.2, 1))))),
    k(40, READY, root=(0, 0.3, -0.05, -28, 3, 0), chest=(0, 2, 0),
      footR=(0.2, -0.08, 0.011), footL=(-0.18, 0.42, 0.011)),
    at(50, 0.3),
]


def brace(f, **over):
    """Shouldered, wide braced stance for the Lance."""
    d = dict(root=(0, 0.02, -0.12, -45, 6, 0), torso=(0, 3, 0), chest=(0, 3, 0), head=(45, -2, 0),
             footR=(0.24, -0.16, 0.011), footL=(-0.2, 0.3, 0.011), support=(1, 0, 0), bodyframe=(0, 0, 0),
             poleR=(0.7, -0.3, -0.6), poleL=(-0.5, -0.1, -0.85), handL=(-0.1, 0.6, 1.3))
    d.update(shoulder((0.2, -0.12, 1.36), (0, 1, 0), (0, 0, 1)))
    d.update(over)
    return k(f, READY, **d)


def tremble(f, dx, dz):
    s = shoulder((0.2 + dx, -0.12, 1.36 + dz), (dx * 0.4, 1, dz * 0.4), (0, 0, 1))
    return brace(f, **s)


OVERDRIVE = [  # innate: Annihilation Overdrive
    at(0),
    brace(12),
    tremble(30, 0.004, 0.003), tremble(46, -0.004, 0.006), tremble(62, 0.006, -0.002), tremble(74, -0.005, 0.008),
    brace(80),  # the Lance fires (charge started at 0.2 s; EF event), recoil slams the body back
    brace(86, root=(0, -0.3, -0.1, -45, -6, 0), chest=(0, -8, 0), head=(45, 8, 0),
          **shoulder((0.2, -0.2, 1.42), (0, 0.85, 0.52), (0, -0.52, 0.85)),
          footR=(0.24, -0.4, 0.011), footL=(-0.2, 0.1, 0.011)),
    brace(100, root=(0, -0.38, -0.12, -45, 2, 0), chest=(0, 0, 0),
          **shoulder((0.2, -0.18, 1.38), (0, 0.97, 0.25), (0, -0.25, 0.97)),
          footR=(0.24, -0.48, 0.011), footL=(-0.2, 0.0, 0.011)),
    brace(114, root=(0, -0.4, -0.12, -45, 4, 0),
          **shoulder((0.2, -0.16, 1.36), (0, 1, 0.08), (0, -0.08, 1)),
          footR=(0.24, -0.48, 0.011), footL=(-0.2, 0.0, 0.011)),
    k(124, READY, root=(0, -0.4, -0.05, -30, 3, 0), bodyframe=(1, 0, 0), chest=(0, 2, 0),
      footR=(0.24, -0.48, 0.011), footL=(-0.2, 0.0, 0.011)),
    at(134, -0.4),
]

LIVING = {'exo_hold': HOLD, 'exo_hold_run': HOLD_RUN,
          'exo_aim_mid': AIM_MID, 'exo_aim_up': AIM_UP, 'exo_aim_down': AIM_DOWN, 'exo_aim_lying': AIM_LYING}
ATTACKS = {'exo_auto1': AUTO1, 'exo_auto2': AUTO2, 'exo_auto3': AUTO3, 'exo_overdrive': OVERDRIVE}
MOVES = dict(LIVING, **ATTACKS)
FOLDER = {n: ('living' if n.startswith('exo_hold') else 'combat' if n.startswith(('exo_aim', 'exo_auto')) else 'skill')
          for n in MOVES}

if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    outdir = argv[0]; mode = argv[1] if len(argv) > 1 else 'preview'
    E.build_body(); E.build_gun()
    bpy.context.scene.render.fps = 60
    for name, keys in MOVES.items():
        if len(argv) > 2 and name not in argv[2:]:
            continue
        P.bake(name, [dict(x) for x in keys])
        end = keys[-1]['f']
        if mode == 'export':
            os.makedirs(f'{outdir}/{FOLDER[name]}', exist_ok=True)
            E.export_json(f'{outdir}/{FOLDER[name]}/{name}.json', 0, end)
        else:
            E.render_frames(outdir, list(range(0, end + 1, 4)), views=('quarter', 'behind'), prefix=name, res=260)
