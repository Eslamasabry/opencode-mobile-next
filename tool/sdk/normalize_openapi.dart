import 'dart:convert';
import 'dart:io';

const httpMethods = {
  'get',
  'put',
  'post',
  'delete',
  'options',
  'head',
  'patch',
  'trace',
};

// OpenAPI Generator names the inline `Model.capabilities` object
// `ModelCapabilities`, which collides with the canonical v2 catalog component
// of the same name. Preserve both declarations under truthful, stable names so
// `v2.model.list` can deserialize its actual `tools`/`input`/`output` shape.
const componentRenameOverrides = <String, String>{
  'ModelCapabilities': 'ModelV2Capabilities',
};

class NormalizationReport {
  NormalizationReport({required this.componentRenames});

  final Map<String, String> componentRenames;
  final List<String> duplicateRootTagNames = [];
  var duplicateUnionBranchesRemoved = 0;
  var locationParametersExpanded = 0;
  var locationWireParametersCreated = 0;
  var patternPropertiesConverted = 0;
  var historyParametersMadeNumeric = 0;
  var nullableUnionsMarked = 0;
  var unconstrainedUnionsCollapsed = 0;
  var effectStreamExtensionsRemoved = 0;
  var componentRefsRewritten = 0;
  var inlineMeaningfulUnionsHoisted = 0;
  var operationIdsAnnotated = 0;

  Map<String, dynamic> toJson() => {
    'duplicateRootTagDeclarationsRemoved': duplicateRootTagNames.length,
    'duplicateRootTagNames': duplicateRootTagNames,
    'duplicateUnionBranchesRemoved': duplicateUnionBranchesRemoved,
    'locationParametersExpanded': locationParametersExpanded,
    'locationWireParametersCreated': locationWireParametersCreated,
    'patternPropertiesConverted': patternPropertiesConverted,
    'historyParametersMadeNumeric': historyParametersMadeNumeric,
    'nullableUnionsMarked': nullableUnionsMarked,
    'unconstrainedUnionsCollapsed': unconstrainedUnionsCollapsed,
    'componentSchemasRenamed': componentRenames.length,
    'componentRefsRewritten': componentRefsRewritten,
    'componentRenames': componentRenames,
    'effectStreamExtensionsRemoved': effectStreamExtensionsRemoved,
    'inlineMeaningfulUnionsHoisted': inlineMeaningfulUnionsHoisted,
    'operationIdsAnnotated': operationIdsAnnotated,
  };
}

NormalizationReport normalizeOpenApi(Map<String, dynamic> document) {
  final componentRenames = _componentRenameMap(document);
  final report = NormalizationReport(componentRenames: componentRenames);

  _deduplicateRootTags(document, report);
  _rewriteComponentNamesAndRefs(document, componentRenames, report);
  _normalizeOperations(document, report);
  _normalizeSchemas(document, report);
  _hoistInlineMeaningfulUnions(document, report);
  return report;
}

String dartTypeName(String source) {
  final words = source.split(RegExp(r'[^A-Za-z0-9]+'));
  final result = StringBuffer();
  for (final word in words) {
    if (word.isEmpty) continue;
    result
      ..write(word[0].toUpperCase())
      ..write(word.substring(1));
  }
  final value = result.toString();
  return value.isEmpty ? 'Model' : value;
}

Map<String, String> _componentRenameMap(Map<String, dynamic> document) {
  final components = document['components'] as Map<String, dynamic>;
  final schemas = components['schemas'] as Map<String, dynamic>;
  final groups = <String, List<String>>{};
  for (final name in schemas.keys) {
    groups.putIfAbsent(dartTypeName(name), () => []).add(name);
  }

  final apiTypeNames = <String>{};
  final tags = document['tags'];
  if (tags is List) {
    for (final tag in tags) {
      if (tag is Map && tag['name'] is String) {
        apiTypeNames.add('${dartTypeName(tag['name'] as String)}Api');
      }
    }
  }

  final occupied = schemas.keys.toSet();
  final renames = <String, String>{};
  for (final entry in componentRenameOverrides.entries) {
    if (!schemas.containsKey(entry.key)) continue;
    if (!occupied.add(entry.value)) {
      throw FormatException(
        'Component rename target already exists: ${entry.value}',
      );
    }
    renames[entry.key] = entry.value;
  }
  final sortedGroups = groups.entries.toList()
    ..sort((left, right) => left.key.compareTo(right.key));
  for (final entry in sortedGroups) {
    final names = entry.value;
    if (names.length < 2) continue;
    names.sort((left, right) {
      final leftExact = left == entry.key;
      final rightExact = right == entry.key;
      if (leftExact != rightExact) return leftExact ? -1 : 1;
      return left.compareTo(right);
    });
    for (var index = 1; index < names.length; index++) {
      renames[names[index]] = _availableComponentName(
        '${entry.key}Schema${index + 1}',
        occupied,
      );
    }
  }

  for (final name in schemas.keys.toList()..sort()) {
    if (renames.containsKey(name) ||
        !apiTypeNames.contains(dartTypeName(name))) {
      continue;
    }
    renames[name] = _availableComponentName(
      '${dartTypeName(name)}Model',
      occupied,
    );
  }
  return renames;
}

