import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/launch_shortcut.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/shortcut');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  Future<void> launched(Object? action) => messenger.handlePlatformMessage(
    channel.name,
    channel.codec.encodeMethodCall(MethodCall('launched', action)),
    (_) {},
  );

  setUp(() => debugDefaultTargetPlatformOverride = TargetPlatform.android);
  tearDown(() {
    debugDefaultTargetPlatformOverride = null;
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('drains the shortcut that launched the app, once', () async {
    var consumes = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'consumeLaunchAction') {
        consumes += 1;
        return 'new_task';
      }
      return null;
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    await shortcut.start();
    await shortcut.start();

    expect(consumes, 1);
    expect(shortcut.pending.value, LaunchAction.newTask);
    expect(shortcut.take(), LaunchAction.newTask);
    expect(shortcut.pending.value, isNull);
    expect(shortcut.take(), isNull);
  });

  test('maps the connect shortcut id from a cold start', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'consumeLaunchAction') return 'connect';
      return null;
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    await shortcut.start();
    expect(shortcut.pending.value, LaunchAction.connect);
  });

  test('accepts a live shortcut tap while running', () async {
    messenger.setMockMethodCallHandler(channel, (call) async => null);
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    await shortcut.start();
    expect(shortcut.pending.value, isNull);

    await launched('connect');
    expect(shortcut.pending.value, LaunchAction.connect);

    await launched('new_task');
    expect(shortcut.pending.value, LaunchAction.newTask);
  });

  test('ignores unknown actions, null and non-string payloads', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'consumeLaunchAction') return 'open_settings';
      return null;
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    var notifications = 0;
    shortcut.pending.addListener(() => notifications++);
    await shortcut.start();
    expect(shortcut.pending.value, isNull);

    await launched('bogus');
    await launched('');
    await launched(null);
    await launched(42);
    await launched(<String>['connect']);

    expect(shortcut.pending.value, isNull);
    expect(notifications, 0);
  });

  test('an unknown live action does not clear a valid pending one', () async {
    messenger.setMockMethodCallHandler(channel, (call) async => null);
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    await shortcut.start();
    await launched('new_task');
    await launched('bogus');
    expect(shortcut.pending.value, LaunchAction.newTask);
  });

  test('tolerates a missing native implementation', () async {
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    await shortcut.start(); // no handler registered → MissingPluginException
    expect(shortcut.pending.value, isNull);
  });

  test('tolerates a native capture failure', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'capture');
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    await shortcut.start();
    expect(shortcut.pending.value, isNull);
  });

  test('is inert off Android', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    var called = false;
    messenger.setMockMethodCallHandler(channel, (call) async {
      called = true;
      return 'connect';
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    expect(LaunchShortcut.supported, isFalse);
    await shortcut.start();
    expect(called, isFalse);
    expect(shortcut.pending.value, isNull);
  });

  test('a newer live tap wins over a stale cold-start consume', () async {
    final consumed = Completer<String?>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'consumeLaunchAction') return consumed.future;
      return null;
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    final start = shortcut.start();
    await launched('connect');
    consumed.complete('new_task');
    await start;

    expect(shortcut.pending.value, LaunchAction.connect);
    expect(shortcut.take(), LaunchAction.connect);
    expect(shortcut.pending.value, isNull);
  });

  test('a live tap taken before cold-start completion stays taken', () async {
    final consumed = Completer<String?>();
    final consumeRequested = Completer<void>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'consumeLaunchAction') {
        consumeRequested.complete();
        return consumed.future;
      }
      return null;
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    final start = shortcut.start();
    await consumeRequested.future;
    await launched('new_task');
    expect(shortcut.take(), LaunchAction.newTask);

    consumed.complete('connect');
    await start;

    expect(shortcut.pending.value, isNull);
  });

  test('dispose while the cold-start consume is pending ignores it', () async {
    final consumed = Completer<String?>();
    final consumeRequested = Completer<void>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'consumeLaunchAction') {
        consumeRequested.complete();
        return consumed.future;
      }
      return null;
    });
    final shortcut = LaunchShortcut(channel: channel);
    addTearDown(shortcut.dispose);
    final start = shortcut.start();
    await consumeRequested.future;
    var notifications = 0;
    shortcut.pending.addListener(() => notifications++);
    shortcut.dispose();

    consumed.complete('connect');
    await start;

    expect(notifications, 0);
    expect(shortcut.take(), isNull);
  });

  test('disposing a never-started receiver preserves an active one', () async {
    messenger.setMockMethodCallHandler(channel, (call) async => null);
    final active = LaunchShortcut(channel: channel);
    final neverStarted = LaunchShortcut(channel: channel);
    addTearDown(active.dispose);
    addTearDown(neverStarted.dispose);
    await active.start();
    neverStarted.dispose();

    await launched('new_task');

    expect(active.pending.value, LaunchAction.newTask);
  });

  test(
    'dispose unregisters the channel and blocks notifier callbacks',
    () async {
      final shortcut = LaunchShortcut(channel: channel);
      addTearDown(shortcut.dispose);
      await shortcut.start();
      var notifications = 0;
      shortcut.pending.addListener(() => notifications++);
      shortcut.dispose();

      await launched('connect');

      expect(notifications, 0);
      expect(shortcut.take(), isNull);
    },
  );
}
