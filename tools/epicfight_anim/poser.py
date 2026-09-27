"""Spec-driven posing for the Epic Fight biped rig, with biomechanical constraints.

A key is a dict of channels; channels are interpolated per frame (Catmull-Rom, optional per-channel lag
for overlapping action), then solved with realism constraints:
  root:  (dx, dy, dz, twist_left, bend_fwd, lean_right)   degrees; offsets in blocks
  spin:  (yaw_left, 0, 0) whole-body yaw that also carries hand/blade/foot frames (spin attacks)
  bodyframe: (w, 0, 0) 1 = arm channels relative to the pelvis twist (stances), 0 = world (attacks)
  support: (w, 0, 0) off hand pulled onto the held item's grip point (two-handed items)
  grip2: Tool-local point the off hand holds (default REALISM['foregrip']); slide it back for extreme aims
  torso/chest/head: (twist_left, bend_fwd, lean_right)
  handR/handL: wrist target in the spin frame, relative to the root offset
  poleR/poleL: elbow direction hint (spin frame)
  blade/edge:  Tool_R -Z (blade) and +Y (cutting edge) directions (spin frame)
  footR/footL: foot target in world (spun about the root column)
Axes: +X right, +Y forward, +Z up. 60 fps.

Constraints applied every frame (see REALISM):
  spine joint limits, clavicle raise/protraction, soft IK reach, hands/elbows pushed out of the body,
  wrist range (blade vs forearm), blade kept out of the torso, knees tracking over toes, feet never crossing.
"""
import bpy, math
from mathutils import Matrix, Vector
import os
import efanim as E

_ENV = None


def load_envelope():
    """Vanilla Epic Fight rotation envelope (built by envelope.build); None if absent."""
    global _ENV
    if _ENV is None:
        path = REALISM.get('envelope') or os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ef_envelope.json')
        if os.path.exists(path):
            import envelope
            _ENV = envelope.load(path)
        else:
            _ENV = {}
    return _ENV

REALISM = dict(
    spine_twist=(-45, 45), spine_bend=(-18, 40), spine_lean=(-20, 20),   # per joint (Torso, Chest)
    head_twist=(-75, 75), head_bend=(-40, 45), head_lean=(-25, 25),
    root_bend=(-20, 45), root_lean=(-20, 20),
    soft_ik=0.95,               # start softening at this fraction of limb length
    humeral_twist={'Arm_R': (-110, 105), 'Arm_L': (-75, 85)},  # vanilla EF sword arm span; off arm = anatomical ROM
    blade_len=1.0,              # held item length from the grip (vanilla handheld sword ~0.95)
    item_segment=None,          # Tool-local (start, end) of the held item; None = sword along -Z * blade_len
    foregrip=(0, 0.22, -0.18),  # Tool-local point the off hand holds when a key sets support > 0
    wrist_envelope=True,        # snap Tool_R to vanilla sword grips (turn off for guns/other items)
    hinge_max=140,              # elbow/knee flexion cap (vanilla EF max ~110 + margin)
    envelope=None,              # path to ef_envelope.json (default: next to poser.py)
    coupling_rate=0.025,        # two-hand coupling shift may change this much per frame (blocks)
    max_plane_rate=25,          # elbow/knee bend plane turn limit, deg per frame
    max_reach=0.9985,           # EF idle holds arms at ~99.9%: never hard-locked, never needlessly bent
    leg_reach=0.965,            # planted legs keep this much bend at most extension
    step_lift=0.1,              # foot lift (blocks) when a grounded foot moves between keys
    wrist=(35, 150),            # allowed angle between forearm direction and blade
    body_margin=0.07,           # keep hands/elbows this far outside torso/head boxes
    clavicle_raise=0.35, clavicle_raise_max=28, clavicle_fwd=0.3, clavicle_fwd_max=22,
    foot_gap=0.17,              # minimum lateral foot separation in body frame
)
LAG = dict(torso=1, chest=2, head=3, handR=2, poleR=2, blade=3, edge=3, handL=3, poleL=3)


def crom(p0, p1, p2, p3, t0, t1, t2, t3, t):
    def lerp(a, b, ta, tb):
        return a if tb == ta else a + (b - a) * ((t - ta) / (tb - ta))
    a1 = lerp(p0, p1, t0, t1); a2 = lerp(p1, p2, t1, t2); a3 = lerp(p2, p3, t2, t3)
    b1 = lerp(a1, a2, t0, t2); b2 = lerp(a2, a3, t1, t3)
    return lerp(b1, b2, t1, t2)


