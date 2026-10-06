"""Check any Epic Fight biped animation JSON against the realism lint and the vanilla envelope.

    blender -b RIG.blend --python check.py -- anim1.json [anim2.json ...] [--render OUTDIR] [--fps 60]
            [--config MODULE] [--neutral STANCE.json] [--loop NAME ...]

  --config   import a moveset module first so its item settings apply (e.g. a gun's item_segment,
             two_handed, wrist_envelope=False); the module is looked up next to this script, then cwd
  --neutral  clip whose first frame is the stance attacks must end in (default: EF idle)
  --loop     clip names (no .json) that loop or hold a pose: END_POSE does not apply

Imports each clip onto the EF rig (keys at their JSON times), runs lint.lint over every frame and prints
two verdicts:
  STRICT  the house authoring standard. POP is a failure, including during strikes.
  PARITY  never worse than vanilla Epic Fight: ENVELOPE, HINGE and hands/elbows in the body, each allowed
          on <= 3 frames (vanilla clips themselves touch the 0.5% tails of the envelope).
Calibrated against vanilla EF 20.14 sword/longsword/tachi clips: they fail STRICT (feet skate, blade tips
under the floor, big exit poses) but pass PARITY. With --render, writes quarter + behind contact sheets.
Works for clips from this pipeline, from Blockbench/Blender exporters, or vanilla EF files.
"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import efanim as E
import lint as L
import poser as P

HUMAN = ('COM_OFF', 'SPIN_RATE', 'FOOT_SPEED', 'AIR_TIME')
STRICT = ('PENETRATION', 'WRIST', 'TWIST', 'GROUND', 'FEET_CROSS', 'FOOT_SLIDE', 'SPINE', 'HINGE', 'ENVELOPE', 'END_POSE', 'LOCKED', 'POP')
PARITY = ('ENVELOPE', 'HINGE', 'PENETRATION')
PARITY_TOLERANCE = 3


def allowed(kind, detail):
    """Findings a moveset module declared legitimate for its style (REALISM['lint_allow'] = [(kind, detail-prefix), ...])."""
    return any(kind == k_ and str(detail).startswith(p_) for k_, p_ in P.REALISM.get('lint_allow', ()))


def verdicts(issues):
    issues = {k_: [(f_, d_) for f_, d_ in v_ if not allowed(k_, d_)] for k_, v_ in issues.items()}
    issues = {k_: v_ for k_, v_ in issues.items() if v_}
    frames = {k: len({f for f, _ in v}) for k, v in issues.items()}
    body = {f for f, d in issues.get('PENETRATION', []) if not d.startswith('blade')}
    strict = sorted(k for k in frames if k in STRICT or (k in HUMAN and P.REALISM.get('human_limits')))
    parity = sorted(k for k in PARITY if k in frames and (len(body) if k == 'PENETRATION' else frames[k]) > PARITY_TOLERANCE)
    return strict, parity


def main(argv):
    files, render, fps, neutral_file, loops = [], None, 60, None, set()
    it = iter(argv)
    for a in it:
        if a == '--render':
            render = next(it)
        elif a == '--fps':
            fps = int(next(it))
        elif a == '--config':
            import importlib
            sys.path.append(os.getcwd()); importlib.import_module(next(it))
        elif a == '--neutral':
            neutral_file = next(it)
        elif a == '--loop':
            loops.add(next(it))
        else:
            files.append(a)
    sc = bpy.context.scene; sc.render.fps = fps
    if render:
        E.build_body(); E.build_sword('-Z', length=1.0, color=(0.35, 1.0, 0.55, 1)); os.makedirs(render, exist_ok=True)
    neutral = None
    if neutral_file:
        a = E.arm(); rr = {b.name: E.rest_rel(b) for b in a.data.bones}
        neutral = {j['name']: (rr[j['name']].inverted() @ E.mat_from(j['transform'][0])).to_quaternion()
                   for j in json.load(open(neutral_file))['animation'] if j['name'] in rr}
    failed = 0
    for f in files:
        name = os.path.splitext(os.path.basename(f))[0]
        E.import_json(f, name)
        end = int(round(max(max(j['time']) for j in json.load(open(f))['animation']) * fps))
        print(f'== {name} ({end + 1} frames @ {fps} fps)')
        issues, _ = L.lint(0, end, neutral=neutral, skip_end=name in loops)
        strict, parity = verdicts(issues)
        print('  STRICT:', ('FAIL ' + ','.join(strict)) if strict else 'PASS')
        print('  PARITY:', ('FAIL ' + ','.join(parity)) if parity else 'PASS')
        failed += bool(strict)
        if render:
            E.render_frames(render, list(range(0, end + 1, 4)), views=('quarter', 'behind'), prefix=name, res=260)
    print(f'{len(files) - failed}/{len(files)} passed STRICT')
    if failed:
        raise SystemExit(1)


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
