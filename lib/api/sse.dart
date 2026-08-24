import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart' show CancelToken;

import 'models.dart';
import 'opencode_api.dart';

/// Connection lifecycle surfaced to the UI.
enum StreamStatus { connecting, connected, reconnecting, disconnected }

/// Server-sent events from `/event` with automatic reconnect + exponential backoff.
class EventStream {
  final OpenCodeApi api;
  final void Function(EventEnvelope event) onEvent;
  final void Function(StreamStatus status) onStatus;
  final void Function(Object error)? onError;

  CancelToken? _cancelToken;
  StreamSubscription<Uint8List>? _subscription;
  Completer<void>? _streamDone;
  Timer? _retryTimer;
  bool _running = false;
  bool _disposed = false;
  int _attempt = 0;
  int _generation = 0;

  EventStream({
    required this.api,
    required this.onEvent,
    required this.onStatus,
    this.onError,
  });

  /// After this many failed attempts we stop showing "reconnecting".
  static const _giveUpVisualAfter = 6;

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

  void _connect() {
    if (_disposed || _running) return;
    _retryTimer?.cancel();
    _retryTimer = null;
    _running = true;
    final generation = ++_generation;
    onStatus(
      _attempt == 0 ? StreamStatus.connecting : StreamStatus.reconnecting,
    );
    unawaited(
      _pump(generation).whenComplete(() {
        if (!_isCurrent(generation)) return;
        _running = false;
        _attempt += 1;
        final ms = (500 * (1 << _attempt.clamp(0, 5))).clamp(500, 16000);
        onStatus(
          _attempt >= _giveUpVisualAfter
              ? StreamStatus.disconnected
              : StreamStatus.reconnecting,
        );
        _retryTimer = Timer(Duration(milliseconds: ms), () {
          _retryTimer = null;
          if (_isCurrent(generation)) _connect();
        });
      }),
    );
  }

  Future<void> _pump(int generation) async {
    try {
      final cancelToken = CancelToken();
      _cancelToken = cancelToken;
      final response = await api.openEventStream(cancelToken: cancelToken);
      if (!_isCurrent(generation)) return;
      _attempt = 0;
      onStatus(StreamStatus.connected);

      var pending = <int>[];
      final done = Completer<void>();
      _streamDone = done;
      _subscription = response.data!.stream.cast<Uint8List>().listen(
        (chunk) {
          if (!_isCurrent(generation)) return;
          pending.addAll(chunk);
          while (true) {
            final nl = pending.indexOf(10); // '\n'
            if (nl < 0) break;
            final lineBytes = pending.sublist(0, nl);
            pending.removeRange(0, nl + 1);
            final line = utf8
                .decode(lineBytes, allowMalformed: true)
                .trimRight();
            if (line.isEmpty) continue; // SSE frames are single-data here
            if (line.startsWith(':')) continue; // keepalive comment
            if (!line.startsWith('data:')) {
              continue; // event:/id:/retry: not needed
            }
            final payload = line.substring(5).trim();
            if (payload.isEmpty) continue;
            try {
              final json = jsonDecode(payload);
              if (_isCurrent(generation) && json is Map<String, dynamic>) {
                onEvent(EventEnvelope.fromJson(json));
              }
            } catch (_) {
              // Malformed frame - skip it rather than killing the stream.
            }
          }
        },
        onError: done.completeError,
        onDone: done.complete,
      );
      await done.future;
      // Server closed the stream cleanly -> treated as disconnect by caller.
    } catch (e) {
      if (_isCurrent(generation)) {
        onError?.call(
          e is ApiException ? e : Exception('Event stream lost: $e'),
        );
      }
    } finally {
      if (_isCurrent(generation)) {
        _cancelToken = null;
        _subscription = null;
        _streamDone = null;
      }
    }
  }

  bool _isCurrent(int generation) => !_disposed && generation == _generation;
}
