import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Whether the app may read and change files in the phone's shared storage.
enum StorageAccess {
  /// "All files access" (or, on Android 10 and older, the storage
  /// permission) is on.
  granted,

  /// It is off: a folder in shared storage lists only its hidden entries.
  notGranted,

  /// This device has no such gate (not Android, or the answer is unknown).
  notNeeded,
}

/// Shared-storage file access (`oc/storage`, both halves: this file and
/// StorageAccess.kt). Asked for only when the person opens or creates a
/// project in shared storage; never at start-up, never for the app's own
/// project space.
class StorageAccessBridge {
  StorageAccessBridge._();

  static const _channel = MethodChannel('oc/storage');

  /// Widget tests answer without a platform.
  @visibleForTesting
  static Future<StorageAccess> Function()? statusOverride;

  @visibleForTesting
  static Future<void> Function()? openOverride;

  static bool get supported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// The current state. Read-only.
  static Future<StorageAccess> status() async {
    final override = statusOverride;
    if (override != null) return override();
    if (!supported) return StorageAccess.notNeeded;
    try {
      final raw = await _channel.invokeMethod<String>('status');
      return raw == 'notGranted'
          ? StorageAccess.notGranted
          : StorageAccess.granted;
    } on PlatformException {
      return StorageAccess.notNeeded;
    } on MissingPluginException {
      return StorageAccess.notNeeded;
    }
  }

  /// Opens Android's "All files access" page for this app (API 30+) or shows
  /// the storage permission dialog (API 29 and older), and returns once the
  /// person is back in the app. Read [status] afterwards.
  static Future<void> request() async {
    final override = openOverride;
    if (override != null) return override();
    if (!supported) return;
    // Watched before the page opens, so the pause that follows is not missed.
    final back = Completer<void>();
    final watcher = _ResumeWatcher(() {
      if (!back.isCompleted) back.complete();
    });
    WidgetsBinding.instance.addObserver(watcher);
    try {
      final how = await _channel.invokeMethod<String>('open');
      // The settings page is another screen: wait for the app to come back.
      if (how == 'settings') {
        await back.future.timeout(
          const Duration(minutes: 10),
          onTimeout: () {},
        );
      }
    } on PlatformException {
      return;
    } on MissingPluginException {
      return;
    } finally {
      WidgetsBinding.instance.removeObserver(watcher);
    }
  }
}

class _ResumeWatcher with WidgetsBindingObserver {
  _ResumeWatcher(this.onResumed);

  final VoidCallback onResumed;
  bool _left = false;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden) {
      _left = true;
    } else if (state == AppLifecycleState.resumed && _left) {
      onResumed();
    }
  }
}
