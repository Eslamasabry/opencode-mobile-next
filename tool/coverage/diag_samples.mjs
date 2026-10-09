// Gate 1 inventory for the diagnostics area (setup, installs, agents on this phone).
//
//   node tool/coverage/phone_samples.mjs [out dir]
import { mkdirSync, writeFileSync } from 'node:fs';
import { finishFamily } from './oc_cases_lib.mjs';
import { diagFamilies } from './families/diag_area.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const problems = [];
for (const [name, family] of Object.entries(diagFamilies())) {
  finishFamily({ name, outDir, schema: family.schema, cases: family.cases, problems, source: family.source, writeFileSync, mkdirSync, excluded: family.excluded ?? {} });
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