String _availableComponentName(String preferred, Set<String> occupied) {
  var candidate = preferred;
  var suffix = 2;
  while (!occupied.add(candidate)) {
    candidate = '$preferred$suffix';
    suffix++;
  }
  return candidate;
}

void _deduplicateRootTags(
  Map<String, dynamic> document,
  NormalizationReport report,
) {
  final tags = document['tags'];
  if (tags is! List) return;
  final seen = <String>{};
  document['tags'] = tags.where((tag) {
    if (tag is! Map || tag['name'] is! String) return true;
    final name = tag['name'] as String;
    if (seen.add(name)) return true;
    report.duplicateRootTagNames.add(name);
    return false;
  }).toList();
}

void _rewriteComponentNamesAndRefs(
  Map<String, dynamic> document,
  Map<String, String> renames,
  NormalizationReport report,
) {
  if (renames.isEmpty) return;
  final components = document['components'] as Map<String, dynamic>;
  final schemas = components['schemas'] as Map<String, dynamic>;
  final rewritten = <String, dynamic>{};
  for (final entry in schemas.entries) {
    rewritten[renames[entry.key] ?? entry.key] = entry.value;
  }
  components['schemas'] = rewritten;

  void rewrite(Object? value) {
    if (value is List) {
      for (final item in value) {
        rewrite(item);
      }
      return;
    }
    if (value is! Map<String, dynamic>) return;
    final ref = value[r'$ref'];
    if (ref is String && ref.startsWith('#/components/schemas/')) {
      final name = ref.substring('#/components/schemas/'.length);
      final renamed = renames[name];
      if (renamed != null) {
        value[r'$ref'] = '#/components/schemas/$renamed';
        report.componentRefsRewritten++;
      }
    }
    for (final child in value.values) {
      rewrite(child);
    }
  }

  rewrite(document);
}

void _normalizeOperations(
  Map<String, dynamic> document,
  NormalizationReport report,
) {
  final paths = document['paths'] as Map<String, dynamic>;
  for (final pathItem in paths.values.cast<Map<String, dynamic>>()) {
    for (final method in httpMethods) {
      final operation = pathItem[method];
      if (operation is! Map<String, dynamic>) continue;
      final operationId = operation['operationId'];
      if (operationId is String) {
        operation['x-opencode-operation-id'] = operationId;
        report.operationIdsAnnotated++;
      }
      final parameters = operation['parameters'];
      if (parameters is List) {
        final normalized = <dynamic>[];
        for (final parameter in parameters) {
          if (_isLocationDeepObject(parameter)) {
            normalized.addAll(
              _expandLocationParameter(parameter as Map<String, dynamic>),
            );
            report.locationParametersExpanded++;
            report.locationWireParametersCreated += 2;
          } else {
            normalized.add(parameter);
          }
        }
        operation['parameters'] = normalized;
      }

      if (operationId == 'v2.session.history') {
        final historyParameters = operation['parameters'] as List;
        for (final parameter
            in historyParameters.cast<Map<String, dynamic>>()) {
          if (parameter['in'] != 'query' ||
              (parameter['name'] != 'limit' && parameter['name'] != 'after')) {
            continue;
          }
          final schema = parameter['schema'] as Map<String, dynamic>;
          if (schema['type'] != 'number') {
            schema['type'] = 'number';
            report.historyParametersMadeNumeric++;
          }
        }
      }

      if (operationId == 'v2.session.active') {
        final responseSchema =
            (((operation['responses'] as Map<String, dynamic>)['200']
                        as Map<String, dynamic>)['content']
                    as Map<String, dynamic>)['application/json']
                as Map<String, dynamic>;
        final rootSchema = responseSchema['schema'] as Map<String, dynamic>;
        final dataSchema =
            (rootSchema['properties'] as Map<String, dynamic>)['data']
                as Map<String, dynamic>;
        final patterns = dataSchema['patternProperties'];
        if (patterns is Map<String, dynamic> && patterns.length == 1) {
          final entry = patterns.entries.single;
          dataSchema
            ..remove('patternProperties')
            ..['additionalProperties'] = entry.value
            ..['propertyNames'] = {'pattern': entry.key};
          report.patternPropertiesConverted++;
        }
      }
    }
  }
}

bool _isLocationDeepObject(Object? value) {
  if (value is! Map<String, dynamic>) return false;
  return value['name'] == 'location' &&
      value['in'] == 'query' &&
      value['style'] == 'deepObject';
}

