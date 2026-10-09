import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/evidence.dart';

const _runID = 'fq3-evidence-fixture';
const _revision = '0123456789abcdef0123456789abcdef01234567';
const _attemptID = 'attempt-001';

Map<String, dynamic> _phase({String engine = 'opencode'}) => {
  'runID': _runID,
  'sourceRevision': _revision,
  'attemptID': _attemptID,
  'engine': engine,
  'case': 'stream',
  'appUID': 10123,
  'appBuild': 2202,
  'cliVersion': engine == 'opencode' ? '1.18.32' : '2.0.10',
  'observedVersion': engine == 'opencode' ? '1.18.32' : '2.0.10',
  'cleanupCode': null,
  'serverKind': 'owned',
  'testedModel': 'server-default',
  'results': {
    'stream': {
      'state': 'pass',
      'code': 'verified',
      'facts': {
        'asserted': true,
        'streamedDelta': true,
        'completedReply': true,
      },
    },
  },
  'ownedSessions': ['ses_fixture'],
};

int _validate(
  Map<String, dynamic> evidence, {
  int? expectedAppUID,
  String? expectedTestedModel,
}) => validatePhaseEvidence(
  evidence,
  runID: _runID,
  sourceRevision: _revision,
  attemptID: _attemptID,
  engine: evidence['engine'] == 'opencode2' ? 'opencode2' : 'opencode',
  caseName: 'stream',
  expectedAppUID: expectedAppUID,
  expectedTestedModel: expectedTestedModel,
);

Matcher _failure(String code) => throwsA(
  isA<ProbeFailure>().having(
    (failure) => failure.code,
    'fixed failure code',
    code,
  ),
);

