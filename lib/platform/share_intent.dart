import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Text shared into the app from another Android app via the system share
/// sheet. The Kotlin side captures `ACTION_SEND` (text/plain) and hands the
/// text over here; [pending] holds it until a connected shell can turn it into
/// a new session. Off Android the class is inert.
class ShareIntent {
  ShareIntent({@visibleForTesting MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('oc/share');

  final MethodChannel _channel;

  /// The most recent shared text that nothing has consumed yet.
  final ValueNotifier<String?> pending = ValueNotifier<String?>(null);

  bool _started = false;

  static bool get supported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Registers for shares delivered while the app is running and drains the
  /// share that may have launched it. Safe to call once; later calls no-op.
  Future<void> start() async {
    if (_started || !supported) return;
    _started = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'shared') _accept(call.arguments);
      return null;
    });
    try {
      _accept(await _channel.invokeMethod<String>('consumeSharedText'));
    } on MissingPluginException {
      // Tests and desktop hosts have no channel implementation.
    } on PlatformException {
      // A capture failure must never block startup.
    }
  }

  void _accept(Object? value) {
    final text = value is String ? value.trim() : '';
    if (text.isEmpty) return;
    pending.value = text;
  }

  /// Takes the pending text, leaving nothing behind.
  String? take() {
    final text = pending.value;
    pending.value = null;
    return text;
  }

  void dispose() => pending.dispose();
}