def sample(keys, name, f):
    ts = [k['f'] for k in keys]; vals = [Vector(k[name]) for k in keys]
    if f <= ts[0]:
        return vals[0].copy()
    if f >= ts[-1]:
        return vals[-1].copy()
    i = max(j for j in range(len(ts) - 1) if ts[j] <= f)
    i0, i3 = max(i - 1, 0), min(i + 2, len(ts) - 1)
    t0 = ts[i0] if i0 != i else ts[i] - 1
    t3 = ts[i3] if i3 != i + 1 else ts[i + 1] + 1
    return crom(vals[i0], vals[i], vals[i + 1], vals[i3], t0, ts[i], ts[i + 1], t3, f)


def fill(keys):
    prev = {}
    for k in keys:
        for c, v in prev.items():
            k.setdefault(c, v)
        prev = {c: v for c, v in k.items() if c != 'f'}
    return keys


def clamp(v, lo_hi):
    return max(lo_hi[0], min(lo_hi[1], v))


def euler_local(tw, bend, lean):
    r = math.radians
    return Matrix.Rotation(r(tw), 4, 'Y') @ Matrix.Rotation(-r(bend), 4, 'X') @ Matrix.Rotation(-r(lean), 4, 'Z')


def frame_from(a_primary, a_secondary, b_primary, b_secondary):
    def basis(p, s):
        p = p.normalized(); s = (s - p * p.dot(s))
        if s.length < 1e-6:
            s = p.orthogonal()
        s.normalize(); return Matrix((p, s, p.cross(s))).transposed()
    return basis(b_primary, b_secondary) @ basis(a_primary, a_secondary).transposed()


def with_rot(rot3, pos):
    m = rot3.to_4x4(); m.translation = pos; return m


def rotate_about(M, pivot, axis, deg):
    R = Matrix.Rotation(math.radians(deg), 4, axis)
    return Matrix.Translation(pivot) @ R @ Matrix.Translation(-pivot) @ M


class Box:
    """Oriented box in armature space: centre, 3x3 axes (columns), half extents."""
    def __init__(self, center, axes, half):
        self.c, self.ax, self.h = center, axes, Vector(half)

    def local(self, p):
        return self.ax.transposed() @ (p - self.c)

    def inside(self, p, margin=0.0):
        l = self.local(p)
        return all(abs(l[i]) < self.h[i] + margin for i in range(3))

    def push_out(self, p, margin, prefer=None):
        """Move p to the nearest face (or the `prefer` world direction's face) plus margin."""
        l = self.local(p)
        if not all(abs(l[i]) < self.h[i] + margin for i in range(3)):
            return p
        best = None
        for i in range(3):
            for s in (1, -1):
                d = self.h[i] + margin - s * l[i]
                n = self.ax.col[i] * s
                if prefer is not None:
                    d -= 0.15 * n.dot(prefer)
                if best is None or d < best[0]:
                    best = (d, i, s)
        _, i, s = best
        l[i] = s * (self.h[i] + margin)
        return self.c + self.ax @ l


