import 'dart:convert';
import 'dart:io';

import 'contract_matrix_support.dart';

const _requiredTotals = <String, int>{
  'paths': 162,
  'operations': 188,
  'GET': 87,
  'POST': 78,
  'DELETE': 14,
  'PATCH': 6,
  'PUT': 3,
  'schemas': 472,
  'eventVariants': 89,
  'parameters': 418,
  'requestSchemaSlots': 60,
  'responseObjects': 520,
  'responseSchemaSlots': 497,
  'enumSites': 618,
  'enumEntries': 871,
};

Future<void> main(List<String> arguments) async {
  if (arguments.length != 5) {
    stderr.writeln(
      'Usage: dart run tool/sdk/verify_contract_matrix.dart '
      '<canonical-openapi> <manifest> <package> <matrix-json> <matrix-md>',
    );
    exitCode = 64;
    return;
  }
  if (sha256Hex(utf8.encode('abc')) !=
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad') {
    throw StateError(
      'Internal SHA-256 implementation failed its known vector.',
    );
  }
  final canonicalFile = File(arguments[0]);
  final document =
      jsonDecode(await canonicalFile.readAsString()) as Map<String, dynamic>;
  final manifest =
      jsonDecode(await File(arguments[1]).readAsString())
          as Map<String, dynamic>;
  final package = Directory(arguments[2]);
  final matrixFile = File(arguments[3]);
  final markdownFile = File(arguments[4]);
  final matrix =
      jsonDecode(await matrixFile.readAsString()) as Map<String, dynamic>;
  if (matrix['formatVersion'] != 1) {
    throw StateError(
      'Unsupported SDK matrix format ${matrix['formatVersion']}.',
    );
  }
  final stableJson = '${const JsonEncoder.withIndent('  ').convert(matrix)}\n';
  if (await matrixFile.readAsString() != stableJson) {
    throw StateError('SDK matrix JSON is not deterministically formatted.');
  }
  final expectedMarkdown = markdownForMatrix(matrix);
  if (await markdownFile.readAsString() != expectedMarkdown) {
    throw StateError('SDK matrix Markdown is stale or non-deterministic.');
  }

  final computed = <String, int>{};
  final links = await parseGeneratedOperationLinks(package);
  final operations = (matrix['operations'] as List)
      .cast<Map<String, dynamic>>();
  final matrixOperations = <String, Map<String, dynamic>>{};
  for (final operation in operations) {
    final id = operation['operationId'] as String;
    if (matrixOperations[id] != null) {
      throw StateError('Duplicate matrix operationId $id.');
    }
    matrixOperations[id] = operation;
  }
  final paths = (document['paths'] as Map).cast<String, dynamic>();
  computed['paths'] = paths.length;
  final canonicalOperationIds = <String>{};
  final canonicalErrors = <Map<String, Object?>>[];
  final deprecated = <String>{};
  var parameters = 0;
  var requestSchemaSlots = 0;
  var responseObjects = 0;
  var responseSchemaSlots = 0;
  final methodCounts = <String, int>{};
  for (final pathEntry in paths.entries) {
    final pathItem = (pathEntry.value as Map).cast<String, dynamic>();
    for (final method in contractHttpMethods) {
      final value = pathItem[method];
      if (value is! Map) continue;
      final operation = value.cast<String, dynamic>();
      final id = operation['operationId'];
      if (id is! String || id.isEmpty || !canonicalOperationIds.add(id)) {
        throw StateError('Missing or duplicate canonical operationId $id.');
      }
      final actual = matrixOperations[id];
      if (actual == null) {
        throw StateError('$id is absent from the SDK matrix.');
      }
      final upperMethod = method.toUpperCase();
      methodCounts.update(upperMethod, (count) => count + 1, ifAbsent: () => 1);
      _expect(actual['method'], upperMethod, '$id method');
      _expect(actual['path'], pathEntry.key, '$id path');
      _expectJson(actual['tags'], operation['tags'] ?? const [], '$id tags');
      _expect(
        actual['deprecated'],
        operation['deprecated'] == true,
        '$id deprecated',
      );
      if (operation['deprecated'] == true) deprecated.add(id);
      final operationPointer = '#/paths/${pointerPart(pathEntry.key)}/$method';
      final expectedParameters = <Map<String, Object?>>[];
      void collectParameters(Object? list, String source) {
        if (list is! List) return;
        for (var index = 0; index < list.length; index++) {
          final parameter = (list[index] as Map).cast<String, dynamic>();
          final schema = parameter['schema'];
          expectedParameters.add({
            'pointer': source == 'path'
                ? '#/paths/${pointerPart(pathEntry.key)}/parameters/$index'
                : '$operationPointer/parameters/$index',
            'source': source,
            'name': parameter['name'],
            'in': parameter['in'],
            'required': parameter['required'] == true,
            'deprecated': parameter['deprecated'] == true,
            'canonicalParameterHash': canonicalHash(parameter),
            if (schema != null) 'schemaHash': canonicalHash(schema),
            'canonical': parameter,
          });
        }
      }

      collectParameters(pathItem['parameters'], 'path');
      collectParameters(operation['parameters'], 'operation');
      _expectJson(actual['parameters'], expectedParameters, '$id parameters');
      parameters += expectedParameters.length;

      final expectedBody = _expectedRequestBody(
        operation,
        operationPointer,
        () => requestSchemaSlots++,
      );
      _expectJson(actual['requestBody'], expectedBody, '$id request body');
      final expectedResponses = <Map<String, Object?>>[];
      final responses = (operation['responses'] as Map).cast<String, dynamic>();
      final responseEntries = responses.entries.toList()
        ..sort((left, right) => left.key.compareTo(right.key));
      for (final responseEntry in responseEntries) {
        final response = (responseEntry.value as Map).cast<String, dynamic>();
        final expected = _expectedResponse(
          id,
          operationPointer,
          responseEntry.key,
          response,
          () => responseSchemaSlots++,
          canonicalErrors,
        );
        expectedResponses.add(expected);
        responseObjects++;
      }
      _expectJson(actual['responses'], expectedResponses, '$id responses');

      final link = links[id];
      if (link == null) throw StateError('$id has no generated Dart method.');
      _expectJson(actual['generated'], link.toJson(), '$id generated linkage');
      _expect(link.operationId, id, '$id operationId metadata');
      _expect(link.path, pathEntry.key, '$id generated path');
      _expect(link.httpMethod, upperMethod, '$id generated HTTP method');
      final replacement = transportReplacement(id);
      _expectJson(
        actual['transportReplacement'],
        replacement,
        '$id transport replacement',
      );
    }
  }
  if (canonicalOperationIds.length != links.length ||
      canonicalOperationIds.length != matrixOperations.length) {
    throw StateError(
      'Operation set mismatch: canonical ${canonicalOperationIds.length}, '
      'Dart ${links.length}, matrix ${matrixOperations.length}.',
    );
  }
  if (!_setEquals(deprecated, const {'permission.respond'})) {
    throw StateError('Canonical deprecated operations are $deprecated.');
  }
  computed
    ..['operations'] = canonicalOperationIds.length
    ..addAll({for (final entry in methodCounts.entries) entry.key: entry.value})
    ..['parameters'] = parameters
    ..['requestSchemaSlots'] = requestSchemaSlots
    ..['responseObjects'] = responseObjects
    ..['responseSchemaSlots'] = responseSchemaSlots;

  await _verifySchemas(document, package, matrix, computed);
  _verifyEvents(document, matrix, computed);
  _verifyEnums(document, matrix, computed);
  _verifyCanonicalOverrides(document, matrix);
  await _verifyRuntimeLinks(package, links);
  await _verifyErrorDescriptors(package, canonicalErrors);
  _verifyProvenance(
    matrix,
    manifest,
    sha256Hex(await canonicalFile.readAsBytes()),
  );
  final totals = (matrix['totals'] as Map).cast<String, dynamic>();
  for (final required in _requiredTotals.entries) {
    final found = computed[required.key];
    if (found != required.value) {
      throw StateError(
        '${required.key}: expected ${required.value}, recomputed $found.',
      );
    }
    if (totals[required.key] != found) {
      throw StateError(
        '${required.key}: matrix has ${totals[required.key]}, recomputed $found.',
      );
    }
  }
  stdout.writeln(jsonEncode({'verified': true, ...computed}));
}

