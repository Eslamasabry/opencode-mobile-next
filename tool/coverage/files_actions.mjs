// Gate 2 inventory for files, changes, review, worktrees and undo: every call in
// this area that CHANGES something, from the OpenCode 1 and 2 contracts and
// Paseo's protocol. Writes test/fixtures/coverage/files_actions_samples.json;
// the action test checks each one has an entry in files_actions_ledger.json.
//
//   node tool/coverage/files_actions.mjs <paseo protocol dist/messages.js> [out dir]
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
  /^\/(vcs\/apply|experimental\/(worktree|project\/|workspace$|workspace\/(sync-list|\{id\}))|session\/\{sessionID\}\/(revert|unrevert)|api\/session\/\{sessionID\}\/revert\/)/.test(p));
const oc2 = operations(OC2, 'OC2', (p) => /^\/api\/(worktree|workspace|session\/\{sessionID\}\/revert\/)/.test(p));

const messages = await import(pathToFileURL(protocolPath).href);
const paseoScope = /^(checkout\.(discard_changes|rename_branch|forge\.set_auto_merge|github\.set_auto_merge)\.request|checkout_(commit|merge_from_base|merge|pr_create|pr_merge|pull|push|switch_branch)_request|create_paseo_worktree_request|paseo_worktree_archive_request|stash_(pop|save)_request|fs\.entry\.(create|delete|duplicate|rename)\.request|fs\.file\.write\.request|file\.upload\.request)$/;
const paseo = [];
for (const [name, schema] of Object.entries(messages)) {
  if (!/RequestMessageSchema|RequestSchema/.test(name)) continue;
  let type;
  try { type = schema._zod.def.shape.type._zod.def.values[0]; } catch { continue; }
  if (paseoScope.test(type)) paseo.push({ key: `Paseo ${type}` });
}
paseo.sort((a, b) => a.key.localeCompare(b.key));
const codex = [];

fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(`${outDir}/files_actions_samples.json`, JSON.stringify({ sources: [OC1, OC2, protocolPath], operations: [...oc1, ...oc2, ...paseo, ...codex] }, null, 2));
console.log(`files actions: ${oc1.length} OpenCode 1, ${oc2.length} OpenCode 2, ${paseo.length} Paseo`);
