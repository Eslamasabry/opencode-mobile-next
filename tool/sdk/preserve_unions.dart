import 'dart:convert';
import 'dart:io';

import 'normalize_openapi.dart';

String _dartFilename(String className) => className
    .replaceAll(RegExp(r'[^A-Za-z0-9]+'), '_')
    .replaceAllMapped(
      RegExp(r'([A-Z]+)([A-Z][a-z])'),
      (match) => '${match[1]}_${match[2]}',
    )
    .replaceAllMapped(
      RegExp(r'([a-z0-9])([A-Z])'),
      (match) => '${match[1]}_${match[2]}',
    )
    .toLowerCase();

String _generatedClassName(String schemaName) {
  final className = dartTypeName(schemaName);
  // `part` is a Dart directive and dart-dio reserves it as a model name.
  return className == 'Part' ? 'ModelPart' : className;
}

String _dartString(String value) => jsonEncode(value).replaceAll(r'$', r'\$');

String _wrapperSource(String className, Map<String, dynamic> schema) =>
    '''
//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

import 'dart:convert';

import 'package:opencode_sdk/src/http/wire.dart';

/// Lossless JSON representation of an OpenAPI union.
///
/// dart-dio flattens anyOf/oneOf branches into an aggregate model, which can
/// reject valid branches or discard branch-specific fields. This wrapper keeps
/// the wire value intact while exposing the exact normalized schema descriptor.
class $className implements OpenCodeRawJsonValue {
  $className(Object? value) : value = _copyJsonValue(value);

  factory $className.fromJson(Object? json) => $className(json);

  static const String openApiSchemaJson = ${_dartString(jsonEncode(schema))};

  @override
  final Object? value;

  Object? toJson() => _copyJsonValue(value);

  Map<String, dynamic>? get objectValue =>
      value is Map<String, dynamic> ? value as Map<String, dynamic> : null;

  @override
  String toString() => jsonEncode(value);
}

Object? _copyJsonValue(Object? value) {
  if (value == null || value is String || value is num || value is bool) {
    return value;
  }
  if (value is List) {
    return value.map(_copyJsonValue).toList(growable: false);
  }
  if (value is Map) {
    return value.map<String, dynamic>((key, item) {
      if (key is! String) {
        throw ArgumentError.value(value, 'value', 'JSON object keys must be strings');
      }
      return MapEntry(key, _copyJsonValue(item));
    });
  }
  throw ArgumentError.value(value, 'value', 'Not a JSON value');
}
''';

Future<void> main(List<String> arguments) async {
  if (arguments.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/sdk/preserve_unions.dart <openapi> <package>',
    );
    exitCode = 64;
    return;
  }

  final document =
      jsonDecode(await File(arguments[0]).readAsString())
          as Map<String, dynamic>;
  final schemas =
      (document['components'] as Map<String, dynamic>)['schemas']
          as Map<String, dynamic>;
  final modelDirectory = Directory('${arguments[1]}/lib/src/model');
  final wrappers = <String>[];
  final covered = <Map<String, dynamic>>[];

  final referenceLocations = <String, List<String>>{};
  void collectReferenceLocations(Object? value, List<String> path) {
    if (value is List) {
      for (var index = 0; index < value.length; index++) {
        collectReferenceLocations(value[index], [...path, '$index']);
      }
      return;
    }
    if (value is! Map) return;
    final ref = value[r'$ref'];
    if (ref is String && ref.startsWith('#/components/schemas/')) {
      final name = ref.substring('#/components/schemas/'.length);
      final pointer = path
          .map((part) => part.replaceAll('~', '~0').replaceAll('/', '~1'))
          .join('/');
      referenceLocations.putIfAbsent(name, () => []).add('#/$pointer');
    }
    for (final entry in value.entries) {
      collectReferenceLocations(entry.value, [...path, '${entry.key}']);
    }
  }

  collectReferenceLocations(document, const []);

  for (final entry in schemas.entries) {
    final schema = entry.value;
    if (schema is! Map<String, dynamic> || !isMeaningfulUnion(schema)) {
      continue;
    }
    final className = _generatedClassName(entry.key);
    final file = File(
      '${modelDirectory.path}/${_dartFilename(className)}.dart',
    );
    if (!await file.exists()) {
      throw StateError(
        'dart-dio did not emit a model for union ${entry.key} ($className).',
      );
    }
    await file.writeAsString(_wrapperSource(className, schema), flush: true);
    wrappers.add(className);
    covered.add({
      'schema': entry.key,
      'className': className,
      'file': 'lib/src/model/${_dartFilename(className)}.dart',
      'locations': entry.key.startsWith('OpencodeSdkRawUnion')
          ? referenceLocations[entry.key] ?? const <String>[]
          : ['#/components/schemas/${entry.key}'],
    });
  }

  final deserializeFile = File('${arguments[1]}/lib/src/deserialize.dart');
  var deserializeSource = await deserializeFile.readAsString();
  for (final className in wrappers) {
    deserializeSource = deserializeSource.replaceAllMapped(
      RegExp(
        '${RegExp.escape(className)}\\.fromJson\\('
        r'\s*value as Map<String, dynamic>\s*\)',
      ),
      (_) => '$className.fromJson(value)',
    );
  }
  await deserializeFile.writeAsString(deserializeSource, flush: true);

  final inventory = File('${arguments[1]}/tool/union_inventory.json');
  await inventory.parent.create(recursive: true);
  await inventory.writeAsString(
    '${const JsonEncoder.withIndent('  ').convert({'losslessRawJsonUnions': wrappers, 'covered': covered, 'residualLossyUnions': <String>[]})}\n',
    flush: true,
  );
  stdout.writeln(
    jsonEncode({
      'losslessRawJsonUnions': wrappers.length,
      'wrappers': wrappers,
    }),
  );
}
