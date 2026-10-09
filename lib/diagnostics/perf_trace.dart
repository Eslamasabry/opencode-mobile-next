import 'dart:async';
import 'dart:collection';
import 'dart:math' as math;

import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';

import '../ui/kit/kit_redact.dart';

/// Whether a finished span completed or threw.
enum PerfOutcome { ok, error }

/// One finished span (or an instant [isMark]) in the trace buffer.
@immutable
class PerfSpan {
  const PerfSpan({
    required this.id,
    required this.name,
    required this.startMicros,
    required this.durationMicros,
    required this.wallStart,
    this.parent,
    this.attrs = const {},
    this.outcome = PerfOutcome.ok,
    this.isMark = false,
  });

  final int id;
  final String name;

  /// Monotonic microseconds since the tracer's clock started (≈ main()).
  final int startMicros;
  final int durationMicros;
  final DateTime wallStart;

  /// The name of the span that was open around this one when it started.
  final String? parent;
  final Map<String, String> attrs;
  final PerfOutcome outcome;
  final bool isMark;

  double get durationMs => durationMicros / 1000;
  bool get failed => outcome == PerfOutcome.error;

  /// The device-log line: `OCTRACE 123ms name key=value … parent=name`.
  String toLogLine() {
    final out = StringBuffer(PerfTrace.logTag)..write(' ');
    if (isMark) {
      out.write('mark $name at=${formatMs(startMicros / 1000)}');
    } else {
      out.write('${formatMs(durationMs)} $name');
    }
    for (final entry in attrs.entries) {
      out.write(' ${entry.key}=${_logValue(entry.value)}');
    }
    if (failed) out.write(' outcome=error');
    if (parent != null) out.write(' parent=${_logValue(parent!)}');
    return out.toString();
  }

  static String _logValue(String value) =>
      value.contains(' ') ? '"${value.replaceAll('"', "'")}"' : value;
}

/// Integer milliseconds, with one decimal below 10 ms so fast work still
/// reads as something other than zero.
String formatMs(double ms) =>
    ms < 10 ? '${ms.toStringAsFixed(1)}ms' : '${ms.round()}ms';

/// A span that has started and not yet finished. Finishing twice is a no-op.
class PerfSpanHandle {
  PerfSpanHandle._({
    required this.id,
    required this.name,
    required this.startMicros,
    required this.wallStart,
    required this.parent,
    required this.logMinMicros,
    required Map<String, String> attrs,
  }) : _attrs = attrs;

  final int id;
  final String name;
  final int startMicros;
  final DateTime wallStart;
  final PerfSpanHandle? parent;
  final int logMinMicros;
  final Map<String, String> _attrs;
  bool _done = false;

  bool get isOpen => !_done;

  /// Adds or replaces an attribute before the span finishes.
  void set(String key, Object? value) {
    if (_done) return;
    PerfTrace._putAttr(_attrs, key, value);
  }

  void finish({Object? error, Map<String, Object?>? attrs}) {
    if (_done) return;
    _done = true;
    try {
      if (attrs != null) {
        for (final entry in attrs.entries) {
          PerfTrace._putAttr(_attrs, entry.key, entry.value);
        }
      }
      PerfTrace._add(
        PerfSpan(
          id: id,
          name: name,
          startMicros: startMicros,
          durationMicros: math.max(0, PerfTrace.nowMicros - startMicros),
          wallStart: wallStart,
          parent: parent?.name,
          attrs: Map.unmodifiable(_attrs),
          outcome: error == null ? PerfOutcome.ok : PerfOutcome.error,
        ),
        logMinMicros: logMinMicros,
      );
    } catch (_) {
      // Tracing must never break the work it measures.
    }
  }
}

/// Per-name statistics over the spans still in the buffer.
@immutable
class PerfStat {
  const PerfStat({
    required this.name,
    required this.count,
    required this.errors,
    required this.p50Ms,
    required this.p95Ms,
    required this.maxMs,
    required this.totalMs,
  });

  final String name;
  final int count;
  final int errors;
  final double p50Ms;
  final double p95Ms;
  final double maxMs;
  final double totalMs;
}

/// Tells the diagnostics screen that the buffer changed. Notifications are
/// coalesced into one microtask, and nothing is scheduled while nobody
/// listens, so an app that never opens the screen pays nothing for it.
class PerfTraceChanges extends ChangeNotifier {
  PerfTraceChanges._();

  bool _scheduled = false;

