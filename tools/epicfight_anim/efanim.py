"""Epic Fight animation tools for the EF biped rig: JSON import/export, preview body, contact-sheet renders.

EF animation JSON: {"animation": [{"name": joint, "time": [s...], "transform": [[16 floats row-major]...]}]}
Each transform is the joint's pose matrix relative to its parent joint (Root: armature space).
"""
import bpy, json, math
from mathutils import Matrix, Vector, Euler

ARM = 'Armature'


def arm():
    return bpy.data.objects[ARM]


def rest_rel(bone):
    return bone.matrix_local if bone.parent is None else bone.parent.matrix_local.inverted() @ bone.matrix_local


def mat_from(flat):
    return Matrix([flat[0:4], flat[4:8], flat[8:12], flat[12:16]])


def import_json(path, name):
    a = arm()
    data = json.load(open(path))['animation']
    act = bpy.data.actions.new(name)
    a.animation_data_create(); a.animation_data.action = act
    fps = bpy.context.scene.render.fps
    for j in data:
        pb = a.pose.bones.get(j['name'])
        if pb is None:
            continue
        pb.rotation_mode = 'QUATERNION'
        rr = rest_rel(pb.bone).inverted()
        for t, flat in zip(j['time'], j['transform']):
            basis = rr @ mat_from(flat)
            loc, rot, _ = basis.decompose()
            pb.location = loc; pb.rotation_quaternion = rot
            f = t * fps
            pb.keyframe_insert('location', frame=f); pb.keyframe_insert('rotation_quaternion', frame=f)
    return act


def export_json(path, start, end, fps=None, step=1):
    """Sample the current action every `step` frames from start..end and write EF JSON."""
    a = arm(); sc = bpy.context.scene
    fps = fps or sc.render.fps
    frames = list(range(start, end + 1, step))
    if frames[-1] != end:
        frames.append(end)
    out = []
    for pb in a.pose.bones:
        out.append({'name': pb.name, 'time': [], 'transform': []})
    for f in frames:
        sc.frame_set(f)
        for entry, pb in zip(out, a.pose.bones):
            m = rest_rel(pb.bone) @ pb.matrix_basis
            entry['time'].append(round((f - start) / fps, 4))
            entry['transform'].append([round(v, 6) for row in m for v in row])
    with open(path, 'w') as fh:
        json.dump({'animation': out}, fh, separators=(',', ':'))


def _box(name, bone, size, center_along, color, offset=(0, 0, 0)):
    """Box parented to `bone`; `center_along` is a 0..1 fraction of bone length for the box centre."""
    a = arm(); b = a.data.bones[bone]
    bpy.ops.mesh.primitive_cube_add(size=1)
    o = bpy.context.active_object; o.name = name
    o.scale = size
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.diffuse_color = color; o.data.materials.append(mat)
    o.parent = a; o.parent_type = 'BONE'; o.parent_bone = bone
    # bone-parented children sit at the bone tail in bone space (Y along bone)
    o.location = Vector((offset[0], -b.length + b.length * center_along + offset[1], offset[2]))
    return o


def build_body():
    skin = (0.85, 0.65, 0.5, 1); shirt = (0.2, 0.55, 0.65, 1); pants = (0.25, 0.25, 0.6, 1)
    _box('head', 'Head', (0.5, 0.5, 0.5), 0.55, skin)
    _box('nose', 'Head', (0.1, 0.1, 0.1), 0.45, (0.9, 0.2, 0.2, 1), offset=(0, 0, -0.28))  # marks face (+Y world at rest)
    _box('chest', 'Chest', (0.5, 0.4, 0.25), 0.5, shirt)
    _box('belly', 'Torso', (0.5, 0.3, 0.25), 0.5, shirt)
    for s in 'RL':
        _box('uarm' + s, 'Arm_' + s, (0.25, 0.4, 0.25), 0.5, shirt)
        _box('farm' + s, 'Hand_' + s, (0.25, 0.3, 0.25), 0.5, skin)
        _box('thigh' + s, 'Thigh_' + s, (0.25, 0.375, 0.25), 0.5, pants)
        _box('shin' + s, 'Leg_' + s, (0.25, 0.375, 0.25), 0.5, pants)


