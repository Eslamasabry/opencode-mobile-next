import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart' show CancelToken;

import 'events.dart';
import 'transport.dart';

/// Connection lifecycle surfaced to the UI.
enum Api2StreamStatus { connecting, connected, reconnecting, disconnected }

/// One parsed SSE frame. Unlike the v1 consumer, the `event:` field is
/// preserved — v2 needs it.
class Sse2Frame {
  final String? event;
  final String? id;
  final String data;
  const Sse2Frame({this.event, this.id, required this.data});
}

/// Incremental SSE parser: feed raw bytes, receive complete frames.
///
/// Handles CRLF/LF line endings, `: comment` keep-alives, multi-line `data:`
/// (joined with `\n`), and the `event:`/`id:` fields. A frame larger than
/// [maxFrameBytes] is dropped without killing the stream.
class Sse2Parser {
  final void Function(Sse2Frame frame) onFrame;
  final int maxFrameBytes;

  Sse2Parser(this.onFrame, {this.maxFrameBytes = 16 * 1024 * 1024});

  final List<int> _line = <int>[];
  final List<String> _data = <String>[];
  String? _event;
  String? _id;
  int _frameBytes = 0;
  bool _discarding = false;

  void add(List<int> chunk) {
    for (final byte in chunk) {
      if (byte == 10) {
        final lineBytes = List<int>.from(_line);
        _line.clear();
        _processLine(lineBytes);
        continue;
      }
      if (_line.length >= maxFrameBytes) {
        _line.clear();
        _discarding = true;
        continue;
      }
      _line.add(byte);
    }
  }

  /// Dispatches a trailing frame that was not followed by a blank line.
  void flush() {
    if (_line.isNotEmpty) {
      final lineBytes = List<int>.from(_line);
      _line.clear();
      _processLine(lineBytes);
    }
    _dispatch();
  }

  void _processLine(List<int> lineBytes) {
    if (lineBytes.isNotEmpty && lineBytes.last == 13) {
      lineBytes = lineBytes.sublist(0, lineBytes.length - 1);
    }
    if (lineBytes.isEmpty) {
      if (_discarding) {
        _discarding = false;
        _reset();
        return;
      }
      _dispatch();
      return;
    }
    if (_discarding) return;
    final line = utf8.decode(lineBytes, allowMalformed: true);
    if (line.startsWith(':')) return;
    final colon = line.indexOf(':');
    final field = colon == -1 ? line : line.substring(0, colon);
    var value = colon == -1 ? '' : line.substring(colon + 1);
    if (value.startsWith(' ')) value = value.substring(1);
    switch (field) {
      case 'data':
        _frameBytes += value.length;
        if (_frameBytes > maxFrameBytes) {
          _discarding = true;
          _reset();
          return;
        }
        _data.add(value);
      case 'event':
        _event = value;
      case 'id':
        _id = value;
    }
  }

  void _dispatch() {
    if (_data.isEmpty && _event == null && _id == null) return;
    final frame = Sse2Frame(event: _event, id: _id, data: _data.join('\n'));
    _reset();
    if (frame.data.isNotEmpty) onFrame(frame);
  }

  void _reset() {
    _data.clear();
    _event = null;
    _id = null;
    _frameBytes = 0;
  }
}

/// `GET /api/event` consumer with automatic reconnect + exponential backoff.
///
/// The stream is volatile: events during a disconnect are lost. Callers must
/// reconcile (refetch sessions/messages/pending lists) whenever [onStatus]
/// reports a reconnect — signalled by `server.connected` arriving again.
class Api2EventStream {
  final Api2Transport transport;
  final void Function(Api2EventEnvelope event, Sse2Frame frame) onEvent;
  final void Function(Api2StreamStatus status) onStatus;
  final void Function(Object error)? onError;
  final Map<String, dynamic> Function()? queryBuilder;
  final String path;

  Api2EventStream({
    required this.transport,
    required this.onEvent,
    required this.onStatus,
    this.onError,
    this.queryBuilder,
    this.path = '/event',
  });

  static const _giveUpVisualAfter = 6;
  static const _backoffResetAfter = Duration(seconds: 30);

  CancelToken? _cancelToken;
  StreamSubscription<Uint8List>? _subscription;
  Completer<void>? _streamDone;
  Timer? _retryTimer;
  bool _running = false;
  bool _disposed = false;
  int _attempt = 0;
  int _generation = 0;

  void start() {
    assert(!_disposed);
    if (_disposed) return;
    _connect();
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _generation += 1;
    _retryTimer?.cancel();
    _retryTimer = null;
    _cancelToken?.cancel('Event stream disposed');
    _cancelToken = null;
    final subscription = _subscription;
    _subscription = null;
    await subscription?.cancel();
    final streamDone = _streamDone;
    _streamDone = null;
    if (streamDone != null && !streamDone.isCompleted) streamDone.complete();
  }

  Map<String, dynamic>? buildQuery() => queryBuilder?.call();

  void handleEvent(Api2EventEnvelope envelope, Sse2Frame frame) =>
      onEvent(envelope, frame);

