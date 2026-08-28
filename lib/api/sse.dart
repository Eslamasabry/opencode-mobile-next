import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart' show CancelToken;

import 'models.dart';
import 'opencode_api.dart';

/// Connection lifecycle surfaced to the UI.
enum StreamStatus { connecting, connected, reconnecting, disconnected }

/// Server-sent events with automatic reconnect + exponential backoff.
class EventStream {
  final OpenCodeApi api;
  final void Function(EventEnvelope event) onEvent;
  final void Function(StreamStatus status) onStatus;
  final void Function(Object error)? onError;
  final bool global;

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
    this.global = false,
  });

  /// After this many failed attempts we stop showing "reconnecting".
  static const _giveUpVisualAfter = 6;
  static const _backoffResetAfter = Duration(seconds: 30);

  // Tool output and assistant messages can legitimately exceed 64 KiB. Keep a
  // generous, explicit ceiling so those events are delivered without allowing
  // a broken or hostile server to grow the receive buffer without bound.
  static const _maxLineBytes = 8 * 1024 * 1024;

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
    Timer? backoffResetTimer;
    CancelToken? requestCancelToken;
    StreamSubscription<Uint8List>? subscription;
    var subscriptionTerminated = false;
    try {
      final cancelToken = CancelToken();
      requestCancelToken = cancelToken;
      _cancelToken = cancelToken;
      final response = global
          ? await api.openGlobalEventStream(cancelToken: cancelToken)
          : await api.openEventStream(cancelToken: cancelToken);
      if (!_isCurrent(generation)) return;
      onStatus(StreamStatus.connected);
      backoffResetTimer = Timer(_backoffResetAfter, () {
        if (_isCurrent(generation)) _attempt = 0;
      });

      var pending = <int>[];
      var discardingOversizedLine = false;
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

      void processLine(List<int> lineBytes) {
        final line = utf8.decode(lineBytes, allowMalformed: true).trimRight();
        if (line.isEmpty) return; // SSE frames are single-data here
        if (line.startsWith(':')) return; // keepalive comment
        if (!line.startsWith('data:')) return; // event:/id:/retry: not needed
        final payload = line.substring(5).trim();
        if (payload.isEmpty) return;
        try {
          final json = jsonDecode(payload);
          if (_isCurrent(generation) && json is Map<String, dynamic>) {
            final event = global ? json['payload'] : json;
            if (event is Map) {
              onEvent(EventEnvelope.fromJson(Map<String, dynamic>.from(event)));
            }
          }
        } catch (_) {
          // Malformed frame - skip it rather than killing the stream.
        }
      }

      subscription = response.data!.stream.cast<Uint8List>().listen(
        (chunk) {
          if (!_isCurrent(generation)) return;
          for (final byte in chunk) {
            if (discardingOversizedLine) {
              if (byte == 10) discardingOversizedLine = false;
              continue;
            }
            if (byte == 10) {
              final lineBytes = pending;
              pending = <int>[];
              processLine(lineBytes);
              continue;
            }
            if (pending.length == _maxLineBytes) {
              pending.clear();
              discardingOversizedLine = true;
              continue;
            }
            pending.add(byte);
          }
        },
        onError: (Object error, StackTrace stackTrace) {
          subscriptionTerminated = true;
          finish(error, stackTrace);
        },
        onDone: () {
          subscriptionTerminated = true;
          finish();
        },
        cancelOnError: true,
      );
      _subscription = subscription;
      await done.future;
      // Server closed the stream cleanly -> treated as disconnect by caller.
    } catch (e) {
      if (_isCurrent(generation)) {
        onError?.call(
          e is ApiException ? e : Exception('Event stream lost: $e'),
        );
      }
    } finally {
      backoffResetTimer?.cancel();
      // An error does not necessarily close the source stream. Explicitly
      // cancel both layers before the reconnect loop creates a replacement.
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
