import 'dart:convert';

import 'package:crypto/crypto.dart';

/// Digest of the ordered durable transcript, for private equality checks.
///
/// OC1 retains `info` and ordered `parts`; OC2 retains flat messages and
/// ordered `content`. IDs, roles, text, tool input/result/state and tagged
/// card answer receipts remain in the projection. Only known message,
/// content-item and tool-state timestamp containers are omitted. A field
/// named `time` inside a tool input or result remains durable body data.
/// Neither the canonical body nor its input should enter diagnostics.
String historyProjection(
  List<Map<String, dynamic>> messages, {
  required bool oc2,
}) {
  final projected = messages.map((message) {
    final body = Map<String, dynamic>.from(message);
    if (oc2) {
      _requireIdentity(body, 'type');
      body.remove('time');
      if (body.containsKey('content')) {
        body['content'] = _items(body['content']);
      }
    } else {
      final info = _map(body['info']);
      _requireIdentity(info, 'role');
      info.remove('time');
      body['info'] = info;
      body['parts'] = _items(body['parts']);
    }
    return body;
  }).toList();
  final canonical = _canonical({'oc2': oc2, 'messages': projected});
  return sha256.convert(utf8.encode(jsonEncode(canonical))).toString();
}

void _requireIdentity(Map<String, dynamic> message, String roleKey) {
  final id = message['id'];
  final role = message[roleKey];
  if (id is! String || id.isEmpty || role is! String || role.isEmpty) {
    throw const FormatException('invalid_history_identity');
  }
}

List<Map<String, dynamic>> _items(Object? value) {
  if (value is! List) {
    throw const FormatException('invalid_history_items');
  }
  return value.map((raw) {
    final item = _map(raw)..remove('time');
    if (item['type'] == 'tool' && item['state'] is Map) {
      item['state'] = _map(item['state'])..remove('time');
    }
    return item;
  }).toList();
}

Map<String, dynamic> _map(Object? value) {
  if (value is! Map || value.keys.any((key) => key is! String)) {
    throw const FormatException('invalid_history_object');
  }
  return Map<String, dynamic>.from(value);
}

Object? _canonical(Object? value) {
  if (value is Map) {
    final map = _map(value);
    final keys = map.keys.toList()..sort();
    return {for (final key in keys) key: _canonical(map[key])};
  }
  if (value is List) return value.map(_canonical).toList();
  if (value == null ||
      value is String ||
      value is bool ||
      (value is num && value.isFinite)) {
    return value;
  }
  throw const FormatException('invalid_history_value');
}
