import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test.builtin_work_leases');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<MethodCall> calls;
  Object? response;

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    calls = [];
    response = {'held': true, 'capped': false};
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return response;
    });
  });

  tearDown(() {
    debugPlatformCapabilities = null;
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('named chat acquisition uses the frozen channel contract', () async {
    final status = await BuiltinLinux(
      channel: channel,
    ).setChatWorkLease(leaseId: 'chat.opaque-1', on: true);
    expect(calls.single.method, 'setChatWorkLease');
    expect(calls.single.arguments, {
      'leaseId': 'chat.opaque-1',
      'on': true,
      'forMs': 600000,
    });
    expect(status.held, isTrue);
    expect(status.capped, isFalse);
  });

  test('named chat release retains its ID and requested duration', () async {
    response = {'held': false, 'capped': false};
    final status = await BuiltinLinux(channel: channel).setChatWorkLease(
      leaseId: 'chat.opaque-2',
      on: false,
      hold: const Duration(minutes: 2),
    );
    expect(calls.single.method, 'setChatWorkLease');
    expect(calls.single.arguments, {
      'leaseId': 'chat.opaque-2',
      'on': false,
      'forMs': 120000,
    });
    expect(status.held, isFalse);
    expect(status.capped, isFalse);
  });

  test(
    'native exhausted lease reports capped without reporting held',
    () async {
      response = {'held': false, 'capped': true};
      final status = await BuiltinLinux(
        channel: channel,
      ).setChatWorkLease(leaseId: 'chat.expired', on: true);
      expect(status.held, isFalse);
      expect(status.capped, isTrue);
    },
  );

  test('other work holding the lock does not claim this chat lease', () async {
    response = {'held': false, 'capped': false, 'workHeld': true};
    final status = await BuiltinLinux(
      channel: channel,
    ).setChatWorkLease(leaseId: 'chat.refused', on: true);
    expect(status.held, isFalse);
    expect(status.capped, isFalse);
  });

  test('absent and malformed replies never fabricate admission', () async {
    final linux = BuiltinLinux(channel: channel);
    for (final malformed in <Object?>[
      null,
      true,
      'held',
      7,
      [],
      {},
      {'held': true},
      {'capped': false},
      {'held': 'true', 'capped': false},
      {'held': true, 'capped': 0},
      {'held': true, 'capped': true},
    ]) {
      response = malformed;
      final status = await linux.setChatWorkLease(
        leaseId: 'chat.safe',
        on: true,
      );
      expect(status.held, isFalse);
      expect(status.capped, isFalse);
    }
  });

  test('invalid opaque IDs are refused before native handoff', () async {
    final linux = BuiltinLinux(channel: channel);
    for (final id in [
      '',
      'chat space',
      '../chat',
      'chat/other',
      'chat\n',
      'a' * 81,
    ]) {
      await expectLater(
        linux.setChatWorkLease(leaseId: id, on: true),
        throwsA(
          isA<BuiltinLinuxException>().having(
            (e) => e.code,
            'code',
            'work_lease_invalid',
          ),
        ),
      );
    }
    expect(calls, isEmpty);
  });

  test('maximum length opaque ID reaches native unchanged', () async {
    final id = 'a' * 80;
    await BuiltinLinux(
      channel: channel,
    ).setChatWorkLease(leaseId: id, on: true);
    expect((calls.single.arguments as Map)['leaseId'], id);
  });

  test(
    'unsupported platforms return no admission without native handoff',
    () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      final status = await BuiltinLinux(
        channel: channel,
      ).setChatWorkLease(leaseId: 'chat.unsupported', on: true);
      expect(status.held, isFalse);
      expect(status.capped, isFalse);
      expect(calls, isEmpty);
    },
  );

  test(
    'legacy timed hold keeps its bool return and original arguments',
    () async {
      response = true;
      final held = await BuiltinLinux(
        channel: channel,
      ).holdAwakeForWork(false, hold: const Duration(seconds: 3));
      expect(held, isTrue);
      expect(calls.single.method, 'holdAwakeForWork');
      expect(calls.single.arguments, {'on': false, 'forMs': 3000});
    },
  );
}