  void _changed() {
    if (_scheduled || !hasListeners) return;
    _scheduled = true;
    scheduleMicrotask(() {
      _scheduled = false;
      if (hasListeners) notifyListeners();
    });
  }
}

/// A tiny, always-on tracer: where did the time go, top to bottom.
///
/// Spans nest through [Zone]s, so a request made while "location.select" is
/// open records that as its parent without anything being passed around.
/// Everything lands in an in-memory ring buffer that the diagnostics screen
/// reads; an attached ReportProblemCapture can persist completed spans.
/// Nothing is uploaded, and each finished span is
/// also written to the device log as one `OCTRACE …` line, so
/// `adb logcat -s flutter | grep OCTRACE` works on a release build.
///
/// Attribute values are scrubbed on the way in: keys that name a secret and
/// values that look like tokens never reach the buffer or the log.
abstract final class PerfTrace {
  static const logTag = 'OCTRACE';

  /// Spans kept in memory; the oldest fall off first.
  static int capacity = 2000;

  /// Where log lines go. Null turns the device log off (the test suite does,
  /// so hundreds of tests do not fill their output with trace lines).
  static void Function(String line)? logSink = _defaultSink;

  static void _defaultSink(String line) => debugPrint(line);

  static final Stopwatch _clock = Stopwatch()..start();
  static final ListQueue<PerfSpan> _spans = ListQueue<PerfSpan>();
  static final Set<String> _onceMarks = <String>{};
  static int _nextId = 1;
  static const Symbol _zoneKey = #ocPerfTraceSpan;

  static final PerfTraceChanges changes = PerfTraceChanges._();

  static final _recorded = StreamController<PerfSpan>.broadcast(sync: true);

  /// Completed spans delivered synchronously for crash-durable diagnostic
  /// capture. Listeners must not start traces or throw; cancel when detached.
  /// Existing history remains available through [spans].
  static Stream<PerfSpan> get recorded => _recorded.stream;

  /// Monotonic microseconds since the tracer's clock started. The clock
  /// starts on first use, which main() makes the very first thing it does.
  static int get nowMicros => _clock.elapsedMicroseconds;

  /// The innermost span that is still open around the caller, if any. A
  /// span that already finished is skipped: an event stream started inside
  /// "connect" keeps that zone forever, and requests it triggers minutes
  /// later are not part of the connect.
  static PerfSpanHandle? get current {
    final span = Zone.current[_zoneKey];
    if (span is! PerfSpanHandle) return null;
    PerfSpanHandle? open = span;
    while (open != null && !open.isOpen) {
      open = open.parent;
    }
    return open;
  }

  /// Starts a span to be finished by hand; for work that has no single
  /// closure around it (an HTTP request between interceptor callbacks).
  /// A span shorter than [logMinMs] still goes to the buffer but is not
  /// logged, unless it fails.
  static PerfSpanHandle begin(
    String name, {
    Map<String, Object?> attrs = const {},
    int logMinMs = 0,
    PerfSpanHandle? parent,
  }) {
    final safe = <String, String>{};
    for (final entry in attrs.entries) {
      _putAttr(safe, entry.key, entry.value);
    }
    return PerfSpanHandle._(
      id: _nextId++,
      name: _safeName(name),
      startMicros: nowMicros,
      wallStart: DateTime.now(),
      parent: parent ?? current,
      logMinMicros: logMinMs * 1000,
      attrs: safe,
    );
  }

  /// Times [body]. Spans started inside it record this one as their parent.
  /// The body's result and error pass through untouched.
  static Future<T> span<T>(
    String name,
    Future<T> Function() body, {
    Map<String, Object?> attrs = const {},
    int logMinMs = 0,
  }) async {
    final handle = begin(name, attrs: attrs, logMinMs: logMinMs);
    try {
      final result = await runZoned(body, zoneValues: {_zoneKey: handle});
      handle.finish();
      return result;
    } catch (error) {
      handle.finish(error: error);
      rethrow;
    }
  }

  /// The synchronous [span].
  static T spanSync<T>(
    String name,
    T Function() body, {
    Map<String, Object?> attrs = const {},
    int logMinMs = 0,
  }) {
    final handle = begin(name, attrs: attrs, logMinMs: logMinMs);
    try {
      final result = runZoned(body, zoneValues: {_zoneKey: handle});
      handle.finish();
      return result;
    } catch (error) {
      handle.finish(error: error);
      rethrow;
    }
  }

