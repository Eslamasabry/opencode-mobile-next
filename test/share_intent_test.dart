import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/share_intent.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/share');

  setUp(() => debugDefaultTargetPlatformOverride = TargetPlatform.android);
  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('drains the share that launched the app', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'consumeSharedText') return '  https://x.y/z  ';
          return null;
        });
    final share = ShareIntent(channel: channel);
    await share.start();
    expect(share.pending.value, 'https://x.y/z');
    expect(share.take(), 'https://x.y/z');
    expect(share.pending.value, isNull);
  });

  test('accepts a live share while running', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async => null);
    final share = ShareIntent(channel: channel);
    await share.start();
    await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .handlePlatformMessage(
          channel.name,
          channel.codec.encodeMethodCall(
            const MethodCall('shared', 'stack trace here'),
          ),
          (_) {},
        );
    expect(share.pending.value, 'stack trace here');
  });

  test('ignores empty shares and missing implementations', () async {
    final share = ShareIntent(channel: channel);
    await share.start(); // no handler registered → MissingPluginException
    expect(share.pending.value, isNull);
    await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .handlePlatformMessage(
          channel.name,
          channel.codec.encodeMethodCall(const MethodCall('shared', '   ')),
          (_) {},
        );
    expect(share.pending.value, isNull);
  });

  test('is inert off Android', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    var called = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          called = true;
          return 'text';
        });
    final share = ShareIntent(channel: channel);
    await share.start();
    expect(called, isFalse);
    expect(share.pending.value, isNull);
  });

  test('a newer live share wins over a pending cold-start consume', () async {
    final consumed = Completer<String?>();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'consumeSharedText') return consumed.future;
          return null;
        });
    final share = ShareIntent(channel: channel);
    addTearDown(share.dispose);
    final start = share.start();
    await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .handlePlatformMessage(
          channel.name,
          channel.codec.encodeMethodCall(
            const MethodCall('shared', 'newer warm share'),
          ),
          (_) {},
        );
    consumed.complete('stale cold-start share');
    await start;

    expect(share.pending.value, 'newer warm share');
    expect(share.take(), 'newer warm share');
  });

  test(
    'a warm share taken before cold-start completion stays consumed',
    () async {
      final consumed = Completer<String?>();
      final consumeRequested = Completer<void>();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'consumeSharedText') {
              consumeRequested.complete();
              return consumed.future;
            }
            return null;
          });
      final share = ShareIntent(channel: channel);
      addTearDown(share.dispose);
      final start = share.start();
      await consumeRequested.future;
      await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .handlePlatformMessage(
            channel.name,
            channel.codec.encodeMethodCall(
              const MethodCall('shared', 'warm share'),
            ),
            (_) {},
          );
      expect(share.take(), 'warm share');

      consumed.complete('stale cold-start share');
      await start;

      expect(share.pending.value, isNull);
    },
  );

  test(
    'dispose while cold-start consume is pending ignores its completion',
    () async {
      final consumed = Completer<String?>();
      final consumeRequested = Completer<void>();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'consumeSharedText') {
              consumeRequested.complete();
              return consumed.future;
            }
            return null;
          });
      final share = ShareIntent(channel: channel);
      addTearDown(share.dispose);
      final start = share.start();
      await consumeRequested.future;
      var notifications = 0;
      share.pending.addListener(() => notifications++);
      share.dispose();

      consumed.complete('after dispose');
      await start;

      expect(notifications, 0);
      expect(share.take(), isNull);
    },
  );

  test(
    'disposing a never-started receiver preserves an active receiver',
    () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async => null);
      final active = ShareIntent(channel: channel);
      final neverStarted = ShareIntent(channel: channel);
      addTearDown(active.dispose);
      addTearDown(neverStarted.dispose);
      await active.start();
      neverStarted.dispose();

      await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .handlePlatformMessage(
            channel.name,
            channel.codec.encodeMethodCall(
              const MethodCall('shared', 'still active'),
            ),
            (_) {},
          );

      expect(active.pending.value, 'still active');
    },
  );

  test(
    'dispose unregisters the channel and blocks notifier callbacks',
    () async {
      final share = ShareIntent(channel: channel);
      addTearDown(share.dispose);
      await share.start();
      var notifications = 0;
      share.pending.addListener(() => notifications++);
      share.dispose();

      await TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .handlePlatformMessage(
            channel.name,
            channel.codec.encodeMethodCall(
              const MethodCall('shared', 'after dispose'),
            ),
            (_) {},
          );

      expect(notifications, 0);
      expect(share.take(), isNull);
    },
  );
}
