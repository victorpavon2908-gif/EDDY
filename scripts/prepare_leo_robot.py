#!/usr/bin/env python3
"""Build LEO's offline rig from the CC0 RobotExpressive asset, with rounded surfaces.
Usage: python scripts/prepare_leo_robot.py /path/to/RobotExpressive.glb
Upstream SHA-256 is pinned; artist rig, original actions are retained; finger weights follow the subdivision stencil.
"""
import copy
import hashlib
import json
import math
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA256 = '047f5e5fb3bb6d378bd1df16ca6137f2a596c99b3a1b5690b4020c05aaf6f319'


def build(source, destination):
    data = Path(source).read_bytes()
    if hashlib.sha256(data).hexdigest() != SOURCE_SHA256:
        raise ValueError('Unexpected upstream asset: review provenance before modifying the pin')
    length = struct.unpack_from('<I', data, 12)[0]
    gltf = json.loads(data[20:20 + length])
    binary = bytearray(data[28 + length:])

    def accessor(values, kind, component=5126):
        flat = [v for row in values for v in row] if isinstance(values[0], (list, tuple)) else values
        while len(binary) % 4:
            binary.append(0)
        offset = len(binary)
        binary.extend(struct.pack('<' + {5126: 'f', 5125: 'I', 5123: 'H'}[component] * len(flat), *flat))
        view = len(gltf['bufferViews'])
        gltf['bufferViews'].append({'buffer': 0, 'byteOffset': offset, 'byteLength': len(flat) * (2 if component == 5123 else 4)})
        count = len(values)
        a = {'bufferView': view, 'componentType': component, 'count': count, 'type': kind}
        if kind == 'VEC3':
            a.update(min=[min(v[j] for v in values) for j in range(3)], max=[max(v[j] for v in values) for j in range(3)])
        if kind == 'SCALAR':
            a.update(min=[min(flat)], max=[max(flat)])
        gltf['accessors'].append(a)
        return len(gltf['accessors']) - 1

    def read(index):
        a = gltf['accessors'][index]
        v = gltf['bufferViews'][a['bufferView']]
        width = {'SCALAR': 1, 'VEC3': 3, 'VEC4': 4}[a['type']]
        fmt, byte_width = {5126: ('f',4), 5125: ('I',4), 5123: ('H',2), 5121: ('B',1)}[a['componentType']]
        offset = v.get('byteOffset', 0) + a.get('byteOffset', 0)
        return [struct.unpack_from('<' + fmt * width, binary, offset + i * v.get('byteStride', width * byte_width)) for i in range(a['count'])]

    def duration(clip):
        return max(read(s['input'])[-1][0] for s in clip['samplers'])

    def quat(x=0, y=0, z=0):
        x, y, z = (math.radians(a) / 2 for a in (x, y, z))
        cx, cy, cz, sx, sy, sz = math.cos(x), math.cos(y), math.cos(z), math.sin(x), math.sin(y), math.sin(z)
        return [sx*cy*cz+cx*sy*sz, cx*sy*cz-sx*cy*sz, cx*cy*sz+sx*sy*cz, cx*cy*cz-sx*sy*sz]

    def multiply(a, b):
        ax, ay, az, aw = a; bx, by, bz, bw = b
        q = [aw*bx+ax*bw+ay*bz-az*by, aw*by-ax*bz+ay*bw+az*bx,
             aw*bz+ax*by-ay*bx+az*bw, aw*bw-ax*bx-ay*by-az*bz]
        norm = math.sqrt(sum(v*v for v in q))
        return [v/norm for v in q]

    def track(clip, node, path, times, values):
        clip['channels'] = [c for c in clip['channels'] if c['target'] != {'node': node, 'path': path}]
        sampler = len(clip['samplers'])
        clip['samplers'].append({'input': accessor(times, 'SCALAR'), 'output': accessor(values, 'VEC4' if path == 'rotation' else 'VEC3' if path in ('translation', 'scale') else 'SCALAR'), 'interpolation': 'LINEAR'})
        clip['channels'].append({'sampler': sampler, 'target': {'node': node, 'path': path}})

    # Pearl ceramic, graphite mechanics and a restrained luminous face.
    palette = {
        'Main': ([0.78, 0.88, 0.9, 1], 0.24, 0.3),
        'Grey': ([0.055, 0.1, 0.14, 1], 0.65, 0.32),
        'Black': ([0.02, 0.72, 0.63, 1], 0.08, 0.28),
    }
    for material in gltf['materials']:
        color, metal, rough = palette[material['name']]
        material['pbrMetallicRoughness'] = {'baseColorFactor': color, 'metallicFactor': metal, 'roughnessFactor': rough}
        material.pop('extras', None)
        if material['name'] == 'Black':
            material['emissiveFactor'] = [0.015, 0.38, 0.28]

    # Round the artist's low-poly surfaces with two Loop subdivision passes. Interpolate
    # morph positions with the same stencil and rebuild normals, including morph normals.
    # Rigid parts keep their node rig; finger weights follow the same smooth stencil.
    def smooth_primitive(p, rounds):
        attrs = p['attributes']
        source_positions = read(attrs['POSITION'])
        morphs = [read(t['POSITION']) for t in p.get('targets', [])]
        joints = read(attrs['JOINTS_0']) if 'JOINTS_0' in attrs else None
        source_weights = read(attrs['WEIGHTS_0']) if joints else None
        bones = sorted({int(j) for row in joints for j in row}) if joints else []
        skin = []
        unique, positions, deltas, remap = {}, [], [[] for _ in morphs], []
        for i, pos in enumerate(source_positions):
            key = tuple(round(x, 5) for x in pos) + tuple(round(x, 5) for m in morphs for x in m[i])
            if joints: key += tuple(joints[i]) + tuple(source_weights[i])
            if key not in unique:
                unique[key] = len(positions)
                positions.append(list(pos))
                if joints:
                    influence = {int(j): w for j,w in zip(joints[i],source_weights[i]) if w > 0}
                    skin.append([influence.get(b,0) for b in bones])
                for j, m in enumerate(morphs): deltas[j].append(list(m[i]))
            remap.append(unique[key])
        indices = [remap[int(row[0])] for row in read(p['indices'])]
        faces = [indices[i:i+3] for i in range(0, len(indices), 3)]
        for _ in range(rounds):
            edges, neighbors = {}, [set() for _ in positions]
            for a,b,c in faces:
                for u,v,opposite in [(a,b,c),(b,c,a),(c,a,b)]:
                    edges.setdefault(tuple(sorted((u,v))), []).append(opposite)
                    neighbors[u].add(v); neighbors[v].add(u)
            boundary = [set() for _ in positions]
            for (a,b), opposite in edges.items():
                if len(opposite) != 2: boundary[a].add(b); boundary[b].add(a)
            stencils = []
            for i, ns in enumerate(neighbors):
                if boundary[i]:
                    ns = boundary[i]; beta = .25 / len(ns); base = .75
                else:
                    n = len(ns); beta = (5/8 - (3/8 + .25 * math.cos(math.tau/n))**2) / n if n else 0; base = 1-len(ns)*beta
                stencils.append([(i, base)] + [(j,beta) for j in sorted(ns)])
            edge_ids = {}
            for (a,b), opposite in sorted(edges.items()):
                edge_ids[a,b] = len(stencils)
                stencils.append([(a,.375),(b,.375)]+[(v,.125) for v in opposite] if len(opposite)==2 else [(a,.5),(b,.5)])
            def interpolate(values):
                return [[sum(values[i][axis]*w for i,w in stencil) for axis in range(len(values[0]))] for stencil in stencils]
            positions = interpolate(positions)
            deltas = [interpolate(d) for d in deltas]
            if joints: skin = interpolate(skin)
            new_faces = []
            for a,b,c in faces:
                ab,bc,ca = [edge_ids[tuple(sorted(e))] for e in [(a,b),(b,c),(c,a)]]
                new_faces.extend([[a,ab,ca],[ab,b,bc],[ca,bc,c],[ab,bc,ca]])
            faces = new_faces
        def normals(points):
            result = [[0.,0.,0.] for _ in points]
            fallback = [[0.,1.,0.] for _ in points]
            for a,b,c in faces:
                u = [points[b][j]-points[a][j] for j in range(3)]
                v = [points[c][j]-points[a][j] for j in range(3)]
                cross = [u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
                for i in (a,b,c):
                    if sum(x*x for x in cross) > 1e-20: fallback[i] = cross
                    for j in range(3): result[i][j] += cross[j]
            result = [n if sum(x*x for x in n) > 1e-20 else f for n,f in zip(result,fallback)]
            return [[x / math.sqrt(sum(t*t for t in n)) for x in n] for n in result]
        if joints:
            packed = [sorted(zip(bones,w),key=lambda x: x[1],reverse=True)[:4] for w in skin]
            for row in packed:
                while len(row)<4: row.append((0,0))
            attrs['JOINTS_0'] = accessor([[j if w > 0 else 0 for j,w in row] for row in packed],'VEC4',5123)
            attrs['WEIGHTS_0'] = accessor([[w/max(sum(v for _,v in row),1e-12) for j,w in row] for row in packed],'VEC4')
        base_normals = normals(positions)
        attrs['POSITION'] = accessor(positions, 'VEC3')
        gltf['accessors'][attrs['POSITION']].update(min=[min(v[j] for v in positions) for j in range(3)],max=[max(v[j] for v in positions) for j in range(3)])
        attrs['NORMAL'] = accessor(base_normals, 'VEC3')
        p['indices'] = accessor([i for face in faces for i in face], 'SCALAR', 5125)
        for target, delta in zip(p.get('targets', []), deltas):
            target['POSITION'] = accessor(delta, 'VEC3')
            changed = normals([[p[j]+d[j] for j in range(3)] for p,d in zip(positions,delta)])
            target['NORMAL'] = accessor([[n[j]-b[j] for j in range(3)] for n,b in zip(changed,base_normals)],'VEC3')
    for mesh in gltf['meshes']:
        for primitive in mesh['primitives']:
            smooth_primitive(primitive, 2)
            for index in list(primitive['attributes'].values()) + [i for t in primitive.get('targets',[]) for i in t.values()]:
                gltf['bufferViews'][gltf['accessors'][index]['bufferView']]['target'] = 34962
            gltf['bufferViews'][gltf['accessors'][primitive['indices']]['bufferView']]['target'] = 34963

    idle = next(a for a in gltf['animations'] if a['name'] == 'Idle')
    for name in ['Listen', 'Talk', 'Think', 'Spin']:
        clip = copy.deepcopy(idle)
        clip['name'] = name
        length = duration(clip)
        times = [length * i / 32 for i in range(33)]
        rotations = []
        weights = []
        for t in times:
            phase = t / length * math.tau
            if name == 'Listen':
                angles = (-5 + 2*math.sin(phase), 5*math.sin(phase), -9)
            elif name == 'Think':
                angles = (-9, 10*math.sin(phase), 5)
            elif name == 'Talk':
                angles = (3*math.sin(phase*2), 5*math.sin(phase), 2*math.sin(phase))
            else:
                angles = (0, 0, 0)
            rotations.append(multiply(gltf['nodes'][12]['rotation'], quat(*angles)))
            mouth = 0.14 + 0.25 * abs(math.sin(phase*5)) if name == 'Talk' else 0.045
            weights.extend([0, mouth, 0])
        track(clip, 12, 'rotation', times, rotations)
        track(clip, 13, 'weights', times, weights)
        if name == 'Spin':
            track(clip, 0, 'rotation', times, [quat(y=360*(3*(t/length)**2-2*(t/length)**3)) for t in times])
        if name == 'Talk':
            for node, sign in [(17, 1), (37, -1)]:
                track(clip, node, 'rotation', times, [multiply(gltf['nodes'][node]['rotation'], quat(x=6*math.sin(t/length*math.tau), z=sign*(5+5*math.sin(t/length*math.tau)))) for t in times])
        gltf['animations'].append(clip)

    # Explicit rest tracks prevent a raised arm or a rotated root leaking into the next action.
    targets = {(c['target']['node'], c['target']['path']) for a in gltf['animations'] for c in a['channels']}
    for clip in gltf['animations']:
        present = {(c['target']['node'], c['target']['path']) for c in clip['channels']}
        for node, path in sorted(targets - present):
            default = {'rotation': [0,0,0,1], 'translation': [0,0,0], 'scale': [1,1,1], 'weights': [0,0,0]}[path]
            value = gltf['nodes'][node].get(path, default)
            values = value * 2 if path == 'weights' else [value, value]
            track(clip, node, path, [0, duration(clip)], values)
    gltf['asset']['generator'] = 'LEO robot preparation; original FBX2glTF'
    gltf['asset']['copyright'] = 'RobotExpressive: Tomas Laulhe / Quaternius and Don McCurdy, CC0 1.0. LEO palette and added motion tracks.'
    gltf['buffers'][0]['byteLength'] = len(binary)
    payload = json.dumps(gltf, separators=(',', ':'), ensure_ascii=True).encode()
    payload += b' ' * (-len(payload) % 4)
    binary += b'\0' * (-len(binary) % 4)
    result = struct.pack('<III', 0x46546c67, 2, 28+len(payload)+len(binary)) + struct.pack('<I4s',len(payload),b'JSON') + payload + struct.pack('<I4s',len(binary),b'BIN\0') + binary
    Path(destination).parent.mkdir(parents=True, exist_ok=True)
    Path(destination).write_bytes(result)
    print(f'{destination}: {len(result)} bytes, {len(gltf["animations"])} animations')

if __name__ == '__main__':
    build(sys.argv[1], ROOT/'app/src/main/assets/models/leo_robot.glb')
