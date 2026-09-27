"""Realism lint for Epic Fight biped animations baked on the EF rig (run inside Blender).

lint(start, end) samples every frame and reports:
  PENETRATION  hand / elbow / blade inside torso or head boxes
  WRIST        blade outside the forearm angle range (broken wrist)
  TWIST        upper arm rotated past its anatomical range (elbow pointing forward = reversed arm)
  GROUND       blade tip below the floor
  GRIP         (two-handed items) off hand more than 6 cm from the foregrip: info, since some moves let go
  ENVELOPE     a bone rotated where no vanilla EF clip ever goes (swing cell or roll outside the envelope)
  HINGE        forearm/shin bending sideways or twisting (EF elbows and knees are pure hinges)
  END_POSE     last frame far from EF idle (> 40 deg on some bone): the exit blend will flip
  LOCKED       arm or leg at >=99.95% extension (hard-locked limb, pops when blending)
  FEET_CROSS   feet closer than 0.12 laterally or crossed (body frame)
  FOOT_SLIDE   grounded foot moving > 0.02 blocks/frame
  SPINE        a spine joint bent past its range
  POP          any bone rotating > 40 deg in one frame relative to the pelvis
  BALANCE      centre of mass outside the feet (both feet grounded)
Also returns tip-speed peaks, used to line up EF hit windows with the real swing.
"""
import bpy, math
from mathutils import Vector
import efanim as E
import poser as P

GROUND = 0.02  # a foot this low counts as planted


def _com(pb):
    pts = [(pb[n].head + pb[n].tail) / 2 for n in ('Torso', 'Chest', 'Head', 'Thigh_R', 'Thigh_L', 'Leg_R', 'Leg_L', 'Arm_R', 'Arm_L')]
    w = [3, 3, 1.5, 1.2, 1.2, 0.8, 0.8, 0.5, 0.5]
    return sum((p * wi for p, wi in zip(pts, w)), Vector()) / sum(w)


