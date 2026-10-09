// Gate 2 inventory for the AI Team: every call that CHANGES something, from the
// Gas City supervisor contract the app's client uses, plus the two merge calls
// the host front adds (outside the contract). Writes
// test/fixtures/coverage/team_actions_samples.json; the action test checks each
// one has an entry in team_actions_ledger.json.
//
//   node tool/coverage/team_actions.mjs [out dir]
import fs from 'node:fs';

const [, , outDir = 'test/fixtures/coverage'] = process.argv;
const GC = 'contracts/gascity-supervisor-openapi-v0-3648ca2d499a.json';
const mutating = new Set(['post', 'put', 'patch', 'delete']);
const doc = JSON.parse(fs.readFileSync(GC, 'utf8'));
const ops = [];
for (const [path, item] of Object.entries(doc.paths)) {
  for (const [method, op] of Object.entries(item)) {
    if (mutating.has(method)) ops.push({ key: `GC ${method.toUpperCase()} ${path}`, operationId: op.operationId });
  }
}
ops.push({ key: 'Front POST /v0/city/{cityName}/front/mr/{bead}/approve', operationId: 'front-approve-merge' });
ops.push({ key: 'Front POST /v0/city/{cityName}/front/merge/{run}', operationId: 'front-merge' });
// The team engine's own commands (POST /v1/commands, one `action` each): the
// enum the app sends them from.
const enumSource = fs.readFileSync('lib/domain/team_project_gateway.dart', 'utf8');
const body = enumSource.match(/enum TeamProjectAction \{([\s\S]*?)\n\}/)[1];
for (const name of body.split(',').map((x) => x.trim()).filter(Boolean)) ops.push({ key: `Engine ${name}`, operationId: name });
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(`${outDir}/team_actions_samples.json`, JSON.stringify({ sources: [GC], operations: ops }, null, 2));
console.log(`team actions: ${ops.filter((o) => o.key.startsWith('GC')).length} supervisor calls, 2 front calls, ${ops.filter((o) => o.key.startsWith('Engine')).length} engine commands`);
