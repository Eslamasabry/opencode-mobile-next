// Gate 1 inventory for everything Paseo sends a chat. Reads Paseo's own
// protocol schema, lists every field of every family, and writes one JSON per
// family that the coverage tests run through the app's mapper and widgets.
//
//   node tool/coverage/paseo_samples.mjs <paseo protocol dist/messages.js> <out dir>
//
// Families (tool/coverage/families/*.mjs): tools (tool steps), items (the
// other timeline items), permissions (permission requests, questions, plans).
// The build FAILS when a schema field is in no case, or a case uses a field
// or enum value the schema does not have.
import { pathToFileURL } from 'node:url';
import { mkdirSync, writeFileSync } from 'node:fs';
import { buildFamily } from './paseo_cases_lib.mjs';
import { toolSchema, toolCases } from './families/tools.mjs';
import { itemSchema, itemCases } from './families/items.mjs';
import { providerSchema, providerCases } from './families/providers.mjs';
import { listSchema, listCases, listExcluded } from './families/lists.mjs';
import { permissionSchema, permissionCases, permissionExcluded } from './families/permissions.mjs';

const [, , protocolPath, outDir] = process.argv;
const messages = await import(pathToFileURL(protocolPath).href);
const root = messages.AgentTimelineItemPayloadSchema;

const problems = [];
const run = (name, schema, cases, excluded = {}) => {
  // Fields a case cannot carry (with the reason) count as covered.
  const cover = Object.entries(excluded).map(([key, reason]) => ({ key, reason }));
  const cut = {};
  for (const [group, spec] of Object.entries(schema)) {
    cut[group] = Object.fromEntries(Object.entries(spec).filter(([path]) => !(`${group}.${path}` in excluded)));
  }
  const family = buildFamily({ schema: cut, cases, problems });
  for (const { key } of cover) {
    const [group, ...rest] = key.split('.');
    // group names may contain a dot ("detail.shell"): match the longest.
    const g = Object.keys(schema).filter((n) => key.startsWith(`${n}.`)).sort((a, b) => b.length - a.length)[0];
    if (!g) problems.push(`${name}: excluded "${key}" is not a schema field`);
    else if (!schema[g][key.slice(g.length + 1)]) problems.push(`${name}: excluded "${key}" is not a schema field`);
  }
  const full = Object.fromEntries(Object.entries(schema).map(([g, f]) => [g, Object.entries(f).map(([path, v]) => ({ path, kind: v.kind }))]));
  mkdirSync(outDir, { recursive: true });
  writeFileSync(`${outDir}/paseo_${name}_samples.json`, JSON.stringify({ source: protocolPath, schema: full, excluded, cases: family.cases }, null, 2));
  console.log(`${name}: ${family.cases.length} cases, ${Object.values(full).flat().length} schema fields`);
};

run('tool', toolSchema(root), toolCases);
run('items', itemSchema(root), itemCases);
run('providers', providerSchema(messages), providerCases);
run('lists', listSchema(messages), listCases, listExcluded);
run('permissions', permissionSchema(messages.AgentPermissionRequestPayloadSchema, root), permissionCases, permissionExcluded);
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