class Solver:
    def __init__(self):
        self.a = E.arm(); self.b = self.a.data.bones
        self.rr = {b.name: E.rest_rel(b) for b in self.b}
        self.prev_w = {}; self.prev_swing = {}; self.Ryaw = Matrix.Identity(3); self.prev_shift = Vector()
        self.env = load_envelope()
        hg = self.env.get('_hinges', {}) if self.env else {}
        self.hinge_sign = {n: (1 if v['axis'][0] >= 0 else -1) for n, v in hg.items()} or             {'Hand_R': 1, 'Hand_L': 1, 'Leg_R': -1, 'Leg_L': -1}
        self.hinge_max = {n: min(REALISM['hinge_max'], v['max'] + 15) for n, v in hg.items()}
        self.idle_knee = self._idle_knees()

    def _idle_knees(self):
        """Knee helper directions of EF's idle pose, expressed in the pelvis (Root) frame."""
        idle = self.env.get('_idle') if self.env else None
        if not idle:
            return {}
        M = {}
        for b in self.a.data.bones:
            q = idle.get(b.name)
            M[b.name] = self.carried(M, b.name) @ (q.to_matrix().to_4x4() if q is not None else Matrix.Identity(4))
        Ri = M['Root'].to_3x3().inverted()
        return {s: (Ri @ M['Knee_' + s].to_3x3().col[1]).normalized() for s in 'RL'}

    def carried(self, M, name):
        p = self.b[name].parent
        return (M[p.name] if p else Matrix.Identity(4)) @ self.rr[name]

    def body_boxes(self, M):
        ch = M['Chest']; to = M['Torso']; hd = M['Head']
        def box(m, bone, half, along=0.5):
            ax = m.to_3x3().normalized()
            c = m.translation + ax.col[1] * self.b[bone].length * along
            return Box(c, ax, half)
        # half extents in bone-local (x right, y along bone, z back)
        out = [box(ch, 'Chest', (0.25, 0.2, 0.125)), box(to, 'Torso', (0.25, 0.15, 0.125)),
               box(hd, 'Head', (0.25, 0.25, 0.25), 0.55)]
        for n in ('Thigh_R', 'Thigh_L', 'Leg_R', 'Leg_L'):  # legs last: only the blade checks them
            if n in M:
                out.append(box(M[n], n, (0.125, 0.19, 0.125)))
        return out

    def humeral_twist(self, M, d1, w, side):
        """Signed upper-arm twist (deg): elbow direction w vs the neutral elbow for d1.
        Neutral = hanging arm with the elbow pointing back, swung onto d1. Same convention
        as the vanilla Epic Fight measurements in references/biomechanics.md."""
        ch = M['Chest'].to_3x3().normalized()
        w0 = (-ch.col[1]).rotation_difference(d1) @ ch.col[2]
        w0 = w0 - d1 * d1.dot(w0); h = w - d1 * d1.dot(w)
        if w0.length < 1e-6 or h.length < 1e-6:
            return 0.0
        w0.normalize(); h.normalize()
        ang = math.degrees(w0.angle(h))
        return ang if d1.dot(w0.cross(h)) * side > 0 else -ang

    def soft_target(self, S, target, L):
        v = target - S; d = v.length
        if d < 1e-6:
            return target
        s = REALISM['soft_ik'] * L; cap = REALISM['max_reach'] * L
        if d > s:
            span = cap - s
            d = s + span * (1 - math.exp(-(d - s) / span))
        return S + v.normalized() * d

    def two_bone(self, M, upper, lower, helper, target, pole, boxes=None, soft=True):
        C = self.carried(M, upper); S = C.translation
        L1 = self.b[upper].length; L2 = self.b[lower].length
        if soft:
            target = self.soft_target(S, target, L1 + L2)
        if boxes:  # soft reach can pull a pushed-out hand back inside: push again
            for bx in boxes:
                target = bx.push_out(target, REALISM['body_margin'] * 0.6)
        v = target - S; D = min(max(v.length, 0.05), L1 + L2 - 1e-4); u = v.normalized()
        pw = self.prev_w.get(upper)
        if pw is not None:
            pw = self.Ryaw @ pw
        twist_lim = REALISM['humeral_twist'].get(upper)
        env_cells = self.env.get(upper, {}).get('cells') if self.env else None
        Ci = C.to_3x3().inverted()
        from envelope import swing_twist
        side = 1 if upper.endswith('R') else -1

        if pw is not None:
            # a pole nearly parallel to the limb gives an unstable plane: lean on last frame's
            ref = pw - u * u.dot(pw); pp = pole - u * u.dot(pole)
            t = min(1.0, max(0.0, (abs(u.dot(pole.normalized())) - 0.75) / 0.2))
            if ref.length > 1e-4:
                pole = (pp.normalized() * (1 - t) if pp.length > 1e-4 else Vector()) + ref.normalized() * t

        def elbow_for(p):
            w = p - u * u.dot(p)
            if w.length < 1e-4:
                w = u.orthogonal()
            w.normalize()
            ca = max(-1.0, min(1.0, (L1 * L1 + D * D - L2 * L2) / (2 * L1 * D)))
            a = math.acos(ca)
            return S + (u * math.cos(a) + w * math.sin(a)) * L1, w

        def violation(e, w):
            """0 when the elbow is outside the body and the upper-arm twist is anatomical."""
            bad = 0.0
            if boxes and any(bx.inside(e, 0.03) for bx in boxes[:3]):
                bad += 1000
            if twist_lim:
                tw = self.humeral_twist(M, (e - S).normalized(), w, side)
                bad += max(0.0, twist_lim[0] - tw, tw - twist_lim[1])
            if env_cells:  # upper bone should point where vanilla EF clips point it
                d_loc = Ci @ (e - S).normalized()
                sw, _ = swing_twist(Vector((0, 1, 0)).rotation_difference(d_loc))
                if (round(sw.x / 10), round(sw.y / 10)) not in env_cells:
                    bad += 1  # a nudge (x60 = 60): never worth flipping the elbow for
            return bad

        # swing the elbow around the shoulder-wrist axis: first angle (nearest last frame's) that
        # is collision-free and anatomical wins, so the elbow never jumps or reverses
        # cost = anatomy/collision violation + distance from last frame's angle (continuity)
        # + a small pull toward the requested pole
        last = self.prev_swing.get(upper, 0)
        best = None
        for a_ in range(-180, 181, 5):
            e2, w2 = elbow_for(Matrix.Rotation(math.radians(a_), 3, u) @ pole)
            bad = violation(e2, w2)
            cost = bad * 60 + abs(a_ - last) * 0.6 + abs(a_) * 0.05
            if best is None or cost < best[2]:
                best = (bad, a_, cost)
        swing = best[1]
        if swing != 0:  # relax back toward the requested pole when allowed
            back = swing - 5 * (1 if swing > 0 else -1)
            if violation(*elbow_for(Matrix.Rotation(math.radians(back), 3, u) @ pole)) <= best[0]:
                swing = back
        self.prev_swing[upper] = swing
        elbow, w = elbow_for(Matrix.Rotation(math.radians(swing), 3, u) @ pole)
        if pw is not None:
            # the bend plane may turn at most max_plane_rate deg/frame (spin excluded),
            # unless holding it back would break the body or twist limits
            ref = pw - u * u.dot(pw)
            if ref.length > 1e-4:
                ref.normalize(); lim = math.radians(REALISM['max_plane_rate'])
                if ref.angle(w) > lim:
                    ax = ref.cross(w); ax = ax.normalized() if ax.length > 1e-6 else u
                    el, wl = elbow_for(Matrix.Rotation(lim, 3, ax) @ ref)
                    if violation(el, wl) <= violation(elbow, w):
                        elbow, w = el, wl
        self.prev_w[upper] = self.Ryaw.inverted() @ w
        d1 = (elbow - S).normalized(); d2 = (S + u * D - elbow).normalized()
        self.hinge_limb(M, upper, lower, helper, S, d1, d2, w)

    def hinge_limb(self, M, upper, lower, helper, S, d1, d2, w):
        """Elbows and knees are pure hinges in Epic Fight (vanilla forearm/shin twist ~0, sideways
        swing ~0). The upper bone's roll is set so the lower bone's hinge axis is normal to the bend
        plane; the lower bone then only rotates about that axis."""
        hx = self.rr[lower].to_3x3() @ Vector((1, 0, 0))          # lower hinge axis, upper-local
        hx = (hx - Vector((0, 1, 0)) * hx.y).normalized()
        sign = self.hinge_sign.get(lower, 1)                       # +1 elbow flexes about +X, knee -1
        n = d1.cross(d2)
        if n.length < 1e-3:  # straight limb: keep the bend plane from the elbow direction
            # d2 ~ d1 - k*w (the forearm folds away from the elbow point), so d1 x d2 ~ -(d1 x w)
            wp = w - d1 * d1.dot(w)
            n = -d1.cross(wp.normalized() if wp.length > 1e-4 else d1.orthogonal())
        n.normalize()
        U = frame_from(Vector((0, 1, 0)), hx, d1, n * sign)
        bend = math.degrees(d1.angle(d2))
        if bend < 23:
            # nearly straight: the bend plane is ill-defined, take the roll from the elbow helper
            # pointing at w (exactly what EF's idle and vanilla straight-arm poses do)
            hl = self.rr[helper].to_3x3() @ Vector((0, 1, 0))
            wp = w - d1 * d1.dot(w)
            Uh = frame_from(Vector((0, 1, 0)), hl, d1, wp.normalized() if wp.length > 1e-4 else d1.orthogonal())
            t = max(0.0, (bend - 8) / 15)
            U = Uh.to_quaternion().slerp(U.to_quaternion(), t).to_matrix()
        M[upper] = with_rot(U, S)
        M[helper] = self.carried(M, helper)
        C2 = self.carried(M, lower); y0 = C2.to_3x3().col[1]
        A = (M[upper].to_3x3() @ hx).normalized()
        ang = math.atan2(y0.cross(d2).dot(A), y0.dot(d2))
        lim = math.radians(self.hinge_max.get(lower, 150))
        ang = max(-0.05, min(lim, ang * sign)) * sign
        M[lower] = with_rot(Matrix.Rotation(ang, 3, A) @ C2.to_3x3(), C2.translation)

    def clavicle(self, M, side, target):
        """Raise and protract the shoulder when the hand goes high or forward."""
        name = 'Shoulder_' + side; C = self.carried(M, name)
        ch = M['Chest'].to_3x3().normalized()
        up, back = ch.col[1], ch.col[2]; fwd = -back
        S = (C @ self.rr['Arm_' + side]).translation
        d = (target - S)
        if d.length < 1e-6:
            M[name] = C; return
        d.normalize()
        sx = 1 if side == 'R' else -1
        raise_deg = min(REALISM['clavicle_raise_max'], max(0.0, math.degrees(math.asin(max(-1, min(1, d.dot(up))))) * REALISM['clavicle_raise']))
        fwd_deg = min(REALISM['clavicle_fwd_max'], max(0.0, d.dot(fwd) * 90 * REALISM['clavicle_fwd']))
        pivot = C.translation
        m = rotate_about(C, pivot, fwd, -sx * raise_deg)
        m = rotate_about(m, pivot, up, sx * fwd_deg)
        M[name] = m

    def solve(self, k, _settled=False):
        R = REALISM; M = {}
        rx = k['root']; yaw = k.get('spin', (0, 0, 0))[0]
        Ryaw = Matrix.Rotation(math.radians(yaw), 3, 'Z'); off = Vector(rx[:3]); self.Ryaw = Ryaw
        M['Root'] = Matrix.Translation(off) @ self.rr['Root'] @ euler_local(
            rx[3] + yaw, clamp(rx[4], R['root_bend']), clamp(rx[5], R['root_lean']))
        lim = {'torso': (R['spine_twist'], R['spine_bend'], R['spine_lean']),
               'chest': (R['spine_twist'], R['spine_bend'], R['spine_lean']),
               'head': (R['head_twist'], R['head_bend'], R['head_lean'])}
        for n in ('Torso', 'Chest', 'Head'):
            tw, bd, ln = k[n.lower()]; lt, lb, ll = lim[n.lower()]
            M[n] = self.carried(M, n) @ euler_local(clamp(tw, lt), clamp(bd, lb), clamp(ln, ll))
        rootpos = Vector((off.x, off.y, 0))
        W = lambda v: off + Ryaw @ Vector(v)
        D = lambda v: Ryaw @ Vector(v)
        F = lambda v: rootpos + Ryaw @ (Vector(v) - rootpos)
        fwd = D((0, 1, 0))
        # arm channels: world/spin frame (attacks) or, with bodyframe=1, relative to the pelvis facing
        # (stances that must stay in the hands whatever way the body turns)
        bf = k.get('bodyframe', (0, 0, 0))[0]
        Ra = Matrix.Rotation(math.radians(yaw + rx[3] * bf), 3, 'Z')
        WA = lambda v: off + Ra @ Vector(v)
        DA = lambda v: Ra @ Vector(v)
        feet = {s: F(k['foot' + s]) for s in 'RL'}
        right = M['Root'].to_3x3().col[0].normalized(); right.z = 0; right.normalize()
        gap = (feet['R'] - feet['L']).dot(right)
        if gap < R['foot_gap']:
            # only airborne feet are nudged; moving a planted foot would skate it
            fix = R['foot_gap'] - gap
            air = [s_ for s_ in 'RL' if feet[s_].z > 0.05]
            for s_ in air:
                feet[s_] += right * (fix / len(air)) * (1 if s_ == 'R' else -1)
        if not _settled:
            # grounded feet stay exactly planted: lower the pelvis instead of stretching a leg
            drop = 0.0
            for s in 'RL':
                ft = feet[s]
                if ft.z > 0.05:
                    continue
                hip = self.carried(M, 'Thigh_' + s).translation
                L = (self.b['Thigh_' + s].length + self.b['Leg_' + s].length) * R['leg_reach']
                h = (hip - ft).xy.length
                v = math.sqrt(max(L * L - h * h, 0.0))
                drop = max(drop, (hip.z - ft.z) - v)
            if drop > 1e-4:
                k2 = dict(k); r = list(k['root']); r[2] -= drop; k2['root'] = tuple(r)
                return self.solve(k2, True)
        for s, sx in (('R', 1), ('L', -1)):
            hip = (self.carried(M, 'Thigh_' + s)).translation
            toe = feet[s] - hip; toe.z = 0
            # knee points where EF's idle knee points (in the pelvis frame), nudged toward the toe
            base = self.idle_knee.get(s)
            base = (M['Root'].to_3x3() @ base) if base is not None else fwd * 0.8 + right * (0.25 * sx)
            knee = base + (toe.normalized() * 0.4 if toe.length > 0.05 else Vector())
            self.two_bone(M, 'Thigh_' + s, 'Leg_' + s, 'Knee_' + s, feet[s], knee, soft=feet[s].z > 0.05)
        boxes = self.body_boxes(M)
        hands = {}
        for s in 'RL':
            t = WA(k['hand' + s])
            for b in boxes[:3]:
                t = b.push_out(t, R['body_margin'], prefer=fwd)
            hands[s] = t
            self.clavicle(M, s, t)
        self.two_bone(M, 'Arm_R', 'Hand_R', 'Elbow_R', hands['R'], DA(k['poleR']), boxes[:3])
        M['Tool_R'] = self.tool(M, DA(k['blade']), DA(k['edge']), boxes)
        support = k.get('support', (0, 0, 0))[0]
        grip2 = Vector(k.get('grip2') or REALISM['foregrip'])  # where the off hand holds the item
        if support > 0.5 or self.prev_shift.length > 1e-4:
            # two-hand coupling: if the off hand cannot reach its grip, bring the item (and the right
            # hand) toward the off shoulder by the shortfall; the shift is smoothed over frames
            base = hands['R'].copy(); want = Vector()
            if support > 0.5:
                reach = (self.b['Arm_L'].length + self.b['Hand_L'].length) * 0.96
                t = base.copy()
                for _ in range(4):
                    shL = (self.carried(M, 'Shoulder_L') @ self.rr['Arm_L']).translation
                    short = ((M['Tool_R'] @ grip2) - shL).length - reach
                    if short <= 0.005 or (t - base).length >= 0.3:
                        break
                    t = t + (shL - (M['Tool_R'] @ grip2)).normalized() * min(short + 0.01, 0.3)
                    for bx in boxes[:3]:  # never through the body: slide around it, forward first
                        t = bx.push_out(t, REALISM['body_margin'], prefer=fwd)
                    self.two_bone(M, 'Arm_R', 'Hand_R', 'Elbow_R', t, DA(k['poleR']), boxes[:3])
                    M['Tool_R'] = self.tool(M, DA(k['blade']), DA(k['edge']), boxes)
                want = (t - base) * min(1.0, (support - 0.5) * 2)  # ramps in with the grip
            d = want - self.prev_shift
            lim = REALISM['coupling_rate']
            shift = self.prev_shift + (d if d.length <= lim else d.normalized() * lim)
            self.prev_shift = shift
            t = base + shift
            for bx in boxes[:3]:
                t = bx.push_out(t, REALISM['body_margin'], prefer=fwd)
            hands['R'] = t
            self.two_bone(M, 'Arm_R', 'Hand_R', 'Elbow_R', t, DA(k['poleR']), boxes[:3])
            M['Tool_R'] = self.tool(M, DA(k['blade']), DA(k['edge']), boxes)
        if support > 1e-3:
            # two-handed item: the off hand closes on the foregrip of the solved item
            hands['L'] = hands['L'].lerp(M['Tool_R'] @ grip2, min(1.0, support))
            self.clavicle(M, 'L', hands['L'])
        self.two_bone(M, 'Arm_L', 'Hand_L', 'Elbow_L', hands['L'], DA(k['poleL']), boxes[:3],
                      soft=support < 0.5)  # a gripping hand lands exactly on the grip when it can reach
        M['Tool_L'] = self.carried(M, 'Tool_L')
        return M

    def item_points(self):
        """Tool-local sample points along the held item (collision / ground / lint)."""
        seg = REALISM.get('item_segment')
        if not seg:
            seg = ((0, 0, 0), (0, 0, -REALISM['blade_len']))
        a, b = Vector(seg[0]), Vector(seg[1])
        return [a.lerp(b, t) for t in (0.2, 0.35, 0.5, 0.75, 1.0)] + ([a] if a.length > 0.05 else [])

    def tool(self, M, blade, edge, boxes):
        """Tool_R frame: local -Z -> `blade`, local +Y -> `edge` (for a gun: -Z = top of the gun,
        +Y = barrel). Then keep the item above the ground, out of the body and legs, and within
        the wrist range."""
        C = self.carried(M, 'Tool_R'); fore = M['Hand_R'].to_3x3().col[1].normalized()
        lo, hi = (math.radians(a) for a in REALISM['wrist'])
        grip = C.translation
        R = frame_from(Vector((0, 0, -1)), Vector((0, 1, 0)), blade, edge)

        def wrist(R):
            bl = R @ Vector((0, 0, -1)); ang = fore.angle(bl)
            if lo <= ang <= hi:
                return R
            axis = fore.cross(bl)
            axis = axis.normalized() if axis.length > 1e-5 else fore.orthogonal().normalized()
            return Matrix.Rotation(clamp(ang, (lo, hi)) - ang, 3, axis) @ R

        def turn(R, d, toward, deg):  # rotate the item so direction d swings toward `toward`
            ax = d.cross(toward)
            return Matrix.Rotation(math.radians(deg), 3, ax.normalized()) @ R if ax.length > 1e-6 else R

        R = wrist(R)
        pts = self.item_points()
        for _ in range(9):  # lowest point above the ground
            low = min((R @ p for p in pts), key=lambda v: v.z)
            if (grip + low).z >= 0.06 or low.length < 1e-4:
                break
            R = turn(R, low.normalized(), Vector((0, 0, 1)), 6)
        for _ in range(10):  # out of torso, head and legs
            hit = [(R @ p, b) for p in pts for b in boxes if b.inside(grip + R @ p, 0.03)]
            if not hit:
                break
            d, bx = hit[0]
            away = (grip + d - bx.c); away -= d.normalized() * away.dot(d.normalized())
            if away.length < 1e-5:
                away = bx.ax.col[0]
            R = turn(R, d.normalized(), away.normalized(), 10)
        R = wrist(R)
        if REALISM.get('wrist_envelope', True):
            R = self.wrist_envelope(C, R, R @ Vector((0, 0, -1)))
        return with_rot(R, grip)

    def wrist_envelope(self, C, R, blade):
        """Snap the sword's in-hand rotation to the nearest one vanilla EF clips use: first roll the
        edge about the blade, then tilt the blade a little. Keeps the item from flipping in the fist."""
        if not self.env or 'Tool_R' not in self.env:
            return R
        import envelope
        Ci = C.to_3x3().inverted()
        def ok(Rm):
            s_ok, t_ok, _, _ = envelope.check(self.env, 'Tool_R', (Ci @ Rm).to_quaternion())
            return s_ok and t_ok
        if ok(R):
            return R
        for step in range(1, 13):
            for sgn in (1, -1):
                Rr = Matrix.Rotation(math.radians(15 * step * sgn), 3, blade) @ R
                if ok(Rr):
                    return Rr
        side = blade.orthogonal().normalized()
        for tilt in (10, 20, 30):
            for k in range(8):
                ax = Matrix.Rotation(math.radians(45 * k), 3, blade) @ side
                Rt = Matrix.Rotation(math.radians(tilt), 3, ax) @ R
                for step in range(0, 13):
                    for sgn in (1, -1):
                        Rr = Matrix.Rotation(math.radians(15 * step * sgn), 3, Rt @ Vector((0, 0, -1))) @ Rt
                        if ok(Rr):
                            return Rr
        return R

    def key(self, M, frame, prevq):
        for pb in self.a.pose.bones:
            m = M.get(pb.name)
            if m is None:
                m = self.carried(M, pb.name); M[pb.name] = m
            basis = self.carried(M, pb.name).inverted() @ m
            loc, rot, _ = basis.decompose()
            pb.rotation_mode = 'QUATERNION'
            if pb.name in prevq and prevq[pb.name].dot(rot) < 0:
                rot.negate()
            prevq[pb.name] = rot
            pb.rotation_quaternion = rot
            pb.keyframe_insert('rotation_quaternion', frame=frame)
            if pb.name == 'Root':
                pb.location = loc; pb.keyframe_insert('location', frame=frame)


