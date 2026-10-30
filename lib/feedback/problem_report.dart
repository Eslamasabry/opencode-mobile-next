import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../diagnostics/app_diagnostics.dart';
import '../diagnostics/report_problem.dart';
import '../platform/platform_capabilities.dart';
import '../ui/kit/kit_notice.dart' show KitReport;
import '../ui/kit/kit_redact.dart';

/// The one first-party destination for "something is broken": the GitHub
/// issue form of this app's repository. App-authored; it still opens through
/// `openExternalLink`, like every other link.
const String bugReportRepoUrl =
    'https://github.com/Eslamasabry/opencode-mobile-next';

/// The longest link the app hands to `openExternalLink` (its own ceiling).
const int problemReportMaxLinkLength = 2048;

/// Coarse, user-facing label for the platform a report is coming from. The
/// desktop rows carry the alpha/contributor framing so a Windows report
/// arrives already contexted: that target builds but is not hardware-tested.
String bugReportPlatformLabel() {
  final capabilities = platformCapabilities;
  if (capabilities.isWeb) return 'Web (unsupported)';
  return switch (capabilities.platform) {
    TargetPlatform.android => 'Android',
    TargetPlatform.windows =>
      'Windows desktop (experimental — contributor-tested)',
    TargetPlatform.linux => 'Linux desktop (alpha)',
    TargetPlatform.macOS => 'macOS (untested)',
    TargetPlatform.iOS => 'iOS (untested)',
    TargetPlatform.fuchsia => 'Fuchsia (untested)',
  };
}

/// "1.0.44+50", or "unknown+0" when the plugin fails or never answers: a
/// report must never hang on it, so it gives up after [timeout] (two
/// seconds). A caller that already shows "unknown+0" until the answer comes
/// passes a null [timeout] and waits without a timer.
Future<String> problemReportAppVersion({
  PackageInfo? info,
  Duration? timeout = const Duration(seconds: 2),
}) async {
  try {
    final package =
        info ??
        await switch (timeout) {
          null => PackageInfo.fromPlatform(),
          final limit => PackageInfo.fromPlatform().timeout(limit),
        };
    return '${package.version}+${package.buildNumber}';
  } on Object {
    return 'unknown+0';
  }
}

/// One thing the app noticed, for the page's list and the report: an error
/// or Android exit (both [isError]), a thermal change or a timing.
@immutable
class ProblemReportEvent {
  const ProblemReportEvent({
    required this.kind,
    required this.time,
    required this.source,
    required this.message,
    this.stack = '',
    this.occurrences = 1,
    this.id,
  });

  final ProblemEventKind kind;
  final DateTime time;
  final String source;
  final String message;
  final String stack;
  final int occurrences;

  /// The in-memory entry's id, when the event came from there.
  final int? id;

  bool get isError =>
      kind == ProblemEventKind.error || kind == ProblemEventKind.androidExit;
}

/// Newest first. The persisted store is preferred: it survives a crash and
/// a restart and already holds what the in-memory list captured. Without it
/// (it could not open, or tests), the in-memory errors of this run.
List<ProblemReportEvent> problemReportEvents({
  ReportProblem? store,
  AppDiagnosticsController? diagnostics,
}) {
  if (store != null) {
    return [
      for (final event in store.entries.reversed)
        ProblemReportEvent(
          kind: event.kind,
          time: event.timestamp,
          source: event.source,
          message: event.message,
          stack: event.stack,
        ),
    ];
  }
  return [
    for (final entry in (diagnostics?.entries ?? const []).reversed)
      ProblemReportEvent(
        kind: ProblemEventKind.error,
        time: entry.timestamp,
        source: entry.source,
        message: entry.message,
        stack: entry.stack,
        occurrences: entry.occurrences,
        id: entry.id,
      ),
  ];
}