  void _connect() {
    if (_disposed || _running) return;
    _retryTimer?.cancel();
    _retryTimer = null;
    _running = true;
    final generation = ++_generation;
    onStatus(
      _attempt == 0
          ? Api2StreamStatus.connecting
          : Api2StreamStatus.reconnecting,
    );
    unawaited(
      _pump(generation).whenComplete(() {
        if (!_isCurrent(generation)) return;
        _running = false;
        _attempt += 1;
        final ms = (500 * (1 << _attempt.clamp(0, 5))).clamp(500, 16000);
        onStatus(
          _attempt >= _giveUpVisualAfter
              ? Api2StreamStatus.disconnected
              : Api2StreamStatus.reconnecting,
        );
        _retryTimer = Timer(Duration(milliseconds: ms), () {
          _retryTimer = null;
          if (_isCurrent(generation)) _connect();
        });
      }),
    );
  }

  Future<void> _pump(int generation) async {
    Timer? backoffResetTimer;
    CancelToken? requestCancelToken;
    StreamSubscription<Uint8List>? subscription;
    var subscriptionTerminated = false;
    try {
      final cancelToken = CancelToken();
      requestCancelToken = cancelToken;
      _cancelToken = cancelToken;
      final response = await transport.openStream(
        path,
        query: buildQuery(),
        cancelToken: cancelToken,
      );
      if (!_isCurrent(generation)) return;
      onStatus(Api2StreamStatus.connected);
      backoffResetTimer = Timer(_backoffResetAfter, () {
        if (_isCurrent(generation)) _attempt = 0;
      });

      final done = Completer<void>();
      _streamDone = done;

      void finish([Object? error, StackTrace? stackTrace]) {
        if (done.isCompleted) return;
        if (error == null) {
          done.complete();
        } else {
          done.completeError(error, stackTrace);
        }
      }

      final parser = Sse2Parser((frame) {
        if (!_isCurrent(generation)) return;
        try {
          final json = jsonDecode(frame.data);
          if (json is Map<String, dynamic>) {
            final envelope = Api2EventEnvelope.fromJson(json);
            if (envelope.type.isNotEmpty) handleEvent(envelope, frame);
          }
        } catch (_) {
          // Malformed frame - skip it rather than killing the stream.
        }
      });

      subscription = response.data!.stream.cast<Uint8List>().listen(
        (chunk) {
          if (!_isCurrent(generation)) return;
          parser.add(chunk);
        },
        onError: (Object error, StackTrace stackTrace) {
          subscriptionTerminated = true;
          finish(error, stackTrace);
        },
        onDone: () {
          subscriptionTerminated = true;
          parser.flush();
          finish();
        },
        cancelOnError: true,
      );
      _subscription = subscription;
      await done.future;
    } catch (e) {
      if (_isCurrent(generation)) {
        onError?.call(
          e is Api2Error ? e : Exception('Event stream lost: $e'),
        );
      }
    } finally {
      backoffResetTimer?.cancel();
      if (!subscriptionTerminated) await subscription?.cancel();
      if (requestCancelToken != null && !requestCancelToken.isCancelled) {
        requestCancelToken.cancel('Event stream pump finished');
      }
      if (_isCurrent(generation)) {
        _cancelToken = null;
        _subscription = null;
        _streamDone = null;
      }
    }
  }

  bool _isCurrent(int generation) => !_disposed && generation == _generation;
}

/// Durable per-session log reader:
/// `GET /api/experimental/session/{id}/log?after=N&follow=true`.
///
/// Replays the session's durable events from the exclusive [after] cursor,
/// signals the replay/live boundary via [onSynced] when the `log.synced`
/// marker arrives, and — unlike `/api/event` — resumes precisely after a
/// reconnect because the last seen `durable.seq` becomes the next `after`.
class Api2SessionLogStream extends Api2EventStream {
  final String sessionID;
  final bool follow;
  int? _after;

  Api2SessionLogStream({
    required super.transport,
    required this.sessionID,
    int? after,
    this.follow = true,
    required void Function(Api2EventEnvelope event) onEvent,
    void Function(int? seq)? onSynced,
    required super.onStatus,
    super.onError,
  }) : _after = after,
       _onLogEvent = onEvent,
       _onSynced = onSynced,
       super(
         path: '/experimental/session/$sessionID/log',
         onEvent: _ignore,
       );

  static void _ignore(Api2EventEnvelope envelope, Sse2Frame frame) {}

  final void Function(Api2EventEnvelope event) _onLogEvent;
  final void Function(int? seq)? _onSynced;

  /// The last durable sequence number observed (replayed or live).
  int? get lastSeq => _after;

  @override
  Map<String, dynamic>? buildQuery() => {
    if (_after != null) 'after': _after,
    'follow': follow,
  };

  @override
  void handleEvent(Api2EventEnvelope envelope, Sse2Frame frame) {
    final event = envelope.event;
    if (event is Api2LogSyncedEvent) {
      if (event.seq != null) _after = event.seq;
      _onSynced?.call(event.seq);
      return;
    }
    final seq = envelope.durable?.seq;
    if (seq != null) _after = seq;
    _onLogEvent(envelope);
  }
}
