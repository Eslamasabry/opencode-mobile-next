import 'package:flutter/services.dart';

import 'bridge.dart';

/// Why the app cannot use OpenCode in Termux right now: one typed cause,
/// read from what Android and Termux answered, so every place that shows
/// the phone's Termux server says the same thing and offers the one act
/// that fixes it (slice-termux-clarity, 2026-09-28).
enum TermuxProblem {
  /// Termux is not on this phone.
  notInstalled,

  /// The installed Termux is too old to take commands from other apps
  /// (no RunCommandService, or a version before the result protocol).
  outdated,

  /// Android has not given this app Termux's "Run commands" permission.
  /// After a reset of the app's storage this is the usual case: Android
  /// takes runtime permissions back with the data.
  accessNeeded,

  /// The person said "Don't allow" and Android no longer shows the
  /// question: only the app's settings page can grant it now.
  accessBlocked,

  /// Termux refuses commands from other apps: `allow-external-apps` is not
  /// `true` in `~/.termux/termux.properties`.
  otherAppsOff,

  /// Termux did not answer in time: Android stopped or froze it.
  asleep,

  /// Termux answers, and says OpenCode is set up and running, but OpenCode
  /// itself does not answer.
  notAnswering,

  /// Something else failed; trying again is the honest offer.
  unknown;

  /// Whether a plain "Try again" can help. For every other cause retrying
  /// changes nothing until the person does the fix.
  bool get retryHelps => this == TermuxProblem.unknown;

  /// Whether the cause keeps the app from running anything in Termux, so
  /// acts that need Termux (update, switch, add tools, storage) cannot
  /// work. OpenCode not answering still lets Termux run commands.
  bool get blocksTermux => this != TermuxProblem.notAnswering;
}

/// What Android answered to the access request.
enum TermuxAccessAnswer { granted, denied, permanentlyDenied, missing }

/// The cause behind a failed Termux command, from the bridge's code and
/// Termux's own message (never shown; the message is only classified).
TermuxProblem termuxProblemOfError(Object error) {
  final (code, message) = switch (error) {
    TermuxBridgeException(:final code, :final message) => (code, message),
    PlatformException(:final code, :final message) => (code, message ?? ''),
    _ => ('', error.toString()),
  };
  final lower = message.toLowerCase();
  if (lower.contains('allow-external-apps')) return TermuxProblem.otherAppsOff;
  return switch (code) {
    'permission_denied' => TermuxProblem.accessNeeded,
    'service_unavailable' => TermuxProblem.outdated,
    'termux_missing' => TermuxProblem.notInstalled,
    'command_timeout' || 'missing_result' => TermuxProblem.asleep,
    _ => TermuxProblem.unknown,
  };
}

/// The cause the capabilities alone show, or null when commands may run.
TermuxProblem? termuxProblemOfCapabilities(TermuxCapabilities capabilities) {
  if (!capabilities.platformSupported) return null;
  if (!capabilities.installed) return TermuxProblem.notInstalled;
  if (!capabilities.serviceAvailable || !capabilities.protocolSupported) {
    return TermuxProblem.outdated;
  }
  if (!capabilities.permissionGranted) {
    return TermuxAccess.blocked
        ? TermuxProblem.accessBlocked
        : TermuxProblem.accessNeeded;
  }
  return null;
}

/// Asking Android for Termux access, and remembering (for this run of the
/// app) that Android stopped asking.
abstract final class TermuxAccess {
  /// Android answered "permanently denied" in this run of the app. Android
  /// keeps that across restarts; the app learns it again on the next ask.
  static bool blocked = false;

  /// Shows Android's permission question for Termux's "Run commands". Its
  /// answer in words; a runner without the method answers with the older
  /// yes/no request.
  static Future<TermuxAccessAnswer> request() async {
    if (!TermuxBridge.supported) return TermuxAccessAnswer.missing;
    TermuxAccessAnswer answer;
    try {
      final raw = await TermuxBridge.requestRunCommandAccess();
      answer = switch (raw) {
        'granted' => TermuxAccessAnswer.granted,
        'permanentlyDenied' => TermuxAccessAnswer.permanentlyDenied,
        'missing' => TermuxAccessAnswer.missing,
        _ => TermuxAccessAnswer.denied,
      };
    } on MissingPluginException {
      try {
        answer = await TermuxBridge.requestPermission()
            ? TermuxAccessAnswer.granted
            : TermuxAccessAnswer.denied;
      } on MissingPluginException {
        answer = TermuxAccessAnswer.denied;
      } on PlatformException {
        // Exceptions in a compatibility catch do not reach the sibling
        // catch below. A concurrent/cancelled request still answers safely.
        answer = TermuxAccessAnswer.denied;
      }
    } on PlatformException {
      answer = TermuxAccessAnswer.denied;
    }
    blocked = answer == TermuxAccessAnswer.permanentlyDenied;
    return answer;
  }
}