def build_sword(axis='Y', length=1.4, bone='Tool_R', color=(1.0, 0.3, 0.9, 1)):
    """Blade box along the tool joint's local axis, starting at the joint head."""
    a = arm(); b = a.data.bones[bone]
    bpy.ops.mesh.primitive_cube_add(size=1)
    o = bpy.context.active_object; o.name = 'blade_' + axis
    sizes = {'Y': (0.06, length, 0.16), '-Y': (0.06, length, 0.16), 'Z': (0.06, 0.16, length), '-Z': (0.06, 0.16, length),
             'X': (length, 0.06, 0.16), '-X': (length, 0.06, 0.16)}[axis]
    o.scale = sizes
    sign = -1 if axis.startswith('-') else 1
    ax = axis[-1]
    off = Vector((0, -b.length, 0))  # joint head
    off[{'X': 0, 'Y': 1, 'Z': 2}[ax]] += sign * length / 2
    mat = bpy.data.materials.new('blade' + axis); mat.diffuse_color = color; o.data.materials.append(mat)
    o.parent = a; o.parent_type = 'BONE'; o.parent_bone = bone
    o.location = off
    return o


def build_gun(bone='Tool_R', color=(1.0, 0.45, 0.05, 1)):
    """Exo Disintegrator stand-in in Tool-local coords (barrel +Y, top -Z): stock, receiver, barrel, grip, loop."""
    a = arm(); b = a.data.bones[bone]
    parts = [  # (center, size) in Tool-local blocks
        ((0, -0.25, -0.29), (0.1, 0.22, 0.16)),   # stock
        ((0, 0.03, -0.3), (0.13, 0.34, 0.2)),     # receiver
        ((0, 0.33, -0.29), (0.1, 0.28, 0.12)),    # shroud + barrel
        ((0, 0.47, -0.29), (0.06, 0.07, 0.06)),   # muzzle
        ((0, -0.05, -0.12), (0.07, 0.1, 0.2)),    # grip
        ((0, 0.34, -0.52), (0.05, 0.16, 0.16)),   # loop
    ]
    for i, (c, sz) in enumerate(parts):
        bpy.ops.mesh.primitive_cube_add(size=1)
        o = bpy.context.active_object; o.name = f'gun{i}'; o.scale = sz
        mat = bpy.data.materials.get('gun') or bpy.data.materials.new('gun'); mat.diffuse_color = color
        o.data.materials.append(mat)
        o.parent = a; o.parent_type = 'BONE'; o.parent_bone = bone
        o.location = Vector(c) + Vector((0, -b.length, 0))


def setup_render(res=360):
    sc = bpy.context.scene
    sc.render.engine = 'BLENDER_WORKBENCH'
    sc.display.shading.color_type = 'MATERIAL'
    sc.display.shading.light = 'STUDIO'
    sc.render.resolution_x = res; sc.render.resolution_y = res
    for o in list(bpy.data.objects):
        if o.name in ('Cube.001',):
            o.hide_render = True
    cam = bpy.data.objects.get('cam')
    if cam is None:
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.type = 'ORTHO'; cam.data.ortho_scale = 4.2
    return cam


VIEWS = {  # camera position; subject faces +Y
    'front': (0, 8, 1.1), 'side': (8, 0, 1.1), 'quarter': (5.5, 5.5, 3.0), 'top': (0, 0.01, 9), 'back3q': (-5, -5, 2.5),
    'behind': (1.8, -5.5, 3.2),  # gameplay third-person camera
}


def render_frames(outdir, frames, views=('quarter', 'side'), prefix='f', res=360, follow_root=True):
    sc = bpy.context.scene; cam = setup_render(res)
    paths = []
    for f in frames:
        sc.frame_set(f)
        center = Vector((0, 0, 1.0))
        if follow_root:
            r = arm().pose.bones['Root'].matrix.translation
            center = Vector((r.x, r.y, 1.0))
        for v in views:
            cam.location = center + Vector(VIEWS[v]) - Vector((0, 0, 1.0)) + Vector((0, 0, 1.0 if v != 'top' else 0))
            cam.rotation_euler = (center - cam.location).to_track_quat('-Z', 'Y').to_euler()
            p = f'{outdir}/{prefix}_{v}_{f:03d}.png'
            sc.render.filepath = p
            bpy.ops.render.render(write_still=True)
            paths.append(p)
    return paths
