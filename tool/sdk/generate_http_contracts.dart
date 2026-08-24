import 'dart:convert';
import 'dart:io';

import 'contract_matrix_support.dart';
import 'normalize_openapi.dart';

const _expectedCounts = <String, int>{
  'optionalRequestBodies': 46,
  'pathParameters': 99,
  'scalarUnionQueryParameters': 3,
  'nullableOmittedQueryParameters': 1,
  'errorResponses': 332,
  'operationsWithErrors': 186,
};

Future<void> main(List<String> arguments) async {
  if (arguments.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/sdk/generate_http_contracts.dart '
      '<canonical-openapi> <package>',
    );
    exitCode = 64;
    return;
  }

  final document =
      jsonDecode(await File(arguments[0]).readAsString())
          as Map<String, dynamic>;
  final package = Directory(arguments[1]);
  final optionalBodies = <Map<String, Object?>>[];
  final pathParameters = <Map<String, Object?>>[];
  final unionQueries = <Map<String, Object?>>[];
  final nullableOmittedQueries = <Map<String, Object?>>[];
  final errors = <Map<String, Object?>>[];
  final operationsWithErrors = <String>{};
  final paths = document['paths'] as Map<String, dynamic>;
  final unionDecoders = await _unionDecoderLocations(package);
  final deserializeSource = await File(
    '${package.path}/lib/src/deserialize.dart',
  ).readAsString();

  for (final pathEntry in paths.entries) {
    final pathItem = pathEntry.value as Map<String, dynamic>;
    for (final method in httpMethods) {
      final operation = pathItem[method];
      if (operation is! Map<String, dynamic>) continue;
      final operationId = operation['operationId'] as String;
      final requestBody = operation['requestBody'];
      if (requestBody is Map && requestBody['required'] != true) {
        optionalBodies.add({
          'operationId': operationId,
          'method': method.toUpperCase(),
          'path': pathEntry.key,
        });
      }

      final parameters = <Map<String, dynamic>>[
        ...(pathItem['parameters'] as List? ?? const <Object?>[])
            .whereType<Map>()
            .map((item) => item.cast<String, dynamic>()),
        ...(operation['parameters'] as List? ?? const <Object?>[])
            .whereType<Map>()
            .map((item) => item.cast<String, dynamic>()),
      ];
      for (final match in RegExp(r'\{([^}]+)\}').allMatches(pathEntry.key)) {
        final name = match.group(1)!;
        final parameter = parameters.singleWhere(
          (item) => item['in'] == 'path' && item['name'] == name,
        );
        pathParameters.add({
          'operationId': operationId,
          'name': name,
          'allowReserved': parameter['allowReserved'] == true,
        });
      }
      for (final parameter in parameters) {
        if (parameter['in'] != 'query') continue;
        final schema = parameter['schema'];
        if (schema is Map<String, dynamic> && _isScalarUnion(schema)) {
          unionQueries.add({
            'operationId': operationId,
            'name': parameter['name'] as String,
            'branches': _unionBranches(
              schema,
            ).map((branch) => branch['type']).toList(),
          });
        }
        if (operationId == 'v2.session.list' &&
            parameter['name'] == 'workspace' &&
            parameter['required'] != true) {
          nullableOmittedQueries.add({
            'operationId': operationId,
            'name': 'workspace',
          });
        }
      }

      final responses = operation['responses'] as Map<String, dynamic>;
      for (final responseEntry in responses.entries) {
        final status = int.tryParse(responseEntry.key);
        if (status == null || (status >= 200 && status < 300)) continue;
        final response = responseEntry.value as Map<String, dynamic>;
        final content = response['content'] as Map<String, dynamic>?;
        if (content == null || content.isEmpty) {
          errors.add({
            'operationId': operationId,
            'status': status,
            'mediaType': '',
            'schema': const <String, Object?>{},
            'schemaLocation': null,
          });
          operationsWithErrors.add(operationId);
          continue;
        }
        for (final mediaEntry in content.entries) {
          final media = mediaEntry.value as Map<String, dynamic>;
          errors.add({
            'operationId': operationId,
            'status': status,
            'mediaType': mediaEntry.key,
            'schema': media['schema'] ?? const <String, Object?>{},
            'schemaLocation': _schemaLocation(
              pathEntry.key,
              method,
              responseEntry.key,
              mediaEntry.key,
            ),
          });
          operationsWithErrors.add(operationId);
        }
      }
    }
  }

  final actualCounts = <String, int>{
    'optionalRequestBodies': optionalBodies.length,
    'pathParameters': pathParameters.length,
    'scalarUnionQueryParameters': unionQueries.length,
    'nullableOmittedQueryParameters': nullableOmittedQueries.length,
    'errorResponses': errors.length,
    'operationsWithErrors': operationsWithErrors.length,
  };
  var generatedModelErrorDecoders = 0;
  var generatedUnionWrapperErrorDecoders = 0;
  var losslessDescriptorErrorDecoders = 0;
  for (final error in errors) {
    final location = error['schemaLocation'] as String?;
    final unionType = location == null ? null : unionDecoders[location];
    final schema = error['schema'] as Map<String, dynamic>;
    final referencedType = _singleReferencedType(schema);
    final generatedType =
        unionType ??
        _generatedTypeForComponent(referencedType, deserializeSource);
    final hasGeneratedDecoder =
        generatedType != null &&
        deserializeSource.contains("case '$generatedType':");
    error['payloadType'] = hasGeneratedDecoder
        ? generatedType
        : 'OpenCodeLosslessErrorPayload';
    if (hasGeneratedDecoder) {
      if (unionType != null) {
        generatedUnionWrapperErrorDecoders++;
        error['decoderKind'] = 'generatedLosslessUnionWrapper';
      } else {
        generatedModelErrorDecoders++;
        error['decoderKind'] = 'generatedModel';
      }
    } else {
      losslessDescriptorErrorDecoders++;
      error['decoderKind'] = 'losslessDescriptorWrapper';
    }
  }
  actualCounts['errorDecoders'] = errors.length;
  actualCounts['generatedModelErrorDecoders'] = generatedModelErrorDecoders;
  actualCounts['generatedUnionWrapperErrorDecoders'] =
      generatedUnionWrapperErrorDecoders;
  actualCounts['losslessDescriptorErrorDecoders'] =
      losslessDescriptorErrorDecoders;
  for (final expected in _expectedCounts.entries) {
    if (actualCounts[expected.key] != expected.value) {
      throw StateError(
        '${expected.key}: expected ${expected.value}, '
        'found ${actualCounts[expected.key]}',
      );
    }
  }

  final inventory = <String, Object?>{
    'counts': actualCounts,
    'optionalRequestBodies': optionalBodies,
    'pathParameters': pathParameters,
    'scalarUnionQueryParameters': unionQueries,
    'nullableOmittedQueryParameters': nullableOmittedQueries,
    'errorDecoderCounts': <String, int>{
      'total': errors.length,
      'generatedModel': generatedModelErrorDecoders,
      'generatedLosslessUnionWrapper': generatedUnionWrapperErrorDecoders,
      'losslessDescriptorWrapper': losslessDescriptorErrorDecoders,
    },
    'errorDecoderContracts': errors.map((error) {
      return {
        'operationId': error['operationId'],
        'status': error['status'],
        'mediaType': error['mediaType'],
        'schemaJson': jsonEncode(error['schema']),
        'payloadType': error['payloadType'],
        'decoderKind': error['decoderKind'],
      };
    }).toList(),
    'errorResponses': errors.map((error) {
      return {
        'operationId': error['operationId'],
        'status': error['status'],
        'mediaType': error['mediaType'],
        'schemaJson': jsonEncode(error['schema']),
      };
    }).toList(),
  };
  final inventoryFile = File('${package.path}/tool/http_wire_inventory.json');
  await inventoryFile.parent.create(recursive: true);
  await inventoryFile.writeAsString(
    '${const JsonEncoder.withIndent('  ').convert(inventory)}\n',
    flush: true,
  );

  final contracts = StringBuffer()
    ..writeln("part of 'errors.dart';")
    ..writeln()
    ..writeln(
      'final Map<OpenCodeErrorContractKey, OpenCodeErrorContract> '
      'openCodeErrorContracts = {',
    );
  for (final error in errors) {
    final operationId = _dartString(error['operationId'] as String);
    final status = error['status'] as int;
    final mediaType = _dartString(error['mediaType'] as String);
    final schemaJson = _dartString(jsonEncode(error['schema']));
    final key =
        'OpenCodeErrorContractKey(operationId: $operationId, status: $status, '
        'mediaType: $mediaType, schemaJson: $schemaJson)';
    final payloadType = _dartString(error['payloadType'] as String);
    final decoder = error['payloadType'] == 'OpenCodeLosslessErrorPayload'
        ? 'decodeOpenCodeLosslessErrorPayload'
        : '(payload, contract) => decodeOpenCodeErrorModel('
              'payload, $payloadType, contract)';
    contracts.writeln(
      '  const $key: OpenCodeErrorContract($key, '
      'payloadType: $payloadType, decoder: $decoder),',
    );
  }
  contracts
    ..writeln('};')
    ..writeln()
    ..writeln('const Set<String> openCodeErrorFallbackOperations = {')
    ..writeln("  'global.event',")
    ..writeln("  'event.subscribe',")
    ..writeln("  'v2.session.events',")
    ..writeln("  'v2.event.subscribe',")
    ..writeln('};');
  final contractsFile = File(
    '${package.path}/lib/src/http/error_contracts.g.dart',
  );
  await contractsFile.parent.create(recursive: true);
  await contractsFile.writeAsString(contracts.toString(), flush: true);

  stdout.writeln(jsonEncode(actualCounts));
}

