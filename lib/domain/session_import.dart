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

bool _validImportID(Object? value, String prefix) =>
    value is String &&
    value.length > prefix.length &&
    value.startsWith(prefix) &&
    !_hasControl(value) &&
    !value.runes.any((rune) => rune == 0x20);

bool _safeImportText(Object? value) =>
    value is String && value.trim().isNotEmpty && !_hasControl(value);

bool _hasControl(String value) => value.runes.any(
  (rune) => rune < 0x20 || rune == 0x7f || (rune >= 0x80 && rune <= 0x9f),
);

class SessionImportDestination {
  final String directory;
  final String? workspaceID;
  const SessionImportDestination({required this.directory, this.workspaceID});

  Map<String, dynamic> toJson() {
    if (!_safeImportText(directory) ||
        (workspaceID != null && !_validImportID(workspaceID, 'wrk'))) {
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
    if (value is! Map) throw const SessionImportInvalid();
    // Accept the exact export envelope and the bare SessionTransfer.Data form.
    if (value.containsKey('data')) {
      if (value.length != 1 || value['data'] is! Map) {
        throw const SessionImportInvalid();
      }
      value = value['data'] as Map;
    }
    if (value.length != 2 ||
        value['info'] is! Map ||
        value['messages'] is! List) {
      throw const SessionImportInvalid();
    }
    final info = value['info'] as Map;
    final time = info['time'];
    final location = info['location'];
    if (!_validImportID(info['id'], 'ses') ||
        !_safeImportText(info['projectID']) ||
        !_number(info['cost']) ||
        !_tokens(info['tokens']) ||
        time is! Map ||
        !_number(time['created']) ||
        !_number(time['updated']) ||
        (time.containsKey('archived') && !_number(time['archived'])) ||
        location is! Map ||
        !_safeImportText(location['directory']) ||
        (location.containsKey('workspaceID') &&
            !_validImportID(location['workspaceID'], 'wrk')) ||
        (info.containsKey('title') && info['title'] is! String) ||
        (info.containsKey('parentID') &&
            !_validImportID(info['parentID'], 'ses'))) {
      throw const SessionImportInvalid();
    }
    final ids = <String>{};
    final messages = <Map<String, dynamic>>[];
    for (final message in value['messages'] as List) {
      if (message is! Map ||
          !_validImportID(message['id'], 'msg_') ||
          !ids.add(message['id'] as String) ||
          !_safeImportText(message['type']) ||
          message['time'] is! Map ||
          !_number((message['time'] as Map)['created'])) {
        throw const SessionImportInvalid();
      }
      try {
        messages.add(_immutableMap(message));
      } on SessionImportInvalid {
        rethrow;
      } on Object {
        throw const SessionImportInvalid();
      }
    }
    try {
      final immutableInfo = _immutableMap(info);
      return SessionImportDocument._(
        immutableInfo,
        List.unmodifiable(messages),
        _redacted(immutableInfo) || _redacted(messages),
      );
    } on SessionImportInvalid {
      rethrow;
    } on Object {
      throw const SessionImportInvalid();
    }
  }

  Map<String, dynamic> requestBody(SessionImportDestination destination) {
    // Return a fresh mutable JSON tree. The reviewed snapshot remains isolated
    // if a transport or caller mutates this request before submission.
    return {
      'info': _mutableCopy(_info),
      'messages': _mutableCopy(_messages),
      // Always explicit: never import into the source file's directory implicitly.
      'location': destination.toJson(),
    };
  }

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

  static const _maxNesting = 256;

  static Map<String, dynamic> _immutableMap(
    Map value, [
    Set<Object>? ancestors,
    int depth = 0,
  ]) {
    final active = ancestors ?? Set<Object>.identity();
    if (depth > _maxNesting || !active.add(value)) {
      throw const SessionImportInvalid();
    }
    final result = <String, dynamic>{};
    try {
      for (final entry in value.entries) {
        if (entry.key is! String) throw const SessionImportInvalid();
        result[entry.key as String] = _immutableCopy(
          entry.value,
          active,
          depth + 1,
        );
      }
    } finally {
      active.remove(value);
    }
    return Map.unmodifiable(result);
  }

  static dynamic _immutableCopy(
    Object? value, [
    Set<Object>? ancestors,
    int depth = 0,
  ]) {
    if (value == null || value is String || value is bool) return value;
    if (value is num) {
      if (!value.isFinite) throw const SessionImportInvalid();
      return value;
    }
    final active = ancestors ?? Set<Object>.identity();
    if (depth > _maxNesting) throw const SessionImportInvalid();
    if (value is Map) return _immutableMap(value, active, depth);
    if (value is List) {
      if (!active.add(value)) throw const SessionImportInvalid();
      try {
        return List.unmodifiable(
          value.map((item) => _immutableCopy(item, active, depth + 1)),
        );
      } finally {
        active.remove(value);
      }
    }
    throw const SessionImportInvalid();
  }

  static dynamic _mutableCopy(Object? value) {
    if (value is Map) {
      return <String, dynamic>{
        for (final entry in value.entries)
          entry.key as String: _mutableCopy(entry.value),
      };
    }
    if (value is List) return value.map(_mutableCopy).toList();
    return value;
  }
}
