import 'dart:convert';
import 'dart:io';

import '../normalize_openapi.dart';

Never fail(String message) => throw StateError(message);

void expect(bool condition, String message) {
  if (!condition) fail(message);
}

Map<String, Map<String, dynamic>> operationsById(
  Map<String, dynamic> document,
) {
  final result = <String, Map<String, dynamic>>{};
  final paths = document['paths'] as Map<String, dynamic>;
  for (final pathItem in paths.values.cast<Map<String, dynamic>>()) {
    for (final method in httpMethods) {
      final operation = pathItem[method];
      if (operation is Map<String, dynamic>) {
        result[operation['operationId'] as String] = operation;
      }
    }
  }
  return result;
}

int countKeys(Object? value, String key) {
  if (value is List) {
    return value.fold(0, (count, item) => count + countKeys(item, key));
  }
  if (value is! Map) return 0;
  var count = value.containsKey(key) ? 1 : 0;
  for (final child in value.values) {
    count += countKeys(child, key);
  }
  return count;
}

void assertNoDuplicateUnionBranches(Object? value) {
  if (value is List) {
    for (final item in value) {
      assertNoDuplicateUnionBranches(item);
    }
    return;
  }
  if (value is! Map) return;
  for (final keyword in const ['anyOf', 'oneOf']) {
    final branches = value[keyword];
    if (branches is List) {
      final encoded = branches.map(jsonEncode).toList();
      expect(
        encoded.toSet().length == encoded.length,
        '$keyword still contains an exact duplicate: $branches',
      );
    }
  }
  for (final child in value.values) {
    assertNoDuplicateUnionBranches(child);
  }
}

void assertUnionFixture() {
  final fixture =
      jsonDecode(
            jsonEncode(<String, dynamic>{
              'tags': <dynamic>[],
              'paths': <String, dynamic>{},
              'components': {
                'schemas': {
                  'Union': {
                    'oneOf': [
                      {
                        'type': 'object',
                        'properties': {
                          'value': {'type': 'string'},
                        },
                      },
                      {
                        'properties': {
                          'value': {'type': 'string'},
                        },
                        'type': 'object',
                      },
                      {
                        'type': 'object',
                        'properties': {
                          'value': {'type': 'number'},
                        },
                      },
                    ],
                  },
                },
              },
            }),
          )
          as Map<String, dynamic>;
  final report = normalizeOpenApi(fixture);
  final branches =
      (((fixture['components'] as Map)['schemas'] as Map)['Union']
              as Map)['oneOf']
          as List;
  expect(report.duplicateUnionBranchesRemoved == 1, 'fixture dedup count');
  expect(branches.length == 2, 'fixture should retain two meaningful branches');
  expect(
    ((branches.last as Map)['properties'] as Map)['value'] is Map &&
        ((((branches.last as Map)['properties'] as Map)['value']
                as Map)['type'] ==
            'number'),
    'deduplication weakened a distinct union branch',
  );
}

