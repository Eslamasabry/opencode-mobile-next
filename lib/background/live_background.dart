import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/agent_sign_in_foreground.dart';
import '../domain/background_pause.dart';
import '../domain/diagnostics_error.dart';
import '../platform/platform_capabilities.dart';
import 'notification_status.dart';
import 'widget_v2_contract.dart';

export '../domain/agent_sign_in_foreground.dart';

part 'signin_foreground.dart';

typedef BackgroundMethodInvoker =
    Future<Map<String, dynamic>> Function(
      String method, [
      Map<String, dynamic>? arguments,
    ]);

enum CodingAlertKind {
  permission('permission'),
  question('question'),
  complete('complete'),
  error('error'),
  quota('quota'),

  /// A plan limit came back after it was reached. Same session id and
  /// monitor token rules as [quota]. Must match the `"quota_reset"` branch in
  /// BackgroundConnectionService.kt.
  quotaReset('quota_reset'),

  /// A session has been observed busy past the profile's check-in rule.
  /// Must match the `"checkin"` branch in BackgroundConnectionService.kt.
  checkIn('checkin'),

  /// AI Team (TEAM-203, 06 decision 8): a new pending interaction. The
  /// alert's session id is the gate id; tapping opens that Gate sheet.
  /// Must match the `"team_decision"` branch in
  /// BackgroundConnectionService.kt.
  teamDecision('team_decision'),

  /// AI Team: a run failed. The session id is the failed-run gate id.
  teamRunFailed('team_run_failed'),

  /// AI Team: a run is ready for review. The session id is the review
  /// gate id.
  teamReview('team_review'),

  /// AI Team: a run completed. The session id is the run id; tapping
  /// opens the run.
  teamCompleted('team_completed'),

  /// AI Team: the one ongoing, silent progress line while tasks work. The
  /// session id is the run id a tap opens. Must match the
  /// `"team_progress"` branch in BackgroundConnectionService.kt.
  teamProgress('team_progress');

  const CodingAlertKind(this.wireValue);

  final String wireValue;

  /// One of the four AI Team kinds, whose session id is a gate or run id.
  bool get isTeam => switch (this) {
    teamDecision ||
    teamRunFailed ||
    teamReview ||
    teamCompleted ||
    teamProgress => true,
    permission ||
    question ||
    complete ||
    error ||
    quota ||
    quotaReset ||
    checkIn => false,
  };

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
    this.monitorToken = '',
    this.reply = false,
  });

  final CodingAlertKind kind;
  final String sessionID;

  /// The server profile stamped by widgets and all newly posted alerts.
  /// Monitor tokens additionally bind a persisted location and exact request.
  final String profileID;
  final String monitorToken;

  /// A home-screen widget's Reply: the chat opens with its composer focused.
  final bool reply;

  static CodingAlertOpen? fromPlatform(Map<String, dynamic> value) {
    final kind = CodingAlertKind.fromWireValue(value['kind']);
    final sessionID = value['sessionID']?.toString().trim() ?? '';
    if (kind == null || sessionID.isEmpty) return null;
    if (kind == CodingAlertKind.quota &&
        (sessionID != 'quota' ||
            (value['profileID']?.toString().trim().isEmpty ?? true) ||
            !RegExp(
              r'^[a-f0-9]{64}$',
            ).hasMatch(value['monitorToken']?.toString() ?? ''))) {
      return null;
    }
    return CodingAlertOpen(
      kind: kind,
      sessionID: sessionID,
      profileID: value['profileID']?.toString().trim() ?? '',
      monitorToken: value['monitorToken']?.toString().trim() ?? '',
      reply: value['reply']?.toString() == 'true',
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
    this.profileID = '',
    this.reply,
  });

  final CodingAlertKind kind;
  final String sessionID;

  /// The exact pending request this notification represented. Resolution is
  /// bound to this ID; an empty or stale ID never resolves a different
  /// request for the same session.
  final String requestID;
  final String profileID;

  /// 'allow' | 'deny' for permission alerts, 'reply' for question alerts.
  final String decision;

  /// The RemoteInput text for a 'reply' decision.
  final String? reply;

  static CodingAlertAction? fromPlatform(Object? arguments) {
    if (arguments is! Map) return null;
    final kind = CodingAlertKind.fromWireValue(arguments['kind']);
    final sessionID = arguments['sessionID']?.toString().trim() ?? '';
    final decision = arguments['decision']?.toString().trim() ?? '';
    if (kind == null ||
        (kind != CodingAlertKind.permission &&
            kind != CodingAlertKind.question) ||
        sessionID.isEmpty ||
        decision.isEmpty) {
      return null;
    }
    return CodingAlertAction(
      kind: kind,
      sessionID: sessionID,
      decision: decision,
      requestID: arguments['requestID']?.toString().trim() ?? '',
      profileID: arguments['profileID']?.toString().trim() ?? '',
      reply: arguments['reply']?.toString(),
    );
  }
}