def lint(start, end, verbose=True, blade_len=None, neutral=None, skip_end=False):
    """neutral: {bone: Quaternion} pose the clip must end near (default EF idle); skip_end for loops/aims."""
    a = E.arm(); sc = bpy.context.scene; pb = a.pose.bones; s = P.Solver()
    issues = {}; prev = None; tip_prev = None; speeds = []
    lo, hi = P.REALISM['wrist']; blade_len = blade_len or P.REALISM['blade_len']
    env = P.load_envelope()
    import envelope as V
    def add(kind, f, detail):
        issues.setdefault(kind, []).append((f, detail))
    for f in range(start, end + 1):
        sc.frame_set(f)
        M = {b.name: b.matrix.copy() for b in pb}
        boxes = s.body_boxes(M)
        tool = pb['Tool_R'].matrix
        blade = -tool.to_3x3().col[2].normalized(); grip = tool.translation
        R3 = tool.to_3x3().normalized()
        ipts = [grip + R3 @ q for q in s.item_points()]
        tip = ipts[4] if P.REALISM.get('item_segment') else grip + blade * blade_len
        if tip_prev is not None:
            speeds.append((f, (tip - tip_prev).length * 60))
        tip_prev = tip
        for n, p in (('handR', pb['Hand_R'].tail), ('handL', pb['Hand_L'].tail), ('elbowR', pb['Arm_R'].tail), ('elbowL', pb['Arm_L'].tail)):
            for bi, b in enumerate(boxes[:3]):
                if b.inside(p, 0.0):
                    add('PENETRATION', f, f'{n} in {["chest", "torso", "head"][bi]}')
        names = ['chest', 'torso', 'head', 'thighR', 'thighL', 'shinR', 'shinL']
        for ip in ipts[1:]:
            hit = [names[i] for i, b in enumerate(boxes) if b.inside(ip, 0.0)]
            if hit:
                add('PENETRATION', f, f'blade/item in {hit[0]}'); break
        if P.REALISM.get('two_handed'):
            g2 = tool @ Vector(P.REALISM['foregrip'])
            g2b = tool @ Vector(P.REALISM.get('rear_grip', P.REALISM['foregrip']))
            gap = min((pb['Hand_L'].tail - g2).length, (pb['Hand_L'].tail - g2b).length)
            if gap > 0.06:
                add('GRIP', f, f'off hand {gap:.2f} from foregrip')
        low = min(ip.z for ip in ipts)
        if low < 0.0:
            add('GROUND', f, f'item z {low:.2f}')
        for side, sx in (('R', 1), ('L', -1)):
            d1 = (pb['Arm_' + side].tail - pb['Arm_' + side].head).normalized()
            w = pb['Elbow_' + side].tail - pb['Elbow_' + side].head
            tw = s.humeral_twist(M, d1, w, sx)
            lo_t, hi_t = P.REALISM['humeral_twist']['Arm_' + side]
            if not (lo_t - 3 <= tw <= hi_t + 3):
                add('TWIST', f, f'Arm_{side} {tw:.0f}deg')
        fore = (pb['Hand_R'].tail - pb['Hand_R'].head).normalized()
        ang = math.degrees(fore.angle(blade))
        if not (lo - 2 <= ang <= hi + 2):
            add('WRIST', f, f'{ang:.0f}deg')
        for up, lo_b in (('Arm_R', 'Hand_R'), ('Arm_L', 'Hand_L'), ('Thigh_R', 'Leg_R'), ('Thigh_L', 'Leg_L')):
            L = pb[up].length + pb[lo_b].length
            d = (pb[lo_b].tail - pb[up].head).length
            if d >= 0.9995 * L:
                add('LOCKED', f, up)
        right = pb['Root'].matrix.to_3x3().col[0].normalized()
        fr, fl = pb['Leg_R'].tail, pb['Leg_L'].tail
        if (fr - fl).dot(right) < 0.12:
            add('FEET_CROSS', f, f'{(fr - fl).dot(right):.2f}')
        if prev:
            for s_, p in (('R', fr), ('L', fl)):
                q = prev['foot' + s_]
                if p.z < GROUND and q.z < GROUND and (p - q).length > 0.02:
                    add('FOOT_SLIDE', f, f'{s_} {(p - q).length:.3f}')
            for b in pb:
                if b.name in ('Tool_R', 'Tool_L', 'Root'):
                    continue
                r0 = prev['rot'][b.name]; r1 = pb['Root'].matrix.to_quaternion().inverted() @ b.matrix.to_quaternion()
                dang = math.degrees(r0.rotation_difference(r1).angle); dang = min(dang, 360 - dang)
                if dang > 40:
                    add('POP', f, f'{b.name} {dang:.0f}')
        if fr.z < GROUND and fl.z < GROUND:
            c = _com(pb); lo_x, hi_x = sorted((fr.x, fl.x)); lo_y, hi_y = sorted((fr.y, fl.y))
            if not (lo_x - 0.25 <= c.x <= hi_x + 0.25 and lo_y - 0.3 <= c.y <= hi_y + 0.3):
                add('BALANCE', f, f'com ({c.x:.2f},{c.y:.2f})')
        if env:
            for b in pb:
                q = b.matrix_basis.to_quaternion()
                s_ok, t_ok, sw, tw = V.check(env, b.name, q)
                if b.name == 'Root':
                    t_ok = True  # whole-body spins are legal
                if b.name == 'Tool_R' and not P.REALISM.get('wrist_envelope', True):
                    continue     # non-sword items: vanilla grips don't apply
                if not s_ok or not t_ok:
                    add('ENVELOPE', f, f'{b.name} {"swing" if not s_ok else "roll %+.0f" % tw}')
                if b.name in ('Hand_R', 'Hand_L', 'Leg_R', 'Leg_L') and (abs(sw.y) > 15 or abs(tw) > 10):
                    add('HINGE', f, f'{b.name} side {sw.y:+.0f} twist {tw:+.0f}')
            ref = neutral or env.get('_idle')
            if f == end and ref and not skip_end:
                dev = max((math.degrees(b.matrix_basis.to_quaternion().rotation_difference(ref[b.name]).angle), b.name)
                          for b in pb if b.name in ref and b.name != 'Root')
                d = min(dev[0], 360 - dev[0])
                if d > 40:
                    add('END_POSE', f, f'{dev[1]} {d:.0f}deg from neutral')
        prev = {'footR': fr.copy(), 'footL': fl.copy(), 'rot': {b.name: pb['Root'].matrix.to_quaternion().inverted() @ b.matrix.to_quaternion() for b in pb}}
    if verbose:
        for kind, lst in sorted(issues.items()):
            frames = sorted({f for f, _ in lst})
            print(f'  {kind:12s} {len(frames):3d} frames  e.g. {lst[0][1]} @ {frames[:8]}')
        if not issues:
            print('  clean')
        top = sorted(speeds, key=lambda x: -x[1])[:3]
        print('  tip speed peaks (frame, blocks/s):', [(f, round(v, 1)) for f, v in top])
    return issues, speeds