def step_arc(keys, c, f, v):
    """Lift a grounded foot along a sine arc while it travels between two grounded keys."""
    ts = [k['f'] for k in keys]
    if f <= ts[0] or f >= ts[-1]:
        return v
    i = max(j for j in range(len(ts) - 1) if ts[j] <= f)
    a, b = Vector(keys[i][c]), Vector(keys[i + 1][c])
    if a.z > 0.05 or b.z > 0.05:
        return (v[0], v[1], max(v[2], 0.011))  # airborne or landing: never below the ground
    travel = (b - a).xy.length
    t = (f - ts[i]) / (ts[i + 1] - ts[i])
    if travel < 0.05:  # planted: ease between keys, never overshoot through them
        e = t * t * (3 - 2 * t); p = a.lerp(b, e)
        return (p.x, p.y, max(p.z, 0.011))
    lift = REALISM['step_lift'] * min(1.0, travel / 0.25) * math.sin(math.pi * t)
    e = t * t * (3 - 2 * t)  # travel mostly while lifted: no skating at plant/push-off
    xy = a.xy.lerp(b.xy, e)
    return (xy.x, xy.y, max(v[2], 0.011 + lift))


ARM_POINTS = ('handR', 'handL')
ARM_DIRS = ('poleR', 'poleL', 'blade', 'edge')