/// What the ongoing "OpenCode is connected" notification should say right
/// now. Built by the connection controller from session truth and pushed to
/// Android through [BackgroundLiveController.publishLiveStatus].
///
/// Only counts and short, privacy-conscious sentences travel: the session
/// title the user chose to show and a generic tool sentence ("Editing
/// files…"), never prompts, commands, paths, or outputs.
@immutable
class LiveStatus {
  const LiveStatus({
    this.runningCount = 0,
    this.pendingCount = 0,
    this.title,
    this.detail,
    this.status,
  });

  /// What the one status notification says (title, text, lines, progress,
  /// actions). Null only where no wording was built, as in unit tests of
  /// the plain counts.
  final NotificationStatus? status;

  /// Sessions currently busy (running or retrying).
  final int runningCount;

  /// Requests waiting on the user: permissions, questions, and forms.
  final int pendingCount;

  /// Title of the most recently active busy session, or null when idle or
  /// untitled.
  final String? title;

  /// Short sentence for the tool running right now ("Running a command…"),
  /// or null when nothing is known.
  final String? detail;

  static const idle = LiveStatus();

  Map<String, dynamic> toPayload() => {
    'runningCount': runningCount,
    'pendingCount': pendingCount,
    'title': title,
    'detail': detail,
    if (status != null) 'status': status!.toPayload(),
  };

  @override
  bool operator ==(Object other) =>
      other is LiveStatus &&
      other.runningCount == runningCount &&
      other.pendingCount == pendingCount &&
      other.title == title &&
      other.detail == detail &&
      other.status == status;

  @override
  int get hashCode =>
      Object.hash(runningCount, pendingCount, title, detail, status);

  @override
  String toString() =>
      'LiveStatus(running: $runningCount, pending: $pendingCount, '
      'title: $title, detail: $detail)';
}

