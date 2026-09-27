"""Per-bone rotation envelope learned from vanilla Epic Fight biped animations.

Each bone's local pose rotation (relative to its rest pose) is split into
  swing: where the bone axis points, as an exp-map 2D vector (x, z) in degrees
  twist: roll about the bone's own axis, degrees
build(dir) scans every vanilla clip and stores, per bone, the twist range (percentiles) and the set of
occupied 10-degree swing cells (dilated by one cell). A pose outside is OFF_ENVELOPE: the bone is doing
something no shipped Epic Fight animation does, which is what reads as "rotated off / not neutral".
"""
import json, math, os, glob
from mathutils import Matrix, Quaternion, Vector
import efanim as E

CELL = 10.0


def swing_twist(q):
    q = q.normalized()
    t = Quaternion((q.w, 0.0, q.y, 0.0))
    if t.magnitude < 1e-8:
        t = Quaternion()
    t.normalize()
    sw = q @ t.inverted()
    tw = 2 * math.degrees(math.atan2(t.y, t.w))
    tw = (tw + 180) % 360 - 180
    ax = sw.axis; ang = math.degrees(sw.angle)
    if ang > 180:
        ang -= 360
    return Vector((ax.x * ang, ax.z * ang)), tw


def bone_rot(rest_rel, flat_or_matrix):
    m = flat_or_matrix if isinstance(flat_or_matrix, Matrix) else E.mat_from(flat_or_matrix)
    return (rest_rel.inverted() @ m).to_quaternion()


def build(ref_dir, out_path):
    a = E.arm(); rr = {b.name: E.rest_rel(b) for b in a.data.bones}
    data = {n: {'tw': [], 'cells': set(), 'n': 0} for n in rr}
    files = glob.glob(os.path.join(ref_dir, '**', '*.json'), recursive=True)
    used = 0
    for f in files:
        try:
            anim = json.load(open(f))['animation']
        except Exception:
            continue
        used += 1
        for j in anim:
            n = j.get('name')
            if n not in rr or 'transform' not in j:
                continue
            for flat in j['transform']:
                if not isinstance(flat, list) or len(flat) != 16:
                    continue
                sw, tw = swing_twist(bone_rot(rr[n], flat))
                d = data[n]; d['tw'].append(tw); d['n'] += 1
                d['cells'].add((round(sw.x / CELL), round(sw.y / CELL)))
                d.setdefault('sw', []).append(sw)
    # hinge axes: mean swing direction of clearly bent samples (elbows/knees are hinges in EF)
    hinges = {}
    for n in ('Hand_R', 'Hand_L', 'Leg_R', 'Leg_L'):
        pts = data[n].get('sw', [])
        big = [v for v in pts if v.length > 25]
        if big:
            m = sum(big, Vector((0, 0))) / len(big)
            ang = [v.length for v in big]
            hinges[n] = {'axis': [m.x / m.length, m.y / m.length], 'max': sorted(ang)[int(len(ang) * 0.99)]}
    env = {}
    for n, d in data.items():
        if not d['tw']:
            continue
        tws = sorted(d['tw']); k = len(tws)
        lo, hi = tws[int(k * 0.005)], tws[min(k - 1, int(k * 0.995))]
        cells = set()
        for (x, y) in d['cells']:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    cells.add((x + dx, y + dy))
        env[n] = {'twist': [lo - 5, hi + 5], 'cells': sorted(cells), 'samples': d['n']}
    # rest-relative idle pose (first frame of living/idle.json) for settle-to-neutral blending
    idle = {}
    ip = os.path.join(ref_dir, 'living', 'idle.json')
    if os.path.exists(ip):
        for j in json.load(open(ip))['animation']:
            if j['name'] in rr:
                q = bone_rot(rr[j['name']], j['transform'][0]); idle[j['name']] = [q.w, q.x, q.y, q.z]
    json.dump({'clips': used, 'cell': CELL, 'bones': env, 'hinges': hinges, 'idle': idle}, open(out_path, 'w'))
    return env


def load(path):
    d = json.load(open(path))
    env = {n: {'twist': v['twist'], 'cells': set(map(tuple, v['cells']))} for n, v in d['bones'].items()}
    env['_hinges'] = d.get('hinges', {}); env['_idle'] = {n: Quaternion(v) for n, v in d.get('idle', {}).items()}
    return env


def check(env, name, q):
    """Returns (swing_ok, twist_ok, swing_vec, twist)."""
    e = env.get(name)
    sw, tw = swing_twist(q)
    if e is None:
        return True, True, sw, tw
    cell = (round(sw.x / CELL), round(sw.y / CELL))
    return cell in e['cells'], e['twist'][0] <= tw <= e['twist'][1], sw, tw