void main() {
  test('current phase requires build 2202 and rejects archived 2196', () {
    final current = _phase()..['appBuild'] = 2202;
    expect(_validate(current), 10123);
    final archived = _phase()..['appBuild'] = 2196;
    expect(() => _validate(archived), _failure('phase_build_mismatch'));
  });

  test('accepts pinned live metadata without changing evidence', () {
    for (final engine in ['opencode', 'opencode2']) {
      final evidence = _phase(engine: engine);
      final results = evidence['results'];
      final sessions = evidence['ownedSessions'];
      expect(_validate(evidence, expectedAppUID: 10123), 10123);
      expect(evidence, _phase(engine: engine));
      expect(evidence['results'], same(results));
      expect(evidence['ownedSessions'], same(sessions));
    }
  });

  test('requires all metadata including explicit nullable cleanup status', () {
    for (final key in [
      'runID',
      'sourceRevision',
      'attemptID',
      'engine',
      'case',
      'appUID',
      'appBuild',
      'cliVersion',
      'observedVersion',
      'cleanupCode',
      'serverKind',
      'testedModel',
    ]) {
      final evidence = _phase()..remove(key);
      expect(() => _validate(evidence), _failure('phase_metadata_missing'));
    }
  });

  test('rejects stale run, revision, attempt, engine and case metadata', () {
    final replacements = <String, Object>{
      'runID': 'fq3-previous-run',
      'sourceRevision': 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      'attemptID': 'attempt-previous',
      'engine': 'other-engine',
      'case': 'reconnect',
    };
    for (final entry in replacements.entries) {
      final evidence = _phase()..[entry.key] = entry.value;
      expect(() => _validate(evidence), _failure('phase_identity_mismatch'));
    }
    expect(
      () => validatePhaseEvidence(
        _phase(engine: 'opencode2'),
        runID: _runID,
        sourceRevision: _revision,
        attemptID: _attemptID,
        engine: 'opencode',
        caseName: 'stream',
      ),
      _failure('phase_identity_mismatch'),
    );
  });

  test('UID and build must be live integer values', () {
    for (final value in [null, 0, 2000, 9999, 10123.0, '10123', true]) {
      final evidence = _phase()..['appUID'] = value;
      expect(() => _validate(evidence), _failure('phase_uid_invalid'));
    }
    for (final value in [null, 2194, 2195, 2196, 2202.0, '2202', true]) {
      final evidence = _phase()..['appBuild'] = value;
      expect(() => _validate(evidence), _failure('phase_build_mismatch'));
    }
  });

  test('each engine needs both pinned CLI and HTTP versions', () {
    for (final engine in ['opencode', 'opencode2']) {
      for (final key in ['cliVersion', 'observedVersion']) {
        for (final value in [null, '9.9.9', '1.18.32-dev', 'provider-secret']) {
          final evidence = _phase(engine: engine)..[key] = value;
          expect(() => _validate(evidence), _failure('phase_version_mismatch'));
        }
      }
    }
  });

  test('non-null cleanup status prevents completed evidence acceptance', () {
    for (final value in ['owned_process_cleanup_failed', '', false, 0]) {
      final evidence = _phase()..['cleanupCode'] = value;
      expect(() => _validate(evidence), _failure('phase_cleanup_failed'));
    }
  });

  test('phase provenance is required and limited to fixed runtime kinds', () {
    for (final kind in ['app-managed', 'owned']) {
      expect(_validate(_phase()..['serverKind'] = kind), 10123);
    }
    for (final kind in [null, 'unit-server', 'provider-secret', true]) {
      expect(
        () => _validate(_phase()..['serverKind'] = kind),
        _failure('phase_server_kind_invalid'),
      );
    }
  });

  test(
    'base model scope is public, bounded and matches the CLI expectation',
    () {
      for (final model in [
        'server-default',
        'opencode/big-pickle',
        'zai-coding-plan/glm-5.3',
      ]) {
        expect(
          _validate(
            _phase()..['testedModel'] = model,
            expectedTestedModel: model,
          ),
          10123,
        );
      }
      for (final model in [
        null,
        '',
        'https://server/model',
        'Bearer secret',
        '/root/token',
        'provider/model/extra',
        'provider/model\n',
        'provider/${List.filled(129, 'm').join()}',
      ]) {
        expect(
          () => _validate(_phase()..['testedModel'] = model),
          _failure('phase_model_invalid'),
        );
      }
      expect(
        () => _validate(
          _phase()..['testedModel'] = 'opencode/big-pickle',
          expectedTestedModel: 'server-default',
        ),
        _failure('phase_model_mismatch'),
      );
      expect(modelSelectionFor(null), {
        'source': 'server-default',
        'requested': null,
      });
      expect(modelSelectionFor('opencode/big-pickle'), {
        'source': 'explicit',
        'requested': 'opencode/big-pickle',
      });
      expect(
        () => modelSelectionFor('Bearer secret'),
        _failure('phase_model_invalid'),
      );
    },
  );

  test(
    'late cleanup failure replaces every pass before matrix qualification',
    () {
      final report = <String, dynamic>{
        'schemaVersion': 2,
        'attestation': {'cleanupCompleted': false},
        'engines': {
          for (final engine in ['opencode', 'opencode2'])
            engine: {
              'modelSelection': {
                'source': 'explicit',
                'requested': 'opencode/big-pickle',
              },
              'results': {
                for (final key in capabilityKeys)
                  key: {
                    'state': 'pass',
                    'code': 'verified',
                    'facts': {'asserted': true},
                  },
              },
            },
        },
        'protocolSwitch': {
          'state': 'pass',
          'code': 'verified',
          'facts': {'asserted': true},
        },
      };
      final succeeded = reportAfterCleanup(report, cleanupCompleted: true);
      expect(succeeded['engines'], report['engines']);
      expect(succeeded['attestation']['cleanupCompleted'], isTrue);
      final failed = reportAfterCleanup(report, cleanupCompleted: false);
      for (final engine in (failed['engines'] as Map).values) {
        expect((engine['results'] as Map).keys.toSet(), capabilityKeys.toSet());
        for (final result in (engine['results'] as Map).values) {
          expect(result, {
            'state': 'fail',
            'code': 'owned_session_cleanup_failed',
            'facts': {},
          });
        }
        expect(engine['modelSelection'], {
          'source': 'explicit',
          'requested': 'opencode/big-pickle',
        });
      }
      expect(failed['protocolSwitch']['state'], 'fail');
      expect(failed['attestation']['cleanupCompleted'], isFalse);
      expect(report['attestation']['cleanupCompleted'], isFalse);
      expect(report['protocolSwitch']['state'], 'pass');
    },
  );

  test('one UID is retained across engines and conflicts cannot merge', () {
    int? uid;
    uid = _validate(_phase(), expectedAppUID: uid);
    uid = _validate(_phase(engine: 'opencode2'), expectedAppUID: uid);
    expect(uid, 10123);
    final conflicting = _phase(engine: 'opencode2')..['appUID'] = 10124;
    expect(
      () => _validate(conflicting, expectedAppUID: uid),
      _failure('phase_uid_conflict'),
    );
    expect(requireConsistentAppUID(null, 10123), 10123);
    expect(requireConsistentAppUID(10123, 10123), 10123);
    expect(
      () => requireConsistentAppUID(10123, 10124),
      _failure('phase_uid_conflict'),
    );
    expect(
      () => requireConsistentAppUID(0, 10123),
      _failure('phase_uid_invalid'),
    );
    expect(
      () => requireConsistentAppUID(null, 2000),
      _failure('phase_uid_invalid'),
    );
  });

  test('untrusted metadata never reaches failure code or error text', () {
    const secret = 'DO-NOT-PRINT-provider-secret';
    for (final key in [
      'runID',
      'sourceRevision',
      'attemptID',
      'engine',
      'case',
      'cliVersion',
      'observedVersion',
      'cleanupCode',
    ]) {
      final evidence = _phase()..[key] = secret;
      try {
        _validate(evidence);
        fail('Untrusted metadata was accepted');
      } on ProbeFailure catch (error) {
        expect(error.code, matches(r'^[a-z][a-z0-9_]{0,63}$'));
        expect(error.code, isNot(contains(secret)));
        expect(error.toString(), 'Protocol assertion failed');
      }
    }
  });

  test(
    'invalid caller expectations cannot validate self-matching evidence',
    () {
      for (final field in [
        'runID',
        'sourceRevision',
        'attemptID',
        'engine',
        'case',
      ]) {
        final evidence = _phase()..[field] = '';
        expect(
          () => validatePhaseEvidence(
            evidence,
            runID: evidence['runID'] as String,
            sourceRevision: evidence['sourceRevision'] as String,
            attemptID: evidence['attemptID'] as String,
            engine: evidence['engine'] as String,
            caseName: evidence['case'] as String,
          ),
          _failure('phase_expectation_invalid'),
        );
      }
    },
  );
}