  /// An instant: "this happened now". Always logged.
  static void mark(String name, {Map<String, Object?> attrs = const {}}) {
    try {
      final safe = <String, String>{};
      for (final entry in attrs.entries) {
        _putAttr(safe, entry.key, entry.value);
      }
      _add(
        PerfSpan(
          id: _nextId++,
          name: _safeName(name),
          startMicros: nowMicros,
          durationMicros: 0,
          wallStart: DateTime.now(),
          parent: current?.name,
          attrs: Map.unmodifiable(safe),
          isMark: true,
        ),
        logMinMicros: 0,
      );
    } catch (_) {}
  }

  /// [mark], once per process (first frame, first connected).
  static void markOnce(String name, {Map<String, Object?> attrs = const {}}) {
    if (!_onceMarks.add(name)) return;
    mark(name, attrs: attrs);
  }

  /// Records a span that started at [startMicros] (a [nowMicros] reading)
  /// and ends now; for durations measured across callbacks.
  static void recordSince(
    String name,
    int startMicros, {
    Map<String, Object?> attrs = const {},
    Object? error,
    int logMinMs = 0,
  }) {
    try {
      final elapsed = math.max(0, nowMicros - startMicros);
      _recordDuration(
        name,
        elapsed,
        startMicros: startMicros,
        attrs: attrs,
        error: error,
        logMinMs: logMinMs,
      );
    } catch (_) {}
  }

  /// Records a span measured elsewhere (the native setup runner's own
  /// timestamps), ending now.
  static void recordDuration(
    String name,
    Duration duration, {
    Map<String, Object?> attrs = const {},
    Object? error,
  }) {
    try {
      final micros = math.max(0, duration.inMicroseconds);
      _recordDuration(
        name,
        micros,
        startMicros: nowMicros - micros,
        attrs: attrs,
        error: error,
        logMinMs: 0,
      );
    } catch (_) {}
  }

  static void _recordDuration(
    String name,
    int micros, {
    required int startMicros,
    required Map<String, Object?> attrs,
    required Object? error,
    required int logMinMs,
  }) {
    final safe = <String, String>{};
    for (final entry in attrs.entries) {
      _putAttr(safe, entry.key, entry.value);
    }
    _add(
      PerfSpan(
        id: _nextId++,
        name: _safeName(name),
        startMicros: startMicros,
        durationMicros: micros,
        wallStart: DateTime.now().subtract(Duration(microseconds: micros)),
        parent: current?.name,
        attrs: Map.unmodifiable(safe),
        outcome: error == null ? PerfOutcome.ok : PerfOutcome.error,
      ),
      logMinMicros: logMinMs * 1000,
    );
  }

  static void _add(PerfSpan span, {required int logMinMicros}) {
    if (capacity <= 0) return;
    span = PerfSpan(
      id: span.id,
      name: KitRedact.text(span.name),
      startMicros: span.startMicros,
      durationMicros: span.durationMicros,
      wallStart: span.wallStart,
      parent: span.parent == null ? null : KitRedact.text(span.parent!),
      attrs: span.attrs,
      outcome: span.outcome,
      isMark: span.isMark,
    );
    _spans.addLast(span);
    while (_spans.length > capacity) {
      _spans.removeFirst();
    }
    final sink = logSink;
    if (sink != null &&
        (span.isMark || span.failed || span.durationMicros >= logMinMicros)) {
      try {
        sink(KitRedact.text(span.toLogLine()));
      } catch (_) {}
    }
    _recorded.add(span);
    changes._changed();
  }

  // ---- reading -------------------------------------------------------------

  /// Every span still in the buffer, oldest first.
  static List<PerfSpan> get spans => List.unmodifiable(_spans);

  /// The last [count] finished spans and marks, newest first.
  static List<PerfSpan> recent([int count = 50]) {
    final list = _spans.toList(growable: false);
    final start = math.max(0, list.length - count);
    return list.sublist(start).reversed.toList(growable: false);
  }

  /// Spans (not marks) grouped by name, slowest p95 first.
  static List<PerfStat> stats() {
    final byName = <String, List<PerfSpan>>{};
    for (final span in _spans) {
      if (span.isMark) continue;
      (byName[span.name] ??= <PerfSpan>[]).add(span);
    }
    final stats = <PerfStat>[
      for (final entry in byName.entries) _statFor(entry.key, entry.value),
    ];
    stats.sort((a, b) {
      final byP95 = b.p95Ms.compareTo(a.p95Ms);
      return byP95 != 0 ? byP95 : b.maxMs.compareTo(a.maxMs);
    });
    return stats;
  }

