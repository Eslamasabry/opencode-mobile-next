import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/tested_versions.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon;

Future<PaseoFailure> _refusedWith(String reason, {String password = ''}) async {
  final daemon = FakeDaemon()..rejectReason = reason;
  final gateway = PaseoGateway(
    transport: PaseoTransport(
      endpoint: 'ws://127.0.0.1:6767',
      password: password,
      socketFactory: (_, _) async => daemon,
    ),
    directory: '/work/app',
  );
  addTearDown(gateway.close);
  try {
    await gateway.health();
  } on PaseoFailure catch (failure) {
    return failure;
  }
  fail('the handshake should have been refused');
}

void main() {
  group('hello.rejected', () {
    test(
      'a missing or wrong password reads as an authentication failure',
      () async {
        for (final reason in ['password_required', 'incorrect_password']) {
          final failure = await _refusedWith(reason, password: 'x1y2z3');
          expect(failure.kind, PaseoFailureKind.authentication);
          // Plain words for the screen; the daemon's reason only for Details.
          expect(failure.message, 'The Paseo daemon rejected the password.');
          expect(failure.message, isNot(contains(reason)));
          expect(failure.reason, reason);
          expect(failure.toString(), isNot(contains('x1y2z3')));
        }
      },
    );

    test('an incompatible protocol is named plainly', () async {
      final failure = await _refusedWith('incompatible_protocol');
      expect(failure.kind, PaseoFailureKind.incompatibleProtocol);
      expect(failure.message, isNot(contains('incompatible_protocol')));
      expect(failure.reason, 'incompatible_protocol');
    });

    test('an unknown reason is a plain disconnect', () async {
      final failure = await _refusedWith('something_new');
      expect(failure.kind, PaseoFailureKind.disconnected);
    });

    test('the close code alone still maps when no frame arrived', () async {
      final daemon = FakeDaemon()
        ..rejectWith = PaseoTransport.closeIncompatibleProtocol;
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      );
      addTearDown(transport.close);
      await expectLater(
        transport.connect(),
        throwsA(
          isA<PaseoFailure>().having(
            (f) => f.kind,
            'kind',
            PaseoFailureKind.incompatibleProtocol,
          ),
        ),
      );
    });
  });

  group('handshake', () {
    test('advertises hello_rejection and the in-protocol password', () async {
      final daemon = FakeDaemon();
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        password: 'x1y2z3',
        socketFactory: (_, _) async => daemon,
      );
      addTearDown(transport.close);
      await transport.connect();
      expect(
        daemon.hello!['capabilities'],
        containsPair('hello_rejection', true),
      );
      expect(daemon.hello!['auth'], {'kind': 'password', 'password': 'x1y2z3'});
    });

    test('no password means no auth field', () async {
      final daemon = FakeDaemon();
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      );
      addTearDown(transport.close);
      await transport.connect();
      expect(daemon.hello!.containsKey('auth'), isFalse);
    });

    test('frames this client does not know are ignored, not errors', () async {
      final daemon = FakeDaemon();
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      );
      addTearDown(transport.close);
      await transport.connect();
      final events = <PaseoEvent>[];
      transport.events.listen(events.add);
      daemon.pushRaw({'type': 'brand.new.frame', 'data': 1});
      daemon.push('usage.list_reports.response', {
        'requestId': 'nobody',
        'reports': [],
      });
      daemon.push('usage.something_pushed', {'n': 1});
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(transport.connected, isTrue);
      expect(events.map((e) => e.type), ['usage.something_pushed']);
    });
  });

  group('tested versions', () {
    test('the old and the new daemon are both accepted', () {
      expect(paseoSupports('0.9.2'), isTrue);
      expect(paseoSupports('0.11.2'), isTrue);
      // Daemons that updated themselves on a computer, each run live.
      expect(paseoSupports('0.12.0'), isTrue);
      expect(paseoSupports('0.13.2'), isTrue);
      expect(paseoSupports('0.14.0'), isTrue);
      // Not run: stays refused until it has evidence.
      expect(paseoSupports('0.13.0'), isFalse);
      expect(paseoSupports('0.13.1'), isFalse);
      expect(paseoSupports('0.14.1'), isFalse);
      expect(paseoSupports('0.11.3'), isFalse);
      expect(paseoSupports('0.9.3'), isFalse);
      expect(paseoSupports(null), isFalse);
    });

    test('Claude Code: the previous and the current pin are accepted', () {
      expect(claudeCodeSupports('2.1.283'), isTrue);
      expect(claudeCodeSupports('2.1.294'), isTrue);
      expect(claudeCodeSupports('2.1.316'), isTrue);
      expect(claudeCodeSupports('2.1.317'), isFalse);
      // The catalog pin is always a tested release, or gen-UI would silently
      // stop qualifying on a fresh install.
      expect(
        claudeCodeSupports(
          AgentCatalog.builtIn.byId('claude')!.recipe!.version,
        ),
        isTrue,
      );
      expect(claudeCodeSupports('2.1.282'), isFalse);
      expect(claudeCodeSupports(null), isFalse);
    });
  });
}
