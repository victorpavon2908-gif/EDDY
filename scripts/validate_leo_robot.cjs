// Run with the pinned Khronos glTF Validator module path (see Android CI).
const fs = require('node:fs');
const path = require('node:path');
const validator = require(process.argv[2] || 'gltf-validator');
const file = path.join(__dirname, '../app/src/main/assets/models/leo_robot.glb');
const bytes = fs.readFileSync(file);
const gltf = JSON.parse(bytes.subarray(20, 20 + bytes.readUInt32LE(12)).toString());
const required = ['Idle', 'Listen', 'Talk', 'Think', 'Wave', 'Jump', 'Dance', 'Spin'];
for (const name of required) {
    if (!gltf.animations.some(a => a.name === name)) throw new Error(`Missing robot animation: ${name}`);
}
// The two original finger skins use world-space joints beneath a parent. These four
// upstream notices are reviewed; changing the hierarchy would alter the authored rig.
const originalRigNotices = new Set([
    'NODE_SKINNED_MESH_NON_ROOT:/nodes/72',
    'NODE_SKINNED_MESH_LOCAL_TRANSFORMS:/nodes/72',
    'NODE_SKINNED_MESH_NON_ROOT:/nodes/73',
    'NODE_SKINNED_MESH_LOCAL_TRANSFORMS:/nodes/73',
]);
validator.validateBytes(new Uint8Array(bytes), { maxIssues: 10000 }).then(report => {
    const { messages, ...totals } = report.issues;
    const unexpected = messages.filter(m => m.severity <= 1 && !(
        m.severity === 1 && originalRigNotices.has(`${m.code}:${m.pointer}`)
    ));
    console.log(JSON.stringify({ ...totals, unexpected: unexpected.length }));
    if (report.issues.truncated || report.issues.numErrors || unexpected.length) {
        console.error(JSON.stringify(unexpected, null, 2));
        process.exitCode = 1;
    }
}).catch(error => { console.error(error); process.exitCode = 1; });