/// Text safe for a public issue: [KitRedact] (provider keys, tokens,
/// passwords, registered secrets), then server addresses and long opaque
/// tokens the kit leaves alone. The report is public on GitHub once filed,
/// so a server's host name or address never goes in it.
String problemReportScrub(String text) {
  var safe = KitRedact.text(text);
  // scheme://host:port → scheme://[server] (the path stays: it says which
  // call failed). User-info is already masked by KitRedact.
  safe = safe.replaceAllMapped(
    RegExp(r'\b([a-zA-Z][a-zA-Z0-9+.-]*://)([^/\s?#"<>]+)'),
    (match) => '${match.group(1)}[server]',
  );
  safe = safe.replaceAll(
    RegExp(r'\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b'),
    '[address]',
  );
  // Opaque tokens (32+ letters/digits in one run, no path separators).
  safe = safe.replaceAll(RegExp(r'\b[A-Za-z0-9_-]{32,}\b'), KitRedact.mask);
  return safe;
}

String _clip(String text, int limit) {
  final runes = text.runes;
  if (runes.length <= limit) return text;
  return '${String.fromCharCodes(runes.take(limit))}…';
}

String _utc(DateTime time) {
  final t = time.toUtc();
  String two(int v) => v.toString().padLeft(2, '0');
  return '${t.year}-${two(t.month)}-${two(t.day)} '
      '${two(t.hour)}:${two(t.minute)}:${two(t.second)} UTC';
}

/// Where the diagnostics went when the GitHub link could not carry them.
enum ProblemReportClipboard {
  /// The link carries the whole report; nothing is copied.
  none,

  /// The link carries everything but the diagnostics, which are copied.
  diagnostics,

  /// Even the description was too long: the whole report is copied.
  whole,
}

/// The prefilled GitHub form and what, if anything, is copied first.
@immutable
class ProblemReportLink {
  const ProblemReportLink(this.uri, this.clipboard, this.clipboardText);

  final Uri uri;
  final ProblemReportClipboard clipboard;

  /// Copied before the link opens; null for [ProblemReportClipboard.none].
  final String? clipboardText;
}

/// Exactly what a report sends: [text] is what the preview shows and what
/// Copy and Share send; the GitHub form gets the same parts in its fields.
/// Every part is scrubbed by [problemReportScrub] when built.
@immutable
class ProblemReport {
  const ProblemReport._({
    required this.title,
    required this.whatHappened,
    required this.version,
    required this.diagnostics,
  });

  /// Builds the report. [events] are newest first; errors keep their
  /// stack's first lines, timings only the newest few. [connection] is the
  /// connection in use in words with no address or name ("OpenCode 2.0.28
  /// · on this phone · connected"); [fallbackTitle] titles a report with
  /// no attached error and no description (the newest error kept).
  factory ProblemReport.build({
    required String description,
    required String version,
    required String platform,
    KitReport? error,
    String? connection,
    String? fallbackTitle,
    List<ProblemReportEvent> events = const [],
    int maxErrors = 10,
    int maxTimings = 10,
    int maxStackLines = 8,
  }) {
    final said = problemReportScrub(description.trim());
    final errorTitle = error == null ? '' : problemReportScrub(error.title);
    final firstLine = said.split('\n').first.trim();
    final title = _clip(
      errorTitle.isNotEmpty
          ? errorTitle
          : firstLine.isNotEmpty
          ? firstLine
          : (fallbackTitle?.trim().isNotEmpty ?? false)
          ? problemReportScrub(fallbackTitle!.trim())
          : 'Problem report',
      80,
    );
    final what = StringBuffer(
      said.isEmpty ? '(not described)' : _clip(said, 4000),
    );
    if (error != null) {
      // The error's title is the report's title; the rest says what it was.
      final facts = [
        if (error.errorType case final type? when type.isNotEmpty)
          'Type: $type',
        if (error.source case final source? when source.isNotEmpty)
          'Where: ${problemReportScrub(source)}',
        if (error.details case final details? when details.trim().isNotEmpty)
          _clip(problemReportScrub(details.trim()), 2000),
      ];
      if (facts.isNotEmpty) what.write('\n\nError\n${facts.join('\n')}');
    }
    what
      ..write('\n\nEnvironment\n')
      ..write('App: $version\n')
      ..write('Platform: $platform');
    if (connection case final connection? when connection.trim().isNotEmpty) {
      what.write('\nConnection: ${problemReportScrub(connection.trim())}');
    }

    final lines = <String>[];
    // A failed job's log (P8.4) leads the diagnostics: it is part of the
    // attached failure, so the "recent diagnostics" choice does not drop it.
    if (error?.log case final log?) {
      final excerpt = problemReportScrub(log.trimRight());
      lines
        ..add(
          excerpt.isEmpty
              ? 'Failed job log: none was kept'
              : 'Failed job log (last lines)',
        )
        ..addAll([if (excerpt.isNotEmpty) excerpt, '']);
    }
    var errors = 0;
    var timings = 0;
    // A failed job's report ends with that job's own lines and the errors
    // around it: timings (OCTRACE) would bury its reason under status
    // polls, so they stay in the device log and on the diagnostics page.
    final withTimings = error?.log == null;
    for (final event in events) {
      if (event.kind == ProblemEventKind.timing) {
        if (!withTimings || timings++ >= maxTimings) continue;
      } else if (errors++ >= maxErrors) {
        continue;
      }
      final times = event.occurrences > 1 ? ' ×${event.occurrences}' : '';
      lines.add(
        '[${_utc(event.time)}] ${event.kind.name} · '
        '${problemReportScrub(event.source)}$times',
      );
      lines.add(_clip(problemReportScrub(event.message.trim()), 1000));
      final stack = event.stack.trim();
      if (stack.isNotEmpty && event.isError) {
        final frames = stack.split('\n');
        for (final frame in frames.take(maxStackLines)) {
          lines.add('    ${problemReportScrub(frame.trim())}');
        }
        if (frames.length > maxStackLines) {
          lines.add('    … ${frames.length - maxStackLines} more lines');
        }
      }
      lines.add('');
    }
    return ProblemReport._(
      title: title,
      whatHappened: what.toString(),
      version: version,
      diagnostics: lines.join('\n').trim(),
    );
  }

