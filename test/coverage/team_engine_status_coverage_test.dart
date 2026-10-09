// Coverage ratchet for what the native AI Team engine reports over its channel
// (the phone engine status from the native bridge) and its /v1/health answer.
// team_engine_status_ledger.json decides each field:
//   covered elsewhere: <test file> ~ <token>   a test of that screen or rule
//   ignored: <reason>                          nothing a person reads
// The field lists are read from the parsers' own source, so a new field in
// either answer fails here until it is decided.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

Set<String> _fields(String file, String start, String end, RegExp pattern) {
  final text = File(file).readAsStringSync();
  final from = text.indexOf(start);
  final to = text.indexOf(end, from);
  return {
    for (final m in pattern.allMatches(text.substring(from, to)))
      for (var i = 1; i <= m.groupCount; i++)
        if (m.group(i) != null) m.group(i)!,
  };
}

void main() {
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/team_engine_status_ledger.json',
                ).readAsStringSync(),
              )
              as Map)
          .cast<String, String>();

  test('every field the engine reports has a decision', () {
    final status = _fields(
      'lib/builtin/builtin_linux.dart',
      'factory BuiltinPhoneEngineStatus.fromMap',
      'final String profileId;',
      RegExp(r"map\['(\w+)'\]"),
    );
    final health = _fields(
      'lib/domain/phone_project_engine.dart',
      'factory PhoneEngineHealth.fromJson',
      'commandActions: Set.unmodifiable',
      RegExp(r"(?:value|flags)\['(\w+)'\]"),
    );
    final known = {
      for (final f in status) 'status.$f',
      for (final f in health) 'health.$f',
      // The capability flags come through `capabilities`; the answer's own
      // fields are listed above.
      'health.execution',
      'health.boundary',
      'health.oc1Verified',
      'health.oc2',
    };
    expect(
      known.difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'fields with no entry in team_engine_status_ledger.json',
    );
    expect(
      ledger.keys.toSet().difference(known),
      isEmpty,
      reason: 'ledger entries for fields the parsers no longer read',
    );
  });

  test('each decision is a reason or a test that exists', () {
    for (final e in ledger.entries) {
      if (e.value.startsWith('ignored: ')) {
        expect(e.value.length, greaterThan(30), reason: e.key);
        continue;
      }
      final m = RegExp(
        r'^covered elsewhere: (\S+) ~ (\w+)$',
      ).firstMatch(e.value);
      expect(
        m,
        isNotNull,
        reason: '${e.key}: "covered elsewhere: file ~ token"',
      );
      expect(
        File(m!.group(1)!).readAsStringSync(),
        contains(m.group(2)!),
        reason: '${e.key}: ${m.group(1)} does not mention ${m.group(2)}',
      );
    }
  });
}