  static PerfStat _statFor(String name, List<PerfSpan> spans) {
    final durations = [for (final span in spans) span.durationMs]..sort();
    double percentile(double p) {
      // Nearest-rank: the smallest value with at least p of the samples at
      // or below it. Honest for the small counts a phone session produces.
      final rank = (p * durations.length).ceil().clamp(1, durations.length);
      return durations[rank - 1];
    }

    return PerfStat(
      name: name,
      count: durations.length,
      errors: spans.where((span) => span.failed).length,
      p50Ms: percentile(0.5),
      p95Ms: percentile(0.95),
      maxMs: durations.last,
      totalMs: durations.fold(0, (sum, value) => sum + value),
    );
  }

  static void clear() {
    _spans.clear();
    changes._changed();
  }

  /// Plain text, safe to share: names, durations and scrubbed attributes.
  /// No bodies, headers or query values were ever recorded.
  static String reportText({int slowest = 30, int recentCount = 50}) {
    final out = StringBuffer()
      ..writeln('OpenCode Mobile performance trace')
      ..writeln(
        'spans: ${_spans.length} · clock: '
        '${formatMs(nowMicros / 1000)} since start',
      )
      ..writeln()
      ..writeln('Slowest (by p95): name — count, p50, p95, max, errors');
    for (final stat in stats().take(slowest)) {
      out.writeln(
        '  ${stat.name} — ${stat.count}×, '
        'p50 ${formatMs(stat.p50Ms)}, p95 ${formatMs(stat.p95Ms)}, '
        'max ${formatMs(stat.maxMs)}'
        '${stat.errors > 0 ? ', ${stat.errors} failed' : ''}',
      );
    }
    out
      ..writeln()
      ..writeln('Recent (newest first):');
    for (final span in recent(recentCount)) {
      out.writeln('  ${span.toLogLine().substring(logTag.length + 1)}');
    }
    return KitRedact.text(out.toString());
  }

  // ---- scrubbing -----------------------------------------------------------

  static final _secretKey = RegExp(
    r'auth|token|password|passwd|secret|cookie|api[-_]?key|credential',
    caseSensitive: false,
  );
  static final _tokenLike = RegExp(r'[A-Za-z0-9_+/=.-]{32,}');

  /// A step's name as it may be recorded: names are written in code, but one
  /// that carried a registered secret, a credential pattern or a long opaque
  /// token (an id spliced into a path) must never reach a report or the log.
  static String _safeName(String name) =>
      KitRedact.opaqueTokens(KitRedact.text(name));

  static void _putAttr(Map<String, String> into, String key, Object? value) {
    if (value == null) return;
    if (_secretKey.hasMatch(key)) {
      into[scrub(key)] = '[redacted]';
      return;
    }
    into[scrub(key)] = scrub(value.toString());
  }

  /// A value as it may be recorded: single line, bounded, with anything that
  /// looks like a token or a credential in a URL replaced.
  static String scrub(String value, {int limit = 80}) {
    var safe = KitRedact.text(value).replaceAll(RegExp(r'\s+'), ' ').trim();
    safe = safe.replaceAll(RegExp(r'//[^/@\s]*@'), '//[redacted]@');
    safe = safe.replaceAll(_tokenLike, '[redacted]');
    if (safe.length > limit) safe = '${safe.substring(0, limit - 1)}…';
    return safe;
  }

  /// The route-shaped template of a request path: ids and other opaque
  /// segments become `:id`, the query is dropped. `/session/ses_4f…/message`
  /// becomes `/session/:id/message`, so every chat groups under one name.
  static String pathTemplate(String path) {
    var raw = path;
    final schemeEnd = raw.indexOf('://');
    if (schemeEnd != -1) {
      final pathStart = raw.indexOf('/', schemeEnd + 3);
      raw = pathStart == -1 ? '/' : raw.substring(pathStart);
    }
    final queryStart = raw.indexOf(RegExp('[?#]'));
    if (queryStart != -1) raw = raw.substring(0, queryStart);
    final segments = raw.split('/');
    final template = [
      for (final segment in segments)
        segment.isEmpty || !_opaqueSegment(segment) ? segment : ':id',
    ].join('/');
    return template.isEmpty ? '/' : template;
  }

  static final _prefixedId = RegExp(r'^[a-z]{2,5}_[A-Za-z0-9]{6,}$');
  static final _uuid = RegExp(
    r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
    caseSensitive: false,
  );
  static final _digits = RegExp(r'^\d+$');
  static final _hexish = RegExp(r'^[0-9a-f]{12,}$', caseSensitive: false);

