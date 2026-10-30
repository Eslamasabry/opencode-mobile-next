import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'platform_capabilities.dart';

/// The whitelisted launcher actions. `connect` and `newTask` are the static
/// shortcuts in `res/xml/shortcuts.xml`; `activity` is what the Quick
/// Settings tile sends when tapped; `phoneSetup` and `phoneSetupDone` are the
/// phone setup notifications (SetupService.kt): the running or stopped job,
/// and the finished one; `usage` and `usage_settings` are the Usage home-screen widget (its rows, and its empty state). Wire values are the action ids; anything else the
/// native side or a future build might send is dropped rather than mapped.
enum LaunchAction {
  connect('connect'),
  newTask('new_task'),
  activity('activity'),
  phoneSetup('phone_setup'),
  phoneSetupDone('phone_setup_done'),
  usage('usage'),
  usageSettings('usage_settings'),

  /// The Status widget's built-in Linux line: This phone's page.
  linux('linux'),

  /// Widget recovery opens the background connection setting without enabling it.
  backgroundSettings('background_settings'),

  /// A Usage widget row that needs a sign-in or a key. The wire value is
  /// `usage_target:<action>:<provider>:<profile>`, parsed by
  /// [UsageSignInTarget]; the enum value stands for "the target is waiting".
  usageSignIn('usage_target');

  const LaunchAction(this.wireValue);

  /// The `oc.shortcut` extra / channel string for this action.
  final String wireValue;

  static LaunchAction? fromWire(Object? value) {
    if (value is! String) return null;
    final normalized = value.trim();
    if (UsageSignInTarget.fromWire(normalized) != null) return usageSignIn;
    for (final action in values) {
      if (action.wireValue == normalized) return action;
    }
    return null;
  }
}

/// The sign-in a Usage widget row asks for: which server (saved profile id,
/// or `phone` for this phone's agents), which provider, and whether it is a
/// sign-in or a key. IDs and fixed words only; nothing else travels.
@immutable
class UsageSignInTarget {
  const UsageSignInTarget({
    required this.profileId,
    required this.providerId,
    required this.action,
  });

  static const prefix = 'usage_target:';
  static final _idPattern = RegExp(r'^[A-Za-z0-9._-]{1,64}$');
  static final _providerPattern = RegExp(r'^[a-z0-9][a-z0-9_-]{0,31}$');

  final String profileId;
  final String providerId;

  /// `sign_in`, `add_key` or `set_up` (read usage from a server).
  final String action;

  String get wire => '$prefix$action:$providerId:$profileId';

