// Gate 1 inventory for the terminal area.
//
//   node tool/coverage/terminal_samples.mjs [out dir] [paseo protocol messages.js]
import { mkdirSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { finishFamily } from './oc_cases_lib.mjs';
import { terminalFamilies } from './families/terminal_area.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const protocol = process.argv[3] ?? '/home/eslam/node_modules/@getpaseo/protocol/dist/messages.js';
const protocolMessages = await import(pathToFileURL(protocol).href);
const families = terminalFamilies({ oc1File: 'contracts/opencode-openapi-f12e14cf.json', protocolMessages });
const problems = [];
for (const [name, family] of Object.entries(families)) {
  finishFamily({ name, outDir, schema: family.schema, cases: family.cases, problems, source: family.source, writeFileSync, mkdirSync, excluded: family.excluded ?? {} });
}
// The mutating calls of the area: each needs a decision in
// terminal_actions_ledger.json (a control, or why none).
{
  const fs = await import('node:fs');
  const oc1 = JSON.parse(fs.readFileSync('contracts/opencode-openapi-f12e14cf.json', 'utf8'));
  const wire = [];
  for (const [path, ops] of Object.entries(oc1.paths)) {
    if (!/^\/(api\/)?pty(\/|$)/.test(path)) continue;
    for (const method of Object.keys(ops)) if (method !== 'get') wire.push(`oc1 ${method.toUpperCase()} ${path}`);
  }
  for (const value of Object.values(protocolMessages)) {
    const type = value?._zod?.def?.shape?.type?._zod?.def?.values?.[0];
    if (typeof type === 'string' && /terminal/i.test(type) && /request$/.test(type) && !/(list|get|capture|subscribe|unsubscribe)/i.test(type)) wire.push(`paseo ${type}`);
  }
  wire.sort();
  fs.writeFileSync(`${outDir}/terminal_actions_samples.json`, JSON.stringify({ source: 'contracts/opencode-openapi-f12e14cf.json, @getpaseo/protocol messages.js', wire: [...new Set(wire)] }, null, 2));
  console.log(`terminal_actions: ${new Set(wire).size} mutating calls`);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