  static bool _opaqueSegment(String segment) {
    if (_prefixedId.hasMatch(segment) ||
        _uuid.hasMatch(segment) ||
        _digits.hasMatch(segment) ||
        _hexish.hasMatch(segment)) {
      return true;
    }
    // Percent-encoded paths (a folder in the URL) and anything long and
    // mixed are data, not route words.
    if (segment.contains('%')) return true;
    if (segment.length >= 20 && RegExp(r'\d').hasMatch(segment)) return true;
    return false;
  }

  /// Test seam: forgets everything, including which once-marks fired.
  @visibleForTesting
  static void resetForTesting() {
    _spans.clear();
    _onceMarks.clear();
    capacity = 2000;
  }
}

/// Times every request on a Dio: one span per request named
/// `http <api> <METHOD> <path template>`, with status and response bytes.
/// Only requests of 50 ms or more (or failures) reach the device log; all of
/// them reach the buffer. No headers, bodies or query values are read.
class PerfTraceInterceptor extends Interceptor {
  PerfTraceInterceptor(this.api);

  /// Which server family the Dio talks to (oc1, oc2, gascity, …).
  final String api;

  static const _extraKey = 'ocPerfTraceSpan';
  static const logMinMs = 50;

  /// Adds one interceptor to [dio] unless it already has one.
  static void attach(Dio dio, String api) {
    try {
      if (dio.interceptors.any((i) => i is PerfTraceInterceptor)) return;
      dio.interceptors.add(PerfTraceInterceptor(api));
    } catch (_) {}
  }

  /// [attach], for initializer lists.
  static Dio traced(Dio dio, String api) {
    attach(dio, api);
    return dio;
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    try {
      options.extra[_extraKey] = PerfTrace.begin(
        // Dio upper-cases the method when it composes the request.
        'http $api ${options.method} '
        '${PerfTrace.pathTemplate(options.path)}',
        logMinMs: logMinMs,
      );
    } catch (_) {}
    handler.next(options);
  }

  @override
  void onResponse(
    Response<dynamic> response,
    ResponseInterceptorHandler handler,
  ) {
    _finish(response.requestOptions, response, null);
    handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    _finish(err.requestOptions, err.response, err);
    handler.next(err);
  }

  static void _finish(
    RequestOptions options,
    Response<dynamic>? response,
    DioException? error,
  ) {
    try {
      final span = options.extra.remove(_extraKey);
      if (span is! PerfSpanHandle) return;
      final bytes = response == null ? null : _responseBytes(response);
      span.finish(
        error: error,
        attrs: {
          'status': response?.statusCode,
          'bytes': bytes,
          if (response == null && error != null) 'cause': error.type.name,
        },
      );
    } catch (_) {}
  }

  /// Bytes on the wire when the server said (Content-Length), otherwise the
  /// size of a raw text or byte body. A decoded JSON body is not re-encoded
  /// just to be measured.
  static int? _responseBytes(Response<dynamic> response) {
    final header = response.headers.value(Headers.contentLengthHeader);
    final declared = header == null ? null : int.tryParse(header);
    if (declared != null && declared >= 0) return declared;
    final data = response.data;
    if (data is String) return data.length;
    if (data is List<int>) return data.length;
    return null;
  }
}

/// Marks route pushes and times each pushed route to its first frame, as a
/// span named `route <name>`. Unnamed routes (most pages here) are named
/// after the first screen-like widget inside them once it has built.
class PerfTraceNavigatorObserver extends NavigatorObserver {
  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    try {
      final start = PerfTrace.nowMicros;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        PerfTrace.recordSince('route ${routeName(route)}', start);
      });
    } catch (_) {}
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    if (newRoute != null) didPush(newRoute, oldRoute);
  }

  /// The route's own name, else the first widget in it whose type reads as
  /// a screen, else the route's type.
  static String routeName(Route<dynamic> route) {
    final named = route.settings.name;
    if (named != null && named.isNotEmpty) return named;
    if (route is ModalRoute) {
      final context = route.subtreeContext;
      if (context != null) {
        final screen = _findScreen(context);
        if (screen != null) return screen;
      }
    }
    return route.runtimeType.toString().split('<').first;
  }

  static String? _findScreen(BuildContext context) {
    String? found;
    var visited = 0;
    void visit(Element element) {
      if (found != null || visited > 400) return;
      visited++;
      final type = element.widget.runtimeType.toString();
      if (type.endsWith('Screen') ||
          type.endsWith('Page') ||
          type.endsWith('Sheet') ||
          type.endsWith('Dialog')) {
        found = type.split('<').first;
        return;
      }
      element.visitChildElements(visit);
    }

    try {
      context.visitChildElements(visit);
    } catch (_) {}
    return found;
  }
}

