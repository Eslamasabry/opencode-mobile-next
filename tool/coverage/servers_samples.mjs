// Gate 1 inventory for the servers area: what a server answers when the app
// checks it, the saved server record, every ServerCapabilities switch, and the
// connection / monitor state the status line and rows show.
//
//   node tool/coverage/servers_samples.mjs [out dir] [paseo protocol messages.js]
//
// The build FAILS when a schema field is in no case, or a case uses a field
// the schema does not have.
import { mkdirSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { finishFamily } from './oc_cases_lib.mjs';
import { serversFamilies } from './families/servers.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const protocol = process.argv[3] ?? '/home/eslam/node_modules/@getpaseo/protocol/dist/messages.js';
const protocolMessages = await import(pathToFileURL(protocol).href);
const families = serversFamilies({
  oc1File: 'contracts/opencode-openapi-f12e14cf.json',
  oc2File: 'contracts/opencode2-openapi-beta-18600.json',
  protocolMessages,
});
const problems = [];
for (const [name, family] of Object.entries(families)) {
  if (family.custom) {
    mkdirSync(outDir, { recursive: true });
    const schema = Object.fromEntries(Object.entries(family.schema).map(([g, f]) => [g, Object.entries(f).map(([path, v]) => ({ path, kind: v.kind }))]));
    writeFileSync(`${outDir}/${name}_samples.json`, JSON.stringify({ source: family.source, schema, excluded: {}, cases: [] }, null, 2));
    console.log(`${name}: schema only, ${Object.values(schema).flat().length} schema fields`);
    continue;
  }
  finishFamily({ name, outDir, schema: family.schema, cases: family.cases, problems, source: family.source, writeFileSync, mkdirSync, excluded: family.excluded ?? {} });
}

// The mutating calls of the area, from the contract and the protocol: each
// needs a decision in servers_actions_ledger.json (a control, or why not).
{
  const fs = await import('node:fs');
  const oc1 = JSON.parse(fs.readFileSync('contracts/opencode-openapi-f12e14cf.json', 'utf8'));
  const wire = [];
  for (const [path, ops] of Object.entries(oc1.paths)) {
    if (!/^\/(global|instance|config|log|auth)(\/|$)/.test(path)) continue;
    for (const method of Object.keys(ops)) if (method !== 'get') wire.push(`oc1 ${method.toUpperCase()} ${path}`);
  }
  const requestTypes = [];
  for (const value of Object.values(protocolMessages)) {
    const shape = value?._zod?.def?.shape;
    const type = shape?.type?._zod?.def?.values?.[0];
    if (typeof type === 'string' && /^(daemon\.(update|config\.reload)\.request|set_daemon_config_request|restart_server_request|shutdown_server_request|hub\.management\.daemon\.(connect|disconnect|permissions\.update)\.request|refresh_providers_snapshot_request)$/.test(type)) requestTypes.push(type);
  }
  for (const type of requestTypes) wire.push(`paseo ${type}`);
  wire.sort();
  mkdirSync(outDir, { recursive: true });
  writeFileSync(`${outDir}/servers_actions_samples.json`, JSON.stringify({ source: 'contracts/opencode-openapi-f12e14cf.json, @getpaseo/protocol messages.js', wire }, null, 2));
  console.log(`servers_actions: ${wire.length} mutating calls`);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