void _verifyCanonicalOverrides(
  Map<String, dynamic> document,
  Map<String, dynamic> matrix,
) {
  final overrides = (matrix['canonicalOverrides'] as List).cast<Map>();
  if (overrides.length != 1) {
    throw StateError('Expected one explicit canonical override.');
  }
  final override = overrides.single;
  _expect(override['operationId'], 'v2.session.history', 'override operation');
  _expect(override['kind'], 'parameterSchemaOverride', 'override kind');
  _expect(
    override['canonicalSource'],
    'OpenAPI parameters are strings',
    'override canonical source',
  );
  _expect(
    override['upstreamGeneratedSource'],
    'JS SDK build patches parameters to numbers',
    'override upstream source',
  );
  _expect(
    override['parityTarget'],
    'upstream-generated-sdk',
    'override target',
  );
  final operation =
      ((document['paths'] as Map)['/api/session/{sessionID}/history']
              as Map)['get']
          as Map;
  final canonicalParameters = (operation['parameters'] as List).cast<Map>();
  final actualParameters = (override['parameters'] as List).cast<Map>();
  for (final name in const ['limit', 'after']) {
    final canonical = canonicalParameters.singleWhere(
      (item) => item['name'] == name,
    )['schema'];
    final actual = actualParameters.singleWhere((item) => item['name'] == name);
    _expectJson(
      actual['canonicalSchema'],
      canonical,
      '$name canonical override',
    );
    _expect(
      actual['canonicalSchemaHash'],
      canonicalHash(canonical),
      '$name canonical override hash',
    );
    _expectJson(actual['generatedSchema'], const {
      'type': 'number',
    }, '$name target');
    _expect(
      actual['generatedSchemaHash'],
      canonicalHash(const {'type': 'number'}),
      '$name target hash',
    );
  }
}

