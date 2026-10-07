import '../api/gen_ui_history_http.dart';
import '../domain/genui/gen_ui_history.dart';
import 'gateway_mappers.dart';
import 'models.dart';
import 'transport.dart';

mixin Api2GenUiHistory implements GenUiHistoryGateway {
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
      return GenUiHistoryPage(
        items: List.unmodifiable(
          mapApi2Messages(sessionID, data.reversed.toList()),
        ),
        olderCursor: more ? next : null,
        hasMore: more,
      );
    } catch (_) {
      throw genUiHistoryFailure;
    }
  }
}
