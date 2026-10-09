// Gate 2 inventory for models, providers, usage and quota: every call in
// this area that CHANGES something, from the OpenCode 1 and 2 contracts and
// Paseo's protocol. Writes test/fixtures/coverage/models_actions_samples.json;
// the action test checks each one has an entry in models_actions_ledger.json.
//
//   node tool/coverage/models_actions.mjs <paseo protocol dist/messages.js> [out dir]
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

const [, , protocolPath, outDir = 'test/fixtures/coverage'] = process.argv;
const OC1 = 'contracts/opencode-openapi-f12e14cf.json';
const OC2 = 'contracts/opencode2-openapi-beta-18600.json';
const mutating = new Set(['post', 'put', 'patch', 'delete']);

function operations(file, label, keep) {
  const doc = JSON.parse(fs.readFileSync(file, 'utf8'));
  const out = [];
  for (const [path, item] of Object.entries(doc.paths)) {
    for (const [method, op] of Object.entries(item)) {
      if (mutating.has(method) && keep(path)) out.push({ key: `${label} ${method.toUpperCase()} ${path}`, operationId: op.operationId });
    }
  }
  return out;
}

const oc1 = operations(OC1, 'OC1', (p) =>
  /^\/(auth\/|config$|global\/config$|provider\/)/.test(p) ||
  /^\/api\/(integration|credential|session\/\{sessionID\}\/(agent|model))/.test(p));
const oc2 = operations(OC2, 'OC2', (p) =>
  /^\/api\/(experimental\/integration|integration|credential|session\/\{sessionID\}\/(agent|model))/.test(p));

const messages = await import(pathToFileURL(protocolPath).href);
const paseoScope = /^(set_agent_(model|mode|thinking|feature)_request|refresh_providers_snapshot_request|agent\.config\.apply\.request)$/;
const paseo = [];
for (const [name, schema] of Object.entries(messages)) {
  if (!/RequestMessageSchema|RequestSchema/.test(name)) continue;
  let type;
  try { type = schema._zod.def.shape.type._zod.def.values[0]; } catch { continue; }
  if (paseoScope.test(type)) paseo.push({ key: `Paseo ${type}` });
}
paseo.sort((a, b) => a.key.localeCompare(b.key));
// The Codex app-server's account calls have no contract here; they are the
// two the Account page makes (lib/codex/account.dart).
const codex = [{ key: 'Codex account/login/start' }, { key: 'Codex account/login/cancel' }];

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(`${outDir}/models_actions_samples.json`, JSON.stringify({ sources: [OC1, OC2, protocolPath], operations: [...oc1, ...oc2, ...paseo, ...codex] }, null, 2));
console.log(`models actions: ${oc1.length} OpenCode 1, ${oc2.length} OpenCode 2, ${paseo.length} Paseo`);
