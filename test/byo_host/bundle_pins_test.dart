import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/host/byo_host_bundle_pins.dart';

void main() {
  test('compiled artifacts match the exact app-version review ledger', () {
    final ledger =
        jsonDecode(
              File('scripts/byo-host/release-pins.json').readAsStringSync(),
            )
            as Map<String, dynamic>;
    expect(ledger['openCodeVersion'], '2.1.1');
    expect(
      ledger['appVersions'],
      isEmpty,
    ); // Publication requires owner review.
    for (final version in ['1.1.0+51', '1.1.0+52', '1.1.0', '', 'latest']) {
      expect(byoHostBundleForAppVersion(version), isNull);
    }
  });
}