/// Follows one prompt from send to the end of its turn:
/// `prompt.accepted` (the server took it), `prompt.first_token` (the first
/// assistant output arrived on the event stream) and `prompt.turn` (the
/// session went idle). Keyed by session; at most a handful are tracked.
abstract final class PromptTrace {
  static final _pending = <String, _PromptTiming>{};
  static const _maxTracked = 16;

  /// Call as the prompt request goes out.
  static void sent(String sessionID) {
    try {
      if (_pending.length >= _maxTracked && !_pending.containsKey(sessionID)) {
        _pending.remove(_pending.keys.first);
      }
      _pending[sessionID] = _PromptTiming(PerfTrace.nowMicros);
      PerfTrace.mark('prompt.sent');
    } catch (_) {}
  }

  /// Wraps the send: [sent] before, [accepted] after (with the error when
  /// the server refused it). The send's result and error pass through.
  static Future<T> track<T>(String sessionID, Future<T> Function() send) async {
    sent(sessionID);
    try {
      final result = await send();
      accepted(sessionID);
      return result;
    } catch (error) {
      accepted(sessionID, error: error);
      rethrow;
    }
  }

  /// Call once the server accepted the prompt (or refused it: [error]).
  static void accepted(String sessionID, {Object? error}) {
    try {
      final timing = _pending[sessionID];
      if (timing == null) return;
      PerfTrace.recordSince('prompt.accepted', timing.start, error: error);
      if (error != null) _pending.remove(sessionID);
    } catch (_) {}
  }

  /// Feeds every live event through; cheap for the ones that do not matter.
  static void observe(String type, Map<String, dynamic> props) {
    if (_pending.isEmpty) return;
    try {
      switch (type) {
        case 'message.updated':
          final info = props['info'];
          if (info is Map && info['role'] == 'assistant') {
            final timing = _pending[info['sessionID']?.toString()];
            final id = info['id']?.toString();
            if (timing != null && id != null) timing.assistant.add(id);
          }
        case 'message.part.delta':
          _firstToken(props['sessionID']?.toString());
        case 'message.part.updated':
          final part = props['part'];
          if (part is! Map) return;
          final sid = part['sessionID']?.toString();
          final timing = _pending[sid];
          if (timing == null || timing.firstToken) return;
          final partType = part['type']?.toString();
          // The user's own text part echoes back first; only a part of an
          // assistant message (or a kind only assistants produce) counts.
          if (timing.assistant.contains(part['messageID']?.toString()) ||
              partType == 'step-start' ||
              partType == 'reasoning' ||
              partType == 'tool') {
            _firstToken(sid);
          }
        case 'session.status':
          final raw = props['status'];
          final status = raw is Map ? raw['type'] : raw;
          final sid = props['sessionID']?.toString();
          if (status == 'busy' || status == 'retry') {
            _pending[sid]?.busy = true;
          } else if (status == 'idle') {
            _done(sid);
          }
        case 'session.idle':
          _done(props['sessionID']?.toString());
        case 'session.error':
          _done(props['sessionID']?.toString(), error: 'session.error');
      }
    } catch (_) {}
  }

  static void _firstToken(String? sessionID) {
    final timing = _pending[sessionID];
    if (timing == null || timing.firstToken) return;
    timing.firstToken = true;
    PerfTrace.recordSince('prompt.first_token', timing.start);
  }

  static void _done(String? sessionID, {Object? error}) {
    final timing = _pending[sessionID];
    // An idle left over from the previous turn can arrive right after the
    // send; only an idle after this turn visibly started ends it.
    if (timing == null ||
        (error == null && !timing.busy && !timing.firstToken)) {
      return;
    }
    _pending.remove(sessionID);
    PerfTrace.recordSince('prompt.turn', timing.start, error: error);
  }

  @visibleForTesting
  static void resetForTesting() => _pending.clear();
}

class _PromptTiming {
  _PromptTiming(this.start);
  final int start;
  final Set<String> assistant = <String>{};
  bool firstToken = false;
  bool busy = false;
}
