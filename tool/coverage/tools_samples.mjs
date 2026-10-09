// Gate 1 inventory for the tools area (Settings > Tools).
//
//   node tool/coverage/tools_samples.mjs [out dir] [paseo protocol messages.js]
import { mkdirSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { finishFamily } from './oc_cases_lib.mjs';
import { toolsFamilies } from './families/tools_area.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const protocol = process.argv[3] ?? '/home/eslam/node_modules/@getpaseo/protocol/dist/messages.js';
const protocolMessages = await import(pathToFileURL(protocol).href);
const families = toolsFamilies({ oc1File: 'contracts/opencode-openapi-f12e14cf.json', protocolMessages });
const problems = [];
for (const [name, family] of Object.entries(families)) {
  finishFamily({ name, outDir, schema: family.schema, cases: family.cases, problems, source: family.source, writeFileSync, mkdirSync, excluded: family.excluded ?? {} });
}
// The conversation's active context is the server's message list read for a
// different page: it reuses the chat's message cases (every field of every
// message kind), and decides each field again for that page.
{
  const fs = await import('node:fs');
  const messages = JSON.parse(fs.readFileSync(`${outDir}/oc2_messages_samples.json`, 'utf8'));
  messages.source = `${messages.source} (the same message cases, read for the active-context page)`;
  fs.writeFileSync(`${outDir}/tools_context_samples.json`, JSON.stringify(messages, null, 2));
  console.log(`tools_context: ${messages.cases.length} cases (from oc2_messages)`);
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
