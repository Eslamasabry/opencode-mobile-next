import 'dart:convert';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../ui/kit/kit_redact.dart';
import '../ui/kit/kit_text.dart' show KitText, KitTextRole;
import '../ui/kit/kit_tokens.dart' show KitTokens;
import '../ui/theme_roles.dart' show ThemeRoles;

@immutable
class AppDiagnosticEntry {
  const AppDiagnosticEntry({
    required this.id,
    required this.timestamp,
    required this.source,
    required this.message,
    required this.stack,
    this.occurrences = 1,
  });

  final int id;
  final DateTime timestamp;
  final String source;
  final String message;
  final String stack;
  final int occurrences;

  AppDiagnosticEntry repeatedAt(DateTime value) => AppDiagnosticEntry(
    id: id,
    timestamp: value,
    source: source,
    message: message,
    stack: stack,
    occurrences: occurrences + 1,
  );

  Map<String, Object?> toJson() => {
    'time': timestamp.toUtc().toIso8601String(),
    'source': source,
    'message': message,
    if (stack.isNotEmpty) 'stack': stack,
    if (occurrences > 1) 'occurrences': occurrences,
  };
}

/// Process-local, privacy-bounded diagnostics for handled application errors.
///
/// Entries remain in memory unless a ReportProblemCapture is attached to
/// persist them. Nothing is uploaded automatically.
class AppDiagnosticsController extends ChangeNotifier {
  AppDiagnosticsController({
    this.maxEntries = 20,
    this.dedupeWindow = const Duration(seconds: 10),
  });

  final int maxEntries;
  final Duration dedupeWindow;
  final List<AppDiagnosticEntry> _entries = [];
  int _nextID = 1;

  List<AppDiagnosticEntry> get entries => List.unmodifiable(_entries);
  int get count => _entries.length;
  bool get isEmpty => _entries.isEmpty;

  void record(
    Object error,
    StackTrace? stack, {
    required String source,
    DateTime? at,
  }) {
    if (maxEntries <= 0) return;
    final timestamp = at ?? DateTime.now();
    final message = sanitize(error.toString(), limit: 2000).trim();
    final stackText = sanitize(stack?.toString() ?? '', limit: 12000).trim();
    final safeSource = sanitize(source, limit: 80).trim();
    final previous = _entries.isEmpty ? null : _entries.last;
    if (previous != null &&
        previous.source == safeSource &&
        previous.message == message &&
        previous.stack == stackText &&
        timestamp.difference(previous.timestamp).abs() <= dedupeWindow) {
      _entries[_entries.length - 1] = previous.repeatedAt(timestamp);
      notifyListeners();
      return;
    }
    _entries.add(
      AppDiagnosticEntry(
        id: _nextID++,
        timestamp: timestamp,
        source: safeSource.isEmpty ? 'app' : safeSource,
        message: message.isEmpty ? 'Unknown application error' : message,
        stack: stackText,
      ),
    );
    if (_entries.length > maxEntries) {
      _entries.removeRange(0, _entries.length - maxEntries);
    }
    notifyListeners();
  }

  void clear() {
    _entries.clear();
    // Persistence listeners must also erase a restored report when this
    // process has not recorded an error yet.
    notifyListeners();
  }

  String sanitize(String value, {int limit = 12000}) {
    var safe = KitRedact.text(value).replaceAllMapped(
      RegExp(r'''https?://[^\s<>"']+''', caseSensitive: false),
      (match) {
        final raw = match.group(0)!;
        try {
          final uri = Uri.parse(raw);
          return Uri(
            scheme: uri.scheme,
            host: uri.host,
            port: uri.hasPort ? uri.port : null,
            path: uri.path,
          ).toString();
        } catch (_) {
          return '[REDACTED URL]';
        }
      },
    );
    // Header syntax (`Authorization: Bearer …`) and the `KEY=VALUE` form an
    // MCP header uses (mcp_setup_screen.dart, P0.1) both carry a secret in
    // the same place.
    safe = safe.replaceAllMapped(
      RegExp(
        r'\b(authorization|proxy-authorization)\s*[:=]\s*[^\r\n]+',
        caseSensitive: false,
      ),
      (match) => '${match.group(1)}: [REDACTED]',
    );
    safe = safe.replaceAllMapped(
      RegExp(
        r'''("?(?:api[-_ ]?key|token|password|passwd|secret|access[-_ ]?token|refresh[-_ ]?token)"?\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s,;}]+)''',
        caseSensitive: false,
      ),
      (match) => '${match.group(1)}[REDACTED]',
    );
    // Long tokens carry digits; long letters-only words are code names
    // (ForegroundServiceDidNotStartInTimeException) a crash report needs.
    safe = safe.replaceAll(
      RegExp(r'\b(?=[A-Za-z0-9_+./=-]*[0-9])[A-Za-z0-9_+./=-]{32,}\b'),
      '[REDACTED]',
    );
    if (safe.length <= limit) return safe;
    return '${safe.substring(0, limit)}\n… [truncated]';
  }

