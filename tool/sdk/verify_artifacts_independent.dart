import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

const _httpMethods = <String>{
  'get',
  'put',
  'post',
  'delete',
  'options',
  'head',
  'patch',
  'trace',
};

const _requiredTotals = <String, int>{
  'paths': 162,
  'operations': 188,
  'schemas': 472,
  'generatedParameters': 449,
  'wrappers': 141,
  'errors': 332,
  'mixedAdditionalProperties': 6,
};

const _forcedDeprecations = <String>{
  'global.event',
  'event.subscribe',
  'v2.event.subscribe',
  'v2.session.events',
  'v2.fs.read',
};

const _mixedModelFiles = <String, String>{
  '#/components/schemas/AgentConfig': 'agent_config.dart',
  '#/components/schemas/ProviderConfig/properties/options':
      'provider_config_options.dart',
  '#/components/schemas/ProviderConfig/properties/models/additionalProperties/properties/variants/additionalProperties':
      'provider_config_models_value_variants_value.dart',
  '#/components/schemas/Config/properties/mode': 'config_mode.dart',
  '#/components/schemas/Config/properties/agent': 'config_agent.dart',
};

Future<void> main(List<String> arguments) async {
  if (arguments.length == 3 && arguments.first == '--print-source-hashes') {
    final package = Directory(arguments[1]);
    final root = Directory(arguments[2]);
    final trees = <String, List<Directory>>{
      'generatedApi': [Directory('${package.path}/lib/src/api')],
      'generatedModels': [Directory('${package.path}/lib/src/model')],
      'generatedRuntime': [
        Directory('${package.path}/lib/src/http'),
        Directory('${package.path}/lib/src/sse'),
      ],
      'runtimeSources': [Directory('${root.path}/tool/sdk/runtime')],
      'templates': [Directory('${root.path}/tool/sdk/templates')],
    };
    final testTemplates = _generationTestTemplates(root);
    stdout.writeln(
      const JsonEncoder.withIndent(' ').convert({
        for (final entry in trees.entries)
          entry.key: {
            'algorithm': 'sha256(relative-path-nul-bytes-v1)',
            'sha256': await _sourceTreeHash(entry.value),
          },
        'testTemplates': {
          'algorithm': 'sha256(relative-path-nul-bytes-v1)',
          'sha256': await _sourceFileSetHash(testTemplates, 'tool/sdk'),
        },
      }),
    );
    return;
  }
  var verifyHashes = true;
  if (arguments.isNotEmpty && arguments.first == '--skip-source-hashes') {
    verifyHashes = false;
    arguments = arguments.sublist(1);
  }
  if (arguments.length != 5) {
    stderr.writeln(
      'Usage: dart run tool/sdk/verify_artifacts_independent.dart '
      '[--skip-source-hashes] <canonical-openapi> <manifest> <package> '
      '<matrix-json> <matrix-md>',
    );
    exitCode = 64;
    return;
  }

  final canonicalFile = File(arguments[0]);
  final manifestFile = File(arguments[1]);
  final package = Directory(arguments[2]);
  final matrixFile = File(arguments[3]);
  final document =
      jsonDecode(await canonicalFile.readAsString()) as Map<String, dynamic>;
  final manifest =
      jsonDecode(await manifestFile.readAsString()) as Map<String, dynamic>;
  final matrix =
      jsonDecode(await matrixFile.readAsString()) as Map<String, dynamic>;

  _expect(
    _sha256(utf8.encode('abc')),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    'independent SHA-256 known vector',
  );
  _expect(
    _sha256(await canonicalFile.readAsBytes()),
    (manifest['source'] as Map)['sha256'],
    'canonical source hash',
  );

  final normalized = _deepCopy(document) as Map<String, dynamic>;
  final normalization = _normalizeIndependently(normalized);
  _verifyNormalizationLedger(
    document,
    normalized,
    normalization,
    manifest,
    matrix,
  );
  final declarations = await _indexDeclarations(package);
  final operations = await _parseApiOperations(package);
  final operationCounts = _verifyOperations(document, operations, matrix);
  final wrapperCount = await _verifyWrappers(normalized, declarations, package);
  final schemaCount = await _verifyCanonicalSchemas(
    document,
    normalized,
    normalization.renames,
    declarations,
    package,
    matrix,
  );
  final errorCount = await _verifyErrors(document, declarations, package);
  final mixedCount = await _verifyMixedAdditionalProperties(document, package);
  await _verifyRuntime(package);
  if (verifyHashes) {
    await _verifySourceHashes(manifest, package, manifestFile.parent.parent);
  }

  final totals = <String, int>{
    'paths': (document['paths'] as Map).length,
    'operations': operations.length,
    'schemas': schemaCount,
    'generatedParameters': operationCounts.generatedParameters,
    'wrappers': wrapperCount,
    'errors': errorCount,
    'mixedAdditionalProperties': mixedCount,
  };
  for (final expected in _requiredTotals.entries) {
    _expect(totals[expected.key], expected.value, expected.key);
  }
  stdout.writeln(jsonEncode({'independentlyVerified': true, ...totals}));
}

class _NormalizationResult {
  _NormalizationResult(this.renames);

  final Map<String, String> renames;
  var deepObjectsExpanded = 0;
  var historyParametersChanged = 0;
  var activeMapsChanged = 0;
  var duplicateBranchesRemoved = 0;
  var nullableMarkersAdded = 0;
  var unconstrainedUnionsCollapsed = 0;
  var effectExtensionsRemoved = 0;
  var inlineUnionsHoisted = 0;
}

