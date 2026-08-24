import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/src/api.dart';
import 'package:opencode_sdk/src/http/wire.dart';
import 'package:opencode_sdk/src/model/event.dart';
import 'package:opencode_sdk/src/model/global_event.dart';
import 'package:opencode_sdk/src/model/session_durable_event.dart';
import 'package:opencode_sdk/src/model/v2_event.dart';
import 'package:opencode_sdk/src/sse/sse_transport.dart';

export 'sse_transport.dart';

typedef SessionDurableEventStream = ServerSentEvent<SessionDurableEvent>;

/// Error sent by Effect in an `effect/httpapi/stream/failure` SSE frame.
class EffectStreamFailure implements Exception {
  const EffectStreamFailure(this.event);

  final ServerSentEvent<Object?> event;

  Object? get cause => event.data;

  @override
  String toString() => 'EffectStreamFailure: $cause';
}

/// Streaming replacements for the four SSE operations in the OpenAPI contract.
extension OpencodeSdkEventStreams on OpencodeSdk {
  Stream<ServerSentEvent<GlobalEvent>> globalEventStream({
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    CancelToken? cancelToken,
    String? lastEventId,
    SseReconnectOptions reconnect = const SseReconnectOptions(),
  }) => SseTransport(dio).connect<GlobalEvent>(
    path: '/global/event',
    operationId: 'global.event',
    decodeData: (event) => GlobalEvent.fromJson(_jsonObject(event.data)),
    headers: headers,
    extra: extra,
    cancelToken: cancelToken,
    lastEventId: lastEventId,
    reconnect: reconnect,
  );

  Stream<ServerSentEvent<Event>> eventSubscribeStream({
    String? directory,
    String? workspace,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    CancelToken? cancelToken,
    String? lastEventId,
    SseReconnectOptions reconnect = const SseReconnectOptions(),
  }) => SseTransport(dio).connect<Event>(
    path: '/event',
    operationId: 'event.subscribe',
    decodeData: (event) => Event.fromJson(jsonDecode(event.data)),
    queryParameters: <String, dynamic>{
      if (directory != null) 'directory': directory,
      if (workspace != null) 'workspace': workspace,
    },
    headers: headers,
    extra: extra,
    cancelToken: cancelToken,
    lastEventId: lastEventId,
    reconnect: reconnect,
  );

  /// Uses upstream `Last-Event-ID` reconnect behavior.
  ///
  /// The pinned server accepts replay only through [after] and does not emit or
  /// consume an SSE event ID contract. It may therefore replay events after a
  /// failed connection; consumers must deduplicate by durable event sequence.
  Stream<SessionDurableEventStream> v2SessionEventsStream({
    required String sessionID,
    String? after,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    CancelToken? cancelToken,
    String? lastEventId,
    SseReconnectOptions reconnect = const SseReconnectOptions(),
  }) => SseTransport(dio).connect<SessionDurableEvent>(
    path: '/api/session/${encodeOpenCodePathSegment(sessionID)}/event',
    operationId: 'v2.session.events',
    decodeData: _decodeSessionEvent,
    queryParameters: <String, dynamic>{if (after != null) 'after': after},
    headers: headers,
    extra: extra,
    cancelToken: cancelToken,
    lastEventId: lastEventId,
    reconnect: reconnect,
  );

  Stream<ServerSentEvent<V2Event>> v2EventSubscribeStream({
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    CancelToken? cancelToken,
    String? lastEventId,
    SseReconnectOptions reconnect = const SseReconnectOptions(),
  }) => SseTransport(dio).connect<V2Event>(
    path: '/api/event',
    operationId: 'v2.event.subscribe',
    decodeData: (event) => V2Event.fromJson(jsonDecode(event.data)),
    headers: headers,
    extra: extra,
    cancelToken: cancelToken,
    lastEventId: lastEventId,
    reconnect: reconnect,
  );
}

SessionDurableEvent _decodeSessionEvent(ServerSentEvent<String> event) {
  final decoded = jsonDecode(event.data);
  if (event.event == 'effect/httpapi/stream/failure') {
    throw EffectStreamFailure(
      ServerSentEvent<Object?>(
        data: decoded,
        id: event.id,
        event: event.event,
        retry: event.retry,
      ),
    );
  }
  final encoded = decoded;
  if (encoded is! String) {
    throw const FormatException(
      'SessionDurableEventStream data must contain a JSON string.',
    );
  }
  return SessionDurableEvent.fromJson(jsonDecode(encoded));
}

Map<String, dynamic> _jsonObject(String data) {
  final decoded = jsonDecode(data);
  if (decoded is! Map<String, dynamic>) {
    throw const FormatException('Expected an SSE JSON object.');
  }
  return decoded;
}