  Map<String, Object?> reportJson() => {
    'app': 'opencode-mobile',
    'privacy': 'redacted-process-memory-only',
    'entryCount': _entries.length,
    'entries': [for (final entry in _entries) entry.toJson()],
  };

  String reportText() {
    const encoder = JsonEncoder.withIndent('  ');
    return encoder.convert(reportJson());
  }
}

class AppErrorCaptureHandle {
  AppErrorCaptureHandle._({
    required FlutterExceptionHandler? flutterHandler,
    required bool Function(Object, StackTrace)? platformHandler,
    required ErrorWidgetBuilder errorWidgetBuilder,
  }) : _flutterHandler = flutterHandler,
       _platformHandler = platformHandler,
       _errorWidgetBuilder = errorWidgetBuilder;

  final FlutterExceptionHandler? _flutterHandler;
  final bool Function(Object, StackTrace)? _platformHandler;
  final ErrorWidgetBuilder _errorWidgetBuilder;
  bool _restored = false;

  void restore() {
    if (_restored) return;
    _restored = true;
    FlutterError.onError = _flutterHandler;
    PlatformDispatcher.instance.onError = _platformHandler;
    ErrorWidget.builder = _errorWidgetBuilder;
  }
}

AppErrorCaptureHandle installAppErrorCapture(
  AppDiagnosticsController diagnostics,
) {
  final previousFlutter = FlutterError.onError;
  final previousPlatform = PlatformDispatcher.instance.onError;
  final previousBuilder = ErrorWidget.builder;

  FlutterError.onError = (details) {
    diagnostics.record(details.exception, details.stack, source: 'flutter');
    final safeDetails = FlutterErrorDetails(
      exception: diagnostics.sanitize(details.exceptionAsString()),
      stack: details.stack == null
          ? null
          : StackTrace.fromString(
              diagnostics.sanitize(details.stack.toString()),
            ),
      library: details.library == null
          ? null
          : diagnostics.sanitize(details.library!),
      context: details.context == null
          ? null
          : ErrorDescription(
              diagnostics.sanitize(details.context!.toDescription()),
            ),
      stackFilter: details.stackFilter == null
          ? null
          : (lines) => details.stackFilter!(lines).map(diagnostics.sanitize),
      informationCollector: details.informationCollector == null
          ? null
          : () => details.informationCollector!().map(
              (node) =>
                  ErrorDescription(diagnostics.sanitize(node.toDescription())),
            ),
      silent: details.silent,
    );
    final handler = previousFlutter;
    if (handler != null) {
      handler(safeDetails);
    } else {
      FlutterError.presentError(safeDetails);
    }
  };
  PlatformDispatcher.instance.onError = (error, stack) {
    diagnostics.record(error, stack, source: 'platform');
    final safeError = diagnostics.sanitize(error.toString());
    final safeStack = StackTrace.fromString(
      diagnostics.sanitize(stack.toString()),
    );
    // Also to the device log, so `adb logcat -s flutter` shows it.
    debugPrintSynchronously('Uncaught error: $safeError\n$safeStack');
    previousPlatform?.call(safeError, safeStack);
    return true;
  };
  ErrorWidget.builder = (details) {
    diagnostics.record(details.exception, details.stack, source: 'widget');
    // Drawn wherever the failed widget was, possibly above every theme and
    // MediaQuery: fixed colours and the body role's style, nothing that
    // looks anything up.
    return Directionality(
      textDirection: TextDirection.ltr,
      child: ColoredBox(
        color: ThemeRoles.bootErrorGround,
        child: Padding(
          padding: const EdgeInsets.all(KitTokens.panelPadding),
          child: Text(
            'This part of OpenCode hit an error. Open App diagnostics for details.',
            style: KitText.styleFor(
              KitTextRole.body,
            ).copyWith(color: ThemeRoles.bootErrorText),
          ),
        ),
      ),
    );
  };

  return AppErrorCaptureHandle._(
    flutterHandler: previousFlutter,
    platformHandler: previousPlatform,
    errorWidgetBuilder: previousBuilder,
  );
}
