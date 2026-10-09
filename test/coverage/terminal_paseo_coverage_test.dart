// Paseo's terminal messages are messages the app never asks for (a Claude
// Code or Pi server has no terminal tab): every field is in the ledger as
// ignored, and this ratchet fails when Paseo adds one that has no decision.

import 'paseo_coverage_support.dart';

void main() {
  registerLedgerTests(CoverageFamily('terminal_paseo', prefix: ''));
}
