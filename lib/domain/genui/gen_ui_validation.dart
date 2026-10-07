part of 'gen_ui_codec.dart';

final class _Invalid implements Exception {
  const _Invalid([this.reason = GenUiProblem.badValue]);
  final GenUiProblem reason;
}

Never _fail([GenUiProblem reason = GenUiProblem.badValue]) =>
    throw _Invalid(reason);

int _scalars(String value) {
  for (var i = 0; i < value.length; i++) {
    final c = value.codeUnitAt(i);
    if (c >= 0xd800 && c <= 0xdbff) {
      i++;
      if (i >= value.length ||
          value.codeUnitAt(i) < 0xdc00 ||
          value.codeUnitAt(i) > 0xdfff) {
        _fail();
      }
    } else if (c >= 0xdc00 && c <= 0xdfff) {
      _fail();
    }
  }
  return value.runes.length;
}

void _bounded(Object? input) {
  var count = 0;
  var stringBytes = 0;
  final seen = HashSet<Object>.identity();
  void string(String s) {
    if (s.length > 65536 || _scalars(s) > 32768) _fail(GenUiProblem.tooLarge);
    stringBytes += utf8.encode(s).length;
    if (stringBytes > 32768) _fail(GenUiProblem.tooLarge);
  }

  void visit(Object? x, int depth) {
    if (++count > 4096 || depth > 8) _fail(GenUiProblem.tooLarge);
    if (x == null || x is bool) return;
    if (x is String) {
      string(x);
      return;
    }
    if (x is num) {
      if (!x.isFinite) _fail();
      return;
    }
    if (x is! Map && x is! List) _fail();
    if (!seen.add(x)) _fail();
    if (x is Map) {
      for (final entry in x.entries) {
        if (entry.key is! String) _fail();
        string(entry.key as String);
        visit(entry.value, depth + 1);
      }
    } else if (x is List) {
      for (final value in x) {
        visit(value, depth + 1);
      }
    }
    seen.remove(x);
  }

  visit(input, 1);
  if (utf8.encode(jsonEncode(input)).length > 32768) {
    _fail(GenUiProblem.tooLarge);
  }
}

Map<String, dynamic> _obj(
  Object? value,
  List<String> allowed, [
  List<String> required = const [],
]) {
  if (value is! Map) _fail();
  if (value.keys.any((key) => key is! String || !allowed.contains(key))) {
    _fail(GenUiProblem.unknownKey);
  }
  if (required.any((key) => !value.containsKey(key)) ||
      value.values.any((v) => v == null)) {
    _fail();
  }
  return Map<String, dynamic>.from(value);
}

final _controls = RegExp(
  r'[\x00-\x08\x0b-\x1f\x7f-\x9f\u061c\u200e-\u200f\u202a-\u202e\u2066-\u2069]',
);
String _text(Object? value, [int max = 2000, bool label = false, int min = 0]) {
  if (value is! String || _scalars(value) > max) _fail();
  var result = value.replaceAll(_controls, '');
  if (label) result = result.trim();
  if (_scalars(result) < min) _fail();
  return result;
}

List<dynamic> _arr(Object? value, int min, int max) {
  if (value is! List || value.length < min || value.length > max) _fail();
  return value;
}

num _num(Object? value) {
  if (value is! num || !value.isFinite) _fail();
  final number = value.toDouble();
  if (number.abs() <= 9007199254740991 && number == number.truncateToDouble()) {
    return number.toInt();
  }
  return number;
}

int _int(Object? value, int min, int max) {
  final n = _num(value);
  if (n != n.truncateToDouble() || n < min || n > max) _fail();
  return n.toInt();
}

bool _bool(Object? value) {
  if (value is! bool) _fail();
  return value;
}

String _pick(Object? value, List<String> allowed) {
  if (value is! String || !allowed.contains(value)) _fail();
  return value;
}

String _id(Object? value, String pattern) {
  if (value is! String ||
      RegExp(pattern).firstMatch(value)?.group(0) != value) {
    _fail();
  }
  return value;
}

void _secret(String value) {
  final normalized = value.replaceAll(
    RegExp('[^a-z0-9]', caseSensitive: false),
    '',
  );
  if (RegExp(
    'password|passwd|token|secret|apikey',
    caseSensitive: false,
  ).hasMatch(normalized)) {
    _fail(GenUiProblem.secretField);
  }
}

void _unique(Iterable<String> values) {
  final list = values.toList();
  if (list.toSet().length != list.length) _fail();
}

Map<String, dynamic> _optText(
  Map<String, dynamic> o,
  String key,
  int max, [
  bool label = false,
  int min = 0,
]) => o.containsKey(key) ? {key: _text(o[key], max, label, min)} : {};

Map<String, dynamic> _option(Object? value) {
  final o = _obj(value, ['id', 'label', 'detail'], ['id', 'label']);
  final id = _id(o['id'], r'^[a-zA-Z0-9_\-]{1,32}$');
  final label = _text(o['label'], 120, true, 1);
  _secret(id);
  _secret(label);
  return {'id': id, 'label': label, ..._optText(o, 'detail', 200)};
}

