import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../platform/app_exit.dart';
import '../platform/native_crash.dart';
import '../ui/kit/kit_notice.dart' show KitReport;
import 'problem_report.dart';

/// What an opt-in crash report says about the crash (story E02-S91): only
/// facts the app already holds in a bounded, safe form. The exception type
/// and stack come from [NativeCrashRecord], which keeps Java symbols and a
/// fixed set of messages only; the device facts from [KeepAliveInfo], which
/// drops anything that is not plain letters and digits. Nothing here is sent
/// by itself: the person reviews it on Report a problem and files it there.
@immutable
class CrashOfferFacts {
  CrashOfferFacts({
    required DateTime at,
    this.exceptionType,
    this.exceptionMessage,
    List<String> stack = const [],
    this.exitReason,
    this.androidRelease = '',
    this.sdk = 0,
    this.manufacturer = '',
    this.model = '',
  }) : at = at.toUtc(),
       stack = List.unmodifiable(stack);

  /// When the crash happened; also the "already offered" mark.
  final DateTime at;

  /// "java.lang.IllegalStateException"; null when only Android's exit
  /// record says it crashed (a native crash or "not responding").
  final String? exceptionType;

  /// One of [NativeCrashRecord]'s fixed messages ("Invalid state").
  final String? exceptionMessage;

  /// The rendered stack lines, newest frame first, then any causes.
  final List<String> stack;

  /// Android's own words for the exit, when its record said crash.
  final String? exitReason;
  final String androidRelease;
  final int sdk;
  final String manufacturer;
  final String model;

  CrashOfferFacts withDevice(KeepAliveInfo info) => CrashOfferFacts(
    at: at,
    exceptionType: exceptionType,
    exceptionMessage: exceptionMessage,
    stack: stack,
    exitReason: exitReason,
    androidRelease: info.androidRelease,
    sdk: info.sdk,
    manufacturer: KeepAliveInfo.plainFact(info.manufacturer),
    model: info.model,
  );

  /// The crash in [report] that was not offered yet ([lastOfferedMillis]),
  /// or null: nothing after a normal exit, an update or a stop, nothing
  /// older than [CrashReportOffer.maxAge], and nothing already offered.
  ///
  /// The app's own JVM crash record and Android's exit record usually
  /// describe the same crash; the JVM record lends its type and stack only
  /// when it is that crash (within [_sameCrash]) or the only one.
  static CrashOfferFacts? fromLaunch(
    AppLaunchReport report, {
    required int lastOfferedMillis,
    required DateTime now,
  }) {
    bool fresh(DateTime at) =>
        at.millisecondsSinceEpoch > lastOfferedMillis &&
        !at.isAfter(now.add(const Duration(days: 1))) &&
        now.difference(at) <= CrashReportOffer.maxAge;
    final exit = switch (report.exit) {
      final exit?
          when exit.kind == AppExitKind.crash && fresh(exit.timestamp) =>
        exit,
      _ => null,
    };
    final jvm = switch (report.lastCrash) {
      final crash? when fresh(crash.timestamp) => crash,
      _ => null,
    };
    if (exit == null && jvm == null) return null;
    final paired =
        jvm != null &&
            (exit == null ||
                exit.timestamp.difference(jvm.timestamp).abs() <= _sameCrash)
        ? jvm
        : null;
    final at = [
      ?exit?.timestamp,
      ?jvm?.timestamp,
    ].reduce((a, b) => a.isAfter(b) ? a : b);
    return CrashOfferFacts(
      at: at,
      exceptionType: paired?.exceptionClass,
      exceptionMessage: paired?.message,
      stack: paired != null
          ? paired.diagnosticStack
                .split('\n')
                .where((line) => line.trim().isNotEmpty)
                .toList()
          : const [],
      exitReason: exit == null ? null : _exitWords(exit.reason),
    );
  }

  static const _sameCrash = Duration(minutes: 2);

  static String _exitWords(int reason) => switch (reason) {
    AndroidExitReason.crashNative => 'native crash',
    AndroidExitReason.anr => 'not responding (ANR)',
    AndroidExitReason.initializationFailure => 'failed to start',
    _ => 'crash',
  };
}

/// Room left in the GitHub link for what the person adds (their words, the
/// connection line) after the crash details are fitted.
const int crashReportWordsRoom = 600;

