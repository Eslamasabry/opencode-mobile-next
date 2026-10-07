import 'package:dio/dio.dart';

import '../domain/genui/gen_ui_history.dart';
import 'gen_ui_history_http.dart';
import 'models.dart';

mixin OpenCodeGenUiHistory implements GenUiHistoryGateway {
  Dio get dio;
  String? get directory;
  String? get workspace;
  bool get isClosed;

  @override
  Future<bool> genUiSessionIdle(String sessionID) async {
    validateGenUiHistoryRequest(sessionID, null, 1);
    final scope = (directory, workspace);
    final response = await readGenUiHistoryJson(
      dio,
      '/session/status',
      query: {
        if (directory != null) 'directory': directory,
        if (workspace != null) 'workspace': workspace,
      },
    );
    if (isClosed || scope != (directory, workspace)) throw genUiHistoryFailure;
    final json = response.json;
    if (json is! Map<String, dynamic>) throw genUiHistoryFailure;
    final status = json[sessionID];
    return status is Map<String, dynamic> && status['type'] == 'idle';
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
      dio,
      '/session/${Uri.encodeComponent(sessionID)}/message',
      query: {
        if (directory != null) 'directory': directory,
        if (workspace != null) 'workspace': workspace,
        'limit': limit,
        'before': ?cursor,
      },
    );
    try {
      if (isClosed || scope != (directory, workspace)) {
        throw genUiHistoryFailure;
      }
      final json = response.json;
      if (json is! List || json.length > limit) throw genUiHistoryFailure;
      final items = <MessageWithParts>[];
      for (final value in json) {
        if (value is! Map<String, dynamic> ||
            value['info'] is! Map<String, dynamic> ||
            value['parts'] is! List) {
          throw genUiHistoryFailure;
        }
        final info = MessageInfo.fromJson(value['info']);
        if (info.sessionID != sessionID) throw genUiHistoryFailure;
        items.add(
          MessageWithParts(
            info: info,
            parts: [for (final part in value['parts']) Part.fromJson(part)],
          ),
        );
      }
      var next = response.headers.value('x-next-cursor');
      if (next == null || next.isEmpty) {
        for (final link in (response.headers.value('link') ?? '').split(',')) {
          if (!RegExp(r'rel="?next"?').hasMatch(link)) continue;
          final url = RegExp(r'<([^>]+)>').firstMatch(link)?.group(1);
          next = Uri.tryParse(url ?? '')?.queryParameters['before'];
          if (next != null && next.isNotEmpty) break;
        }
      }
      if (next != null && next.isNotEmpty) {
        validateGenUiHistoryRequest(sessionID, next, limit);
        if (next == cursor) throw genUiHistoryFailure;
      }
      // A full page without a cursor cannot prove that older history is absent.
      return GenUiHistoryPage(
        items: List.unmodifiable(items),
        olderCursor: next?.isNotEmpty == true ? next : null,
        hasMore: next?.isNotEmpty == true || items.length == limit,
      );
    } catch (_) {
      throw genUiHistoryFailure;
    }
  }
}