List<Map<String, dynamic>> _expandLocationParameter(
  Map<String, dynamic> parameter,
) {
  final schema = parameter['schema'] as Map<String, dynamic>;
  final properties = schema['properties'] as Map<String, dynamic>;
  if (properties.length != 2 ||
      !properties.containsKey('directory') ||
      !properties.containsKey('workspace')) {
    throw FormatException('Unexpected location deepObject schema: $schema');
  }
  return ['directory', 'workspace'].map((property) {
    final result = <String, dynamic>{
      'name': 'location[$property]',
      'in': 'query',
      'schema': properties[property],
      'required': false,
    };
    final description = parameter['description'];
    if (description is String) result['description'] = description;
    return result;
  }).toList();
}

void _normalizeSchemas(
  Map<String, dynamic> document,
  NormalizationReport report,
) {
  void normalize(Object? value) {
    if (value is List) {
      for (final item in value) {
        normalize(item);
      }
      return;
    }
    if (value is! Map<String, dynamic>) return;

    if (value.remove('x-effect-stream') != null) {
      report.effectStreamExtensionsRemoved++;
    }

    for (final keyword in const ['anyOf', 'oneOf']) {
      final branches = value[keyword];
      if (branches is! List) continue;
      final seen = <String>{};
      final unique = <dynamic>[];
      for (final branch in branches) {
        final key = _canonicalJson(branch);
        if (seen.add(key)) {
          unique.add(branch);
        } else {
          report.duplicateUnionBranchesRemoved++;
        }
      }
      value[keyword] = unique;
    }

    final anyOf = value['anyOf'];
    if (anyOf is List && anyOf.length == 2 && anyOf.any(_isNullSchema)) {
      value['nullable'] = true;
      report.nullableUnionsMarked++;
    }
    if (anyOf is List && anyOf.any(_isEmptySchema)) {
      // An empty JSON Schema accepts every value, so this union contributes no
      // constraint. Sibling schema keywords remain in place.
      value.remove('anyOf');
      report.unconstrainedUnionsCollapsed++;
    }

    for (final child in value.values.toList()) {
      normalize(child);
    }
  }

  normalize(document);
}

void _hoistInlineMeaningfulUnions(
  Map<String, dynamic> document,
  NormalizationReport report,
) {
  final components = document['components'] as Map<String, dynamic>;
  final schemas = components['schemas'] as Map<String, dynamic>;
  final originalComponents = schemas.entries.toList();
  var ordinal = 1;

  String nextName() {
    while (true) {
      final name = 'OpencodeSdkRawUnion${ordinal.toString().padLeft(3, '0')}';
      ordinal++;
      if (!schemas.containsKey(name)) return name;
    }
  }

  void visit(Object? value, {required bool componentRoot}) {
    if (value is List) {
      for (final item in value) {
        visit(item, componentRoot: false);
      }
      return;
    }
    if (value is! Map<String, dynamic>) return;

    if (!componentRoot && isMeaningfulUnion(value)) {
      final name = nextName();
      schemas[name] = Map<String, dynamic>.from(value);
      value
        ..clear()
        ..[r'$ref'] = '#/components/schemas/$name';
      report.inlineMeaningfulUnionsHoisted++;
      return;
    }

    for (final child in value.values.toList()) {
      visit(child, componentRoot: false);
    }
  }

  for (final entry in originalComponents) {
    visit(entry.value, componentRoot: true);
  }
  visit(document['paths'], componentRoot: false);
}

bool isMeaningfulUnion(Map<String, dynamic> schema) {
  for (final keyword in const ['anyOf', 'oneOf']) {
    final branches = schema[keyword];
    if (branches is! List) continue;
    if (branches.where((branch) => !_isNullSchema(branch)).length > 1) {
      return true;
    }
  }
  return false;
}

bool _isNullSchema(Object? value) =>
    value is Map && value.length == 1 && value['type'] == 'null';

bool _isEmptySchema(Object? value) => value is Map && value.isEmpty;

String _canonicalJson(Object? value) {
  if (value is List) return '[${value.map(_canonicalJson).join(',')}]';
  if (value is Map) {
    final keys = value.keys.map((key) => key.toString()).toList()..sort();
    return '{${keys.map((key) => '${jsonEncode(key)}:${_canonicalJson(value[key])}').join(',')}}';
  }
  return jsonEncode(value);
}

Future<void> main(List<String> arguments) async {
  if (arguments.length < 2 || arguments.length > 3) {
    stderr.writeln(
      'Usage: dart run tool/sdk/normalize_openapi.dart <input> <output> [report]',
    );
    exitCode = 64;
    return;
  }

  final input = File(arguments[0]);
  final document =
      jsonDecode(await input.readAsString()) as Map<String, dynamic>;
  final report = normalizeOpenApi(document);
  const encoder = JsonEncoder.withIndent('  ');

  final output = File(arguments[1]);
  await output.parent.create(recursive: true);
  await output.writeAsString('${encoder.convert(document)}\n', flush: true);
  if (arguments.length == 3) {
    final reportFile = File(arguments[2]);
    await reportFile.parent.create(recursive: true);
    await reportFile.writeAsString(
      '${encoder.convert(report.toJson())}\n',
      flush: true,
    );
  }
  stderr.writeln('Normalized ${input.path}: ${jsonEncode(report.toJson())}');
}