_NormalizationResult _normalizeIndependently(Map<String, dynamic> document) {
  final renames = _componentRenames(document);
  final result = _NormalizationResult(renames);
  final components = document['components'] as Map<String, dynamic>;
  final originalSchemas = components['schemas'] as Map<String, dynamic>;
  components['schemas'] = <String, dynamic>{
    for (final entry in originalSchemas.entries)
      renames[entry.key] ?? entry.key: entry.value,
  };

  void rewriteRefs(Object? value) {
    if (value is List) {
      for (final child in value) {
        rewriteRefs(child);
      }
      return;
    }
    if (value is! Map) return;
    final ref = value[r'$ref'];
    if (ref is String && ref.startsWith('#/components/schemas/')) {
      final name = ref.substring('#/components/schemas/'.length);
      final replacement = renames[name];
      if (replacement != null) {
        value[r'$ref'] = '#/components/schemas/$replacement';
      }
    }
    for (final child in value.values) {
      rewriteRefs(child);
    }
  }

  rewriteRefs(document);
  final paths = (document['paths'] as Map).cast<String, dynamic>();
  for (final pathValue in paths.values) {
    final pathItem = (pathValue as Map).cast<String, dynamic>();
    for (final method in _httpMethods) {
      final value = pathItem[method];
      if (value is! Map) continue;
      final operation = value.cast<String, dynamic>();
      final parameters = operation['parameters'];
      if (parameters is List) {
        final expanded = <Object?>[];
        for (final value in parameters) {
          final parameter = (value as Map).cast<String, dynamic>();
          if (parameter['name'] == 'location' &&
              parameter['in'] == 'query' &&
              parameter['style'] == 'deepObject') {
            final schema = (parameter['schema'] as Map).cast<String, dynamic>();
            final properties = (schema['properties'] as Map)
                .cast<String, dynamic>();
            for (final property in const ['directory', 'workspace']) {
              expanded.add(<String, Object?>{
                'name': 'location[$property]',
                'in': 'query',
                'schema': properties[property],
                'required': false,
                if (parameter['description'] is String)
                  'description': parameter['description'],
              });
            }
            result.deepObjectsExpanded++;
          } else {
            expanded.add(parameter);
          }
        }
        operation['parameters'] = expanded;
      }
      if (operation['operationId'] == 'v2.session.history') {
        for (final value in (operation['parameters'] as List).cast<Map>()) {
          if (value['in'] == 'query' &&
              (value['name'] == 'limit' || value['name'] == 'after')) {
            final schema = value['schema'] as Map;
            if (schema['type'] != 'number') {
              schema['type'] = 'number';
              result.historyParametersChanged++;
            }
          }
        }
      }
      if (operation['operationId'] == 'v2.session.active') {
        final responses = operation['responses'] as Map;
        final response = responses['200'] as Map;
        final content = response['content'] as Map;
        final media = content['application/json'] as Map;
        final schema = media['schema'] as Map;
        final properties = schema['properties'] as Map;
        final data = properties['data'] as Map;
        final patterns = data['patternProperties'];
        if (patterns is Map && patterns.length == 1) {
          final entry = patterns.entries.single;
          data
            ..remove('patternProperties')
            ..['additionalProperties'] = entry.value
            ..['propertyNames'] = {'pattern': entry.key};
          result.activeMapsChanged++;
        }
      }
    }
  }

  void normalizeSchemas(Object? value) {
    if (value is List) {
      for (final child in value) {
        normalizeSchemas(child);
      }
      return;
    }
    if (value is! Map) return;
    if (value.remove('x-effect-stream') != null) {
      result.effectExtensionsRemoved++;
    }
    for (final keyword in const ['anyOf', 'oneOf']) {
      final branches = value[keyword];
      if (branches is! List) continue;
      final seen = <String>{};
      final unique = <Object?>[];
      for (final branch in branches) {
        if (seen.add(_canonicalJson(branch))) {
          unique.add(branch);
        } else {
          result.duplicateBranchesRemoved++;
        }
      }
      value[keyword] = unique;
    }
    final anyOf = value['anyOf'];
    if (anyOf is List && anyOf.length == 2 && anyOf.any(_isNullSchema)) {
      value['nullable'] = true;
      result.nullableMarkersAdded++;
    }
    if (anyOf is List && anyOf.any((item) => item is Map && item.isEmpty)) {
      value.remove('anyOf');
      result.unconstrainedUnionsCollapsed++;
    }
    for (final child in value.values.toList()) {
      normalizeSchemas(child);
    }
  }

  normalizeSchemas(document);
  final schemas = (components['schemas'] as Map).cast<String, dynamic>();
  final originalComponents = schemas.entries.toList();
  var ordinal = 1;
  String nextUnionName() {
    while (true) {
      final candidate =
          'OpencodeSdkRawUnion${ordinal.toString().padLeft(3, '0')}';
      ordinal++;
      if (!schemas.containsKey(candidate)) return candidate;
    }
  }

  void hoist(Object? value, {required bool componentRoot}) {
    if (value is List) {
      for (final child in value) {
        hoist(child, componentRoot: false);
      }
      return;
    }
    if (value is! Map) return;
    if (!componentRoot && _isMeaningfulUnion(value)) {
      final name = nextUnionName();
      schemas[name] = Map<String, dynamic>.from(value);
      value
        ..clear()
        ..[r'$ref'] = '#/components/schemas/$name';
      result.inlineUnionsHoisted++;
      return;
    }
    for (final child in value.values.toList()) {
      hoist(child, componentRoot: false);
    }
  }

  for (final entry in originalComponents) {
    hoist(entry.value, componentRoot: true);
  }
  hoist(document['paths'], componentRoot: false);
  return result;
}

void _verifyNormalizationLedger(
  Map<String, dynamic> canonical,
  Map<String, dynamic> normalized,
  _NormalizationResult actual,
  Map<String, dynamic> manifest,
  Map<String, dynamic> matrix,
) {
  _expect(actual.deepObjectsExpanded, 31, 'deep-object normalizations');
  _expect(actual.historyParametersChanged, 2, 'history numeric overrides');
  _expect(actual.activeMapsChanged, 1, 'active-map normalizations');
  _expect(actual.duplicateBranchesRemoved, 26, 'deduplicated branches');
  _expect(actual.nullableMarkersAdded, 7, 'nullable markers');
  _expect(actual.unconstrainedUnionsCollapsed, 2, 'unconstrained unions');
  _expect(actual.effectExtensionsRemoved, 1, 'effect-stream removals');
  _expect(actual.inlineUnionsHoisted, 116, 'hoisted inline unions');
  _expect(actual.renames.length, 9, 'component renames');
  _expect(
    ((normalized['components'] as Map)['schemas'] as Map).length,
    588,
    'normalized component count',
  );

  final overrides = (matrix['canonicalOverrides'] as List?)?.cast<Map>();
  if (overrides == null || overrides.length != 1) {
    throw StateError('Matrix must record exactly one canonical override.');
  }
  final override = overrides.single;
  _expect(override['operationId'], 'v2.session.history', 'override operation');
  _expect(override['parityTarget'], 'upstream-generated-sdk', 'parity target');
  _expect(
    override['canonicalSource'],
    'OpenAPI parameters are strings',
    'canonical override source',
  );
  _expect(
    override['upstreamGeneratedSource'],
    'JS SDK build patches parameters to numbers',
    'upstream override source',
  );
  final parameters = (override['parameters'] as List).cast<Map>();
  _expect(parameters.length, 2, 'override parameter count');
  for (final name in const ['limit', 'after']) {
    final parameter = parameters.singleWhere((item) => item['name'] == name);
    _expectJson(parameter['canonicalSchema'], {
      'type': 'string',
    }, '$name canonical');
    _expectJson(parameter['generatedSchema'], {
      'type': 'number',
    }, '$name generated');
  }

  final changes = ((manifest['normalization'] as Map)['changes'] as List)
      .cast<Map>();
  final history = changes.singleWhere(
    (item) => item['kind'] == 'matchSessionHistoryNumericParameters',
  );
  _expect(history['operationId'], 'v2.session.history', 'manifest override');

  final canonicalOperation = _operationById(canonical, 'v2.session.history');
  final normalizedOperation = _operationById(normalized, 'v2.session.history');
  for (final name in const ['limit', 'after']) {
    Map parameter(Map<String, dynamic> operation) =>
        (operation['parameters'] as List).cast<Map>().singleWhere(
          (item) => item['name'] == name,
        );
    _expect(
      (parameter(canonicalOperation)['schema'] as Map)['type'],
      'string',
      '$name canonical type',
    );
    _expect(
      (parameter(normalizedOperation)['schema'] as Map)['type'],
      'number',
      '$name generated target type',
    );
  }
}

class _ApiOperation {
  _ApiOperation({
    required this.id,
    required this.file,
    required this.methodName,
    required this.returnType,
    required this.source,
    required this.signature,
    required this.path,
    required this.httpMethod,
    required this.deprecated,
  });

  final String id;
  final String file;
  final String methodName;
  final String returnType;
  final String source;
  final String signature;
  final String path;
  final String httpMethod;
  final bool deprecated;
}