List<Map<String, dynamic>> _options(Object? value, int min, int max) {
  final result = _arr(value, min, max).map(_option).toList();
  _unique(result.map((o) => o['id'] as String));
  return result;
}

Object _fieldValue(Object? value, Map<String, dynamic> field) {
  switch (field['type']) {
    case 'text':
    case 'multiline':
      if (value is! String ||
          _scalars(value) > 2000 ||
          (field['required'] == true && value.trim().isEmpty)) {
        _fail();
      }
      return value;
    case 'number':
      final n = _num(value);
      if ((field['min'] != null && n < (field['min'] as num)) ||
          (field['max'] != null && n > (field['max'] as num))) {
        _fail();
      }
      return n;
    case 'toggle':
      return _bool(value);
    case 'select':
      if (value is! String ||
          !(field['options'] as List).any((o) => o['id'] == value)) {
        _fail();
      }
      return value;
    case 'date':
      if (value is! String ||
          value.length != 10 ||
          !RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(value)) {
        _fail();
      }
      final parts = value.split('-').map(int.parse).toList();
      final y = parts[0], m = parts[1], d = parts[2];
      final leap = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
      if (y < 1 ||
          m < 1 ||
          m > 12 ||
          d < 1 ||
          d >
              [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m -
                  1]) {
        _fail();
      }
      return value;
  }
  _fail();
}

Map<String, dynamic> _field(Object? value) {
  final o = _obj(
    value,
    [
      'id',
      'label',
      'type',
      'required',
      'placeholder',
      'default',
      'options',
      'min',
      'max',
    ],
    ['id', 'label', 'type'],
  );
  final result = <String, dynamic>{
    'id': _id(o['id'], r'^[a-zA-Z0-9_]{1,32}$'),
    'label': _text(o['label'], 120, true, 1),
    'type': _pick(o['type'], [
      'text',
      'multiline',
      'number',
      'toggle',
      'select',
      'date',
    ]),
    'required': o.containsKey('required') ? _bool(o['required']) : false,
    ..._optText(o, 'placeholder', 120),
  };
  _secret(result['id'] as String);
  _secret(result['label'] as String);
  if (result['type'] == 'select') {
    result['options'] = _options(o['options'], 2, 20);
  } else if (o.containsKey('options')) {
    _fail();
  }
  for (final key in ['min', 'max']) {
    if (o.containsKey(key)) {
      if (result['type'] != 'number') _fail();
      result[key] = _num(o[key]);
    }
  }
  if (result.containsKey('min') &&
      result.containsKey('max') &&
      (result['min'] as num) > (result['max'] as num)) {
    _fail();
  }
  if (o.containsKey('default')) {
    result['default'] = _fieldValue(o['default'], result);
  }
  return result;
}

Map<String, dynamic> _ask(Object? value) {
  final o = _obj(
    value,
    [
      'kind',
      'options',
      'multi',
      'fields',
      'submitLabel',
      'confirmLabel',
      'cancelLabel',
      'tone',
      'purpose',
      'max',
    ],
    ['kind'],
  );
  switch (o['kind']) {
    case 'choice':
      _obj(o, ['kind', 'options', 'multi'], ['kind', 'options']);
      return {
        'kind': 'choice',
        'options': _options(o['options'], 2, 8),
        'multi': o.containsKey('multi') ? _bool(o['multi']) : false,
      };
    case 'form':
      _obj(o, ['kind', 'fields', 'submitLabel'], ['kind', 'fields']);
      final fields = _arr(o['fields'], 1, 12).map(_field).toList();
      _unique(fields.map((f) => f['id'] as String));
      return {
        'kind': 'form',
        'fields': fields,
        ..._optText(o, 'submitLabel', 24, true, 1),
      };
    case 'confirm':
      _obj(o, ['kind', 'confirmLabel', 'cancelLabel', 'tone'], ['kind']);
      return {
        'kind': 'confirm',
        ..._optText(o, 'confirmLabel', 24, true, 1),
        ..._optText(o, 'cancelLabel', 24, true, 1),
        'tone': o.containsKey('tone')
            ? _pick(o['tone'], ['normal', 'danger'])
            : 'normal',
      };
    case 'photo':
      _obj(o, ['kind', 'purpose', 'max'], ['kind', 'purpose']);
      return {
        'kind': 'photo',
        'purpose': _text(o['purpose'], 200, true, 1),
        'max': o.containsKey('max') ? _int(o['max'], 1, 4) : 1,
      };
    case 'file':
    case 'voice':
      _fail(GenUiProblem.unsupportedAsk);
    default:
      _fail();
  }
}

