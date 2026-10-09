// Gate 2 inventory for the chats, projects and sessions lists: every call in
// this area that CHANGES something, from the OpenCode 1 and 2 contracts and
// Paseo's protocol. Writes test/fixtures/coverage/lists_actions_samples.json;
// the action test checks each one has an entry in lists_actions_ledger.json.
//
//   node tool/coverage/lists_actions.mjs <paseo protocol dist/messages.js> [out dir]
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
  !p.startsWith('/api/') &&
  /^\/(session|project|experimental\/(session|project|control-plane|workspace|worktree)|sync\/(steal|start)|tui\/(open-sessions|select-session))/.test(p));
const oc2 = operations(OC2, 'OC2', (p) => /^\/api\/(session|project|worktree|workspace)/.test(p));

const messages = await import(pathToFileURL(protocolPath).href);
const paseoScope = /^(archive_agent|delete_agent|update_agent|close_items|create_agent|agent\.create|resume_agent|import_agent|agent\.fork_context|project\.(add|remove|rename|icon\.set|create_directory|github\.clone)|open_project|archive_workspace|workspace\.(create|title|pin|label|mark_unread|clear_attention|recovery\.restore)|paseo_worktree_archive|create_paseo_worktree)/;
const paseo = [];
for (const [name, schema] of Object.entries(messages)) {
  if (!/RequestMessageSchema|RequestSchema/.test(name)) continue;
  let type;
  try { type = schema._zod.def.shape.type._zod.def.values[0]; } catch { continue; }
  if (paseoScope.test(type) && !/(\.list|\.inspect)\.request$/.test(type)) paseo.push({ key: `Paseo ${type}` });
}
paseo.sort((a, b) => a.key.localeCompare(b.key));

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(`${outDir}/lists_actions_samples.json`, JSON.stringify({ sources: [OC1, OC2, protocolPath], operations: [...oc1, ...oc2, ...paseo] }, null, 2));
console.log(`lists actions: ${oc1.length} OpenCode 1, ${oc2.length} OpenCode 2, ${paseo.length} Paseo`);