  /// The issue title.
  final String title;

  /// The description, the attached error and the environment: the form's
  /// "What happened" field.
  final String whatHappened;

  /// "1.0.44+50": the form's "App version" field.
  final String version;

  /// A failed job's log (when one is attached), then the recent events,
  /// newest first; empty when neither is included: the form's
  /// "Diagnostics or logs" field.
  final String diagnostics;

  /// The whole report, as previewed, copied and shared. A title taken from
  /// the description's first line is not written twice.
  String get text => [
    if (!whatHappened.startsWith(title)) title,
    whatHappened,
    if (diagnostics.isNotEmpty) 'Diagnostics\n$diagnostics',
  ].join('\n\n');

  static const _pasteDiagnostics =
      'The diagnostics were copied to the clipboard: paste them here.';
  static const _pasteWhole =
      'The whole report was copied to the clipboard: paste it here.';

  Uri _uri({required String what, required String logs}) =>
      Uri.parse('$bugReportRepoUrl/issues/new').replace(
        queryParameters: <String, String>{
          'template': 'bug_report.yml',
          'title': title,
          'app-version': version,
          'what-happened': what,
          if (logs.isNotEmpty) 'logs': logs,
        },
      );

  /// The prefilled form, as long as `openExternalLink` accepts: everything
  /// when it fits; otherwise the diagnostics (or, for a very long
  /// description, the whole report) go to the clipboard and the field says
  /// to paste them.
  ProblemReportLink link({int maxLength = problemReportMaxLinkLength}) {
    bool fits(Uri uri) => uri.toString().length <= maxLength;
    final full = _uri(what: whatHappened, logs: diagnostics);
    if (fits(full)) {
      return ProblemReportLink(full, ProblemReportClipboard.none, null);
    }
    if (diagnostics.isNotEmpty) {
      final noLogs = _uri(what: whatHappened, logs: _pasteDiagnostics);
      if (fits(noLogs)) {
        return ProblemReportLink(
          noLogs,
          ProblemReportClipboard.diagnostics,
          diagnostics,
        );
      }
    }
    return ProblemReportLink(
      _uri(what: _pasteWhole, logs: ''),
      ProblemReportClipboard.whole,
      text,
    );
  }
}