Future<Map<String, _ApiOperation>> _parseApiOperations(
  Directory package,
) async {
  final directory = Directory('${package.path}/lib/src/api');
  final files = await directory
      .list()
      .where((entity) => entity is File && entity.path.endsWith('_api.dart'))
      .cast<File>()
      .toList();
  files.sort((left, right) => left.path.compareTo(right.path));
  final result = <String, _ApiOperation>{};
  final methodPattern = RegExp(
    r'Future<Response<([^\r\n]+)>>\s+([A-Za-z0-9_]+)\s*\(\{',
  );
  for (final file in files) {
    final source = await file.readAsString();
    final methods = methodPattern.allMatches(source).toList();
    for (var index = 0; index < methods.length; index++) {
      final match = methods[index];
      final end = index + 1 < methods.length
          ? methods[index + 1].start
          : source.lastIndexOf('\n}');
      final methodSource = source.substring(match.start, end);
      final idMatch = RegExp(
        r"const _operationId = r?'([^']+)';",
      ).firstMatch(methodSource);
      final pathMatch = RegExp(
        r"final _path = r?'([^']+)'",
      ).firstMatch(methodSource);
      final httpMatch = RegExp(
        r"method: r?'([A-Z]+)'",
      ).firstMatch(methodSource);
      final signatureEnd = methodSource.indexOf('}) async {');
      if (idMatch == null ||
          pathMatch == null ||
          httpMatch == null ||
          signatureEnd < 0) {
        throw StateError('Could not parse generated method in ${file.path}.');
      }
      final id = idMatch.group(1)!;
      if (result.containsKey(id)) {
        throw StateError('Duplicate generated operationId $id.');
      }
      final annotationStart = match.start > 300 ? match.start - 300 : 0;
      final annotation = source.substring(annotationStart, match.start);
      result[id] = _ApiOperation(
        id: id,
        file: 'lib/src/api/${file.uri.pathSegments.last}',
        methodName: match.group(2)!,
        returnType: match.group(1)!.trim(),
        source: methodSource,
        signature: methodSource.substring(0, signatureEnd),
        path: pathMatch.group(1)!,
        httpMethod: httpMatch.group(1)!,
        deprecated: annotation.contains('@Deprecated('),
      );
    }
  }
  return result;
}

class _OperationCounts {
  _OperationCounts(this.generatedParameters);

  final int generatedParameters;
}

_OperationCounts _verifyOperations(
  Map<String, dynamic> document,
  Map<String, _ApiOperation> generated,
  Map<String, dynamic> matrix,
) {
  final expectedIds = <String>{};
  final matrixOperations = <String, Map>{};
  for (final value in (matrix['operations'] as List).cast<Map>()) {
    final id = value['operationId'] as String;
    if (matrixOperations.containsKey(id)) {
      throw StateError('Duplicate matrix operation $id.');
    }
    matrixOperations[id] = value;
  }
  var generatedParameters = 0;
  final paths = (document['paths'] as Map).cast<String, dynamic>();
  for (final pathEntry in paths.entries) {
    final pathItem = (pathEntry.value as Map).cast<String, dynamic>();
    for (final method in _httpMethods) {
      final value = pathItem[method];
      if (value is! Map) continue;
      final operation = value.cast<String, dynamic>();
      final id = operation['operationId'] as String;
      if (!expectedIds.add(id)) throw StateError('Duplicate canonical $id.');
      final actual = generated[id];
      if (actual == null) throw StateError('No generated method for $id.');
      final matrixOperation = matrixOperations[id];
      if (matrixOperation == null) {
        throw StateError('Matrix omits operation $id.');
      }
      _expect(actual.path, pathEntry.key, '$id path');
      _expect(actual.httpMethod, method.toUpperCase(), '$id HTTP method');
      final expectedDeprecated =
          operation['deprecated'] == true || _forcedDeprecations.contains(id);
      _expect(actual.deprecated, expectedDeprecated, '$id deprecation');
      _expect(
        matrixOperation['method'],
        method.toUpperCase(),
        '$id matrix method',
      );
      _expect(matrixOperation['path'], pathEntry.key, '$id matrix path');
      _expect(
        matrixOperation['deprecated'],
        operation['deprecated'] == true,
        '$id matrix deprecation',
      );
      final matrixGenerated = matrixOperation['generated'] as Map;
      _expect(
        matrixGenerated['file'],
        actual.file,
        '$id matrix generated file',
      );
      _expect(
        matrixGenerated['method'],
        actual.methodName,
        '$id matrix generated method',
      );
      _expect(
        matrixGenerated['operationIdMetadata'],
        actual.id,
        '$id matrix operation metadata',
      );
      _expect(
        matrixGenerated['methodPath'],
        actual.path,
        '$id matrix generated path',
      );
      _expect(
        matrixGenerated['httpMethod'],
        actual.httpMethod,
        '$id matrix generated HTTP method',
      );
      _expect(
        matrixGenerated['dartDeprecated'],
        actual.deprecated,
        '$id matrix generated deprecation',
      );
      if (!actual.source.contains("'operationId': _operationId") ||
          !actual.source.contains(
            'rethrowOpenCodeApiException(error, operationId: _operationId)',
          ) ||
          !actual.source.contains(
            'throwIfOpenCodeApiError(_response, operationId: _operationId)',
          )) {
        throw StateError(
          '$id does not retain operation-aware response handling.',
        );
      }

      final parameters = <Map<String, dynamic>>[
        ...(pathItem['parameters'] as List? ?? const <Object?>[])
            .cast<Map>()
            .map((item) => item.cast<String, dynamic>()),
        ...(operation['parameters'] as List? ?? const <Object?>[])
            .cast<Map>()
            .map((item) => item.cast<String, dynamic>()),
      ];
      final normalizedParameters = <Map<String, dynamic>>[];
      for (final parameter in parameters) {
        if (parameter['name'] == 'location' &&
            parameter['in'] == 'query' &&
            parameter['style'] == 'deepObject') {
          for (final property in const ['directory', 'workspace']) {
            normalizedParameters.add({
              'name': 'location[$property]',
              'in': 'query',
              'required': false,
            });
          }
        } else {
          normalizedParameters.add(parameter);
        }
      }
      final matrixParameters = (matrixOperation['parameters'] as List)
          .cast<Map>();
      _expect(
        matrixParameters.length,
        parameters.length,
        '$id matrix parameters',
      );
      for (var index = 0; index < parameters.length; index++) {
        final expectedParameter = parameters[index];
        final matrixParameter = matrixParameters[index];
        _expectJson(
          matrixParameter['canonical'],
          expectedParameter,
          '$id matrix parameter $index',
        );
        _expect(
          matrixParameter['canonicalParameterHash'],
          _sha256(utf8.encode(_canonicalJson(expectedParameter))),
          '$id matrix parameter $index hash',
        );
      }
      generatedParameters += normalizedParameters.length;
      final pathParameters = normalizedParameters
          .where((item) => item['in'] == 'path')
          .toList();
      final placeholders = RegExp(
        r'\{([^}]+)\}',
      ).allMatches(pathEntry.key).map((match) => match.group(1)!).toList();
      _expectJson(
        pathParameters.map((item) => item['name']).toList(),
        placeholders,
        '$id path parameter inventory',
      );
      final pathVariables = RegExp(
        r'encodeOpenCodePathSegment\(\s*serializeOpenCodeQueryParameter\(\s*([A-Za-z0-9_]+)',
      ).allMatches(actual.source).map((match) => match.group(1)!).toList();
      _expect(pathVariables.length, placeholders.length, '$id encoded paths');
      for (var index = 0; index < pathVariables.length; index++) {
        _verifySignatureRequired(
          actual,
          pathVariables[index],
          true,
          '$id path ${placeholders[index]}',
        );
      }

      final queryMatches = RegExp(
        r"(?:if\s*\(\s*([A-Za-z0-9_]+)\s*!=\s*null\s*\)\s*)?r'([^']+)'\s*:\s*serializeOpenCodeQueryParameter\(\s*([A-Za-z0-9_]+)",
      ).allMatches(actual.source).toList();
      final expectedQueries = normalizedParameters
          .where((item) => item['in'] == 'query')
          .toList();
      _expect(queryMatches.length, expectedQueries.length, '$id query count');
      final seenWireKeys = <String>{};
      for (final parameter in expectedQueries) {
        final wireName = parameter['name'] as String;
        final match = queryMatches.singleWhere(
          (candidate) => candidate.group(2) == wireName,
          orElse: () => throw StateError('$id omits query wire key $wireName.'),
        );
        if (!seenWireKeys.add(wireName)) {
          throw StateError('$id duplicates query wire key $wireName.');
        }
        final variable = match.group(3)!;
        final conditionVariable = match.group(1);
        final required = parameter['required'] == true;
        _expect(
          conditionVariable,
          required ? null : variable,
          '$id $wireName conditional omission',
        );
        _verifySignatureRequired(
          actual,
          variable,
          required,
          '$id query $wireName',
        );
        if (!actual.source.contains(
          "r'$wireName': serializeOpenCodeQueryParameter",
        )) {
          throw StateError(
            '$id query $wireName bypasses scalar-safe serialization.',
          );
        }
      }

      final requestBody = operation['requestBody'];
      if (requestBody is Map) {
        final matrixBody = matrixOperation['requestBody'] as Map?;
        if (matrixBody == null) {
          throw StateError('$id matrix omits request body.');
        }
        _expect(
          matrixBody['canonicalRequestBodyHash'],
          _sha256(utf8.encode(_canonicalJson(requestBody))),
          '$id matrix request body hash',
        );
        final bodyMatch = RegExp(
          r'jsonEncode\(\s*([A-Za-z0-9_]+)\s*\)',
        ).firstMatch(actual.source);
        if (bodyMatch == null) {
          throw StateError('$id does not JSON-encode its body.');
        }
        final variable = bodyMatch.group(1)!;
        final required = requestBody['required'] == true;
        _verifySignatureRequired(
          actual,
          variable,
          required,
          '$id request body',
        );
        final include = required
            ? 'includeBody: true'
            : 'includeBody: $variable != null';
        if (!actual.source.contains(include)) {
          throw StateError(
            '$id has incorrect request-body omission semantics.',
          );
        }
        if (!required &&
            !RegExp(
              'if \\($variable != null\\) \\{[\\s\\S]*?jsonEncode\\($variable\\)',
            ).hasMatch(actual.source)) {
          throw StateError('$id encodes an omitted optional body.');
        }
        final content = requestBody['content'] as Map;
        final mediaTypes = content.keys.cast<String>().toList();
        _expectJson(mediaTypes, const [
          'application/json',
        ], '$id request media');
        if (!actual.source.contains("contentType: 'application/json'")) {
          throw StateError('$id omits its request content type.');
        }
      } else {
        _expect(
          matrixOperation['requestBody'],
          null,
          '$id matrix request body',
        );
        if (!actual.source.contains('includeBody: false')) {
          throw StateError('$id unexpectedly includes a request body.');
        }
        if (actual.source.contains('contentType:')) {
          throw StateError('$id sends a content type without a request body.');
        }
      }

      final responses = (operation['responses'] as Map).cast<String, dynamic>();
      final matrixResponses = (matrixOperation['responses'] as List)
          .cast<Map>();
      _expect(matrixResponses.length, responses.length, '$id matrix responses');
      var hasSuccessContent = false;
      var hasBinarySuccess = false;
      for (final responseEntry in responses.entries) {
        final matrixResponse = matrixResponses.singleWhere(
          (item) => item['status'] == responseEntry.key,
          orElse: () => throw StateError(
            '$id matrix omits response ${responseEntry.key}.',
          ),
        );
        _expect(
          matrixResponse['canonicalResponseHash'],
          _sha256(utf8.encode(_canonicalJson(responseEntry.value))),
          '$id matrix response ${responseEntry.key} hash',
        );
        final canonicalContent = (responseEntry.value as Map)['content'];
        final matrixContent = (matrixResponse['content'] as List).cast<Map>();
        if (canonicalContent is Map) {
          _expect(
            matrixContent.length,
            canonicalContent.length,
            '$id matrix response ${responseEntry.key} media count',
          );
          for (final mediaEntry in canonicalContent.entries) {
            final matrixMedia = matrixContent.singleWhere(
              (item) => item['mediaType'] == mediaEntry.key,
              orElse: () => throw StateError(
                '$id matrix omits ${responseEntry.key} ${mediaEntry.key}.',
              ),
            );
            final schema = (mediaEntry.value as Map)['schema'];
            if (schema != null) {
              _expect(
                matrixMedia['schemaHash'],
                _sha256(utf8.encode(_canonicalJson(schema))),
                '$id matrix ${responseEntry.key} ${mediaEntry.key} schema',
              );
            }
          }
        } else {
          _expect(matrixContent.length, 0, '$id matrix empty response content');
        }
        final status = int.tryParse(responseEntry.key);
        if (status == null || status < 200 || status >= 300) continue;
        final response = responseEntry.value as Map;
        final content = response['content'];
        if (content is Map && content.isNotEmpty) {
          hasSuccessContent = true;
          hasBinarySuccess =
              hasBinarySuccess ||
              content.containsKey('application/octet-stream');
        }
      }
      if (hasSuccessContent) {
        if (actual.returnType == 'void' ||
            !actual.source.contains('final rawData = _response.data') ||
            !actual.source.contains('return Response<${actual.returnType}>(')) {
          throw StateError(
            '$id does not deserialize and return its success body.',
          );
        }
      } else if (actual.returnType != 'void' ||
          !actual.source.contains('return _response;')) {
        throw StateError('$id should use void response handling.');
      }
      _expect(
        actual.source.contains('responseType: ResponseType.bytes'),
        hasBinarySuccess,
        '$id binary response handling',
      );

      if (id == 'v2.session.history') {
        if (!RegExp(
          r'num\?\s+limit,\s*num\?\s+after,',
        ).hasMatch(actual.signature)) {
          throw StateError(
            'v2.session.history does not target the recorded numeric SDK override.',
          );
        }
      }
    }
  }
  if (expectedIds.length != generated.length ||
      expectedIds.length != matrixOperations.length ||
      !expectedIds.containsAll(generated.keys) ||
      !expectedIds.containsAll(matrixOperations.keys)) {
    throw StateError(
      'Generated operation inventory differs: canonical ${expectedIds.length}, '
      'generated ${generated.length}.',
    );
  }
  return _OperationCounts(generatedParameters);
}

