import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../integration_test/bb8_native_ready.dart';

void main() {
  const channel = MethodChannel('oc/bb8_qa');

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  testWidgets('first frame completes before readiness and auth work', (
    tester,
  ) async {
    final frame = Completer<void>();
    var frameCalls = 0;
    var readyCalls = 0;
    var authCalls = 0;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      readyCalls++;
      return true;
    });
    final work = showBb8FrameAndWaitForNativeReady(
      channel: channel,
      firstFrame: () {
        frameCalls++;
        return frame.future;
      },
    ).then((_) => authCalls++);
    await tester.pump();
    expect(frameCalls, 1);
    expect(readyCalls, 0);
    expect(authCalls, 0);
    frame.complete();
    await tester.pump();
    await work;
    expect(frameCalls, 1);
    expect(readyCalls, 1);
    expect(authCalls, 1);
  });

  testWidgets('auth work waits for native readiness', (tester) async {
    final ready = Completer<bool>();
    var authCalls = 0;
    var readyCalls = 0;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) {
      expect(call.method, 'ready');
      readyCalls++;
      return ready.future;
    });
    final work = waitForBb8NativeReady(
      channel: channel,
    ).then((_) => authCalls++);
    await tester.pump();
    expect(readyCalls, 1);
    expect(authCalls, 0);
    ready.complete(true);
    await tester.pump();
    await work;
    expect(authCalls, 1);
  });

  testWidgets('missing native handler retries before auth work', (
    tester,
  ) async {
    var readyCalls = 0;
    var authCalls = 0;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      readyCalls++;
      if (readyCalls < 3) throw MissingPluginException();
      return true;
    });
    final work = waitForBb8NativeReady(
      channel: channel,
    ).then((_) => authCalls++);
    await tester.pump();
    expect(readyCalls, 1);
    expect(authCalls, 0);
    await tester.pump(const Duration(milliseconds: 100));
    expect(readyCalls, 2);
    expect(authCalls, 0);
    await tester.pump(const Duration(milliseconds: 100));
    await work;
    expect(readyCalls, 3);
    expect(authCalls, 1);
  });

  testWidgets('unexpected native ready reply refuses auth work', (
    tester,
  ) async {
    var authCalls = 0;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (call) async => 'true',
    );
    await expectLater(
      waitForBb8NativeReady(channel: channel).then((_) => authCalls++),
      throwsA(isA<StateError>()),
    );
    expect(authCalls, 0);
  });

  testWidgets('unanswered readiness expires after ten seconds', (tester) async {
    final reply = Completer<bool>();
    var authCalls = 0;
    Object? failure;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (call) => reply.future,
    );
    final work = waitForBb8NativeReady(channel: channel)
        .then<void>((_) {
          authCalls++;
        })
        .catchError((Object error) {
          failure = error;
        });
    await tester.pump();
    await tester.pump(const Duration(seconds: 9));
    expect(failure, isNull);
    expect(authCalls, 0);
    await tester.pump(const Duration(seconds: 1));
    await work;
    expect(failure, isA<TimeoutException>());
    expect(authCalls, 0);
    // A late native reply cannot revive expired authentication work.
    reply.complete(true);
    await tester.pump();
    expect(authCalls, 0);
  });

  testWidgets('missing handler stops retrying at the readiness deadline', (
    tester,
  ) async {
    var readyCalls = 0;
    Object? failure;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      readyCalls++;
      throw MissingPluginException();
    });
    final work = waitForBb8NativeReady(channel: channel).catchError((
      Object error,
    ) {
      failure = error;
    });
    await tester.pump();
    expect(readyCalls, 1);
    await tester.pump(const Duration(seconds: 10));
    await work;
    expect(failure, isA<TimeoutException>());
    final callsAtDeadline = readyCalls;
    await tester.pump(const Duration(seconds: 1));
    expect(readyCalls, callsAtDeadline);
  });
}
