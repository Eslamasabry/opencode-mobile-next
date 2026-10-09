// Gate 1 inventory for what OpenCode 1 and OpenCode 2 send a chat. Reads the
// OpenAPI contracts, lists every field of every family, and writes one JSON
// per family for the coverage tests.
//
//   node tool/coverage/oc_samples.mjs [out dir]     (default test/fixtures/coverage)
//
// The build FAILS when a schema field is in no case, or a case uses a field
// the schema does not have.
import { mkdirSync, writeFileSync } from 'node:fs';
import { finishFamily } from './oc_cases_lib.mjs';
import { oc1PartsFamily } from './families/oc1_parts.mjs';
import { oc1EventsFamily } from './families/oc1_events.mjs';
import { oc2RequestsFamily } from './families/oc2_requests.mjs';
import { oc2MessagesFamily } from './families/oc2_messages.mjs';
import { oc2EventsFamily } from './families/oc2_events.mjs';
import { oc1RequestsFamily } from './families/oc1_requests.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const OC1 = 'contracts/opencode-openapi-f12e14cf.json';
const OC2 = 'contracts/opencode2-openapi-beta-18600.json';
const problems = [];
const run = (name, built, source) =>
  finishFamily({ name, outDir, ...built, problems, source, writeFileSync, mkdirSync });

run('oc1_parts', oc1PartsFamily(OC1), OC1);
run('oc1_requests', oc1RequestsFamily(OC1), OC1);
run('oc1_events', oc1EventsFamily(OC1), OC1);
run('oc2_events', oc2EventsFamily(), 'docs/opencode2-protocol-notes.md section 3.2');
run('oc2_messages', oc2MessagesFamily(OC2), OC2);
run('oc2_requests', oc2RequestsFamily(OC2), OC2);

if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