void _verifySignatureRequired(
  _ApiOperation operation,
  String variable,
  bool required,
  String label,
) {
  final match = RegExp(
    '^\\s*(@Deprecated\\([^\\n]+\\)\\s*)?(required\\s+)?[^\\n]+?\\s+'
    '${RegExp.escape(variable)}(?:\\s*=\\s*[^,]+)?,\\s*\$',
    multiLine: true,
  ).firstMatch(operation.signature);
  if (match == null) {
    throw StateError('$label has no generated signature parameter.');
  }
  _expect(match.group(2) != null, required, '$label requiredness');
}

class _DeclarationIndex {
  _DeclarationIndex(this.byName, this.sources, this.deserializeSource);

  final Map<String, String> byName;
  final Map<String, String> sources;
  final String deserializeSource;
}

Future<_DeclarationIndex> _indexDeclarations(Directory package) async {
  final modelDirectory = Directory('${package.path}/lib/src/model');
  final files = await modelDirectory
      .list()
      .where(
        (entity) =>
            entity is File &&
            entity.path.endsWith('.dart') &&
            !entity.path.endsWith('.g.dart'),
      )
      .cast<File>()
      .toList();
  files.sort((left, right) => left.path.compareTo(right.path));
  final byName = <String, String>{};
  final sources = <String, String>{};
  for (final file in files) {
    final relative = 'lib/src/model/${file.uri.pathSegments.last}';
    final source = await file.readAsString();
    sources[relative] = source;
    for (final match in RegExp(
      r'^(?:class|enum)\s+([A-Za-z0-9_]+)\b',
      multiLine: true,
    ).allMatches(source)) {
      final name = match.group(1)!;
      final previous = byName[name];
      if (previous != null && previous != relative) {
        throw StateError(
          'Declaration $name occurs in $previous and $relative.',
        );
      }
      byName[name] = relative;
    }
  }
  return _DeclarationIndex(
    byName,
    sources,
    await File('${package.path}/lib/src/deserialize.dart').readAsString(),
  );
}

