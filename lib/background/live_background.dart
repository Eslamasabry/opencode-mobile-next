import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../platform/platform_capabilities.dart';

typedef BackgroundMethodInvoker =
    Future<Map<String, dynamic>> Function(
      String method, [
      Map<String, dynamic>? arguments,
    ]);

enum CodingAlertKind {
  permission('permission'),
  question('question'),
  complete('complete'),
  error('error');

  const CodingAlertKind(this.wireValue);

  final String wireValue;

  static CodingAlertKind? fromWireValue(Object? value) {
    for (final kind in values) {
      if (kind.wireValue == value) return kind;
    }
    return null;
  }
}

class CodingAlertOpen {
  const CodingAlertOpen({
    required this.kind,
    required this.sessionID,
    this.profileID = '',
  });

  final CodingAlertKind kind;
  final String sessionID;

  /// The server profile the session belongs to, stamped by home-screen
  /// widget taps so a stale row never routes into another profile's chat.
  /// Notification taps leave it empty.
  final String profileID;

  static CodingAlertOpen? fromPlatform(Map<String, dynamic> value) {
    final kind = CodingAlertKind.fromWireValue(value['kind']);
    final sessionID = value['sessionID']?.toString().trim() ?? '';
    if (kind == null || sessionID.isEmpty) return null;
    return CodingAlertOpen(
      kind: kind,
      sessionID: sessionID,
      profileID: value['profileID']?.toString().trim() ?? '',
    );
  }
}

/// One notification-action tap delivered by Android while the app stays
/// backgrounded: allow/deny for a permission alert, or a typed reply for a
/// question alert.
class CodingAlertAction {
  const CodingAlertAction({
    required this.kind,
    required this.sessionID,
    required this.decision,
    this.requestID = '',
    this.reply,
  });

  final CodingAlertKind kind;
  final String sessionID;

  /// The exact pending request this notification represented. Resolution is
  /// bound to this ID; an empty or stale ID never resolves a different
  /// request for the same session.
  final String requestID;

  /// 'allow' | 'deny' for permission alerts, 'reply' for question alerts.
  final String decision;

  /// The RemoteInput text for a 'reply' decision.
  final String? reply;

  static CodingAlertAction? fromPlatform(Object? arguments) {
    if (arguments is! Map) return null;
    final kind = CodingAlertKind.fromWireValue(arguments['kind']);
    final sessionID = arguments['sessionID']?.toString().trim() ?? '';
    final decision = arguments['decision']?.toString().trim() ?? '';
    if (kind == null || sessionID.isEmpty || decision.isEmpty) return null;
    return CodingAlertAction(
      kind: kind,
      sessionID: sessionID,
      decision: decision,
      requestID: arguments['requestID']?.toString().trim() ?? '',
      reply: arguments['reply']?.toString(),
    );
  }
}

/// Owns the explicit, user-controlled Android foreground-service preference.
///
/// The service keeps the Flutter process important enough for a live OpenCode
/// transport to remain useful while the Activity is backgrounded. Android may
/// still enforce platform runtime limits, so every foreground transition also
/// performs a normal REST reconciliation.
class BackgroundLiveController extends ChangeNotifier {
  static const preferenceKey = 'oc.keepLiveInBackground';
  static const _channel = MethodChannel('oc/background');

  final SharedPreferences preferences;
  final BackgroundMethodInvoker _invoke;

  bool enabled;
  bool active = false;
  bool notificationGranted = false;
  bool batteryOptimizationIgnored = false;
  bool busy = false;
  String? lastError;

  /// Set when Android's foreground-service time limit stopped the service,
  /// and cleared the next time the user turns live mode back on.
  ///
  /// Distinct from [lastError]: nothing failed and nothing can be retried
  /// right now — the daily budget is spent. The screens read it so the user
  /// learns live mode ended from the app rather than from missing events.
  bool stoppedByAndroidTimeout = false;

  BackgroundLiveController({
    required this.preferences,
    BackgroundMethodInvoker? invoke,
  }) : enabled = preferences.getBool(preferenceKey) ?? false,
       _invoke = invoke ?? _invokePlatform;

