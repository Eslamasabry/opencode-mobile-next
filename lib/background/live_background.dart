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
  Future<bool> showCodingAlert({
    required CodingAlertKind kind,
    required String sessionID,
    required String key,
  }) async {
    if (!enabled || !notificationGranted) return false;
    try {
      final result = await _invoke('showCodingAlert', {
        'kind': kind.wireValue,
        'sessionID': sessionID,
        'key': key,
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