Map<String, Object?>? _expectedRequestBody(
  Map<String, dynamic> operation,
  String operationPointer,
  void Function() countSchema,
) {
  final value = operation['requestBody'];
  if (value is! Map) return null;
  final body = value.cast<String, dynamic>();
  final content = <Map<String, Object?>>[];
  final media = body['content'];
  if (media is Map) {
    final entries = media.entries.toList()
      ..sort(
        (left, right) => (left.key as String).compareTo(right.key as String),
      );
    for (final entry in entries) {
      final descriptor = (entry.value as Map).cast<String, dynamic>();
      final schema = descriptor['schema'];
      content.add({
        'pointer':
            '$operationPointer/requestBody/content/'
            '${pointerPart(entry.key as String)}',
        'mediaType': entry.key,
        if (schema != null) 'schemaHash': canonicalHash(schema),
      });
      if (schema != null) countSchema();
    }
  }
  return {
    'pointer': '$operationPointer/requestBody',
    'required': body['required'] == true,
    'canonicalRequestBodyHash': canonicalHash(body),
    'content': content,
  };
}

Map<String, Object?> _expectedResponse(
  String operationId,
  String operationPointer,
  String statusText,
  Map<String, dynamic> response,
  void Function() countSchema,
  List<Map<String, Object?>> errors,
) {
  final content = <Map<String, Object?>>[];
  final status = int.tryParse(statusText);
  final isError = status != null && (status < 200 || status >= 300);
  final media = response['content'];
  if (media is Map) {
    final entries = media.entries.toList()
      ..sort(
        (left, right) => (left.key as String).compareTo(right.key as String),
      );
    for (final entry in entries) {
      final descriptor = (entry.value as Map).cast<String, dynamic>();
      final schema = descriptor['schema'];
      final errorDescriptor = isError
          ? {
              'file': 'lib/src/http/error_contracts.g.dart',
              'operationId': operationId,
              'status': status,
              'mediaType': entry.key,
              'schemaHash': canonicalHash(schema ?? const <String, Object?>{}),
            }
          : null;
      content.add({
        'pointer':
            '$operationPointer/responses/${pointerPart(statusText)}/'
            'content/${pointerPart(entry.key as String)}',
        'mediaType': entry.key,
        if (schema != null) 'schemaHash': canonicalHash(schema),
        'errorDescriptor': ?errorDescriptor,
      });
      if (schema != null) countSchema();
      if (isError) {
        errors.add({
          'operationId': operationId,
          'status': status,
          'mediaType': entry.key,
          'schemaJson': jsonEncode(schema ?? const <String, Object?>{}),
        });
      }
    }
  }
  Map<String, Object?>? emptyError;
  if (isError && content.isEmpty) {
    emptyError = {
      'file': 'lib/src/http/error_contracts.g.dart',
      'operationId': operationId,
      'status': status,
      'mediaType': '',
      'schemaHash': canonicalHash(const <String, Object?>{}),
    };
    errors.add({
      'operationId': operationId,
      'status': status,
      'mediaType': '',
      'schemaJson': '{}',
    });
  }
  return {
    'pointer': '$operationPointer/responses/${pointerPart(statusText)}',
    'status': statusText,
    'description': response['description'],
    'canonicalResponseHash': canonicalHash(response),
    'content': content,
    'errorDescriptor': ?emptyError,
  };
}

