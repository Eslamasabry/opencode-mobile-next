// Paseo's terminal messages are the Terminal page's plumbing (list, open,
// type, rename, close; tests/paseo_project_tools_test.dart drives them from
// the page): every field is in the ledger as ignored, and this ratchet fails
// when Paseo adds one that has no decision.

import 'paseo_coverage_support.dart';

void main() {
  registerLedgerTests(CoverageFamily('terminal_paseo', prefix: ''));
}
