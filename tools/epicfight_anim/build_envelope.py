"""Build ef_envelope.json (per-bone vanilla rotation envelope, hinge axes, idle pose) from Epic Fight.

    blender -b RIG.blend --python build_envelope.py -- <epicfight jar or extracted .../animations/biped dir>

Re-run when the Epic Fight version changes. poser.py / lint.py / check.py read ef_envelope.json next to them.
"""
import sys, os, zipfile, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import envelope as V

src = sys.argv[sys.argv.index('--') + 1]
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ef_envelope.json')
if src.endswith('.jar'):
    tmp = tempfile.mkdtemp(); prefix = 'assets/epicfight/animmodels/animations/biped/'
    with zipfile.ZipFile(src) as z:
        for n in z.namelist():
            if n.startswith(prefix) and n.endswith('.json') and '/data/' not in n and '/pov/' not in n:
                z.extract(n, tmp)
    src = os.path.join(tmp, prefix)
env = V.build(src, out)
print('envelope:', len(env), 'bones ->', out)