  static UsageSignInTarget? fromWire(Object? value) {
    if (value is! String || !value.startsWith(prefix)) return null;
    final parts = value.substring(prefix.length).split(':');
    if (parts.length != 3) return null;
    final action = parts[0];
    if (action != 'sign_in' && action != 'add_key' && action != 'set_up') {
      return null;
    }
    if (!_providerPattern.hasMatch(parts[1]) ||
        !_idPattern.hasMatch(parts[2])) {
      return null;
    }
    return UsageSignInTarget(
      profileId: parts[2],
      providerId: parts[1],
      action: action,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is UsageSignInTarget && other.wire == wire;

  @override
  int get hashCode => wire.hashCode;
}

/// A pinned-session launcher shortcut tapped on the home screen: the exact
/// session to open, addressed by IDs only. Nothing else travels with the
/// tap — no title, no prompt text — so the shortcut can never become an
/// automatic send.
@immutable
class SessionLaunch {
  const SessionLaunch({required this.profileID, required this.sessionID});

  /// The server profile the session belongs to. Routing drops a launch whose
  /// profile is not the active one instead of switching servers silently,
  /// mirroring the home-screen widget rows.
  final String profileID;
  final String sessionID;

  static SessionLaunch? fromWire(Object? value) {
    if (value is! Map) return null;
    final profileID = value['profileID']?.toString().trim() ?? '';
    final sessionID = value['sessionID']?.toString().trim() ?? '';
    if (profileID.isEmpty || sessionID.isEmpty) return null;
    return SessionLaunch(profileID: profileID, sessionID: sessionID);
  }

  @override
  bool operator ==(Object other) =>
      other is SessionLaunch &&
      other.profileID == profileID &&
      other.sessionID == sessionID;

  @override
  int get hashCode => Object.hash(profileID, sessionID);

  @override
  String toString() => 'SessionLaunch($profileID, $sessionID)';
}

/// A launcher shortcut tapped on the home screen. The Kotlin side captures
/// the `oc.shortcut` extra (or the `oc.shortcut.profile` /
/// `oc.shortcut.session` pair of a pinned-session shortcut) and hands it
/// over here; [pending] and [pendingSession] hold it until the app shell
/// routes it. Mirrors ShareIntent: one consume on start for the tap that
/// launched the app, a live `launched` / `launchedSession` push for taps
/// while the engine is already running. This class never connects, creates
/// a session or sends anything; routing owns that. Off Android the class is
/// inert.
class LaunchShortcut {
  LaunchShortcut({@visibleForTesting MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('oc/shortcut');

  final MethodChannel _channel;

  /// The most recent static shortcut action that nothing has consumed yet.
  final ValueNotifier<LaunchAction?> pending = ValueNotifier<LaunchAction?>(
    null,
  );

  /// The most recent pinned-session tap that nothing has consumed yet.
  final ValueNotifier<SessionLaunch?> pendingSession =
      ValueNotifier<SessionLaunch?>(null);

  /// The target of a pending [LaunchAction.usageSignIn], set just before
  /// [pending].
  UsageSignInTarget? usageTarget;

  bool _started = false;
  bool _disposed = false;
  int _acceptGeneration = 0;
  int _acceptSessionGeneration = 0;

  static bool get supported => platformCapabilities.supportsLaunchShortcuts;

  /// Registers for shortcut taps delivered while the app is running and
  /// drains the tap that may have launched it. Safe to call once; later
  /// calls no-op.
  Future<void> start() async {
    if (_started || _disposed || !supported) return;
    _started = true;
    _channel.setMethodCallHandler((call) async {
      if (_disposed) return null;
      switch (call.method) {
        case 'launched':
          _accept(call.arguments);
        case 'launchedSession':
          _acceptSession(call.arguments);
      }
      return null;
    });
    final consumeGeneration = _acceptGeneration;
    final consumeSessionGeneration = _acceptSessionGeneration;
    try {
      final value = await _channel.invokeMethod<String>('consumeLaunchAction');
      // A live tap can arrive while the cold-start consume is in flight.
      // Keep that newer value instead of allowing the stale cold-start
      // value to replace it.
      if (!_disposed && consumeGeneration == _acceptGeneration) {
        _accept(value);
      }
      final session = await _channel.invokeMethod<Map<Object?, Object?>>(
        'consumeSessionLaunch',
      );
      if (!_disposed && consumeSessionGeneration == _acceptSessionGeneration) {
        _acceptSession(session);
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
    if (action == LaunchAction.usageSignIn) {
      usageTarget = UsageSignInTarget.fromWire(value);
      // A repeat tap on the same row must still notify.
      pending.value = null;
    }
    pending.value = action;
  }

  void _acceptSession(Object? value) {
    if (_disposed) return;
    final launch = SessionLaunch.fromWire(value);
    if (launch == null) return;
    _acceptSessionGeneration++;
    pendingSession.value = launch;
  }

  /// Takes the pending action, leaving nothing behind.
  LaunchAction? take() {
    if (_disposed) return null;
    final action = pending.value;
    pending.value = null;
    return action;
  }

  /// Takes the sign-in target of a [LaunchAction.usageSignIn], once.
  UsageSignInTarget? takeUsageTarget() {
    final target = usageTarget;
    usageTarget = null;
    return target;
  }

  /// Takes the pending pinned-session tap, leaving nothing behind.
  SessionLaunch? takeSession() {
    if (_disposed) return null;
    final launch = pendingSession.value;
    pendingSession.value = null;
    return launch;
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    if (_started) _channel.setMethodCallHandler(null);
    pending.dispose();
    pendingSession.dispose();
  }
}
