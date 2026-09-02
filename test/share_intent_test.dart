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
}