  static Future<Map<String, dynamic>> _invokePlatform(
    String method, [
    Map<String, dynamic>? arguments,
  ]) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      method,
      arguments,
    );
    return result ?? const {};
  }

  Future<void> restore() async {
    await _run(enabled ? 'enable' : 'getStatus', persist: false);
  }

  Future<bool> setEnabled(bool value) async {
    if (busy || value == enabled && (value == active || !value)) {
      return enabled;
    }
    final previous = enabled;
    enabled = value;
    // Turning it back on is the user answering the timeout notice; the
    // banner has served its purpose.
    if (value) stoppedByAndroidTimeout = false;
    notifyListeners();
    final succeeded = await _run(value ? 'enable' : 'disable');
    if (!succeeded) {
      enabled = previous;
      await preferences.setBool(preferenceKey, previous);
      notifyListeners();
    }
    return enabled;
  }

  Future<void> refreshStatus() async {
    await _run('getStatus', persist: false);
  }

  Future<bool> requestBatteryOptimizationExemption() async {
    final succeeded = await _run(
      'requestBatteryOptimizationExemption',
      persist: false,
    );
    return succeeded;
  }

  /// Shows a privacy-safe Android notification for a background coding event.
  ///
  /// The native side owns all user-visible copy so no prompt, tool input,
  /// filename, session title, or server error can leak onto the lock screen.
  /// [quickReply] is a capability bit only: it lets a question alert carry a
  /// RemoteInput action; it never carries request content.
  Future<bool> showCodingAlert({
    required CodingAlertKind kind,
    required String sessionID,
    required String key,
    bool quickReply = false,
    String requestID = '',
  }) async {
    if (!platformCapabilities.supportsNotifications) return false;
    if (!enabled || !notificationGranted) return false;
    try {
      final result = await _invoke('showCodingAlert', {
        'kind': kind.wireValue,
        'sessionID': sessionID,
        'key': key,
        'quickReply': quickReply,
        'requestID': requestID,
      });
      return result['shown'] == true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> Function(CodingAlertAction action)? _actionHandler;

  /// Registers the resolver for notification-action taps. The platform
  /// channel replies `{'handled': bool}`; an unhandled action makes Android
  /// re-post the alert (and, when no engine can handle it at all, fall back
  /// to opening the app at the request).
  void bindActionHandler(Future<bool> Function(CodingAlertAction) handler) {
    _actionHandler = handler;
    _channel.setMethodCallHandler((call) async {
      if (call.method == timeoutMethod) {
        handleNativeTimeout(call.arguments);
        return null;
      }
      if (call.method != 'codingAlertAction') return null;
      return handleNativeAction(call.arguments);
    });
  }

  /// The push BackgroundConnectionService.onTimeout sends. Must match
  /// `METHOD_TIMEOUT` in BackgroundConnectionService.kt.
  static const timeoutMethod = 'backgroundServiceTimeout';

  /// Applies the native "Android stopped the service" event.
  ///
  /// The persisted preference used to stay true over a dead service, so the
  /// switch read "on" while nothing was connected. Status is corrected the
  /// moment the event lands rather than at the next foreground poll — which
  /// is exactly when the user would have noticed anyway.
  @visibleForTesting
  void handleNativeTimeout([Object? arguments]) {
    stoppedByAndroidTimeout = true;
    enabled = false;
    active = false;
    unawaited(preferences.setBool(preferenceKey, false));
    notifyListeners();
  }

  /// Resolves one native action delivery; also the test entry point.
  @visibleForTesting
  Future<Map<String, dynamic>> handleNativeAction(Object? arguments) async {
    final action = CodingAlertAction.fromPlatform(arguments);
    final handler = _actionHandler;
    if (action == null || handler == null) return const {'handled': false};
    try {
      return {'handled': await handler(action)};
    } catch (_) {
      return const {'handled': false};
    }
  }

  /// Cancels a coding notification even if live mode has since been disabled.
  Future<bool> dismissCodingAlert(String key) async {
    if (!platformCapabilities.supportsNotifications) return false;
    try {
      final result = await _invoke('dismissCodingAlert', {'key': key});
      return result['dismissed'] == true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    } catch (_) {
      return false;
    }
  }

  /// Consumes one Android notification destination after a cold or warm open.
  Future<CodingAlertOpen?> consumeCodingAlertOpen() async {
    if (!platformCapabilities.supportsNotifications) return null;
    try {
      final result = await _invoke('consumeCodingAlertOpen');
      return CodingAlertOpen.fromPlatform(result);
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    } catch (_) {
      return null;
    }
  }

  Future<bool> _run(String method, {bool persist = true}) async {
    // The foreground service lives in the Android runner alone. Asking for it
    // elsewhere only produced a MissingPluginException and an error string
    // parked on a controller whose UI is already hidden; saying no up front
    // keeps `lastError` meaning "something went wrong", not "wrong OS".
    if (!platformCapabilities.supportsBackgroundService) return false;
    if (busy) return false;
    busy = true;
    lastError = null;
    notifyListeners();
    try {
      final status = await _invoke(method);
      _applyStatus(status);
      if (persist) await preferences.setBool(preferenceKey, enabled);
      return true;
    } on PlatformException catch (error) {
      lastError = error.message ?? 'Android could not change background mode.';
      return false;
    } on MissingPluginException {
      lastError = 'Background live mode is available in the Android app.';
      return false;
    } catch (error) {
      lastError = error.toString();
      return false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  void _applyStatus(Map<String, dynamic> status) {
    active = status['active'] == true;
    notificationGranted = status['notificationGranted'] == true;
    batteryOptimizationIgnored = status['batteryOptimizationIgnored'] == true;
    if (status.containsKey('enabled')) enabled = status['enabled'] == true;
  }
}
