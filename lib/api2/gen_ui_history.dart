import '../domain/agent_handoff_history.dart';
import '../domain/gen_ui_history_http.dart';
import '../domain/models.dart';
import '../domain/genui/gen_ui_history.dart';
import 'gateway_mappers.dart';
import 'models.dart';
import 'transport.dart';

/// A handoff reads the whole conversation, and one message can carry several
/// screenshots as data URIs (a phone's Design review chat had a 1.8 MB
/// message), so its pages get a larger budget than the agent card recovery.
const api2HandoffPageByteLimit = 16 * 1024 * 1024;
const api2HandoffHistoryByteLimit = 64 * 1024 * 1024;
const api2HandoffPageTimeout = Duration(seconds: 30);

mixin Api2GenUiHistory
    implements GenUiHistoryGateway, AgentHandoffHistoryGateway {
  Api2Transport get transport;
  String? get directory;
  String? get workspace;
  bool get isClosed;

  @override
  Future<bool> genUiSessionIdle(String sessionID) async {
    validateGenUiHistoryRequest(sessionID, null, 1);
    final scope = (directory, workspace);
    final session = await readGenUiHistoryJson(
      transport.dio,
      '/session/${Uri.encodeComponent(sessionID)}',
      query: {},
    );
    final rawSession = session.json;
    final sessionData = rawSession is Map<String, dynamic>
        ? rawSession['data']
        : null;
    if (sessionData is! Map<String, dynamic> ||
        sessionData['id'] != sessionID) {
      throw genUiHistoryFailure;
    }
    final response = await readGenUiHistoryJson(
      transport.dio,
      '/session/active',
      query: {},
    );
    if (isClosed || scope != (directory, workspace)) throw genUiHistoryFailure;
    final json = response.json;
    final data = json is Map<String, dynamic> ? json['data'] : null;
    if (data is! Map<String, dynamic> ||
        data.values.any(
          (value) =>
              value is! Map<String, dynamic> ||
              value['type'] is! String ||
              (value['type'] as String).isEmpty,
        )) {
      throw genUiHistoryFailure;
    }
    // Any execution entry blocks admission, even if its status is unfamiliar.
    return !data.containsKey(sessionID);
  }

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async =>
      (await _historyPage(sessionID, cursor: cursor, limit: limit)).page;

  /// Newest page first, returned oldest-first. Fails rather than returning a
  /// partial history; the caller bounds the message count.
  @override
  Future<List<MessageWithParts>> agentHandoffHistory(String sessionID) async {
    final pages = <List<MessageWithParts>>[];
    final seen = <String>{};
    var bytes = 0;
    String? cursor;
    for (var number = 0; number < 100; number++) {
      final read = await _historyPage(
        sessionID,
        cursor: cursor,
        limit: 50,
        byteLimit: api2HandoffPageByteLimit,
        timeout: api2HandoffPageTimeout,
      );
      bytes += read.bytes;
      if (bytes > api2HandoffHistoryByteLimit) throw genUiHistoryFailure;
      pages.add([
        for (final message in read.page.items)
          if (seen.add(message.info.id)) message,
      ]);
      if (!read.page.hasMore) {
        return List.unmodifiable(pages.reversed.expand((items) => items));
      }
      cursor = read.page.olderCursor;
      if (cursor == null) throw genUiHistoryFailure;
    }
    throw genUiHistoryFailure;
  }

  Future<({GenUiHistoryPage page, int bytes})> _historyPage(
    String sessionID, {
    String? cursor,
    required int limit,
    int byteLimit = genUiHistoryByteLimit,
    Duration timeout = genUiHistoryTimeout,
  }) async {
    validateGenUiHistoryRequest(sessionID, cursor, limit);
    final scope = (directory, workspace);
    final response = await readGenUiHistoryJson(
      transport.dio,
      '/session/${Uri.encodeComponent(sessionID)}/message',
      query: {
        'limit': limit,
        if (cursor == null) 'order': 'desc' else 'cursor': cursor,
      },
      byteLimit: byteLimit,
      timeout: timeout,
    );
    try {
      if (isClosed || scope != (directory, workspace)) {
        throw genUiHistoryFailure;
      }
      final json = response.json;
      if (json is! Map<String, dynamic> ||
          json['data'] is! List ||
          (json['data'] as List).length > limit) {
        throw genUiHistoryFailure;
      }
      final data = <Api2Message>[];
      for (final value in json['data']) {
        if (value is! Map<String, dynamic>) throw genUiHistoryFailure;
        final message = Api2Message.fromJson(value);
        if (message == null ||
            (value.containsKey('sessionID') &&
                value['sessionID'] != sessionID)) {
          throw genUiHistoryFailure;
        }
        data.add(message);
      }
      final page = Api2Page.fromJson(json, Api2Message.fromJson);
      final next = page.nextCursor;
      if (next != null) {
        validateGenUiHistoryRequest(sessionID, next, limit);
        if (next == cursor) throw genUiHistoryFailure;
      }
      final more = data.length == limit;
      return (
        page: GenUiHistoryPage(
          items: List.unmodifiable(
            mapApi2Messages(sessionID, data.reversed.toList()),
          ),
          olderCursor: more ? next : null,
          hasMore: more,
        ),
        bytes: response.bytes,
      );
    } catch (_) {
      throw genUiHistoryFailure;
    }
  }
}
