import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// The whitelisted static launcher shortcuts (`res/xml/shortcuts.xml`).
/// Wire values are the shortcut ids; anything else the native side or a
/// future build might send is dropped rather than mapped.
enum LaunchAction {
  connect('connect'),
  newTask('new_task');

  const LaunchAction(this.wireValue);

  /// The `oc.shortcut` extra / channel string for this action.
  final String wireValue;

  static LaunchAction? fromWire(Object? value) {
    if (value is! String) return null;
    final normalized = value.trim();
    for (final action in values) {
      if (action.wireValue == normalized) return action;
    }
    return null;
  }
}

/// A launcher shortcut tapped on the home screen. The Kotlin side captures
/// the `oc.shortcut` extra and hands the action id over here; [pending]
/// holds it until the app shell routes it. Mirrors ShareIntent: one
/// consume on start for the tap that launched the app, a live `launched`
/// push for taps while the engine is already running. This class never
/// connects, creates a session or sends anything; routing owns that.
/// Off Android the class is inert.
class LaunchShortcut {
  LaunchShortcut({@visibleForTesting MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('oc/shortcut');

  final MethodChannel _channel;

  /// The most recent shortcut action that nothing has consumed yet.
  final ValueNotifier<LaunchAction?> pending = ValueNotifier<LaunchAction?>(
    null,
  );

  bool _started = false;
  bool _disposed = false;
  int _acceptGeneration = 0;

  static bool get supported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Registers for shortcut taps delivered while the app is running and
  /// drains the tap that may have launched it. Safe to call once; later
  /// calls no-op.
  Future<void> start() async {
    if (_started || _disposed || !supported) return;
    _started = true;
    _channel.setMethodCallHandler((call) async {
      if (!_disposed && call.method == 'launched') _accept(call.arguments);
      return null;
    });
    final consumeGeneration = _acceptGeneration;
    try {
      final value = await _channel.invokeMethod<String>('consumeLaunchAction');
      // A live tap can arrive while the cold-start consume is in flight.
      // Keep that newer value instead of allowing the stale cold-start
      // value to replace it.
      if (!_disposed && consumeGeneration == _acceptGeneration) {
        _accept(value);
      }
    } on MissingPluginException {
      // Tests and desktop hosts have no channel implementation.
    } on PlatformException {
      // A capture failure must never block startup.
    }
  }

  void _accept(Object? value) {
    if (_disposed) return;
    final action = LaunchAction.fromWire(value);
    if (action == null) return;
    _acceptGeneration++;
    pending.value = action;
  }

  /// Takes the pending action, leaving nothing behind.
  LaunchAction? take() {
    if (_disposed) return null;
    final action = pending.value;
    pending.value = null;
    return action;
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    if (_started) _channel.setMethodCallHandler(null);
    pending.dispose();
  }
}
