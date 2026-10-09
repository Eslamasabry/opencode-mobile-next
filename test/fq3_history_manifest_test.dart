import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/history_manifest.dart';

const _runID = 'fq3-binding-fixture';
const _revision = '0123456789abcdef0123456789abcdef01234567';
const _attemptID = 'attempt-001';

Map<String, dynamic> _phase({
  String engine = 'opencode',
  String session = 'ses_Fixture1',
}) => {
  'runID': _runID,
  'sourceRevision': _revision,
  'attemptID': _attemptID,
  'engine': engine,
  'case': 'stream',
  'appUID': 10123,
  'appBuild': 2202,
  'cliVersion': engine == 'opencode' ? '1.18.32' : '2.0.10',
  'observedVersion': engine == 'opencode' ? '1.18.32' : '2.0.10',
  'testedModel': 'server-default',
  'ownedSessions': [session],
};

List<Map<String, dynamic>> _phases() => [
  _phase(),
  _phase(engine: 'opencode2', session: 'ses_Fixture2'),
];

Map<String, dynamic> _history(String manifest) => {
  'runID': _runID,
  'sourceRevision': _revision,
  'attemptID': _attemptID,
  'phaseManifest': manifest,
  'appUID': 10123,
  'appBuild': 2202,
  'cleanupCompleted': true,
  'result': {
    'state': 'pass',
    'code': 'verified',
    'facts': <String, dynamic>{
      'asserted': true,
      'bothHistoriesPreserved': true,
      'freshClients': true,
      'oc1Sessions': 1,
      'oc2Sessions': 1,
    },
  },
};

void _validate(
  Map<String, dynamic> history,
  String manifest, {
  int oc1Sessions = 1,
  int oc2Sessions = 1,
}) => validateHistoryBinding(
  history,
  runID: _runID,
  sourceRevision: _revision,
  attemptID: _attemptID,
  phaseManifest: manifest,
  appUID: 10123,
  oc1Sessions: oc1Sessions,
  oc2Sessions: oc2Sessions,
);

Matcher _failure(String code) => throwsA(
  isA<ProbeFailure>().having(
    (failure) => failure.code,
    'fixed failure code',
    code,
  ),
);