def resolve_bodyframe(keys):
    """Turn body-relative arm channels (bodyframe=1) into world ones per key, so interpolation between
    stance keys and world-frame attack keys happens in one coordinate frame."""
    for k in keys:
        bf = k.get('bodyframe', (0, 0, 0))[0]
        if bf and k.get('root'):
            R = Matrix.Rotation(math.radians(k['root'][3] * bf), 3, 'Z')
            for c in ARM_POINTS + ARM_DIRS:
                if c in k:
                    k[c] = tuple(R @ Vector(k[c]))
        k['bodyframe'] = (0, 0, 0)
    return keys


def bake(name, keys, lag=None):
    lag = LAG if lag is None else lag
    keys = resolve_bodyframe(fill(keys))
    a = E.arm(); a.animation_data_create()
    act = bpy.data.actions.new(name); a.animation_data.action = act
    s = Solver(); prevq = {}
    chans = [c for c in keys[0] if c != 'f']
    end = keys[-1]['f']
    k0 = {c: tuple(sample(keys, c, keys[0]['f'])) for c in chans}
    for c in ('footR', 'footL'):
        k0[c] = step_arc(keys, c, keys[0]['f'], k0[c])
    for _ in range(24):  # warm up frame-to-frame state (elbow plane, swing, coupling) on the first pose
        s.solve(k0)
    for f in range(keys[0]['f'], end + 1):
        fade = min(1.0, (end - f) / 6.0)  # overlap dies out so the last frame lands on the final key
        k = {c: tuple(sample(keys, c, f - lag.get(c, 0) * fade)) for c in chans}
        for c in ('footR', 'footL'):
            k[c] = step_arc(keys, c, f, k[c])
        s.key(s.solve(k), f, prevq)
    for fc in act.fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation = 'LINEAR'
    return act