Map<String, dynamic> _node(Object? value) {
  if (value is! Map || value['type'] is! String) _fail();
  switch (value['type']) {
    case 'text':
      final o = _obj(value, ['type', 'text'], ['type', 'text']);
      return {'type': 'text', 'text': _text(o['text'])};
    case 'keyValue':
      final o = _obj(value, ['type', 'rows'], ['type', 'rows']);
      return {
        'type': 'keyValue',
        'rows': _arr(o['rows'], 0, 20).map((v) {
          final r = _obj(v, ['key', 'value'], ['key', 'value']);
          return {
            'key': _text(r['key'], 60, true, 1),
            'value': _text(r['value'], 200),
          };
        }).toList(),
      };
    case 'list':
      final o = _obj(
        value,
        ['type', 'items', 'style'],
        ['type', 'items', 'style'],
      );
      return {
        'type': 'list',
        'items': _arr(o['items'], 0, 30).map((v) {
          final r = _obj(v, ['text', 'done'], ['text']);
          return {
            'text': _text(r['text']),
            'done': r.containsKey('done') ? _bool(r['done']) : false,
          };
        }).toList(),
        'style': _pick(o['style'], ['bullet', 'check']),
      };
    case 'table':
      final o = _obj(
        value,
        ['type', 'columns', 'rows'],
        ['type', 'columns', 'rows'],
      );
      final columns = _arr(
        o['columns'],
        1,
        6,
      ).map((v) => _text(v, 80, true)).toList();
      return {
        'type': 'table',
        'columns': columns,
        'rows': _arr(o['rows'], 0, 20)
            .map(
              (v) => _arr(
                v,
                columns.length,
                columns.length,
              ).map((c) => _text(c, 80)).toList(),
            )
            .toList(),
      };
    case 'chart':
      final o = _obj(
        value,
        ['type', 'kind', 'unit', 'labels', 'series'],
        ['type', 'kind', 'labels', 'series'],
      );
      final labels = _arr(
        o['labels'],
        1,
        30,
      ).map((v) => _text(v, 80, true)).toList();
      return {
        'type': 'chart',
        'kind': _pick(o['kind'], ['bar', 'line']),
        ..._optText(o, 'unit', 24, true),
        'labels': labels,
        'series': _arr(o['series'], 1, 3).map((v) {
          final r = _obj(v, ['name', 'values'], ['name', 'values']);
          return {
            'name': _text(r['name'], 80, true),
            'values': _arr(
              r['values'],
              labels.length,
              labels.length,
            ).map(_num).toList(),
          };
        }).toList(),
      };
    case 'code':
      final o = _obj(value, ['type', 'language', 'text'], ['type', 'text']);
      return {
        'type': 'code',
        ..._optText(o, 'language', 32, true),
        'text': _text(o['text'], 4000),
      };
    case 'diffStat':
      final o = _obj(value, ['type', 'files'], ['type', 'files']);
      return {
        'type': 'diffStat',
        'files': _arr(o['files'], 0, 30).map((v) {
          final r = _obj(
            v,
            ['path', 'added', 'removed'],
            ['path', 'added', 'removed'],
          );
          return {
            'path': _text(r['path'], 256),
            'added': _int(r['added'], 0, 9007199254740991),
            'removed': _int(r['removed'], 0, 9007199254740991),
          };
        }).toList(),
      };
    case 'progress':
      final o = _obj(
        value,
        ['type', 'label', 'value'],
        ['type', 'label', 'value'],
      );
      final n = _num(o['value']);
      if (n < 0 || n > 1) _fail();
      return {
        'type': 'progress',
        'label': _text(o['label'], 120, true),
        'value': n,
      };
    case 'callout':
      final o = _obj(value, ['type', 'tone', 'text'], ['type', 'tone', 'text']);
      return {
        'type': 'callout',
        'tone': _pick(o['tone'], ['info', 'warning', 'success']),
        'text': _text(o['text'], 500),
      };
    case 'link':
      final o = _obj(value, ['type', 'label', 'url'], ['type', 'label', 'url']);
      final url = o['url'];
      if (url is! String ||
          _scalars(url) > 2048 ||
          RegExp(r'[\s\x00-\x20\x7f-\x9f\\]', unicode: true).hasMatch(url) ||
          !RegExp(r'^https://', caseSensitive: false).hasMatch(url)) {
        _fail();
      }
      final uri = Uri.tryParse(url);
      if (_controls.hasMatch(url)) _fail();
      if (uri == null ||
          uri.scheme.toLowerCase() != 'https' ||
          uri.host.isEmpty ||
          uri.port > 65535 ||
          uri.userInfo.isNotEmpty ||
          url.split('/')[2].contains('@')) {
        _fail();
      }
      return {
        'type': 'link',
        'label': _text(o['label'], 120, true, 1),
        'url': url,
      };
    default:
      _fail();
  }
}

Map<String, dynamic> _normalize(Object? input) {
  _bounded(input);
  final o = _obj(
    input,
    ['v', 'id', 'title', 'body', 'ask'],
    ['v', 'id', 'title', 'body'],
  );
  if (o['v'] != 1 || o['v'] is! num) _fail(GenUiProblem.version);
  return {
    'v': 1,
    'id': _id(o['id'], r'^[a-z0-9-]{1,48}$'),
    'title': _text(o['title'], 120, true, 1),
    'body': _arr(o['body'], 0, 40).map(_node).toList(),
    if (o.containsKey('ask')) 'ask': _ask(o['ask']),
  };
}
