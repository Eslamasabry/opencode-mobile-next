import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/domain/byo_host_setup.dart';
import 'package:opencode_mobile/host/byo_host_tailnet.dart';

void main() {
  final denied = throwsA(
    isA<ByoHostFailure>().having(
      (error) => error.code,
      'code',
      ByoHostFailureCode.tailnetRequired,
    ),
  );

  test('numeric tailnet ranges preserve user/port and skip DNS', () async {
    final resolver = ByoHostTailnetResolver(
      lookup: (_) => throw StateError('DNS must not run'),
    );
    for (final host in [
      '100.64.0.1',
      '100.127.255.254',
      'fd7a:115c:a1e0::1234',
    ]) {
      final resolved = await resolver.resolve(
        ByoHostTarget(user: 'alice', host: host, port: 2222),
      );
      expect(resolved.user, 'alice');
      expect(resolved.port, 2222);
      expect(InternetAddress.tryParse(resolved.host), isNotNull);
      expect(
        ByoHostTailnetResolver.isTailnetAddress(InternetAddress(resolved.host)),
        isTrue,
      );
    }
  });

  test(
    'refuses public, ordinary private, loopback and near-tailnet literals',
    () async {
      final resolver = ByoHostTailnetResolver();
      for (final host in [
        '8.8.8.8',
        '192.168.1.2',
        '10.0.0.1',
        '127.0.0.1',
        '100.63.255.255',
        '100.128.0.1',
        '2001:db8::1',
        'fd7a:115c:a1e1::1',
        'fc00::1',
        '0:0:0:0:0:0:0:1',
        '0:0:0:0:0:ffff:6440:1',
      ]) {
        await expectLater(
          resolver.resolve(ByoHostTarget(user: 'alice', host: host)),
          denied,
        );
      }
    },
  );

  test('refuses reserved Tailscale service endpoints', () async {
    final resolver = ByoHostTailnetResolver();
    for (final host in [
      '100.100.100.100',
      '100.100.0.2',
      '100.115.92.1',
      '100.115.93.254',
      '100.101.102.103',
      'fd7a:115c:a1e0::53',
    ]) {
      await expectLater(
        resolver.resolve(ByoHostTarget(user: 'alice', host: host)),
        denied,
      );
    }
  });

  test('resolves hostname once and returns stable numeric target', () async {
    var calls = 0;
    final resolver = ByoHostTailnetResolver(
      lookup: (host) async {
        calls++;
        expect(host, 'machine.example.ts.net');
        return [
          InternetAddress('fd7a:115c:a1e0::1234'),
          InternetAddress('100.80.1.2'),
        ];
      },
    );
    final target = await resolver.resolve(
      ByoHostTarget(user: 'alice', host: 'machine.example.ts.net'),
    );
    expect(calls, 1);
    expect(target.host, '100.80.1.2');
  });

  test('suffix and mixed DNS do not authorize public SSH', () async {
    for (final answers in [
      [InternetAddress('203.0.113.1')],
      [InternetAddress('100.80.1.2'), InternetAddress('203.0.113.1')],
      [InternetAddress('100.80.1.2'), InternetAddress('2001:db8::1')],
      <InternetAddress>[],
    ]) {
      final resolver = ByoHostTailnetResolver(lookup: (_) async => answers);
      await expectLater(
        resolver.resolve(
          ByoHostTarget(user: 'alice', host: 'looks-private.ts.net'),
        ),
        denied,
      );
    }
  });

  test('DNS exceptions are replaced with safe fixed failures', () async {
    final resolver = ByoHostTailnetResolver(
      lookup: (_) async => throw StateError('SECRET_LOOKUP_FAILURE'),
    );
    try {
      await resolver.resolve(
        ByoHostTarget(user: 'alice', host: 'unavailable.ts.net'),
      );
      fail('Expected refusal');
    } on ByoHostFailure catch (error) {
      expect(error.code, ByoHostFailureCode.tailnetRequired);
      expect(
        '$error ${error.message}',
        isNot(contains('SECRET_LOOKUP_FAILURE')),
      );
    }
  });

  test(
    'owner steps contain exact manual setup and honest read-only checks',
    () {
      final steps = byoHostOwnerSetup(
        ByoHostTarget(user: 'alice', host: '100.80.1.2'),
      );
      expect(steps.map((step) => step.id), [
        'tailnet',
        'account',
        'sshNetwork',
        'linger',
        'sshPolicy',
        'identity',
      ]);
      final linger = steps.singleWhere((step) => step.id == 'linger');
      expect(linger.commands.first, "sudo loginctl enable-linger 'alice'");
      expect(linger.verification, contains('never enables linger'));
      final policy = steps.singleWhere((step) => step.id == 'sshPolicy');
      expect(policy.commands.first, contains('Match User alice'));
      expect(policy.commands.first, contains('AllowTcpForwarding local'));
      expect(policy.commands.first, contains('AllowStreamLocalForwarding no'));
      expect(policy.commands.first, contains('PermitTunnel no'));
      expect(policy.commands.first, isNot(contains('PasswordAuthentication')));
      expect(policy.commands.last, 'sudo systemctl reload ssh');
      expect(
        policy.verification,
        contains('does not prove the running daemon'),
      );
      expect(
        steps.every(
          (step) => step.copy.isNotEmpty && step.verification.isNotEmpty,
        ),
        isTrue,
      );
      expect(() => steps.clear(), throwsUnsupportedError);
    },
  );
}