/// Owns the explicit, user-controlled Android foreground-service preference.
///
/// The service keeps the Flutter process important enough for a live OpenCode
/// transport to remain useful while the Activity is backgrounded. Android may
/// still enforce platform runtime limits, so every foreground transition also
/// performs a normal REST reconciliation.
class BackgroundLiveController extends ChangeNotifier
    implements AgentSignInForegroundPort {
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
  BackgroundPauseState _backgroundPause = BackgroundPauseState.unsupported;
  int _pauseRevision = 0;
  bool _foregroundDisposed = false;
  late final _SignInForegroundState _signInForeground = _SignInForegroundState(
    this,
  );

  BackgroundPauseState get backgroundPause => _backgroundPause;

  /// Set when Android's foreground-service time limit stopped the service,
  /// and cleared the next time the user turns live mode back on.
  ///
  /// Distinct from [lastError]: nothing failed and nothing can be retried
  /// right now — the daily budget is spent. The screens read it so the user
  /// learns live mode ended from the app rather than from missing events.
  bool stoppedByAndroidTimeout = false;

  /// Minimum spacing between two `updateLiveStatus` pushes: one every two
  /// seconds at most. Session events arrive in bursts (every streamed part),
  /// while the notification only needs to be roughly current; Android also
  /// rate-limits notify() calls.
  final Duration liveStatusDebounce;

  BackgroundLiveController({
    required this.preferences,
    BackgroundMethodInvoker? invoke,
    this.liveStatusDebounce = const Duration(seconds: 2),
  }) : enabled =
           platformCapabilities.supportsBackgroundService &&
           (preferences.getBool(preferenceKey) ?? false),
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

  @override
  AgentSignInForegroundLease reserveAgentSignInForeground() =>
      _signInForeground.reserve();

  Future<void> restore() async {
    if (_disableIfUnsupported()) return;
    final savedEnabled = enabled;
    final pause = await refreshBackgroundPause();
    // A saved opt-in cannot override a durable OS interruption. Unknown native
    // state also fails closed rather than starting a service we could not read.
    if (!pause.supported || pause.paused) return;
    await _run(savedEnabled ? 'enable' : 'getStatus', persist: false);
  }

  Future<BackgroundPauseState> refreshBackgroundPause() async {
    if (_disableIfUnsupported()) return backgroundPause;
    if (busy) return backgroundPause;
    final revision = ++_pauseRevision;
    try {
      final value = await _invoke('getBackgroundPause');
      if (_disableIfUnsupported()) return backgroundPause;
      if (revision != _pauseRevision) return backgroundPause;
      _applyPauseState(BackgroundPauseState.fromPlatform(value));
      if (backgroundPause.paused) {
        await preferences.setBool(preferenceKey, false);
      }
    } catch (_) {
      if (revision == _pauseRevision) {
        _applyPauseState(BackgroundPauseState.unsupported);
      }
    }
    notifyListeners();
    return backgroundPause;
  }

  /// Call only for a visible Resume tap. Android still owns runtime limits.
  Future<BackgroundResumeResult> resumeBackground() async {
    if (_disableIfUnsupported()) {
      return BackgroundResumeResult(
        backgroundPause,
        error: DiagnosticsError.unavailable,
      );
    }
    if (busy) {
      return BackgroundResumeResult(
        backgroundPause,
        error: DiagnosticsError.busy,
      );
    }
    busy = true;
    lastError = null;
    notifyListeners();
    final previousPause = backgroundPause;
    final revision = ++_pauseRevision;
    try {
      // The native permission/start path waits a bounded time for foreground
      // activation. A start request accepted by Android is not yet a success.
      final status = await _signInForeground.invokeEnable();
      if (_disableIfUnsupported()) {
        return BackgroundResumeResult(
          backgroundPause,
          error: DiagnosticsError.unavailable,
        );
      }
      if (revision != _pauseRevision) {
        return BackgroundResumeResult(
          backgroundPause,
          error: DiagnosticsError.resumeFailed,
        );
      }
      final state = BackgroundPauseState.fromPlatform(
        status['backgroundPause'],
      );
      if (!state.supported || !state.active) {
        _applyPauseState(previousPause);
        return BackgroundResumeResult(
          backgroundPause,
          error: DiagnosticsError.resumeFailed,
        );
      }
      _applyStatus(status);
      enabled = true;
      await preferences.setBool(preferenceKey, true);
      if (revision != _pauseRevision) {
        if (!enabled) {
          await preferences.setBool(preferenceKey, false);
        }
        return BackgroundResumeResult(
          backgroundPause,
          error: DiagnosticsError.resumeFailed,
        );
      }
      return BackgroundResumeResult(backgroundPause);
    } on PlatformException catch (error) {
      if (_disableIfUnsupported()) {
        return BackgroundResumeResult(
          backgroundPause,
          error: DiagnosticsError.unavailable,
        );
      }
      if (revision == _pauseRevision) _applyPauseState(previousPause);
      return BackgroundResumeResult(
        backgroundPause,
        error: error.code == 'permission_in_progress'
            ? DiagnosticsError.busy
            : DiagnosticsError.resumeFailed,
      );
    } catch (_) {
      if (_disableIfUnsupported()) {
        return BackgroundResumeResult(
          backgroundPause,
          error: DiagnosticsError.unavailable,
        );
      }
      if (revision == _pauseRevision) _applyPauseState(previousPause);
      return BackgroundResumeResult(
        backgroundPause,
        error: DiagnosticsError.resumeFailed,
      );
    } finally {
      busy = false;
      _disableIfUnsupported();
      notifyListeners();
    }
  }

  Future<bool> setEnabled(bool value) async {
    if (_disableIfUnsupported()) return false;
    if (!value) {
      _signInForeground.cancelForUser();
      if (busy) return _signInForeground.disableUserClaim();
    } else if (_signInForeground.hasClaims) {
      return _signInForeground.enableUserClaim();
    }
    if (busy || value && value == enabled && value == active) {
      return enabled;
    }
    final previous = enabled;
    enabled = value;
    notifyListeners();
    final revision = _pauseRevision + 1;
    final succeeded = await _run(value ? 'enable' : 'disable');
    if (_disableIfUnsupported()) return false;
    if (!succeeded && revision == _pauseRevision) {
      enabled = previous;
      await preferences.setBool(preferenceKey, previous);
      if (revision != _pauseRevision && !enabled) {
        await preferences.setBool(preferenceKey, false);
      }
      notifyListeners();
    }
    return enabled;
  }

  /// Actual Android network policy. Unsupported/unknown never counts as Wi-Fi.
  Future<bool?> monitorWifiAvailable() async {
    if (!platformCapabilities.supportsBackgroundService) return null;
    try {
      final value = await _invoke('monitorNetworkPolicy');
      return value['wifi'] is bool ? value['wifi'] as bool : null;
    } catch (_) {
      return null;
    }
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

  /// Opens this app's Android notification settings (P0.6), so a person the
  /// Notifications page tells is blocked can flip it back on without
  /// hunting through system settings. A no-op off Android or without the
  /// native side (tests, desktop).
  Future<void> openNotificationSettings() async {
    if (!platformCapabilities.supportsBackgroundService) return;
    try {
      await _invoke('openAppSettings');
    } on PlatformException {
      // Nothing to recover: the person is already looking at this screen.
    } on MissingPluginException {
      // No Android runner (tests, desktop).
    } catch (_) {
      // Never let a settings shortcut break the page around it.
    }
  }

  /// Shows a privacy-safe Android notification for a background coding event.
  ///
  /// The native side owns all user-visible copy so no prompt, tool input,
  /// filename, session title, or server error can leak onto the lock screen.
  /// [subtext] is the alert's one subtitle, user-chosen names only: the
  /// saved server and the project ("MacBook · api-server"). [chatTitle],
  /// [detail] (a masked command, a question's words, or the first line of a
  /// reply) and [askAction]/[askTarget] ("run" + "npm install") show in the
  /// shade only: Android's lock screen gets fixed public words. [reason] is
  /// a plain token ('auth', 'context', ...), never raw error text.
  /// [options] are a question's few short choices, listed under it.
  /// [silent] posts without sound (quiet hours).
  Future<bool> showCodingAlert({
    required CodingAlertKind kind,
    required String sessionID,
    required String key,
    String requestID = '',
    String profileID = '',
    String monitorToken = '',
    bool allowActions = true,
    String subtext = '',
    String title = '',
    String text = '',
    String agentName = '',
    String chatTitle = '',
    String detail = '',
    String reason = '',
    List<String> options = const [],
    bool silent = false,
    String askAction = '',
    String askTarget = '',
  }) async {
    if (!platformCapabilities.supportsNotifications) return false;
    if (!enabled || !notificationGranted) return false;
    try {
      final result = await _invoke('showCodingAlert', {
        'kind': kind.wireValue,
        if (agentName.isNotEmpty) 'agentName': agentName,
        'sessionID': sessionID,
        'key': key,
        'requestID': requestID,
        if (profileID.isNotEmpty) 'profileID': profileID,
        if (monitorToken.isNotEmpty) 'monitorToken': monitorToken,
        if (!allowActions) 'allowActions': false,
        if (subtext.isNotEmpty) 'subtext': subtext,
        if (title.isNotEmpty) 'title': title,
        if (text.isNotEmpty) 'text': text,
        if (chatTitle.isNotEmpty) 'chatTitle': chatTitle,
        if (detail.isNotEmpty) 'detail': detail,
        if (reason.isNotEmpty) 'reason': reason,
        if (options.isNotEmpty) 'options': options,
        if (silent) 'silent': true,
        if (askAction.isNotEmpty) 'askAction': askAction,
        if (askTarget.isNotEmpty) 'askTarget': askTarget,
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

  /// Posts one real "needs you" notification so a person can see for
  /// themselves whether Android is actually letting this app notify them
  /// (P0.6), independent of whether live background mode is on: unlike
  /// [showCodingAlert] this never checks [enabled], only that the platform
  /// can notify at all. The native side still refuses when the OS has
  /// notifications blocked, so a false result is itself the answer.
  Future<bool> sendTestNotification() async {
    if (!platformCapabilities.supportsNotifications) return false;
    try {
      final result = await _invoke('showCodingAlert', {
        // Posted on the requests channel, in words that say what it is
        // ("Notifications are on"), not as a question nobody asked. Must
        // match the `"test"` branch in BackgroundConnectionService.kt.
        'kind': 'test',
        'sessionID': testNotificationID,
        'key': testNotificationID,
        'allowActions': false,
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

  /// The fixed, non-secret id the test alert uses for its session and key;
  /// never a real session, so tapping it never opens a run.
  static const testNotificationID = 'oc-test-notification';

  Future<WidgetActionResult> Function(WidgetAction)? _widgetActionHandler;

  void bindWidgetActionHandler(
    Future<WidgetActionResult> Function(WidgetAction) handler,
  ) {
    _widgetActionHandler = handler;
  }

  @visibleForTesting
  Future<Map<String, Object?>> handleNativeWidgetAction(
    Object? arguments,
  ) async {
    final action = WidgetAction.fromPlatform(arguments);
    if (action == null) {
      return const {
        'id': '',
        'action': '',
        'status': 'rejected',
        'error': 'invalidRequest',
      };
    }
    final handler = _widgetActionHandler;
    if (handler == null) {
      return WidgetActionResult(
        action.id,
        action.action,
        'rejected',
        'unavailable',
      ).toJson();
    }
    try {
      return (await handler(action)).toJson();
    } catch (_) {
      return WidgetActionResult(
        action.id,
        action.action,
        'uncertain',
        'uncertainDelivery',
      ).toJson();
    }
  }

  Future<bool> Function(CodingAlertAction action)? _actionHandler;
  Future<bool> Function(String action, String sessionID)? _statusActionHandler;

  /// Registers the resolver for status-notification buttons ("Stop the
  /// reply"). The native side sends `statusAction` with the button's name and
  /// the chat it stops; an unhandled one opens the app instead.
  void bindStatusActionHandler(
    Future<bool> Function(String action, String sessionID) handler,
  ) {
    _statusActionHandler = handler;
  }

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
      if (call.method == statusActionMethod) {
        return handleNativeStatusAction(call.arguments);
      }
      if (call.method == 'widgetAction') {
        return handleNativeWidgetAction(call.arguments);
      }
      if (call.method != 'codingAlertAction') return null;
      return handleNativeAction(call.arguments);
    });
  }

  /// The push StatusActionReceiver sends. Must match `METHOD_STATUS_ACTION`
  /// in StatusNotification.kt.
  static const statusActionMethod = 'statusAction';

  /// Resolves one status-notification button; also the test entry point.
  @visibleForTesting
  Future<Map<String, dynamic>> handleNativeStatusAction(
    Object? arguments,
  ) async {
    final handler = _statusActionHandler;
    if (arguments is! Map || handler == null) return const {'handled': false};
    final action = arguments['action']?.toString().trim() ?? '';
    final sessionID = arguments['sessionID']?.toString().trim() ?? '';
    if (action.isEmpty) return const {'handled': false};
    try {
      return {'handled': await handler(action, sessionID)};
    } catch (_) {
      return const {'handled': false};
    }
  }

  /// The push BackgroundConnectionService.onTimeout sends. Must match
  /// `METHOD_TIMEOUT` in BackgroundConnectionService.kt.
  static const timeoutMethod = 'backgroundServiceTimeout';

  /// `reason` value the "Pause background" notification action sends on
  /// [timeoutMethod]. Must match `REASON_USER_PAUSE` in
  /// BackgroundConnectionService.kt.
  static const pauseReason = 'userPause';

  /// Applies the native "Android stopped the service" event.
  ///
  /// The persisted preference used to stay true over a dead service, so the
  /// switch read "on" while nothing was connected. Status is corrected the
  /// moment the event lands rather than at the next foreground poll — which
  /// is exactly when the user would have noticed anyway.
  @visibleForTesting
  void handleNativeTimeout([Object? arguments]) {
    if (_foregroundDisposed) return;
    // The same push carries the "Pause background" notification action
    // (reason `userPause`): the user asked, so nothing is owed a warning.
    final rawReason = arguments is Map ? arguments['reason'] : null;
    final reason = rawReason is String ? rawReason : null;
    _signInForeground.serviceStopped(
      AgentSignInForegroundFailure.paused,
      code: reason == pauseReason ? 'paused-by-you' : 'android-time-limit',
    );
    ++_pauseRevision;
    stoppedByAndroidTimeout = reason != pauseReason;
    final nativePause = arguments is Map ? arguments['backgroundPause'] : null;
    _backgroundPause = nativePause is Map
        ? BackgroundPauseState.fromPlatform(nativePause)
        : reason == pauseReason
        ? const BackgroundPauseState(supported: true)
        : BackgroundPauseState(
            supported: true,
            paused: true,
            reason: BackgroundPauseReason.timeLimit,
            at: DateTime.now().toUtc(),
            canResume: true,
          );
    enabled = false;
    active = false;
    _cancelPendingLiveStatus();
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

  /// Rebuilds native copy after the saved app language changes. This bypasses
  /// status equality suppression, and does not enable any background service.
  Future<void> refreshNativeLocale() async {
    if (!platformCapabilities.supportsBackgroundService) return;
    try {
      await _invoke('refreshNativeLocale');
    } catch (_) {
      // Locale saving still succeeds on desktop or without an Android runner.
      // Native renders read the saved preference again on the next update.
    }
  }

  LiveStatus? _lastPublishedLiveStatus;
  LiveStatus? _pendingLiveStatus;
  Timer? _liveStatusTimer;
  DateTime? _lastLivePublishAt;

  /// The status Android last received, for tests and diagnostics.
  LiveStatus? get lastPublishedLiveStatus => _lastPublishedLiveStatus;

  /// Refreshes the ongoing notification's content.
  ///
  /// A no-op unless live mode is on and the service is running; skipped when
  /// [status] equals what Android already shows; and spaced so at most one
  /// push leaves per [liveStatusDebounce] — the first goes at once, later
  /// ones coalesce into a single trailing push carrying the newest status.
  /// Never throws: a missing platform channel (tests, desktop) is silent.
  Future<void> publishLiveStatus(LiveStatus status) async {
    if (!platformCapabilities.supportsBackgroundService) return;
    if (!enabled || !active) return;
    if (_pendingLiveStatus == null && status == _lastPublishedLiveStatus) {
      return;
    }
    _pendingLiveStatus = status;
    if (_liveStatusTimer != null) return;
    final lastAt = _lastLivePublishAt;
    final elapsed = lastAt == null
        ? liveStatusDebounce
        : DateTime.now().difference(lastAt);
    if (elapsed >= liveStatusDebounce) {
      await _flushLiveStatus();
      return;
    }
    _liveStatusTimer = Timer(liveStatusDebounce - elapsed, () {
      _liveStatusTimer = null;
      unawaited(_flushLiveStatus());
    });
  }

  Future<void> _flushLiveStatus() async {
    final status = _pendingLiveStatus;
    _pendingLiveStatus = null;
    if (status == null || status == _lastPublishedLiveStatus) return;
    if (!enabled || !active) return;
    _lastLivePublishAt = DateTime.now();
    _lastPublishedLiveStatus = status;
    try {
      await _invoke('updateLiveStatus', status.toPayload());
    } on PlatformException {
      // The notification keeps its previous text; the next change retries.
    } on MissingPluginException {
      // No Android runner (tests, desktop): nothing to update.
    } catch (_) {
      // Never let a cosmetic push break session handling.
    }
  }

  void _cancelPendingLiveStatus() {
    _liveStatusTimer?.cancel();
    _liveStatusTimer = null;
    _pendingLiveStatus = null;
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

  @override
  void dispose() {
    if (_foregroundDisposed) return;
    _foregroundDisposed = true;
    _signInForeground.invalidate(
      AgentSignInForegroundFailure.cancelled,
      code: 'app-closed',
    );
    _cancelPendingLiveStatus();
    super.dispose();
  }

  @override
  void notifyListeners() {
    if (!_foregroundDisposed) super.notifyListeners();
  }

  bool _disableIfUnsupported() {
    if (_foregroundDisposed) return true;
    if (platformCapabilities.supportsBackgroundService) return false;
    // A saved Android opt-in is not evidence that this platform can stay live.
    // Clear runtime state (including a previous platform override's status),
    // but keep the preference: an unsupported platform is not a user opt-out.
    final changed =
        enabled ||
        active ||
        notificationGranted ||
        batteryOptimizationIgnored ||
        lastError != null ||
        backgroundPause.supported;
    _backgroundPause = BackgroundPauseState.unsupported;
    stoppedByAndroidTimeout = false;
    _applyStatus(const {'enabled': false});
    _cancelPendingLiveStatus();
    lastError = null;
    if (changed) notifyListeners();
    return true;
  }

  Future<bool> _run(String method, {bool persist = true}) async {
    // Reject before any platform call or preference write. Unsupported is not
    // an Android failure and must never leave enabled true, which would make
    // the connection controller skip lifecycle suspension.
    if (_disableIfUnsupported()) return false;
    if (busy) return false;
    busy = true;
    final revision = ++_pauseRevision;
    lastError = null;
    notifyListeners();
    try {
      final status = await (method == 'enable'
          ? _signInForeground.invokeEnable()
          : _invoke(method));
      if (_disableIfUnsupported()) return false;
      if (revision != _pauseRevision) return false;
      _applyStatus(status);
      if (method == 'disable') {
        _applyPauseState(const BackgroundPauseState(supported: true));
      }
      if (persist) await preferences.setBool(preferenceKey, enabled);
      return true;
    } on PlatformException catch (error) {
      if (revision != _pauseRevision) return false;
      lastError = error.code == 'notification_denied'
          ? 'Notification access is required.'
          : 'Android could not change background mode.';
      return false;
    } on MissingPluginException {
      if (revision != _pauseRevision) return false;
      lastError = 'Background live mode is available in the Android app.';
      return false;
    } catch (_) {
      if (revision != _pauseRevision) return false;
      lastError = 'Android could not change background mode.';
      return false;
    } finally {
      busy = false;
      _disableIfUnsupported();
      notifyListeners();
    }
  }

  void _applyStatus(Map<String, dynamic> status) {
    _setActive(status['active'] == true);
    notificationGranted = status['notificationGranted'] == true;
    batteryOptimizationIgnored = status['batteryOptimizationIgnored'] == true;
    if (status.containsKey('enabled') &&
        !_signInForeground.preservesUserClaim) {
      enabled = status['enabled'] == true;
    }
    if (status.containsKey('backgroundPause')) {
      _applyPauseState(
        BackgroundPauseState.fromPlatform(status['backgroundPause']),
      );
    } else if (active) {
      _backgroundPause = const BackgroundPauseState(
        supported: true,
        active: true,
      );
      stoppedByAndroidTimeout = false;
    }
  }

  void _setActive(bool value) {
    final wasActive = active;
    active = value;
    if (!active && wasActive) {
      // A status read found the service gone (Android stopped it).
      _signInForeground.serviceStopped(
        AgentSignInForegroundFailure.unavailable,
        code: 'service-gone',
      );
    }
    if (active != wasActive) {
      // A freshly started service shows the default copy until told
      // otherwise, so the next status must go through even if it equals the
      // last one the previous service instance received.
      _cancelPendingLiveStatus();
      _lastPublishedLiveStatus = null;
      _lastLivePublishAt = null;
    }
  }

  void _applyPauseState(BackgroundPauseState state) {
    _backgroundPause = state;
    _setActive(state.active);
    stoppedByAndroidTimeout =
        state.paused && state.reason == BackgroundPauseReason.timeLimit;
    if (!state.supported || state.paused) {
      _signInForeground.serviceStopped(
        state.supported
            ? AgentSignInForegroundFailure.paused
            : AgentSignInForegroundFailure.unsupported,
        code: state.supported
            ? 'paused-${state.reason.name}'
            : 'pause-unreadable',
      );
      enabled = false;
      _cancelPendingLiveStatus();
    }
  }
}