def idle_key():
    """READY channels measured from Epic Fight's own idle pose (vanilla living/idle.json), so moves
    start from and settle back to the stance EF blends in from. Falls back to None without an envelope."""
    env = load_envelope()
    idle = env.get('_idle') if env else None
    if not idle:
        return None
    s = Solver(); M = {}
    for b in s.a.data.bones:  # parents come first in bone order
        q = idle.get(b.name)
        basis = q.to_matrix().to_4x4() if q is not None else Matrix.Identity(4)
        M[b.name] = s.carried(M, b.name) @ basis
    root = M['Root'].translation - s.rr['Root'].translation
    def rel(p):
        return tuple(round(v, 3) for v in (p - root))
    def dirn(v):
        return tuple(round(x, 3) for x in v.normalized())
    tool = M['Tool_R'].to_3x3()
    elbow = lambda sd: dirn(M['Elbow_' + sd].to_3x3().col[1])
    tail = lambda n: M[n] @ Vector((0, s.b[n].length, 0))
    def ang(n):  # inverse of euler_local: R = Ry(twist) Rx(-bend) Rz(-lean) == Euler 'ZXY'
        q = idle.get(n)
        if q is None:
            return (0, 0, 0)
        e = q.to_euler('ZXY')
        return (round(math.degrees(e.y), 1), round(-math.degrees(e.x), 1), round(-math.degrees(e.z), 1))
    return dict(root=(round(root.x, 3), round(root.y, 3), round(root.z, 3)) + ang('Root'), spin=(0, 0, 0),
                torso=ang('Torso'), chest=ang('Chest'), head=ang('Head'),
                handR=rel(tail('Hand_R')), handL=rel(tail('Hand_L')), poleR=elbow('R'), poleL=elbow('L'),
                blade=dirn(-tool.col[2]), edge=dirn(tool.col[1]),
                footR=tuple(round(v, 3) for v in (tail('Leg_R').x, tail('Leg_R').y, 0.011)),
                footL=tuple(round(v, 3) for v in (tail('Leg_L').x, tail('Leg_L').y, 0.011)))