void main() {
  test('manifest is stable across map insertion order and excludes bodies', () {
    final phases = _phases();
    final digest = phaseSessionManifest(phases);
    expect(digest, matches(r'^[0-9a-f]{64}$'));
    final reversedFields = [
      for (final phase in phases)
        Map<String, dynamic>.fromEntries(phase.entries.toList().reversed),
    ];
    expect(phaseSessionManifest(reversedFields), digest);
    phases.first['results'] = {'untrusted': 'DO-NOT-HASH-transcript'};
    phases.first['transcript'] = 'DO-NOT-HASH-provider-secret';
    phases.first['cleanupCode'] = null;
    expect(phaseSessionManifest(phases), digest);
    expect(phases.first['ownedSessions'], ['ses_Fixture1']);
    expect(() => _validate(_history(digest), digest), returnsNormally);
  });

  test('same session counts with changed IDs invalidate prior history', () {
    final original = phaseSessionManifest(_phases());
    final changed = _phases();
    changed.first['ownedSessions'] = ['ses_NewFixture'];
    final current = phaseSessionManifest(changed);
    expect(current, isNot(original));
    expect(
      () => _validate(_history(original), current),
      _failure('history_binding_mismatch'),
    );
  });

  test(
    'phase order, session order and every selected field affect binding',
    () {
      final phases = _phases();
      final original = phaseSessionManifest(phases);
      expect(phaseSessionManifest(phases.reversed.toList()), isNot(original));
      final replacements = <String, Object>{
        'runID': 'fq3-other-run',
        'sourceRevision': 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        'attemptID': 'attempt-002',
        'engine': 'opencode2',
        'case': 'reconnect',
        'appUID': 10124,
        'appBuild': 2194,
        'cliVersion': '1.18.31',
        'observedVersion': '1.18.31',
        'testedModel': 'opencode/big-pickle',
      };
      for (final entry in replacements.entries) {
        final changed = _phases();
        changed.first[entry.key] = entry.value;
        expect(phaseSessionManifest(changed), isNot(original));
      }
      phases.first['ownedSessions'] = ['ses_First', 'ses_Second'];
      final ordered = phaseSessionManifest(phases);
      phases.first['ownedSessions'] = ['ses_Second', 'ses_First'];
      expect(phaseSessionManifest(phases), isNot(ordered));
    },
  );

  test('manifest refuses invalid or unbounded session identifiers', () {
    for (final sessions in [
      null,
      'ses_Fixture1',
      [null],
      ['ses_'],
      ['ses_fixture-with-dash'],
      ['ses_fixture\n'],
      ['provider-secret'],
      ['ses_${List.filled(129, 'a').join()}'],
      List.filled(129, 'ses_Fixture1'),
    ]) {
      final phases = _phases();
      phases.first['ownedSessions'] = sessions;
      expect(
        () => phaseSessionManifest(phases),
        _failure('history_manifest_invalid'),
      );
    }
    expect(
      () => phaseSessionManifest([]),
      _failure('history_manifest_invalid'),
    );
    expect(
      () => phaseSessionManifest(List.generate(65, (_) => _phase())),
      _failure('history_manifest_invalid'),
    );
  });

  test('history binding changes when only the tested base model changes', () {
    final previous = phaseSessionManifest(_phases());
    final current = _phases();
    current.first['testedModel'] = 'opencode/big-pickle';
    final changed = phaseSessionManifest(current);
    expect(changed, isNot(previous));
    expect(
      () => _validate(_history(previous), changed),
      _failure('history_binding_mismatch'),
    );
    current.first['testedModel'] = 'Bearer secret';
    expect(
      () => phaseSessionManifest(current),
      _failure('history_manifest_invalid'),
    );
  });

  test(
    'history metadata must match the candidate and integer runtime values',
    () {
      final manifest = phaseSessionManifest(_phases());
      final replacements = <String, Object?>{
        'runID': 'fq3-other',
        'sourceRevision': 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        'attemptID': 'attempt-002',
        'phaseManifest': List.filled(64, '0').join(),
        'appUID': 10124,
        'appBuild': 2194,
      };
      for (final entry in replacements.entries) {
        final history = _history(manifest)..[entry.key] = entry.value;
        expect(
          () => _validate(history, manifest),
          _failure('history_binding_mismatch'),
        );
        history.remove(entry.key);
        expect(
          () => _validate(history, manifest),
          _failure('history_binding_mismatch'),
        );
      }
      for (final key in ['appUID', 'appBuild']) {
        final history = _history(manifest);
        history[key] = (history[key] as int).toDouble();
        expect(
          () => _validate(history, manifest),
          _failure('history_binding_mismatch'),
        );
      }
      for (final build in [2195, 2196]) {
        final historical = _history(manifest)..['appBuild'] = build;
        expect(
          () => _validate(historical, manifest),
          _failure('history_binding_mismatch'),
        );
      }
    },
  );

  test('cleanup failures cannot be admitted as history evidence', () {
    final manifest = phaseSessionManifest(_phases());
    for (final stamp in [null, false, 1, 'true']) {
      final history = _history(manifest);
      if (stamp == null) {
        history.remove('cleanupCompleted');
      } else {
        history['cleanupCompleted'] = stamp;
      }
      expect(
        () => _validate(history, manifest),
        _failure('history_cleanup_failed'),
      );
    }
    for (final code in [
      'cleanup_failed',
      'owned_process_cleanup_failed',
      'history_cleanup_failed',
    ]) {
      final history = _history(manifest);
      history['result'] = {'state': 'fail', 'code': code, 'facts': {}};
      expect(
        () => _validate(history, manifest),
        _failure('history_cleanup_failed'),
      );
    }
    final history = _history(manifest)..['cleanupCode'] = 'cleanup_failed';
    expect(
      () => _validate(history, manifest),
      _failure('history_cleanup_failed'),
    );
  });

  test('pass requires exact positive integer counts for both engines', () {
    final manifest = phaseSessionManifest(_phases());
    for (final key in ['oc1Sessions', 'oc2Sessions']) {
      for (final count in [null, 0, 2, 1.0, '1', true]) {
        final history = _history(manifest);
        (history['result']['facts'] as Map)[key] = count;
        expect(
          () => _validate(history, manifest),
          _failure('history_session_count_mismatch'),
        );
      }
    }
    expect(
      () => _validate(_history(manifest), manifest, oc1Sessions: 0),
      _failure('history_session_count_mismatch'),
    );
    final history = _history(manifest);
    history['result']['facts']['oc1Sessions'] = 2;
    history['result']['facts']['oc2Sessions'] = 3;
    expect(
      () => _validate(history, manifest, oc1Sessions: 2, oc2Sessions: 3),
      returnsNormally,
    );
  });

  test(
    'ordinary history failure remains explicit without pretending to pass',
    () {
      final manifest = phaseSessionManifest(_phases());
      final history = _history(manifest);
      history['result'] = {
        'state': 'fail',
        'code': 'message_history_changed',
        'facts': {},
      };
      expect(() => _validate(history, manifest), returnsNormally);
    },
  );

  test('malformed results and untrusted errors produce fixed failures', () {
    final manifest = phaseSessionManifest(_phases());
    for (final result in [
      null,
      {'state': 'pass', 'code': 'DO-NOT-PRINT-secret', 'facts': {}},
      {'state': 'unknown', 'code': 'verified', 'facts': {}},
      {'state': 'pass', 'code': 'verified', 'facts': 'secret'},
    ]) {
      final history = _history(manifest)..['result'] = result;
      expect(
        () => _validate(history, manifest),
        _failure('history_result_invalid'),
      );
    }
    final history = _history(manifest)
      ..['phaseManifest'] = 'DO-NOT-PRINT-secret';
    try {
      _validate(history, manifest);
      fail('Untrusted history was accepted');
    } on ProbeFailure catch (error) {
      expect(error.code, 'history_binding_mismatch');
      expect(error.toString(), 'Protocol assertion failed');
    }
  });
}
