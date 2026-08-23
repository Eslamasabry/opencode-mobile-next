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
  bool _running = false;
  bool _disposed = false;
  int _attempt = 0;

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
    _connect();
  }

  Future<void> dispose() async {
    _disposed = true;
    _cancelToken?.cancel();
    _cancelToken = null;
  }

  void _connect() {
    if (_disposed || _running) return;
    _running = true;
    onStatus(_attempt == 0 ? StreamStatus.connecting : StreamStatus.reconnecting);
    unawaited(
      _pump().whenComplete(() {
        _running = false;
        if (_disposed) {
          onStatus(StreamStatus.disconnected);
          return;
        }
        _attempt += 1;
        final ms = (500 * (1 << _attempt.clamp(0, 5))).clamp(500, 16000);
        onStatus(_attempt >= _giveUpVisualAfter
            ? StreamStatus.disconnected
            : StreamStatus.reconnecting);
        Timer(Duration(milliseconds: ms), () {
          if (!_disposed) _connect();
        });
      }),
    );
  }

  Future<void> _pump() async {
    try {
      _cancelToken = CancelToken();
      final response = await api.openEventStream(cancelToken: _cancelToken);
      if (_disposed) return;
      _attempt = 0;
      onStatus(StreamStatus.connected);

      var pending = <int>[];
      await for (final chunk in response.data!.stream.cast<Uint8List>()) {
        if (_disposed) return;
        pending.addAll(chunk);
        while (true) {
          final nl = pending.indexOf(10); // '\n'
          if (nl < 0) break;
          final lineBytes = pending.sublist(0, nl);
          pending.removeRange(0, nl + 1);
          final line = utf8.decode(lineBytes, allowMalformed: true).trimRight();
          if (line.isEmpty) continue; // SSE frames are single-data here
          if (line.startsWith(':')) continue; // keepalive comment
          if (!line.startsWith('data:')) continue; // event:/id:/retry: not needed
          final payload = line.substring(5).trim();
          if (payload.isEmpty) continue;
          try {
            final json = jsonDecode(payload);
            if (json is Map<String, dynamic>) {
              onEvent(EventEnvelope.fromJson(json));
            }
          } catch (_) {
            // Malformed frame - skip it rather than killing the stream.
          }
        }
      }
      // Server closed the stream cleanly -> treated as disconnect by caller.
    } catch (e) {
      if (!_disposed) onError?.call(e is ApiException ? e : Exception('Event stream lost: $e'));
    }
  }
}
