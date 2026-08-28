import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
  const CodingAlertOpen({required this.kind, required this.sessionID});

  final CodingAlertKind kind;
  final String sessionID;

  static CodingAlertOpen? fromPlatform(Map<String, dynamic> value) {
    final kind = CodingAlertKind.fromWireValue(value['kind']);
    final sessionID = value['sessionID']?.toString().trim() ?? '';
    if (kind == null || sessionID.isEmpty) return null;
    return CodingAlertOpen(kind: kind, sessionID: sessionID);
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
    this.reply,
  });

  final CodingAlertKind kind;
  final String sessionID;

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
  }) async {
    if (!enabled || !notificationGranted) return false;
    try {
      final result = await _invoke('showCodingAlert', {
        'kind': kind.wireValue,
        'sessionID': sessionID,
        'key': key,
        'quickReply': quickReply,
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
      if (call.method != 'codingAlertAction') return null;
      return handleNativeAction(call.arguments);
    });
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
