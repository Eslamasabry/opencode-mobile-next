// Gate 1 inventory for the phone area (setup, installs, agents on this phone).
//
//   node tool/coverage/phone_samples.mjs [out dir]
import { mkdirSync, writeFileSync } from 'node:fs';
import { finishFamily } from './oc_cases_lib.mjs';
import { phoneFamilies } from './families/phone_area.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const problems = [];
for (const [name, family] of Object.entries(phoneFamilies())) {
  finishFamily({ name, outDir, schema: family.schema, cases: family.cases, problems, source: family.source, writeFileSync, mkdirSync, excluded: family.excluded ?? {} });
}
if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
