import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';
import '../tool/qa/fq3/session_ownership.dart';
import '../tool/qa/fq3_certify.dart' as driver;

const _run = 'fq3-ownership-fixture';
const _revision = '0123456789abcdef0123456789abcdef01234567';

SessionOwnership _create(
  File file, {
  String revision = _revision,
  String engine = 'opencode',
  bool legacy = false,
  List<String> ids = const [],
}) => SessionOwnership.create(
  file,
  runID: _run,
  sourceRevision: revision,
  attemptID: 'attempt-001',
  engine: engine,
  caseName: 'stream',
  appUID: 10123,
  appBuild: legacy ? 2195 : 2196,
  legacy: legacy,
  sessionIDs: ids,
);

Map<String, dynamic> _session({
  String id = 'ses_Fixture1',
  String title = '$_run-opencode-stream-session-1',
  String directory = '/root/projects/$_run',
}) => {'id': id, 'title': title, 'directory': directory};

Matcher _failure(String code) => throwsA(
  isA<ProbeFailure>().having((error) => error.code, 'fixed failure code', code),
);

void main() {
  late Directory temporary;
  late File file;
  setUp(() {
    temporary = Directory.systemTemp.createTempSync('fq3-ownership-test-');
    file = File('${temporary.path}/intent-ownership.json');
  });
  tearDown(() => temporary.deleteSync(recursive: true));

  test(
    'intent and each ID survive reopening before phase results return',
    () async {
      final ledger = _create(file);
      expect(file.existsSync(), isTrue);
      expect(SessionOwnership.read(file).sessionIDs, isEmpty);
      await ledger.recordCreated('ses_Fixture1');
      await ledger.recordCreated('ses_Fixture2');
      await ledger.recordCreated('ses_Fixture1');
      expect(SessionOwnership.read(file).sessionIDs, [
        'ses_Fixture1',
        'ses_Fixture2',
      ]);
      expect(_create(file).sessionIDs, ['ses_Fixture1', 'ses_Fixture2']);
      expect(
        () => ledger.removeIfEmpty(),
        _failure('ownership_cleanup_incomplete'),
      );
      await ledger.markDeleted('ses_Fixture1');
      expect(SessionOwnership.read(file).sessionIDs, ['ses_Fixture2']);
      await ledger.markDeleted('ses_Fixture2');
      ledger.removeIfEmpty();
      expect(file.existsSync(), isFalse);
    },
  );

  test('recovery recognizes only exact phase title grammar and directory', () {
    final ledger = _create(file);
    expect(ledger.matchesSession(_session()), isTrue);
    expect(
      ledger.sessionIDs,
      isEmpty,
    ); // Discovery closes an interrupted callback gap.
    for (final session in [
      _session(directory: '/root/projects/user'),
      _session(title: '$_run-opencode-reconnect-session-1'),
      _session(title: '$_run-opencode-stream-session-0'),
      _session(title: '$_run-opencode-stream-session-1-user'),
      _session(title: '$_run-opencode-stream-session-1\n'),
      _session(id: 'user-session'),
      {'id': 'ses_Fixture1', 'title': _session()['title']},
    ]) {
      expect(ledger.matchesSession(session), isFalse);
    }
    final oc2 = {
      'id': 'ses_Fixture1',
      'title': _session()['title'],
      'location': {'directory': '/root/projects/$_run'},
    };
    expect(ledger.matchesSession(oc2), isTrue);
  });

  test(
    'archived cleanup requires explicit IDs, exact directory and legacy title',
    () {
      final ledger = _create(file, legacy: true, ids: ['ses_Fixture1']);
      expect(
        ledger.matchesSession(_session(title: '$_run-oc1-stream-nonce')),
        isTrue,
      );
      expect(
        ledger.matchesSession(
          _session(id: 'ses_Uncaptured', title: '$_run-oc1-stream-nonce'),
        ),
        isFalse,
      );
      expect(
        ledger.matchesSession(
          _session(
            title: '$_run-oc1-stream-nonce',
            directory: '/root/projects/user',
          ),
        ),
        isFalse,
      );
      expect(ledger.matchesSession(_session(title: 'user-session')), isFalse);
      final other = File('${temporary.path}/oc2-ownership.json');
      final oc2 = _create(
        other,
        engine: 'opencode2',
        legacy: true,
        ids: ['ses_Fixture1'],
      );
      expect(oc2.matchesSession(_session(title: _run)), isTrue);
      expect(oc2.matchesSession(_session(title: '$_run-1')), isTrue);
      expect(
        oc2.matchesSession(_session(id: 'ses_Uncaptured', title: _run)),
        isFalse,
      );
    },
  );

  test('conflicting intent cannot replace an earlier attempt ledger', () async {
    await _create(file).recordCreated('ses_Fixture1');
    final before = file.readAsStringSync();
    expect(
      () => _create(file, revision: List.filled(40, 'a').join()),
      _failure('ownership_intent_conflict'),
    );
    expect(file.readAsStringSync(), before);
    expect(SessionOwnership.read(file).sessionIDs, ['ses_Fixture1']);
  });

  test(
    'symlink target, parent and pending file never receive writes',
    () async {
      final outside = File('${temporary.path}/outside.json')
        ..writeAsStringSync('untouched');
      Link(file.path).createSync(outside.path);
      expect(() => _create(file), _failure('ownership_path_unsafe'));
      Link(file.path).deleteSync();
      final ledger = _create(file);
      Link('${file.path}.pending').createSync(outside.path);
      await expectLater(
        ledger.recordCreated('ses_Fixture1'),
        _failure('ownership_path_unsafe'),
      );
      expect(outside.readAsStringSync(), 'untouched');
      expect(SessionOwnership.read(file).sessionIDs, isEmpty);
      final actual = Directory('${temporary.path}/actual')..createSync();
      Link('${temporary.path}/linked').createSync(actual.path);
      expect(
        () => _create(File('${temporary.path}/linked/intent.json')),
        _failure('ownership_path_unsafe'),
      );
    },
  );

  test(
    'unbounded or malformed evidence is refused with fixed errors',
    () async {
      final ledger = _create(file);
      for (final id in [
        'ses_',
        'ses_bad-id',
        'ses_Fixture1\n',
        'provider-secret',
      ]) {
        await expectLater(
          ledger.recordCreated(id),
          _failure('ownership_session_invalid'),
        );
      }
      file.writeAsStringSync(List.filled(64 * 1024 + 1, ' ').join());
      expect(
        () => SessionOwnership.read(file),
        _failure('ownership_evidence_too_large'),
      );
      file.writeAsStringSync('{"providerSecret":"DO-NOT-PRINT"}');
      try {
        SessionOwnership.read(file);
        fail('Malformed ownership evidence was accepted');
      } on ProbeFailure catch (error) {
        expect(error.code, 'ownership_evidence_invalid');
        expect(error.toString(), 'Protocol assertion failed');
      }
    },
  );

  test(
    'cleanup discovers interrupted IDs, accepts 404 and preserves user rows',
    () async {
      final ledger = _create(file);
      await ledger.recordCreated('ses_Recorded');
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final wire = Fq3Wire(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture-secret',
      );
      addTearDown(() async {
        await wire.close();
        await server.close(force: true);
      });
      final deleted = <String>[];
      server.listen((request) async {
        await request.drain<void>();
        request.response.headers.contentType = ContentType.json;
        final path = request.uri.path;
        if (path == '/global/health') {
          request.response.write('{"version":"1.18.32"}');
        } else if (path == '/session') {
          request.response.write(
            jsonEncode([
              _session(id: 'ses_Gap'),
              _session(id: 'ses_User', title: 'User conversation'),
            ]),
          );
        } else if (path == '/session/ses_Recorded') {
          request.response.statusCode = 404;
        } else if (request.method == 'DELETE') {
          deleted.add(path);
          request.response.write('{}');
        } else {
          request.response.write(jsonEncode(_session(id: 'ses_Gap')));
        }
        await request.response.close();
      });
      await driver.cleanupLedger(wire, ledger);
      expect(deleted, ['/session/ses_Gap']);
      expect(file.existsSync(), isFalse);
    },
  );

  test(
    'cleanup retains durable ID when exact session scope disagrees',
    () async {
      final ledger = _create(file, legacy: true, ids: ['ses_Fixture1']);
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final wire = Fq3Wire(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture-secret',
      );
      addTearDown(() async {
        await wire.close();
        await server.close(force: true);
      });
      var deleted = false;
      server.listen((request) async {
        await request.drain<void>();
        if (request.method == 'DELETE') deleted = true;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode(
            _session(
              title: '$_run-oc1-stream-nonce',
              directory: '/root/projects/user',
            ),
          ),
        );
        await request.response.close();
      });
      await expectLater(
        driver.cleanupLedger(wire, ledger),
        _failure('ownership_session_scope_mismatch'),
      );
      expect(deleted, isFalse);
      expect(SessionOwnership.read(file).sessionIDs, ['ses_Fixture1']);
    },
  );
}
