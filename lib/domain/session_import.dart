import 'dart:convert';

import '../api/models.dart' show Session;

abstract interface class SessionImportGateway {
  bool get sessionImportSupported;
  Future<Session> importSession(
    SessionImportDocument document,
    SessionImportDestination destination,
  );
}

class SessionImportUnsupported implements Exception {
  const SessionImportUnsupported();
}

class SessionImportInvalid implements Exception {
  const SessionImportInvalid();
}

class SessionImportTooLarge implements Exception {
  const SessionImportTooLarge();
}

class SessionImportDestination {
  final String directory;
  final String? workspaceID;
  const SessionImportDestination({required this.directory, this.workspaceID});

  Map<String, dynamic> toJson() {
    if (directory.trim().isEmpty ||
        (workspaceID != null && !workspaceID!.startsWith('wrk'))) {
      throw const SessionImportInvalid();
    }
    return {
      'directory': directory,
      if (workspaceID != null) 'workspaceID': workspaceID,
    };
  }
}

/// A projected v2 transcript, never a reconstruction from product models.
/// Validate its identifying structure locally; the server validates nested
/// protocol fields. Keep those fields intact, including unknown message types.
class SessionImportDocument {
  static const maxBytes = 128 * 1024 * 1024;
  final Map<String, dynamic> _info;
  final List<Map<String, dynamic>> _messages;
  final bool hasRedactions;
  SessionImportDocument._(this._info, this._messages, this.hasRedactions);

  String get id => _info['id'] as String;
  String? get title => _info['title'] as String?;
  String? get parentID => _info['parentID'] as String?;
  int get messageCount => _messages.length;
  bool get archived => ((_info['time'] as Map)['archived'] as num? ?? 0) > 0;

  static Future<SessionImportDocument> read(Stream<List<int>> source) async {
    var size = 0;
    final bounded = source.map((bytes) {
      size += bytes.length;
      if (size > maxBytes) throw const SessionImportTooLarge();
      return bytes;
    });
    try {
      // Incremental UTF-8/JSON decoding avoids a second full-file string copy.
      final decoded = await bounded
          .transform(utf8.decoder)
          .transform(json.decoder)
          .single;
      return SessionImportDocument.fromJson(decoded);
    } on FormatException {
      throw const SessionImportInvalid();
    }
  }

  factory SessionImportDocument.fromJson(Object? value) {
    if (value is! Map<String, dynamic>) throw const SessionImportInvalid();
    // Accept the exact export envelope and the bare SessionTransfer.Data form.
    if (value.containsKey('data')) {
      if (value.length != 1 || value['data'] is! Map<String, dynamic>) {
        throw const SessionImportInvalid();
      }
      value = value['data'] as Map<String, dynamic>;
    }
    if (value.length != 2 ||
        value['info'] is! Map<String, dynamic> ||
        value['messages'] is! List) {
      throw const SessionImportInvalid();
    }
    final info = value['info'] as Map<String, dynamic>;
    final time = info['time'];
    final location = info['location'];
    if (!_id(info['id'], 'ses') ||
        info['projectID'] is! String ||
        !_number(info['cost']) ||
        !_tokens(info['tokens']) ||
        time is! Map ||
        !_number(time['created']) ||
        !_number(time['updated']) ||
        (time.containsKey('archived') && !_number(time['archived'])) ||
        location is! Map ||
        location['directory'] is! String ||
        (info.containsKey('title') && info['title'] is! String) ||
        (info.containsKey('parentID') && !_id(info['parentID'], 'ses'))) {
      throw const SessionImportInvalid();
    }
    final ids = <String>{};
    final messages = <Map<String, dynamic>>[];
    for (final message in value['messages'] as List) {
      if (message is! Map<String, dynamic> ||
          !_id(message['id'], 'msg_') ||
          !ids.add(message['id'] as String) ||
          message['type'] is! String ||
          (message['type'] as String).isEmpty ||
          message['time'] is! Map ||
          !_number((message['time'] as Map)['created'])) {
        throw const SessionImportInvalid();
      }
      messages.add(message);
    }
    return SessionImportDocument._(
      Map.unmodifiable(info),
      List.unmodifiable(messages),
      _redacted(value),
    );
  }

  Map<String, dynamic> requestBody(SessionImportDestination destination) => {
    'info': _info,
    'messages': _messages,
    // Always explicit: never import into the source file's directory implicitly.
    'location': destination.toJson(),
  };

  static bool _id(Object? value, String prefix) =>
      value is String && value.startsWith(prefix);
  static bool _number(Object? value) => value is num && value.isFinite;
  static bool _tokens(Object? value) =>
      value is Map &&
      ['input', 'output', 'reasoning'].every((key) => _number(value[key])) &&
      value['cache'] is Map &&
      ['read', 'write'].every((key) => _number((value['cache'] as Map)[key]));

  static bool _redacted(Object? value) {
    if (value is String) return value.contains('[redacted:');
    if (value is Map) return value.values.any(_redacted);
    if (value is List) return value.any(_redacted);
    return false;
  }
}