Future<void> _verifySchemas(
  Map<String, dynamic> document,
  Directory package,
  Map<String, dynamic> matrix,
  Map<String, int> computed,
) async {
  final schemas = ((document['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final matrixSchemas = <String, Map<String, dynamic>>{
    for (final item in (matrix['schemas'] as List).cast<Map>())
      item['name'] as String: item.cast<String, dynamic>(),
  };
  final renames = _componentRenames(document);
  for (final entry in schemas.entries) {
    final item = matrixSchemas[entry.key];
    if (item == null) throw StateError('Missing schema map for ${entry.key}.');
    final normalizedName = renames[entry.key] ?? entry.key;
    final declaration = dartTypeNameForContract(normalizedName);
    final fileName = dartFileNameForContract(declaration);
    final relativeFile = 'lib/src/model/$fileName.dart';
    final file = File('${package.path}/$relativeFile');
    if (!await file.exists()) {
      final alias = _independentAliasType(
        normalizedName,
        (entry.value as Map).cast<String, dynamic>(),
        renames,
      );
      _expectJson(item, {
        'pointer': '#/components/schemas/${pointerPart(entry.key)}',
        'name': entry.key,
        'canonicalSchemaHash': canonicalHash(entry.value),
        'generated': {
          'normalizedName': normalizedName,
          'file': 'lib/src/deserialize.dart',
          'declaration': alias,
          'kind': 'generated-alias',
          'codecFile': 'lib/src/deserialize.dart',
          'codec': 'generic collection/scalar deserialize/serialize',
        },
      }, 'schema alias ${entry.key}');
      continue;
    }
    final source = await file.readAsString();
    if (!RegExp(
      '(?:class|enum)\\s+${RegExp.escape(declaration)}\\b',
    ).hasMatch(source)) {
      throw StateError('$relativeFile does not declare $declaration.');
    }
    final wrapper = source.contains('implements OpenCodeRawJsonValue');
    final enumDeclaration = RegExp(
      'enum\\s+${RegExp.escape(declaration)}\\b',
    ).hasMatch(source);
    if (wrapper &&
        (!source.contains('factory $declaration.fromJson(Object? json)') ||
            !source.contains('static const String openApiSchemaJson'))) {
      throw StateError('$declaration is not a descriptor-backed raw union.');
    }
    final generatedCodec = File(
      '${package.path}/lib/src/model/$fileName.g.dart',
    );
    final expected = {
      'pointer': '#/components/schemas/${pointerPart(entry.key)}',
      'name': entry.key,
      'canonicalSchemaHash': canonicalHash(entry.value),
      'generated': {
        'normalizedName': normalizedName,
        'file': relativeFile,
        'declaration': declaration,
        'kind': wrapper
            ? 'raw-union-wrapper'
            : enumDeclaration
            ? 'json-value-enum'
            : 'model',
        'codecFile':
            wrapper || enumDeclaration || !await generatedCodec.exists()
            ? relativeFile
            : 'lib/src/model/$fileName.g.dart',
        'codec': wrapper
            ? 'lossless Object? fromJson/toJson with openApiSchemaJson'
            : enumDeclaration
            ? '@JsonValue wire values'
            : 'json_serializable fromJson/toJson',
      },
    };
    _expectJson(item, expected, 'schema ${entry.key}');
  }
  if (matrixSchemas.length != schemas.length) {
    throw StateError('Matrix has extra schema mappings.');
  }
  computed['schemas'] = schemas.length;
}

String _independentAliasType(
  String schemaName,
  Map<String, dynamic> schema,
  Map<String, String> renames,
) {
  final type = schema['type'];
  if (type == 'string') return 'String';
  if (type == 'object') return 'Object';
  if (type != 'array') {
    throw StateError('$schemaName is neither emitted nor a supported alias.');
  }
  final items = (schema['items'] as Map).cast<String, dynamic>();
  final ref = items[r'$ref'];
  String itemType;
  if (ref is String) {
    final canonicalName = ref.substring('#/components/schemas/'.length);
    itemType = dartTypeNameForContract(renames[canonicalName] ?? canonicalName);
  } else if (items['type'] == 'object') {
    itemType = '${dartTypeNameForContract(schemaName)}Inner';
  } else {
    itemType = switch (items['type']) {
      'string' => 'String',
      'integer' => 'int',
      'number' => 'num',
      'boolean' => 'bool',
      _ => 'Object',
    };
  }
  return 'List<$itemType>';
}

Map<String, String> _componentRenames(Map<String, dynamic> document) {
  final schemas = ((document['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final groups = <String, List<String>>{};
  String typeName(String source) {
    final result = StringBuffer();
    for (final word in source.split(RegExp(r'[^A-Za-z0-9]+'))) {
      if (word.isEmpty) continue;
      result
        ..write(word[0].toUpperCase())
        ..write(word.substring(1));
    }
    return result.isEmpty ? 'Model' : result.toString();
  }

  for (final name in schemas.keys) {
    groups.putIfAbsent(typeName(name), () => []).add(name);
  }
  final apiTypes = <String>{};
  for (final tag in (document['tags'] as List).whereType<Map>()) {
    if (tag['name'] is String) apiTypes.add('${typeName(tag['name'])}Api');
  }
  final occupied = schemas.keys.toSet();
  String available(String preferred) {
    var value = preferred;
    var suffix = 2;
    while (!occupied.add(value)) {
      value = '$preferred$suffix';
      suffix++;
    }
    return value;
  }

  final result = <String, String>{};
  const overrides = <String, String>{
    'ModelCapabilities': 'ModelV2Capabilities',
  };
  for (final entry in overrides.entries) {
    if (!schemas.containsKey(entry.key)) continue;
    if (!occupied.add(entry.value)) {
      throw StateError('Component rename target exists: ${entry.value}.');
    }
    result[entry.key] = entry.value;
  }
  final groupEntries = groups.entries.toList()
    ..sort((left, right) => left.key.compareTo(right.key));
  for (final group in groupEntries) {
    if (group.value.length < 2) continue;
    group.value.sort((left, right) {
      final leftExact = left == group.key;
      final rightExact = right == group.key;
      if (leftExact != rightExact) return leftExact ? -1 : 1;
      return left.compareTo(right);
    });
    for (var index = 1; index < group.value.length; index++) {
      result[group.value[index]] = available('${group.key}Schema${index + 1}');
    }
  }
  final names = schemas.keys.toList()..sort();
  for (final name in names) {
    if (!result.containsKey(name) && apiTypes.contains(typeName(name))) {
      result[name] = available('${typeName(name)}Model');
    }
  }
  return result;
}

void _verifyEvents(
  Map<String, dynamic> document,
  Map<String, dynamic> matrix,
  Map<String, int> computed,
) {
  final schemas = ((document['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final branches = ((schemas['Event'] as Map)['anyOf'] as List);
  final event = (matrix['event'] as Map).cast<String, dynamic>();
  _expect(event['pointer'], '#/components/schemas/Event', 'Event pointer');
  _expect(
    event['discriminatorProperty'],
    'type',
    'Event discriminator property',
  );
  final actual = event['branches'] as List;
  if (actual.length != branches.length) {
    throw StateError('Event branch count mismatch.');
  }
  final discriminators = <String>{};
  for (var index = 0; index < branches.length; index++) {
    final branch = branches[index] as Map;
    final ref = branch[r'$ref'] as String;
    final component = ref.substring('#/components/schemas/'.length);
    final schema = (schemas[component] as Map).cast<String, dynamic>();
    final properties = (schema['properties'] as Map).cast<String, dynamic>();
    final typeSchema = (properties['type'] as Map).cast<String, dynamic>();
    final values = typeSchema['enum'] as List;
    if (values.length != 1 ||
        values.single is! String ||
        !discriminators.add(values.single as String)) {
      throw StateError('Invalid Event discriminator for $component.');
    }
    _expectJson(actual[index], {
      'index': index,
      'pointer': '#/components/schemas/Event/anyOf/$index',
      'ref': ref,
      'component': component,
      'componentSchemaHash': canonicalHash(schema),
      'discriminator': values.single,
    }, 'Event branch $index');
  }
  computed['eventVariants'] = branches.length;
}

void _verifyEnums(
  Map<String, dynamic> document,
  Map<String, dynamic> matrix,
  Map<String, int> computed,
) {
  final expected = <Map<String, Object?>>[];
  void visit(Object? value, List<String> path) {
    if (value is List) {
      for (var index = 0; index < value.length; index++) {
        visit(value[index], [...path, '$index']);
      }
      return;
    }
    if (value is! Map) return;
    final values = value['enum'];
    if (values is List) {
      expected.add({
        'pointer': '#/${[...path, 'enum'].map(pointerPart).join('/')}',
        'schemaHash': canonicalHash(value),
        'values': values
            .map((item) => {'type': _enumType(item), 'value': item})
            .toList(growable: false),
      });
    }
    for (final entry in value.entries) {
      visit(entry.value, [...path, '${entry.key}']);
    }
  }

  visit(document, const []);
  expected.sort(
    (left, right) =>
        (left['pointer'] as String).compareTo(right['pointer'] as String),
  );
  _expectJson(matrix['enumSites'], expected, 'enum-site inventory');
  computed['enumSites'] = expected.length;
  computed['enumEntries'] = expected.fold<int>(
    0,
    (count, item) => count + (item['values'] as List).length,
  );
}

String _enumType(Object? value) {
  if (value == null) return 'null';
  if (value is bool) return 'boolean';
  if (value is int) return 'integer';
  if (value is num) return 'number';
  if (value is String) return 'string';
  throw StateError('Non-scalar enum value $value.');
}

Future<void> _verifyRuntimeLinks(
  Directory package,
  Map<String, GeneratedOperationLink> links,
) async {
  const expectedPaths = <String, String>{
    'global.event': "path: '/global/event'",
    'event.subscribe': "path: '/event'",
    'v2.event.subscribe': "path: '/api/event'",
    'v2.session.events':
        "path: '/api/session/\${encodeOpenCodePathSegment(sessionID)}/event'",
    'v2.fs.read': "'/api/fs/read/\$encodedTail'",
  };
  for (final entry in expectedPaths.entries) {
    final replacement = transportReplacement(entry.key)!;
    final source = await File(
      '${package.path}/${replacement['file']}',
    ).readAsString();
    if (!source.contains('${replacement['declaration']}') ||
        !source.contains('${replacement['method']}(') ||
        !source.contains(entry.value)) {
      throw StateError('Runtime replacement ${entry.key} is not linked.');
    }
    if (replacement['kind'] == 'sse' &&
        links[entry.key]?.dartDeprecated != true) {
      throw StateError(
        'Unsafe generated SSE method ${entry.key} is not deprecated.',
      );
    }
  }
}

Future<void> _verifyErrorDescriptors(
  Directory package,
  List<Map<String, Object?>> expected,
) async {
  final inventory =
      jsonDecode(
            await File(
              '${package.path}/tool/http_wire_inventory.json',
            ).readAsString(),
          )
          as Map<String, dynamic>;
  _expectJson(
    inventory['errorResponses'],
    expected,
    'generated error descriptor inventory',
  );
  if (expected.length != 332) {
    throw StateError(
      'Expected 332 error descriptors, found ${expected.length}.',
    );
  }
  final source = await File(
    '${package.path}/lib/src/http/error_contracts.g.dart',
  ).readAsString();
  final keys = 'OpenCodeErrorContractKey('.allMatches(source).length;
  if (keys != expected.length * 2) {
    throw StateError(
      'Error registry has $keys key occurrences for ${expected.length} descriptors.',
    );
  }
}

void _verifyProvenance(
  Map<String, dynamic> matrix,
  Map<String, dynamic> manifest,
  String canonicalSha,
) {
  final source = (manifest['source'] as Map).cast<String, dynamic>();
  final generator = (manifest['generator'] as Map).cast<String, dynamic>();
  _expectJson(matrix['provenance'], {
    'canonicalFile': source['file'],
    'canonicalSha256': canonicalSha,
    'schemaHashAlgorithm': 'sha256(canonical-json-recursive-key-sort)',
    'upstreamRepository': source['repository'],
    'upstreamCommit': source['commit'],
    'upstreamPath': source['path'],
    'generator': generator['name'],
    'generatorVersion': generator['version'],
    'generatedPackage': 'packages/opencode_sdk',
  }, 'matrix provenance');
  _expect(canonicalSha, source['sha256'], 'canonical source SHA-256');
}

void _expect(Object? actual, Object? expected, String label) {
  if (actual != expected) {
    throw StateError('$label: expected $expected, found $actual.');
  }
}

void _expectJson(Object? actual, Object? expected, String label) {
  if (canonicalJson(actual) != canonicalJson(expected)) {
    throw StateError(
      '$label differs.\nExpected: ${canonicalJson(expected)}\n'
      'Actual: ${canonicalJson(actual)}',
    );
  }
}

bool _setEquals(Set<String> left, Set<String> right) =>
    left.length == right.length && left.containsAll(right);