Future<Map<String, String>> _unionDecoderLocations(Directory package) async {
  final inventory =
      jsonDecode(
            await File(
              '${package.path}/tool/union_inventory.json',
            ).readAsString(),
          )
          as Map<String, dynamic>;
  final result = <String, String>{};
  for (final item in (inventory['covered'] as List).cast<Map>()) {
    final className = item['className'] as String;
    for (final location in (item['locations'] as List).cast<String>()) {
      result[location] = className;
    }
  }
  return result;
}

String _schemaLocation(
  String path,
  String method,
  String status,
  String mediaType,
) =>
    '#/paths/${_pointerToken(path)}/$method/responses/$status/content/'
    '${_pointerToken(mediaType)}/schema';

String _pointerToken(String value) =>
    value.replaceAll('~', '~0').replaceAll('/', '~1');

String? _singleReferencedType(Map<String, dynamic> schema) {
  final direct = schema[r'$ref'];
  if (direct is String) return direct.split('/').last;
  final branches = schema['anyOf'] ?? schema['oneOf'];
  if (branches is! List || branches.isEmpty) return null;
  final refs = branches
      .whereType<Map>()
      .map((branch) => branch[r'$ref'])
      .whereType<String>()
      .map((ref) => ref.split('/').last)
      .toSet();
  return refs.length == 1 &&
          branches.whereType<Map>().length == branches.length &&
          branches.cast<Map>().every((branch) => branch[r'$ref'] is String)
      ? refs.single
      : null;
}

String? _generatedTypeForComponent(
  String? component,
  String deserializeSource,
) {
  if (component == null) return null;
  final candidates = <String>{component, dartTypeNameForContract(component)};
  for (final candidate in candidates) {
    if (deserializeSource.contains("case '$candidate':")) return candidate;
  }
  return null;
}

List<Map<String, dynamic>> _unionBranches(Map<String, dynamic> schema) =>
    ((schema['anyOf'] ?? schema['oneOf']) as List).cast<Map<String, dynamic>>();

bool _isScalarUnion(Map<String, dynamic> schema) {
  final options = schema['anyOf'] ?? schema['oneOf'];
  if (options is! List || options.length < 2) return false;
  const scalarTypes = {'boolean', 'integer', 'number', 'string'};
  return options.whereType<Map>().length == options.length &&
      options.cast<Map>().every(
        (branch) => scalarTypes.contains(branch['type']),
      );
}

String _dartString(String value) => jsonEncode(value).replaceAll(r'$', r'\$');
