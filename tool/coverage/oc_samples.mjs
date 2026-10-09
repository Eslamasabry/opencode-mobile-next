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
import { oc1ListsFamily } from './families/oc1_lists.mjs';
import { oc2ListsFamily } from './families/oc2_lists.mjs';
import { oc1SettingsFamily, oc2SettingsFamily } from './families/settings.mjs';
import { oc1FilesFamily, oc2FilesFamily } from './families/files.mjs';
import { oc2ModelsFamily, oc1ModelsFamily, oc2UsageFamily, quotaFamily, codexAccountFamily } from './families/models.mjs';

const outDir = process.argv[2] ?? 'test/fixtures/coverage';
const OC1 = 'contracts/opencode-openapi-f12e14cf.json';
const OC2 = 'contracts/opencode2-openapi-beta-18600.json';
const problems = [];
const run = (name, built, source) =>
  finishFamily({ name, outDir, ...built, problems, source, writeFileSync, mkdirSync });

run('oc1_parts', oc1PartsFamily(OC1), OC1);
run('oc1_requests', oc1RequestsFamily(OC1), OC1);
run('oc1_events', oc1EventsFamily(OC1), OC1);
run('oc1_lists', oc1ListsFamily(OC1), OC1);
run('oc2_lists', oc2ListsFamily(OC2), OC2);
run('oc1_settings', oc1SettingsFamily(OC1), OC1);
run('oc2_settings', oc2SettingsFamily(OC2), OC2);
run('oc1_files', oc1FilesFamily(OC1), OC1);
run('oc2_files', oc2FilesFamily(OC2), OC2);
run('oc1_models', oc1ModelsFamily(OC1), OC1);
run('oc2_models', oc2ModelsFamily(OC2), OC2);
run('oc2_usage', oc2UsageFamily(OC2), OC2);
run('codex_account', codexAccountFamily(), 'lib/codex/account.dart (the Codex app-server account API; no contract here)');
run('quota', quotaFamily(), 'lib/domain/provider_quota.dart (a deployment extension; no contract)');
run('oc2_events', oc2EventsFamily(), 'docs/opencode2-protocol-notes.md section 3.2');
run('oc2_messages', oc2MessagesFamily(OC2), OC2);
run('oc2_requests', oc2RequestsFamily(OC2), OC2);

if (problems.length) {
  console.error(problems.join('\n'));
  process.exit(1);
}
