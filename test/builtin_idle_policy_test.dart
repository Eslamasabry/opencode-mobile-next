import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';

Map<String, Object?> receipt() => {
  'installed': true,
  'phase': 'ready',
  'serverRunning': false,
  'serverRestartWanted': true,
  'serverIdlePolicySupported': true,
  'serverIdleEnabled': true,
  'serverIdleMinutes': 5,
  'serverIdleStopped': true,
  'serverIdleHelperStopped': false,
  'serverIdleGeneration': 9,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test.builtin_idle_policy');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late List<MethodCall> calls;
  Object? response;
  late BuiltinLinux linux;
  setUp(() {
    calls = [];
    response = receipt();
    linux = BuiltinLinux(channel: channel);
    debugPlatformCapabilities = const PlatformCapabilities.android();
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (response is Exception) throw response!;
      return response;
    });
  });
  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
    debugPlatformCapabilities = null;
  });

  test('idle policy carries authored booleans and minute bounds', () async {
    final state = await linux.setPhoneServerIdlePolicy(
      enabled: false,
      idleMinutes: 60,
    );
    expect(calls.single.method, 'setPhoneServerIdlePolicy');
    expect(calls.single.arguments, {'enabled': false, 'idleMinutes': 60});
    expect(state.serverIdleReceiptValid, isTrue);
    for (final minutes in [-1, 0, 61]) {
      await expectLater(
        () =>
            linux.setPhoneServerIdlePolicy(enabled: true, idleMinutes: minutes),
        throwsA(isA<BuiltinLinuxException>()),
      );
    }
    expect(calls, hasLength(1));
  });

  test(
    'resume and completion retain the exact owner generation and have no start fallback',
    () async {
      await linux.resumeIdleStoppedPhoneServer(
        profileId: 'phone_owner',
        expectedIdleGeneration: 9,
      );
      await linux.completePhoneServerIdleResume(
        profileId: 'phone_owner',
        expectedIdleGeneration: 9,
      );
      expect(calls.map((c) => c.method), [
        'resumeIdleStoppedPhoneServer',
        'completePhoneServerIdleResume',
      ]);
      expect(
        calls.every(
          (c) =>
              (c.arguments as Map)['profileId'] == 'phone_owner' &&
              (c.arguments as Map)['expectedIdleGeneration'] == 9,
        ),
        isTrue,
      );
    },
  );

  test(
    'unsafe owners and nonpositive generations never reach the native channel',
    () async {
      for (final id in ['', '../owner', 'owner\n', 'a' * 81]) {
        await expectLater(
          () => linux.resumeIdleStoppedPhoneServer(
            profileId: id,
            expectedIdleGeneration: 9,
          ),
          throwsA(isA<BuiltinLinuxException>()),
        );
        await expectLater(
          () => linux.completePhoneServerIdleResume(
            profileId: id,
            expectedIdleGeneration: 9,
          ),
          throwsA(isA<BuiltinLinuxException>()),
        );
      }
      for (final generation in [0, -1]) {
        await expectLater(
          () => linux.resumeIdleStoppedPhoneServer(
            profileId: 'owner',
            expectedIdleGeneration: generation,
          ),
          throwsA(isA<BuiltinLinuxException>()),
        );
      }
      expect(calls, isEmpty);
    },
  );

  test('supporting status requires every correctly typed admission field', () {
    final good = receipt();
    expect(BuiltinLinuxStatus.fromMap(good).serverIdleReceiptValid, isTrue);
    for (final field in [
      'serverIdleEnabled',
      'serverIdleStopped',
      'serverIdleHelperStopped',
      'serverRunning',
      'serverRestartWanted',
    ]) {
      for (final value in [null, 0, 'true']) {
        expect(
          BuiltinLinuxStatus.fromMap({
            ...good,
            field: value,
          }).serverIdleReceiptValid,
          isFalse,
        );
      }
    }
    for (final value in [null, -1, 9.0, '9', double.nan]) {
      expect(
        BuiltinLinuxStatus.fromMap({
          ...good,
          'serverIdleGeneration': value,
        }).serverIdleReceiptValid,
        isFalse,
      );
    }
    for (final value in [null, 0, 61, 5.0, '5']) {
      expect(
        BuiltinLinuxStatus.fromMap({
          ...good,
          'serverIdleMinutes': value,
        }).serverIdleReceiptValid,
        isFalse,
      );
    }
    expect(
      BuiltinLinuxStatus.fromMap({
        ...good,
        'serverIdleGeneration': 0,
      }).serverIdleReceiptValid,
      isFalse,
    );
    expect(
      BuiltinLinuxStatus.fromMap({
        ...good,
        'serverIdleStopped': false,
        'serverIdleGeneration': 0,
      }).serverIdleReceiptValid,
      isTrue,
    );
    expect(const BuiltinLinuxStatus.absent().serverIdleReceiptValid, isFalse);
    expect(BuiltinLinuxStatus.fromMap({}).serverIdlePolicySupported, isFalse);
  });

  test(
    'unknown or malformed native replies expose only the fixed recovery error',
    () async {
      for (final value in [
        null,
        {},
        {...receipt(), 'serverIdleGeneration': 9.0},
        PlatformException(code: 'private-code', message: 'private-detail'),
      ]) {
        response = value;
        await expectLater(
          () => linux.resumeIdleStoppedPhoneServer(
            profileId: 'owner',
            expectedIdleGeneration: 9,
          ),
          throwsA(
            isA<BuiltinLinuxException>()
                .having((e) => e.code, 'code', 'idle_resume_unavailable')
                .having(
                  (e) => e.message,
                  'message',
                  'The phone server could not start. Open setup or try Start again.',
                ),
          ),
        );
      }
      expect(
        calls.every((c) => c.method == 'resumeIdleStoppedPhoneServer'),
        isTrue,
      );
    },
  );

  test(
    'tri-state work heartbeat carries no work text and validates the readable alias',
    () async {
      response = null;
      for (final busy in <bool?>[true, false, null]) {
        await linux.observePhoneAgentWork(
          profileId: 'readable_alias',
          busy: busy,
        );
      }
      expect(calls.map((c) => c.arguments), [
        for (final busy in <bool?>[true, false, null])
          {'profileId': 'readable_alias', 'busy': busy},
      ]);
      expect(calls.every((c) => c.method == 'observePhoneAgentWork'), isTrue);
      await linux.observePhoneAgentWork(profileId: '../owner', busy: false);
      expect(calls, hasLength(3));
    },
  );

  test(
    'an old channel can refuse a heartbeat without authorizing or exposing output',
    () async {
      response = PlatformException(
        code: 'not_available',
        message: 'private-detail',
      );
      await linux.observePhoneAgentWork(profileId: 'owner', busy: false);
      expect(calls, hasLength(1));
    },
  );

  test(
    'unsupported platforms never dispatch idle start or heartbeat methods',
    () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      expect(
        (await linux.setPhoneServerIdlePolicy(
          enabled: true,
          idleMinutes: 1,
        )).serverIdlePolicySupported,
        isFalse,
      );
      expect(
        (await linux.resumeIdleStoppedPhoneServer(
          profileId: 'owner',
          expectedIdleGeneration: 9,
        )).serverIdlePolicySupported,
        isFalse,
      );
      await linux.observePhoneAgentWork(profileId: 'owner', busy: false);
      expect(calls, isEmpty);
    },
  );

  testWidgets(
    'a stalled native completion is bounded and has no manual fallback',
    (tester) async {
      final pending = Completer<Object?>();
      messenger.setMockMethodCallHandler(channel, (call) {
        calls.add(call);
        return pending.future;
      });
      Object? failure;
      final future = linux
          .completePhoneServerIdleResume(
            profileId: 'owner',
            expectedIdleGeneration: 9,
          )
          .then<void>(
            (_) {},
            onError: (Object error) {
              failure = error;
            },
          );
      await tester.pump(const Duration(seconds: 8));
      await future;
      expect(failure, isA<BuiltinLinuxException>());
      expect(calls.single.method, 'completePhoneServerIdleResume');
      pending.complete(receipt());
      await tester.pump();
    },
  );
}
