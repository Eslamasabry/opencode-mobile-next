import 'dart:convert';
import 'dart:io';

import 'contract_matrix_support.dart';
import 'normalize_openapi.dart';

Future<void> main(List<String> arguments) async {
  if (arguments.length != 5) {
    stderr.writeln(
      'Usage: dart run tool/sdk/generate_contract_matrix.dart '
      '<canonical-openapi> <manifest> <package> <matrix-json> <matrix-md>',
    );
    exitCode = 64;
    return;
  }
  final canonicalFile = File(arguments[0]);
  final document =
      jsonDecode(await canonicalFile.readAsString()) as Map<String, dynamic>;
  final manifest =
      jsonDecode(await File(arguments[1]).readAsString())
          as Map<String, dynamic>;
  final package = Directory(arguments[2]);
  final links = await parseGeneratedOperationLinks(package);
  final paths = document['paths'] as Map<String, dynamic>;
  final operations = <Map<String, Object?>>[];
  final methodCounts = <String, int>{};
  var parameterCount = 0;
  var requestSchemaSlots = 0;
  var responseObjects = 0;
  var responseSchemaSlots = 0;

  for (final pathEntry in paths.entries) {
    final pathItem = pathEntry.value as Map<String, dynamic>;
    for (final method in contractHttpMethods) {
      final operation = pathItem[method];
      if (operation is! Map<String, dynamic>) continue;
      final operationId = operation['operationId'] as String;
      final link = links[operationId];
      if (link == null) {
        throw StateError('No generated Dart operation for $operationId.');
      }
      final operationPointer = '#/paths/${pointerPart(pathEntry.key)}/$method';
      final parameters = <Map<String, Object?>>[];
      void addParameters(Object? value, String source) {
        if (value is! List) return;
        for (var index = 0; index < value.length; index++) {
          final parameter = (value[index] as Map).cast<String, dynamic>();
          final schema = parameter['schema'];
          parameters.add({
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

      addParameters(pathItem['parameters'], 'path');
      addParameters(operation['parameters'], 'operation');
      parameterCount += parameters.length;

      Map<String, Object?>? requestBody;
      final canonicalRequestBody = operation['requestBody'];
      if (canonicalRequestBody is Map) {
        final body = canonicalRequestBody.cast<String, dynamic>();
        final content = <Map<String, Object?>>[];
        final media = body['content'];
        if (media is Map) {
          final entries = media.entries.toList()
            ..sort(
              (left, right) =>
                  (left.key as String).compareTo(right.key as String),
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
            if (schema != null) requestSchemaSlots++;
          }
        }
        requestBody = {
          'pointer': '$operationPointer/requestBody',
          'required': body['required'] == true,
          'canonicalRequestBodyHash': canonicalHash(body),
          'content': content,
        };
      }

      final responses = <Map<String, Object?>>[];
      final canonicalResponses = (operation['responses'] as Map)
          .cast<String, dynamic>();
      final responseEntries = canonicalResponses.entries.toList()
        ..sort((left, right) => left.key.compareTo(right.key));
      for (final responseEntry in responseEntries) {
        final response = (responseEntry.value as Map).cast<String, dynamic>();
        final content = <Map<String, Object?>>[];
        final status = int.tryParse(responseEntry.key);
        final isError = status != null && (status < 200 || status >= 300);
        final canonicalContent = response['content'];
        if (canonicalContent is Map) {
          final mediaEntries = canonicalContent.entries.toList()
            ..sort(
              (left, right) =>
                  (left.key as String).compareTo(right.key as String),
            );
          for (final mediaEntry in mediaEntries) {
            final media = (mediaEntry.value as Map).cast<String, dynamic>();
            final schema = media['schema'];
            content.add({
              'pointer':
                  '$operationPointer/responses/'
                  '${pointerPart(responseEntry.key)}/content/'
                  '${pointerPart(mediaEntry.key as String)}',
              'mediaType': mediaEntry.key,
              if (schema != null) 'schemaHash': canonicalHash(schema),
              if (isError)
                'errorDescriptor': {
                  'file': 'lib/src/http/error_contracts.g.dart',
                  'operationId': operationId,
                  'status': status,
                  'mediaType': mediaEntry.key,
                  'schemaHash': canonicalHash(
                    schema ?? const <String, Object?>{},
                  ),
                },
            });
            if (schema != null) responseSchemaSlots++;
          }
        }
        responses.add({
          'pointer':
              '$operationPointer/responses/'
              '${pointerPart(responseEntry.key)}',
          'status': responseEntry.key,
          'description': response['description'],
          'canonicalResponseHash': canonicalHash(response),
          'content': content,
          if (isError && content.isEmpty)
            'errorDescriptor': {
              'file': 'lib/src/http/error_contracts.g.dart',
              'operationId': operationId,
              'status': status,
              'mediaType': '',
              'schemaHash': canonicalHash(const <String, Object?>{}),
            },
        });
        responseObjects++;
      }

      final upperMethod = method.toUpperCase();
      methodCounts.update(upperMethod, (value) => value + 1, ifAbsent: () => 1);
      operations.add({
        'operationId': operationId,
        'method': upperMethod,
        'path': pathEntry.key,
        'tags': (operation['tags'] as List? ?? const <Object?>[]),
        'deprecated': operation['deprecated'] == true,
        'parameters': parameters,
        'requestBody': requestBody,
        'responses': responses,
        'generated': link.toJson(),
        'transportReplacement': transportReplacement(operationId),
      });
    }
  }
  operations.sort((left, right) {
    final leftGenerated = left['generated'] as Map<String, Object?>;
    final rightGenerated = right['generated'] as Map<String, Object?>;
    final fileOrder = (leftGenerated['file'] as String).compareTo(
      rightGenerated['file'] as String,
    );
    if (fileOrder != 0) return fileOrder;
    return (leftGenerated['method'] as String).compareTo(
      rightGenerated['method'] as String,
    );
  });

  final schemas = await _schemaInventory(document, package);
  final eventVariants = _eventInventory(document);
  final enumSites = _enumInventory(document);
  final enumEntries = enumSites.fold<int>(
    0,
    (count, site) => count + (site['values'] as List).length,
  );
  final source = manifest['source'] as Map<String, dynamic>;
  final totals = <String, Object?>{
    'paths': paths.length,
    'operations': operations.length,
    'GET': methodCounts['GET'] ?? 0,
    'POST': methodCounts['POST'] ?? 0,
    'DELETE': methodCounts['DELETE'] ?? 0,
    'PATCH': methodCounts['PATCH'] ?? 0,
    'PUT': methodCounts['PUT'] ?? 0,
    'schemas': schemas.length,
    'eventVariants': eventVariants.length,
    'parameters': parameterCount,
    'requestSchemaSlots': requestSchemaSlots,
    'responseObjects': responseObjects,
    'responseSchemaSlots': responseSchemaSlots,
    'enumSites': enumSites.length,
    'enumEntries': enumEntries,
  };
  final matrix = <String, Object?>{
    'formatVersion': 1,
    'provenance': {
      'canonicalFile': source['file'],
      'canonicalSha256': sha256Hex(await canonicalFile.readAsBytes()),
      'schemaHashAlgorithm': 'sha256(canonical-json-recursive-key-sort)',
      'upstreamRepository': source['repository'],
      'upstreamCommit': source['commit'],
      'upstreamPath': source['path'],
      'generator': (manifest['generator'] as Map)['name'],
      'generatorVersion': (manifest['generator'] as Map)['version'],
      'generatedPackage': 'packages/opencode_sdk',
    },
    'totals': totals,
    'canonicalOverrides': _canonicalOverrides(document),
    'operations': operations,
    'schemas': schemas,
    'event': {
      'pointer': '#/components/schemas/Event',
      'discriminatorProperty': 'type',
      'branches': eventVariants,
    },
    'enumSites': enumSites,
  };
  final jsonFile = File(arguments[3]);
  final markdownFile = File(arguments[4]);
  await jsonFile.parent.create(recursive: true);
  await jsonFile.writeAsString(
    '${const JsonEncoder.withIndent('  ').convert(matrix)}\n',
    flush: true,
  );
  await markdownFile.writeAsString(
    markdownForMatrix(matrix.cast<String, dynamic>()),
    flush: true,
  );
  stdout.writeln(jsonEncode(totals));
}

List<Map<String, Object?>> _canonicalOverrides(Map<String, dynamic> document) {
  final operation =
      ((document['paths'] as Map)['/api/session/{sessionID}/history']
              as Map)['get']
          as Map;
  final parameters = (operation['parameters'] as List).cast<Map>();
  return [
    {
      'operationId': 'v2.session.history',
      'kind': 'parameterSchemaOverride',
      'canonicalSource': 'OpenAPI parameters are strings',
      'upstreamGeneratedSource': 'JS SDK build patches parameters to numbers',
      'parityTarget': 'upstream-generated-sdk',
      'decision':
          'Generate numeric Dart query parameters to match the upstream JS SDK build override while retaining canonical string schemas in this matrix.',
      'parameters': [
        for (final name in const ['limit', 'after'])
          {
            'name': name,
            'canonicalSchema': Map<String, dynamic>.from(
              parameters.singleWhere((item) => item['name'] == name)['schema']
                  as Map,
            ),
            'canonicalSchemaHash': canonicalHash(
              parameters.singleWhere((item) => item['name'] == name)['schema'],
            ),
            'generatedSchema': const {'type': 'number'},
            'generatedSchemaHash': canonicalHash(const {'type': 'number'}),
          },
      ],
    },
  ];
}

Future<List<Map<String, Object?>>> _schemaInventory(
  Map<String, dynamic> document,
  Directory package,
) async {
  final normalized = jsonDecode(jsonEncode(document)) as Map<String, dynamic>;
  final renames = normalizeOpenApi(normalized).componentRenames;
  final schemas = ((document['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final result = <Map<String, Object?>>[];
  for (final entry in schemas.entries) {
    final generatedName = renames[entry.key] ?? entry.key;
    final className = dartTypeNameForContract(generatedName);
    final fileName = dartFileNameForContract(className);
    final relativeFile = 'lib/src/model/$fileName.dart';
    final sourceFile = File('${package.path}/$relativeFile');
    if (!await sourceFile.exists()) {
      result.add({
        'pointer': '#/components/schemas/${pointerPart(entry.key)}',
        'name': entry.key,
        'canonicalSchemaHash': canonicalHash(entry.value),
        'generated': {
          'normalizedName': generatedName,
          'file': 'lib/src/deserialize.dart',
          'declaration': _aliasType(
            generatedName,
            (entry.value as Map).cast<String, dynamic>(),
            renames,
          ),
          'kind': 'generated-alias',
          'codecFile': 'lib/src/deserialize.dart',
          'codec': 'generic collection/scalar deserialize/serialize',
        },
      });
      continue;
    }
    final source = await sourceFile.readAsString();
    final isWrapper = source.contains('implements OpenCodeRawJsonValue');
    final isEnum = RegExp(
      'enum\\s+${RegExp.escape(className)}\\b',
    ).hasMatch(source);
    if (!RegExp(
      '(?:class|enum)\\s+${RegExp.escape(className)}\\b',
    ).hasMatch(source)) {
      throw StateError('$relativeFile does not declare $className.');
    }
    final generatedCodec = File(
      '${package.path}/lib/src/model/$fileName.g.dart',
    );
    result.add({
      'pointer': '#/components/schemas/${pointerPart(entry.key)}',
      'name': entry.key,
      'canonicalSchemaHash': canonicalHash(entry.value),
      'generated': {
        'normalizedName': generatedName,
        'file': relativeFile,
        'declaration': className,
        'kind': isWrapper
            ? 'raw-union-wrapper'
            : isEnum
            ? 'json-value-enum'
            : 'model',
        'codecFile': isWrapper || isEnum || !await generatedCodec.exists()
            ? relativeFile
            : 'lib/src/model/$fileName.g.dart',
        'codec': isWrapper
            ? 'lossless Object? fromJson/toJson with openApiSchemaJson'
            : isEnum
            ? '@JsonValue wire values'
            : 'json_serializable fromJson/toJson',
      },
    });
  }
  result.sort(
    (left, right) =>
        (left['name'] as String).compareTo(right['name'] as String),
  );
  return result;
}

String _aliasType(
  String schemaName,
  Map<String, dynamic> schema,
  Map<String, String> renames,
) {
  if (schema['type'] == 'string') return 'String';
  if (schema['type'] == 'object') return 'Object';
  if (schema['type'] != 'array') {
    throw StateError('Unsupported generated alias $schemaName.');
  }
  final items = (schema['items'] as Map).cast<String, dynamic>();
  final ref = items[r'$ref'];
  if (ref is String) {
    final canonicalName = ref.substring('#/components/schemas/'.length);
    return 'List<${dartTypeNameForContract(renames[canonicalName] ?? canonicalName)}>';
  }
  final itemType = switch (items['type']) {
    'string' => 'String',
    'integer' => 'int',
    'number' => 'num',
    'boolean' => 'bool',
    'object' => '${dartTypeNameForContract(schemaName)}Inner',
    _ => 'Object',
  };
  return 'List<$itemType>';
}

List<Map<String, Object?>> _eventInventory(Map<String, dynamic> document) {
  final schemas = ((document['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final event = (schemas['Event'] as Map).cast<String, dynamic>();
  final branches = event['anyOf'] as List;
  return <Map<String, Object?>>[
    for (var index = 0; index < branches.length; index++)
      _eventBranch(schemas, branches[index] as Map, index),
  ];
}

Map<String, Object?> _eventBranch(
  Map<String, dynamic> schemas,
  Map branch,
  int index,
) {
  final ref = branch[r'$ref'] as String;
  final component = ref.substring('#/components/schemas/'.length);
  final schema = (schemas[component] as Map).cast<String, dynamic>();
  final properties = (schema['properties'] as Map).cast<String, dynamic>();
  final type = (properties['type'] as Map).cast<String, dynamic>();
  final values = type['enum'] as List;
  if (values.length != 1 || values.single is! String) {
    throw StateError('Event branch $component has no unique string type.');
  }
  return {
    'index': index,
    'pointer': '#/components/schemas/Event/anyOf/$index',
    'ref': ref,
    'component': component,
    'componentSchemaHash': canonicalHash(schema),
    'discriminator': values.single,
  };
}

List<Map<String, Object?>> _enumInventory(Map<String, dynamic> document) {
  final result = <Map<String, Object?>>[];
  void visit(Object? value, List<String> path) {
    if (value is List) {
      for (var index = 0; index < value.length; index++) {
        visit(value[index], [...path, '$index']);
      }
      return;
    }
    if (value is! Map) return;
    final enumValues = value['enum'];
    if (enumValues is List) {
      result.add({
        'pointer': '#/${[...path, 'enum'].map(pointerPart).join('/')}',
        'schemaHash': canonicalHash(value),
        'values': enumValues
            .map((item) {
              return {'type': _jsonType(item), 'value': item};
            })
            .toList(growable: false),
      });
    }
    for (final entry in value.entries) {
      visit(entry.value, [...path, '${entry.key}']);
    }
  }

  visit(document, const []);
  result.sort(
    (left, right) =>
        (left['pointer'] as String).compareTo(right['pointer'] as String),
  );
  return result;
}

String _jsonType(Object? value) {
  if (value == null) return 'null';
  if (value is bool) return 'boolean';
  if (value is int) return 'integer';
  if (value is num) return 'number';
  if (value is String) return 'string';
  throw StateError('Enum value is not a JSON scalar: $value.');
}