Future<void> main(List<String> arguments) async {
  if (arguments.length != 1) {
    fail(
      'Usage: dart run tool/sdk/test/normalize_openapi_test.dart <contract>',
    );
  }
  assertUnionFixture();

  final sourceFile = File(arguments.single);
  final sourceText = await sourceFile.readAsString();
  final source = jsonDecode(sourceText) as Map<String, dynamic>;
  final normalized = jsonDecode(sourceText) as Map<String, dynamic>;
  final sourceOperations = operationsById(source);
  final report = normalizeOpenApi(normalized);
  final normalizedOperations = operationsById(normalized);

  expect(sourceOperations.length == 188, 'canonical operation count changed');
  expect(
    normalizedOperations.keys.toSet().containsAll(sourceOperations.keys) &&
        normalizedOperations.length == sourceOperations.length,
    'normalization did not preserve operation coverage',
  );
  expect(
    report.duplicateRootTagNames.length == 6,
    'expected 6 duplicate root tags, found ${report.duplicateRootTagNames.length}',
  );
  expect(report.operationIdsAnnotated == 188, 'operation ID annotation count');
  for (final operation in normalizedOperations.values) {
    expect(
      operation['x-opencode-operation-id'] == operation['operationId'],
      'operation ID metadata mismatch',
    );
  }
  expect(
    report.duplicateUnionBranchesRemoved == 26,
    'expected 26 duplicate union branches, found '
    '${report.duplicateUnionBranchesRemoved}',
  );
  assertNoDuplicateUnionBranches(normalized);
  expect(
    report.inlineMeaningfulUnionsHoisted == 116,
    'expected 116 inline meaningful unions to be hoisted',
  );
  final schemas = (normalized['components'] as Map)['schemas'] as Map;
  final componentRoots = <Object>{...schemas.values};
  void assertMeaningfulUnionsAreComponentRoots(Object? value) {
    if (value is List) {
      for (final item in value) {
        assertMeaningfulUnionsAreComponentRoots(item);
      }
      return;
    }
    if (value is! Map) return;
    if (value is Map<String, dynamic> && isMeaningfulUnion(value)) {
      expect(
        componentRoots.contains(value),
        'meaningful union remains inline after normalization',
      );
      return;
    }
    for (final child in value.values) {
      assertMeaningfulUnionsAreComponentRoots(child);
    }
  }

  assertMeaningfulUnionsAreComponentRoots(normalized);

  final locationOperationIds = <String>{};
  for (final entry in sourceOperations.entries) {
    final parameters = entry.value['parameters'];
    if (parameters is List &&
        parameters.any(
          (parameter) =>
              parameter is Map &&
              parameter['name'] == 'location' &&
              parameter['in'] == 'query' &&
              parameter['style'] == 'deepObject',
        )) {
      locationOperationIds.add(entry.key);
    }
  }
  expect(locationOperationIds.length == 31, 'expected 31 location operations');
  expect(report.locationParametersExpanded == 31, 'location expansion count');
  expect(report.locationWireParametersCreated == 62, 'location wire-key count');
  for (final operationId in locationOperationIds) {
    final parameters = normalizedOperations[operationId]!['parameters'] as List;
    final names = parameters
        .whereType<Map>()
        .where((parameter) => parameter['in'] == 'query')
        .map((parameter) => parameter['name'])
        .toList();
    expect(
      names.where((name) => name == 'location[directory]').length == 1 &&
          names.where((name) => name == 'location[workspace]').length == 1,
      '$operationId does not contain both exact location wire keys',
    );
    expect(
      !names.contains('location'),
      '$operationId retained deepObject location',
    );
  }

  final active = normalizedOperations['v2.session.active']!;
  final activeSchema =
      (((active['responses'] as Map)['200'] as Map)['content']
              as Map)['application/json']
          as Map;
  final activeData =
      ((((activeSchema['schema'] as Map)['properties'] as Map)['data'])) as Map;
  expect(
    !activeData.containsKey('patternProperties'),
    'active pattern remains',
  );
  expect(
    ((activeData['additionalProperties'] as Map)[r'$ref'] ==
        '#/components/schemas/SessionActive'),
    'active map value schema changed',
  );
  expect(
    ((activeData['propertyNames'] as Map)['pattern'] == '^ses'),
    'active map key constraint was not retained',
  );

  final historyParameters =
      normalizedOperations['v2.session.history']!['parameters'] as List;
  for (final name in const ['limit', 'after']) {
    final parameter = historyParameters.whereType<Map>().singleWhere(
      (parameter) => parameter['name'] == name,
    );
    expect(
      (parameter['schema'] as Map)['type'] == 'number',
      'history $name is not numeric',
    );
  }
  expect(report.historyParametersMadeNumeric == 2, 'history numeric count');

  final nullableSchemas = <Map>[
    (((normalizedOperations['experimental.workspace.create']!['requestBody']
                    as Map)['content']
                as Map)['application/json']
            as Map)['schema']
        as Map,
    (((normalizedOperations['experimental.workspace.warp']!['requestBody']
                    as Map)['content']
                as Map)['application/json']
            as Map)['schema']
        as Map,
    schemas['GlobalSession'] as Map,
    schemas['Workspace'] as Map,
  ];
  final nullableProperties = <Map>[
    (nullableSchemas[0]['properties'] as Map)['branch'] as Map,
    (nullableSchemas[0]['properties'] as Map)['extra'] as Map,
    (nullableSchemas[1]['properties'] as Map)['id'] as Map,
    (nullableSchemas[2]['properties'] as Map)['project'] as Map,
    (nullableSchemas[3]['properties'] as Map)['branch'] as Map,
    (nullableSchemas[3]['properties'] as Map)['directory'] as Map,
    (nullableSchemas[3]['properties'] as Map)['extra'] as Map,
  ];
  expect(report.nullableUnionsMarked == 7, 'nullable union count');
  expect(
    nullableProperties.every((property) => property['nullable'] == true),
    'an explicit null union lacks generator nullability metadata',
  );
  expect(
    ((nullableProperties[2]['anyOf'] as List).last as Map)['type'] == 'null' &&
        ((nullableProperties[3]['anyOf'] as List).last as Map)['type'] ==
            'null',
    'meaningful required nullable unions were flattened',
  );

  const expectedRenames = {
    'ModelCapabilities': 'ModelV2Capabilities',
    'Event.tui.command.execute': 'EventTuiCommandExecuteSchema2',
    'Event.tui.prompt.append': 'EventTuiPromptAppendSchema2',
    'Event.tui.session.select': 'EventTuiSessionSelectSchema2',
    'Event.tui.toast.show': 'EventTuiToastShowSchema2',
    'question.rejected': 'QuestionRejectedSchema2',
    'question.replied': 'QuestionRepliedSchema2',
    'session.status': 'SessionStatusSchema2',
    'ProviderApi': 'ProviderApiModel',
  };
  expect(
    jsonEncode(report.componentRenames) == jsonEncode(expectedRenames),
    'unexpected collision renames: ${report.componentRenames}',
  );
  expect(report.componentRefsRewritten == 9, 'component ref rewrite count');
  final dartNames = schemas.keys
      .map((name) => dartTypeName(name as String))
      .toList();
  expect(
    dartNames.toSet().length == dartNames.length,
    'normalized components still collide after Dart sanitization',
  );

  expect(
    countKeys(source, 'x-effect-stream') == 1,
    'canonical stream inventory',
  );
  expect(
    countKeys(normalized, 'x-effect-stream') == 0 &&
        report.effectStreamExtensionsRemoved == 1,
    'generator-irrelevant stream schema was not stripped exactly once',
  );

  for (final operationId in sourceOperations.keys) {
    final before = sourceOperations[operationId]!['requestBody'];
    final after = normalizedOperations[operationId]!['requestBody'];
    if (before is Map && after is Map) {
      expect(
        before['required'] == after['required'],
        '$operationId requestBody requiredness changed',
      );
    }
  }
  final config =
      jsonDecode(await File('tool/sdk/generator-config.json').readAsString())
          as Map;
  expect(
    config['disallowAdditionalPropertiesIfNotPresent'] == false,
    'additionalProperties mode is not OAS-compliant',
  );
  expect(
    await sourceFile.readAsString() == sourceText,
    'normalization modified the canonical source file',
  );

  stdout.writeln(jsonEncode(report.toJson()));
}
