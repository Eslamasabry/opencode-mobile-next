import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel(BuiltinLinux.channelName);
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return null;
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('missing native intent fails closed even when an old server ran', () {
    for (final status in [
      const BuiltinLinuxStatus.absent(),
      BuiltinLinuxStatus.fromMap({'serverRunning': true}),
      BuiltinLinuxStatus.fromMap({'serverRestartWanted': 'true'}),
    ]) {
      expect(status.serverRestartWanted, isFalse);
      expect(status.serverRecoveryGeneration, isNull);
    }
  });

  test('native intent remains readable after process death', () {
    final status = BuiltinLinuxStatus.fromMap({
      'serverRunning': false,
      'serverRestartWanted': true,
      'serverRecoveryGeneration': 42,
    });
    expect(status.serverRunning, isFalse);
    expect(status.serverRestartWanted, isTrue);
    expect(status.serverRecoveryGeneration, 42);
  });

  test(
    'automatic launch and confirmation carry the same admission token',
    () async {
      final linux = BuiltinLinux();
      await linux.restartServer('serve', expectedGeneration: 42);
      await linux.confirmServerRecovery(expectedGeneration: 42);
      await linux.cancelServerRecovery();
      expect(calls.map((call) => call.method), [
        'restartServer',
        'confirmServerRecovery',
        'cancelServerRecovery',
      ]);
      expect(calls[0].arguments, {
        'script': 'serve',
        'port': BuiltinLinux.serverPort,
        'expectedGeneration': 42,
      });
      expect(calls[1].arguments, {'expectedGeneration': 42});
      expect(calls[2].arguments, isNull);
    },
  );

  test('recovery errors never expose native exception details', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          throw PlatformException(
            code: 'native_error',
            message: 'private runtime path and confidential content',
          );
        });
    final linux = BuiltinLinux();
    for (final attempt in [
      () => linux.restartServer('serve', expectedGeneration: 42),
      () => linux.confirmServerRecovery(expectedGeneration: 42),
      linux.cancelServerRecovery,
    ]) {
      await expectLater(
        attempt(),
        throwsA(
          isA<BuiltinLinuxException>()
              .having(
                (error) => error.message,
                'plain words',
                'The phone server could not restart.',
              )
              .having(
                (error) => error.code,
                'bounded code',
                'recovery_unavailable',
              ),
        ),
      );
    }
  });
}