/// The crash as the failure Report a problem opens with: [title] is the
/// page's heading and the issue title (fixed app copy, never the exception
/// text); the type and the details only show in the page's Details, which
/// holds exactly what is sent. ProblemReport scrubs every part again with
/// the problem report's redaction.
///
/// The stack keeps as many lines as let the prefilled GitHub link stay
/// within [maxLength] (the app's link ceiling less [crashReportWordsRoom]);
/// the lines left out are counted in a note.
KitReport crashKitReport(
  CrashOfferFacts facts, {
  required String title,
  String version = 'unknown+0',
  String platform = 'Android',
  int maxLength = problemReportMaxLinkLength - crashReportWordsRoom,
  int maxStackLines = 12,
}) {
  KitReport build(int lines) => KitReport(
    title: title,
    // ProblemReport writes the type as given (it expects a runtimeType), so
    // it is scrubbed here like every other part.
    errorType: switch (facts.exceptionType) {
      final type? => problemReportScrub(type),
      null => null,
    },
    source: 'crash.last',
    details: _details(facts, lines),
  );
  for (var lines = facts.stack.length.clamp(0, maxStackLines); ; lines--) {
    final kit = build(lines);
    if (lines == 0) return kit;
    final report = ProblemReport.build(
      description: '',
      version: version,
      platform: platform,
      error: kit,
    );
    if (report.link(maxLength: maxLength).clipboard ==
        ProblemReportClipboard.none) {
      return kit;
    }
  }
}

String _details(CrashOfferFacts facts, int lines) {
  final android = [
    if (facts.androidRelease.isNotEmpty) facts.androidRelease,
    if (facts.sdk > 0) '(API ${facts.sdk})',
  ].join(' ');
  final device = [
    if (facts.manufacturer.isNotEmpty) facts.manufacturer,
    if (facts.model.isNotEmpty) facts.model,
  ].join(' ');
  final out = <String>[
    'Crashed: ${_utc(facts.at)}',
    if (facts.exitReason case final reason?) 'Android exit: $reason',
    if (facts.exceptionMessage case final message?) 'Message: $message',
    'Android: ${android.isEmpty ? 'unknown' : android}',
    'Device: ${device.isEmpty ? 'unknown' : device}',
  ];
  if (facts.stack.isEmpty) {
    out.add('Stack: not captured');
  } else {
    final kept = lines.clamp(0, facts.stack.length);
    final left = facts.stack.length - kept;
    out.add(
      left == 0
          ? 'Stack:'
          : 'Stack (first $kept of ${facts.stack.length} lines):',
    );
    out.addAll(facts.stack.take(kept));
    if (left > 0) {
      out.add(
        '… $left more ${left == 1 ? 'line' : 'lines'} left out to keep '
        'the GitHub link short',
      );
    }
  }
  return out.join('\n');
}

String _utc(DateTime time) {
  final t = time.toUtc();
  String two(int v) => v.toString().padLeft(2, '0');
  return '${t.year}-${two(t.month)}-${two(t.day)} '
      '${two(t.hour)}:${two(t.minute)}:${two(t.second)} UTC';
}

/// The one-time offer after a crash (E02-S91): read at start from the same
/// launch report [AppExitRecovery] reads, held until the person answers.
///
/// Each crash is offered at most once: its time is saved as seen before the
/// notice shows, so a dismissed, ignored or answered offer never returns,
/// and an offer turned off in Settings marks crashes seen without showing
/// anything (turning it on later does not raise an old one). It never sends
/// anything: "Review and send" opens Report a problem.
class CrashReportOffer extends ChangeNotifier {
  /// App-wide, not per server: a crash is the app's, not a profile's.
  static const enabledKey = 'oc.crashReports.offer';
  static const lastOfferedKey = 'oc.crashReports.lastOffered';

  /// A crash older than this is not offered (a record kept from before the
  /// offer existed, or a phone left unopened for long).
  static const maxAge = Duration(days: 7);

  /// The setting "Offer to send crash reports"; on unless turned off.
  static bool enabled(SharedPreferences prefs) =>
      prefs.getBool(enabledKey) ?? true;

  static Future<void> setEnabled(SharedPreferences prefs, bool value) =>
      prefs.setBool(enabledKey, value);

  bool _considered = false;
  bool _disposed = false;
  CrashOfferFacts? _pending;

  /// The crash to offer, until the person answers or dismisses it.
  CrashOfferFacts? get pending => _pending;

  /// Runs once per process; later calls do nothing. [device] is read only
  /// when there is something to offer.
  Future<void> consider(
    AppLaunchReport report,
    SharedPreferences prefs, {
    Future<KeepAliveInfo> Function()? device,
    DateTime? now,
  }) async {
    if (_considered) return;
    _considered = true;
    final crash = CrashOfferFacts.fromLaunch(
      report,
      lastOfferedMillis: prefs.getInt(lastOfferedKey) ?? 0,
      now: now ?? DateTime.now(),
    );
    if (crash == null) return;
    try {
      // Seen before shown: a crash whose mark cannot be saved is not
      // offered, so it can never be offered on every start.
      if (!await prefs.setInt(
        lastOfferedKey,
        crash.at.millisecondsSinceEpoch,
      )) {
        return;
      }
    } catch (_) {
      return;
    }
    if (!enabled(prefs)) return;
    var info = const KeepAliveInfo();
    if (device != null) {
      try {
        info = await device();
      } catch (_) {}
    }
    if (_disposed || !enabled(prefs)) return;
    _pending = crash.withDevice(info);
    notifyListeners();
  }

  void dismiss() {
    if (_pending == null) return;
    _pending = null;
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