Future<int> _verifyWrappers(
  Map<String, dynamic> normalized,
  _DeclarationIndex declarations,
  Directory package,
) async {
  final schemas = ((normalized['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final expected = <String, Object?>{};
  for (final entry in schemas.entries) {
    final schema = entry.value;
    if (schema is Map && _isMeaningfulUnion(schema)) {
      expected[_dartTypeName(entry.key)] = schema;
    }
  }
  final actual = <String, Object?>{};
  for (final entry in declarations.sources.entries) {
    final source = entry.value;
    final descriptorMatch = RegExp(
      r'openApiSchemaJson\s*=\s*("(?:\\.|[^"\\])*")\s*;',
      dotAll: true,
    ).firstMatch(source);
    if (descriptorMatch == null) continue;
    final declaration = RegExp(
      r'class\s+([A-Za-z0-9_]+)\s+implements\s+OpenCodeRawJsonValue',
    ).firstMatch(source);
    if (declaration == null) {
      throw StateError('${entry.key} has a descriptor without a raw wrapper.');
    }
    final className = declaration.group(1)!;
    final descriptorText = _decodeDartString(descriptorMatch.group(1)!);
    final descriptor = jsonDecode(descriptorText);
    if (actual.containsKey(className)) {
      throw StateError('Duplicate raw wrapper $className.');
    }
    actual[className] = descriptor;
    if (!source.contains('factory $className.fromJson(Object? json)') ||
        !source.contains('Object? toJson() => _copyJsonValue(value);') ||
        !source.contains('final Object? value;')) {
      throw StateError(
        '$className is not a lossless Object? round-trip wrapper.',
      );
    }
  }
  _expect(actual.length, expected.length, 'raw wrapper inventory');
  for (final entry in expected.entries) {
    final descriptor = actual[entry.key];
    if (descriptor == null) {
      throw StateError('Missing raw wrapper ${entry.key}.');
    }
    _expectJson(descriptor, entry.value, '${entry.key} normalized descriptor');
    _expect(
      _sha256(utf8.encode(_canonicalJson(descriptor))),
      _sha256(utf8.encode(_canonicalJson(entry.value))),
      '${entry.key} normalized descriptor hash',
    );
    if (!declarations.deserializeSource.contains("case '${entry.key}':") ||
        !RegExp(
          '${RegExp.escape(entry.key)}\\.fromJson\\(value\\)',
        ).hasMatch(declarations.deserializeSource)) {
      throw StateError('${entry.key} has no lossless endpoint codec.');
    }
  }
  final extras = actual.keys
      .where((name) => !expected.containsKey(name))
      .toList();
  if (extras.isNotEmpty) throw StateError('Unexpected raw wrappers: $extras.');
  return actual.length;
}

Future<int> _verifyCanonicalSchemas(
  Map<String, dynamic> canonical,
  Map<String, dynamic> normalized,
  Map<String, String> renames,
  _DeclarationIndex declarations,
  Directory package,
  Map<String, dynamic> matrix,
) async {
  final canonicalSchemas = ((canonical['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final normalizedSchemas =
      ((normalized['components'] as Map)['schemas'] as Map)
          .cast<String, dynamic>();
  final matrixSchemas = <String, Map>{};
  for (final value in (matrix['schemas'] as List).cast<Map>()) {
    final name = value['name'] as String;
    if (matrixSchemas.containsKey(name)) {
      throw StateError('Duplicate matrix schema $name.');
    }
    matrixSchemas[name] = value;
  }
  _expect(
    matrixSchemas.length,
    canonicalSchemas.length,
    'matrix schema inventory',
  );
  for (final entry in canonicalSchemas.entries) {
    final matrixItem = matrixSchemas[entry.key];
    if (matrixItem == null) {
      throw StateError('Matrix omits schema ${entry.key}.');
    }
    _expect(
      matrixItem['canonicalSchemaHash'],
      _sha256(utf8.encode(_canonicalJson(entry.value))),
      '${entry.key} canonical schema hash',
    );
    final normalizedName = renames[entry.key] ?? entry.key;
    final className = _dartTypeName(normalizedName);
    final normalizedSchema = normalizedSchemas[normalizedName] as Map;
    final matrixGenerated = matrixItem['generated'] as Map;
    _expect(
      matrixGenerated['normalizedName'],
      normalizedName,
      '${entry.key} matrix normalized name',
    );
    final file = declarations.byName[className];
    if (file == null) {
      final alias = _aliasType(entry.key, entry.value as Map, renames);
      _expect(
        matrixGenerated['kind'],
        'generated-alias',
        '${entry.key} alias kind',
      );
      _expect(matrixGenerated['declaration'], alias, '${entry.key} alias type');
      _expect(
        matrixGenerated['file'],
        'lib/src/deserialize.dart',
        '${entry.key} alias codec file',
      );
      _verifyAliasCodec(alias, declarations.deserializeSource, entry.key);
      continue;
    }
    final expectedFile = 'lib/src/model/${_dartFileName(className)}.dart';
    _expect(file, expectedFile, '${entry.key} declaration file');
    _expect(matrixGenerated['file'], expectedFile, '${entry.key} matrix file');
    _expect(
      matrixGenerated['declaration'],
      className,
      '${entry.key} matrix declaration',
    );
    final source = declarations.sources[file]!;
    if (_isMeaningfulUnion(normalizedSchema)) {
      _expect(
        matrixGenerated['kind'],
        'raw-union-wrapper',
        '${entry.key} matrix wrapper kind',
      );
      if (!source.contains('implements OpenCodeRawJsonValue')) {
        throw StateError('${entry.key} is not mapped to its lossless wrapper.');
      }
      continue;
    }
    if (RegExp(
      '^enum ${RegExp.escape(className)}\\s*\\{',
      multiLine: true,
    ).hasMatch(source)) {
      _expect(
        matrixGenerated['kind'],
        'json-value-enum',
        '${entry.key} matrix enum kind',
      );
      final values = normalizedSchema['enum'];
      if (values is! List) {
        throw StateError(
          '${entry.key} became an enum without canonical values.',
        );
      }
      final wireValues =
          RegExp(
                r'''@JsonValue\((r?"(?:\\.|[^"\\])*"|r?'(?:\\.|[^'\\])*'|[^\)]+)\)''',
              )
              .allMatches(source)
              .map((match) {
                final literal = match.group(1)!.trim();
                if (literal.startsWith("r'") || literal.startsWith("'")) {
                  return _decodeSingleQuotedDartString(literal);
                }
                if (literal.startsWith('r"') || literal.startsWith('"')) {
                  return _decodeDartString(literal);
                }
                return jsonDecode(literal);
              })
              .where((value) => value != 'unknown_default_open_api')
              .toList();
      _expectJson(wireValues, values, '${entry.key} enum wire values');
    } else {
      _expect(
        matrixGenerated['kind'],
        'model',
        '${entry.key} matrix model kind',
      );
      if (!source.contains('factory $className.fromJson(') ||
          !source.contains('toJson()')) {
        throw StateError('${entry.key} has no generated model codec.');
      }
      final codec = File(
        '${package.path}/lib/src/model/${_dartFileName(className)}.g.dart',
      );
      if (!await codec.exists()) {
        throw StateError('${entry.key} has no generated serializer artifact.');
      }
    }
    if (!declarations.deserializeSource.contains("case '$className':")) {
      throw StateError('${entry.key} has no endpoint deserializer case.');
    }
  }
  return canonicalSchemas.length;
}

void _verifyAliasCodec(String alias, String deserialize, String schemaName) {
  if (alias == 'String') {
    if (!deserialize.contains("case 'String':")) {
      throw StateError('$schemaName has no String codec.');
    }
    return;
  }
  if (alias == 'Object') {
    // Unconstrained JSON Schema components map to Dart's native JSON value
    // domain. This is distinct from the forbidden error-decoder fallback.
    _expect(alias, 'Object', '$schemaName native JSON codec');
    return;
  }
  if (alias.startsWith('List<')) {
    if (!deserialize.contains('_regList.firstMatch(targetType)') ||
        !deserialize.contains('deserialize<BaseType, BaseType>(')) {
      throw StateError('$schemaName has no generated list codec.');
    }
    return;
  }
  throw StateError('$schemaName has unsupported alias $alias.');
}

Future<int> _verifyErrors(
  Map<String, dynamic> document,
  _DeclarationIndex declarations,
  Directory package,
) async {
  final expected = <String, Map<String, Object?>>{};
  final paths = (document['paths'] as Map).cast<String, dynamic>();
  for (final pathValue in paths.values) {
    final pathItem = (pathValue as Map).cast<String, dynamic>();
    for (final method in _httpMethods) {
      final value = pathItem[method];
      if (value is! Map) continue;
      final operation = value.cast<String, dynamic>();
      final id = operation['operationId'] as String;
      final responses = (operation['responses'] as Map).cast<String, dynamic>();
      for (final responseEntry in responses.entries) {
        final status = int.tryParse(responseEntry.key);
        if (status == null || (status >= 200 && status < 300)) continue;
        final response = responseEntry.value as Map;
        final content = response['content'];
        if (content is! Map || content.isEmpty) {
          _addExpectedError(
            expected,
            id,
            status,
            '',
            const <String, Object?>{},
          );
        } else {
          for (final mediaEntry in content.entries) {
            final descriptor = mediaEntry.value as Map;
            _addExpectedError(
              expected,
              id,
              status,
              mediaEntry.key as String,
              descriptor['schema'] ?? const <String, Object?>{},
            );
          }
        }
      }
    }
  }

  final source = await File(
    '${package.path}/lib/src/http/error_contracts.g.dart',
  ).readAsString();
  final starts = RegExp(
    r'^  const OpenCodeErrorContractKey\(',
    multiLine: true,
  ).allMatches(source).toList();
  _expect(starts.length, expected.length, 'error registry entry count');
  final actualKeys = <String>{};
  for (var index = 0; index < starts.length; index++) {
    final start = starts[index].start;
    final end = index + 1 < starts.length
        ? starts[index + 1].start
        : source.indexOf('\n};', start);
    final block = source.substring(start, end);
    final ids = RegExp(r'operationId:\s*("(?:\\.|[^"\\])*")')
        .allMatches(block)
        .map((match) => _decodeDartString(match.group(1)!))
        .toList();
    final statuses = RegExp(
      r'status:\s*([0-9]+)',
    ).allMatches(block).map((match) => int.parse(match.group(1)!)).toList();
    final media = RegExp(r'mediaType:\s*("(?:\\.|[^"\\])*")')
        .allMatches(block)
        .map((match) => _decodeDartString(match.group(1)!))
        .toList();
    final schemas = RegExp(r'schemaJson:\s*("(?:\\.|[^"\\])*")')
        .allMatches(block)
        .map((match) => _decodeDartString(match.group(1)!))
        .toList();
    if (ids.length != 2 ||
        statuses.length != 2 ||
        media.length != 2 ||
        schemas.length != 2 ||
        ids[0] != ids[1] ||
        statuses[0] != statuses[1] ||
        media[0] != media[1] ||
        schemas[0] != schemas[1]) {
      throw StateError(
        'Error registry key and value descriptors diverge at entry $index.',
      );
    }
    final key = _errorKey(
      ids[0],
      statuses[0],
      media[0],
      jsonDecode(schemas[0]),
    );
    if (!actualKeys.add(key)) {
      throw StateError('Duplicate error registry key $key.');
    }
    final expectedError = expected[key];
    if (expectedError == null) {
      throw StateError('Non-canonical error registry substitution $key.');
    }
    _expectJson(
      jsonDecode(schemas[0]),
      expectedError['schema'],
      '${ids[0]} ${statuses[0]} ${media[0]} error schema',
    );
    final payloadMatch = RegExp(
      r'payloadType:\s*("(?:\\.|[^"\\])*")',
    ).firstMatch(block);
    if (payloadMatch == null) {
      throw StateError('$key has no decoder payload type.');
    }
    final payloadType = _decodeDartString(payloadMatch.group(1)!);
    if (payloadType == 'Object' ||
        payloadType == 'dynamic' ||
        payloadType.isEmpty) {
      throw StateError('$key uses forbidden plain Object decoder fallback.');
    }
    if (payloadType == 'OpenCodeLosslessErrorPayload') {
      if (!block.contains('decoder: decodeOpenCodeLosslessErrorPayload')) {
        throw StateError('$key has the wrong lossless decoder identity.');
      }
    } else {
      final hasDeclaration = declarations.byName[payloadType] != null;
      final hasDeserialize = declarations.deserializeSource.contains(
        "case '$payloadType':",
      );
      final compactBlock = block.replaceAll(RegExp(r'\s+'), '');
      final hasDecoder = RegExp(
        'decodeOpenCodeErrorModel\\(payload,"${RegExp.escape(payloadType)}",'
        'contract,?\\)',
      ).hasMatch(compactBlock);
      if (!hasDeclaration || !hasDeserialize || !hasDecoder) {
        throw StateError(
          '${ids[0]} ${statuses[0]} ${media[0]} has invalid decoder identity '
          '$payloadType '
          '(declaration=$hasDeclaration, deserialize=$hasDeserialize, '
          'decoder=$hasDecoder).',
        );
      }
    }
  }
  if (actualKeys.length != expected.length ||
      !actualKeys.containsAll(expected.keys)) {
    throw StateError(
      'Error registry does not exactly equal canonical descriptors.',
    );
  }
  return actualKeys.length;
}

void _addExpectedError(
  Map<String, Map<String, Object?>> result,
  String operationId,
  int status,
  String mediaType,
  Object? schema,
) {
  final key = _errorKey(operationId, status, mediaType, schema);
  if (result.containsKey(key)) {
    throw StateError('Duplicate canonical error $key.');
  }
  result[key] = {'schema': schema};
}

String _errorKey(String id, int status, String media, Object? schema) =>
    '$id\u0000$status\u0000$media\u0000${_canonicalJson(schema)}';

Future<int> _verifyMixedAdditionalProperties(
  Map<String, dynamic> document,
  Directory package,
) async {
  final sites = <String, Map>{};
  void visit(Object? value, List<String> path) {
    if (value is List) {
      for (var index = 0; index < value.length; index++) {
        visit(value[index], [...path, '$index']);
      }
      return;
    }
    if (value is! Map) return;
    final properties = value['properties'];
    if (properties is Map &&
        properties.isNotEmpty &&
        value['additionalProperties'] is Map) {
      sites['#/${path.map(_pointerPart).join('/')}'] = value;
    }
    for (final entry in value.entries) {
      visit(entry.value, [...path, '${entry.key}']);
    }
  }

  visit(document, const []);
  final expectedSites = <String>{
    '#/components/schemas/PermissionConfig/anyOf/1',
    ..._mixedModelFiles.keys,
  };
  _expectJson(sites.keys.toList(), expectedSites.toList(), 'mixed map sites');
  for (final entry in _mixedModelFiles.entries) {
    final source = await File(
      '${package.path}/lib/src/model/${entry.value}',
    ).readAsString();
    if (!source.contains(
          'get additionalProperties => _additionalProperties;',
        ) ||
        !source.contains('if (!knownKeys.contains(entry.key))') ||
        !source.contains('Map.unmodifiable') ||
        !source.contains('..._\$') ||
        !source.contains('ToJson(this)')) {
      throw StateError(
        '${entry.key} does not losslessly preserve unknown entries.',
      );
    }
  }
  final permission = await File(
    '${package.path}/lib/src/model/permission_config.dart',
  ).readAsString();
  final match = RegExp(
    r'openApiSchemaJson\s*=\s*("(?:\\.|[^"\\])*")\s*;',
    dotAll: true,
  ).firstMatch(permission);
  if (match == null) {
    throw StateError('PermissionConfig has no schema descriptor.');
  }
  final descriptor = jsonDecode(_decodeDartString(match.group(1)!)) as Map;
  final branch = (descriptor['anyOf'] as List)[1];
  _expectJson(
    branch,
    sites['#/components/schemas/PermissionConfig/anyOf/1'],
    'PermissionConfig mixed-map descriptor',
  );
  return sites.length;
}

Future<void> _verifyRuntime(Directory package) async {
  final wire = await File(
    '${package.path}/lib/src/http/wire.dart',
  ).readAsString();
  if (!wire.contains('if (includeBody)') ||
      !wire.contains('requestOptions.contentType = null;') ||
      !wire.contains('Uri.encodeComponent(value)') ||
      !wire.contains(".replaceAll('*', '%2A')")) {
    throw StateError('Runtime body/header or RFC 3986 handling is incomplete.');
  }
  final errors = await File(
    '${package.path}/lib/src/http/errors.dart',
  ).readAsString();
  if (!errors.contains('rawPayload') ||
      !errors.contains('OpenCodeErrorContractKey') ||
      !errors.contains('schemaJson')) {
    throw StateError(
      'Runtime error handling does not retain exact descriptors.',
    );
  }
}

Future<void> _verifySourceHashes(
  Map<String, dynamic> manifest,
  Directory package,
  Directory root,
) async {
  final hashes = (manifest['deterministicSourceHashes'] as Map?)
      ?.cast<String, dynamic>();
  if (hashes == null) {
    throw StateError('Manifest has no deterministic source hashes.');
  }
  final expected = <String, List<Directory>>{
    'generatedApi': [Directory('${package.path}/lib/src/api')],
    'generatedModels': [Directory('${package.path}/lib/src/model')],
    'generatedRuntime': [
      Directory('${package.path}/lib/src/http'),
      Directory('${package.path}/lib/src/sse'),
    ],
    'runtimeSources': [Directory('${root.path}/tool/sdk/runtime')],
    'templates': [Directory('${root.path}/tool/sdk/templates')],
  };
  for (final entry in expected.entries) {
    final descriptor = hashes[entry.key] as Map?;
    if (descriptor == null) {
      throw StateError('Manifest omits ${entry.key} hash.');
    }
    _expect(
      descriptor['algorithm'],
      'sha256(relative-path-nul-bytes-v1)',
      '${entry.key} hash algorithm',
    );
    _expect(
      await _sourceTreeHash(entry.value),
      descriptor['sha256'],
      '${entry.key} source hash',
    );
  }
  final testTemplates = hashes['testTemplates'] as Map?;
  if (testTemplates == null) {
    throw StateError('Manifest omits testTemplates hash.');
  }
  _expect(
    await _sourceFileSetHash(_generationTestTemplates(root), 'tool/sdk'),
    testTemplates['sha256'],
    'testTemplates source hash',
  );
}

List<File> _generationTestTemplates(Directory root) => [
  for (final name in const [
    'additional_properties_test.dart.template',
    'contract_parity_test.dart.template',
    'independent_verifier_mutation_test.dart.template',
    'schema_category_fixtures_test.dart.template',
    'smoke.dart.template',
    'union_fixtures_test.dart.template',
  ])
    File('${root.path}/tool/sdk/$name'),
];

Future<String> _sourceFileSetHash(List<File> files, String prefix) async {
  final records = <String>[];
  files.sort((left, right) => left.path.compareTo(right.path));
  for (final file in files) {
    if (!await file.exists()) {
      throw StateError('Missing source file ${file.path}.');
    }
    final bytes = await file.readAsBytes();
    records.add(
      '$prefix/${file.uri.pathSegments.last}\u0000${bytes.length}\u0000${_sha256(bytes)}',
    );
  }
  return _sha256(utf8.encode(records.join('\n')));
}

Future<String> _sourceTreeHash(List<Directory> roots) async {
  final records = <String>[];
  for (final root in roots) {
    if (!await root.exists()) {
      throw StateError('Missing source tree ${root.path}.');
    }
    final files = await root
        .list(recursive: true, followLinks: false)
        .where((entity) => entity is File)
        .cast<File>()
        .toList();
    files.sort((left, right) => left.path.compareTo(right.path));
    final prefix = root.uri.pathSegments
        .where((part) => part.isNotEmpty)
        .toList()
        .reversed
        .take(2)
        .toList()
        .reversed
        .join('/');
    for (final file in files) {
      final relative = file.path
          .substring(root.path.length + 1)
          .replaceAll('\\', '/');
      final bytes = await file.readAsBytes();
      records.add(
        '$prefix/$relative\u0000${bytes.length}\u0000${_sha256(bytes)}',
      );
    }
  }
  records.sort();
  return _sha256(utf8.encode(records.join('\n')));
}

Map<String, dynamic> _operationById(Map<String, dynamic> document, String id) {
  for (final pathValue in (document['paths'] as Map).values) {
    final path = pathValue as Map;
    for (final method in _httpMethods) {
      final operation = path[method];
      if (operation is Map && operation['operationId'] == id) {
        return operation.cast<String, dynamic>();
      }
    }
  }
  throw StateError('No operation $id.');
}

Map<String, String> _componentRenames(Map<String, dynamic> document) {
  final schemas = ((document['components'] as Map)['schemas'] as Map)
      .cast<String, dynamic>();
  final groups = <String, List<String>>{};
  for (final name in schemas.keys) {
    groups.putIfAbsent(_basicTypeName(name), () => []).add(name);
  }
  final apiNames = <String>{};
  for (final tag in (document['tags'] as List).whereType<Map>()) {
    if (tag['name'] is String) {
      apiNames.add('${_basicTypeName(tag['name'])}Api');
    }
  }
  final occupied = schemas.keys.toSet();
  String available(String preferred) {
    var candidate = preferred;
    var ordinal = 2;
    while (!occupied.add(candidate)) {
      candidate = '$preferred$ordinal';
      ordinal++;
    }
    return candidate;
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
  final entries = groups.entries.toList()
    ..sort((left, right) => left.key.compareTo(right.key));
  for (final entry in entries) {
    if (entry.value.length < 2) continue;
    entry.value.sort((left, right) {
      if ((left == entry.key) != (right == entry.key)) {
        return left == entry.key ? -1 : 1;
      }
      return left.compareTo(right);
    });
    for (var index = 1; index < entry.value.length; index++) {
      result[entry.value[index]] = available('${entry.key}Schema${index + 1}');
    }
  }
  for (final name in schemas.keys.toList()..sort()) {
    if (!result.containsKey(name) && apiNames.contains(_basicTypeName(name))) {
      result[name] = available('${_basicTypeName(name)}Model');
    }
  }
  return result;
}

String _aliasType(String name, Map schema, Map<String, String> renames) {
  if (schema['type'] == 'string') return 'String';
  if (schema['type'] == 'object') return 'Object';
  if (schema['type'] != 'array') {
    throw StateError('$name has no declaration or alias.');
  }
  final items = schema['items'] as Map;
  final ref = items[r'$ref'];
  if (ref is String) {
    final canonical = ref.substring('#/components/schemas/'.length);
    return 'List<${_dartTypeName(renames[canonical] ?? canonical)}>';
  }
  final itemType = switch (items['type']) {
    'string' => 'String',
    'integer' => 'int',
    'number' => 'num',
    'boolean' => 'bool',
    'object' => '${_dartTypeName(name)}Inner',
    _ => 'Object',
  };
  return 'List<$itemType>';
}

bool _isMeaningfulUnion(Map schema) {
  for (final keyword in const ['anyOf', 'oneOf']) {
    final branches = schema[keyword];
    if (branches is List &&
        branches.where((item) => !_isNullSchema(item)).length > 1) {
      return true;
    }
  }
  return false;
}

bool _isNullSchema(Object? value) =>
    value is Map && value.length == 1 && value['type'] == 'null';

String _dartTypeName(String source) {
  final value = _basicTypeName(source);
  return value == 'Part' ? 'ModelPart' : value;
}

String _basicTypeName(String source) {
  final output = StringBuffer();
  for (final word in source.split(RegExp(r'[^A-Za-z0-9]+'))) {
    if (word.isEmpty) continue;
    output
      ..write(word[0].toUpperCase())
      ..write(word.substring(1));
  }
  return output.isEmpty ? 'Model' : output.toString();
}

String _dartFileName(String className) => className
    .replaceAllMapped(
      RegExp(r'([A-Z]+)([A-Z][a-z])'),
      (match) => '${match[1]}_${match[2]}',
    )
    .replaceAllMapped(
      RegExp(r'([a-z0-9])([A-Z])'),
      (match) => '${match[1]}_${match[2]}',
    )
    .toLowerCase();

String _decodeDartString(String literal) {
  var value = literal;
  final raw = value.startsWith('r"');
  if (raw) value = value.substring(1);
  if (!value.startsWith('"') || !value.endsWith('"')) {
    throw FormatException('Unsupported Dart string $literal.');
  }
  final content = value.substring(1, value.length - 1);
  if (raw) return content;
  return jsonDecode('"${content.replaceAll(r'\$', r'$')}"') as String;
}

String _decodeSingleQuotedDartString(String literal) {
  var value = literal;
  final raw = value.startsWith("r'");
  if (raw) value = value.substring(1);
  if (!value.startsWith("'") || !value.endsWith("'")) {
    throw FormatException('Unsupported Dart string $literal.');
  }
  var content = value.substring(1, value.length - 1);
  if (!raw) {
    content = content
        .replaceAll(r"\'", "'")
        .replaceAll(r'\\', r'\')
        .replaceAll(r'\$', r'$');
  }
  return content;
}

Object? _deepCopy(Object? value) => jsonDecode(jsonEncode(value));

String _canonicalJson(Object? value) {
  if (value is List) return '[${value.map(_canonicalJson).join(',')}]';
  if (value is Map) {
    final keys = value.keys.map((key) => '$key').toList()..sort();
    return '{${keys.map((key) => '${jsonEncode(key)}:${_canonicalJson(value[key])}').join(',')}}';
  }
  return jsonEncode(value);
}

String _pointerPart(String value) =>
    value.replaceAll('~', '~0').replaceAll('/', '~1');

void _expect(Object? actual, Object? expected, String label) {
  if (actual != expected) {
    throw StateError('$label: expected $expected, found $actual.');
  }
}

void _expectJson(Object? actual, Object? expected, String label) {
  final left = _canonicalJson(actual);
  final right = _canonicalJson(expected);
  if (left != right) {
    throw StateError('$label differs.\nExpected: $right\nActual: $left');
  }
}

const _shaConstants = <int>[
  0x428a2f98,
  0x71374491,
  0xb5c0fbcf,
  0xe9b5dba5,
  0x3956c25b,
  0x59f111f1,
  0x923f82a4,
  0xab1c5ed5,
  0xd807aa98,
  0x12835b01,
  0x243185be,
  0x550c7dc3,
  0x72be5d74,
  0x80deb1fe,
  0x9bdc06a7,
  0xc19bf174,
  0xe49b69c1,
  0xefbe4786,
  0x0fc19dc6,
  0x240ca1cc,
  0x2de92c6f,
  0x4a7484aa,
  0x5cb0a9dc,
  0x76f988da,
  0x983e5152,
  0xa831c66d,
  0xb00327c8,
  0xbf597fc7,
  0xc6e00bf3,
  0xd5a79147,
  0x06ca6351,
  0x14292967,
  0x27b70a85,
  0x2e1b2138,
  0x4d2c6dfc,
  0x53380d13,
  0x650a7354,
  0x766a0abb,
  0x81c2c92e,
  0x92722c85,
  0xa2bfe8a1,
  0xa81a664b,
  0xc24b8b70,
  0xc76c51a3,
  0xd192e819,
  0xd6990624,
  0xf40e3585,
  0x106aa070,
  0x19a4c116,
  0x1e376c08,
  0x2748774c,
  0x34b0bcb5,
  0x391c0cb3,
  0x4ed8aa4a,
  0x5b9cca4f,
  0x682e6ff3,
  0x748f82ee,
  0x78a5636f,
  0x84c87814,
  0x8cc70208,
  0x90befffa,
  0xa4506ceb,
  0xbef9a3f7,
  0xc67178f2,
];

int _rotate(int value, int count) =>
    ((value >>> count) | (value << (32 - count))) & 0xffffffff;

String _sha256(List<int> input) {
  final bitLength = input.length * 8;
  final length = ((input.length + 9 + 63) ~/ 64) * 64;
  final bytes = Uint8List(length)..setRange(0, input.length, input);
  bytes[input.length] = 0x80;
  for (var index = 0; index < 8; index++) {
    bytes[length - 1 - index] = (bitLength >> (index * 8)) & 0xff;
  }
  final hash = <int>[
    0x6a09e667,
    0xbb67ae85,
    0x3c6ef372,
    0xa54ff53a,
    0x510e527f,
    0x9b05688c,
    0x1f83d9ab,
    0x5be0cd19,
  ];
  final words = Uint32List(64);
  for (var offset = 0; offset < bytes.length; offset += 64) {
    for (var index = 0; index < 16; index++) {
      final byte = offset + index * 4;
      words[index] =
          (bytes[byte] << 24) |
          (bytes[byte + 1] << 16) |
          (bytes[byte + 2] << 8) |
          bytes[byte + 3];
    }
    for (var index = 16; index < 64; index++) {
      final s0 =
          _rotate(words[index - 15], 7) ^
          _rotate(words[index - 15], 18) ^
          (words[index - 15] >>> 3);
      final s1 =
          _rotate(words[index - 2], 17) ^
          _rotate(words[index - 2], 19) ^
          (words[index - 2] >>> 10);
      words[index] =
          (words[index - 16] + s0 + words[index - 7] + s1) & 0xffffffff;
    }
    var a = hash[0];
    var b = hash[1];
    var c = hash[2];
    var d = hash[3];
    var e = hash[4];
    var f = hash[5];
    var g = hash[6];
    var h = hash[7];
    for (var index = 0; index < 64; index++) {
      final sum1 = _rotate(e, 6) ^ _rotate(e, 11) ^ _rotate(e, 25);
      final choice = (e & f) ^ ((~e) & g);
      final temp1 =
          (h + sum1 + choice + _shaConstants[index] + words[index]) &
          0xffffffff;
      final sum0 = _rotate(a, 2) ^ _rotate(a, 13) ^ _rotate(a, 22);
      final majority = (a & b) ^ (a & c) ^ (b & c);
      final temp2 = (sum0 + majority) & 0xffffffff;
      h = g;
      g = f;
      f = e;
      e = (d + temp1) & 0xffffffff;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) & 0xffffffff;
    }
    hash[0] = (hash[0] + a) & 0xffffffff;
    hash[1] = (hash[1] + b) & 0xffffffff;
    hash[2] = (hash[2] + c) & 0xffffffff;
    hash[3] = (hash[3] + d) & 0xffffffff;
    hash[4] = (hash[4] + e) & 0xffffffff;
    hash[5] = (hash[5] + f) & 0xffffffff;
    hash[6] = (hash[6] + g) & 0xffffffff;
    hash[7] = (hash[7] + h) & 0xffffffff;
  }
  return hash.map((value) => value.toRadixString(16).padLeft(8, '0')).join();
}
