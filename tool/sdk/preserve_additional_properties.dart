import 'dart:convert';
import 'dart:io';

final class _GeneratedMixedObject {
  const _GeneratedMixedObject({
    required this.className,
    required this.fileName,
    this.valueClassName,
  });

  final String className;
  final String fileName;
  final String? valueClassName;
}

const _generatedMixedObjects = <String, _GeneratedMixedObject>{
  '#/components/schemas/AgentConfig': _GeneratedMixedObject(
    className: 'AgentConfig',
    fileName: 'agent_config.dart',
  ),
  '#/components/schemas/ProviderConfig/properties/options':
      _GeneratedMixedObject(
        className: 'ProviderConfigOptions',
        fileName: 'provider_config_options.dart',
      ),
  '#/components/schemas/ProviderConfig/properties/models/additionalProperties/properties/variants/additionalProperties':
      _GeneratedMixedObject(
        className: 'ProviderConfigModelsValueVariantsValue',
        fileName: 'provider_config_models_value_variants_value.dart',
      ),
  '#/components/schemas/Config/properties/mode': _GeneratedMixedObject(
    className: 'ConfigMode',
    fileName: 'config_mode.dart',
    valueClassName: 'AgentConfig',
  ),
  '#/components/schemas/Config/properties/agent': _GeneratedMixedObject(
    className: 'ConfigAgent',
    fileName: 'config_agent.dart',
    valueClassName: 'AgentConfig',
  ),
};

const _losslessUnionMixedObjects = <String>{
  '#/components/schemas/PermissionConfig/anyOf/1',
};

Future<void> main(List<String> arguments) async {
  if (arguments.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/sdk/preserve_additional_properties.dart '
      '<openapi> <package>',
    );
    exitCode = 64;
    return;
  }

  final document =
      jsonDecode(await File(arguments[0]).readAsString())
          as Map<String, dynamic>;
  final mixedObjects = <String, Map<String, dynamic>>{};
  _collectMixedObjects(document, const <String>[], mixedObjects);

  final expected = {
    ..._generatedMixedObjects.keys,
    ..._losslessUnionMixedObjects,
  };
  final residual = mixedObjects.keys.toSet().difference(expected);
  final missing = expected.difference(mixedObjects.keys.toSet());
  if (residual.isNotEmpty || missing.isNotEmpty) {
    throw StateError(
      'Mixed additionalProperties coverage changed. Missing: '
      '${missing.toList()}; residual: ${residual.toList()}',
    );
  }

  final modelDirectory = Directory('${arguments[1]}/lib/src/model');
  for (final entry in _generatedMixedObjects.entries) {
    final schema = mixedObjects[entry.key]!;
    final propertyNames = (schema['properties'] as Map).keys.cast<String>();
    final file = File('${modelDirectory.path}/${entry.value.fileName}');
    await _preserveUnknownProperties(file, entry.value, propertyNames.toList());
  }

  final inventory = File(
    '${arguments[1]}/tool/additional_properties_inventory.json',
  );
  await inventory.parent.create(recursive: true);
  await inventory.writeAsString(
    '${const JsonEncoder.withIndent('  ').convert({
      'losslessMixedAdditionalProperties': [
        for (final entry in mixedObjects.entries) {'location': entry.key, 'strategy': _losslessUnionMixedObjects.contains(entry.key) ? 'descriptorBackedRawJsonUnion' : 'typedFieldsWithAdditionalProperties', if (_generatedMixedObjects[entry.key] case final generated?) 'className': generated.className, 'schema': entry.value},
      ],
      'residualLossyAdditionalProperties': residual.toList(),
    })}\n',
    flush: true,
  );
  stdout.writeln(
    jsonEncode({
      'losslessMixedAdditionalProperties': mixedObjects.length,
      'residualLossyAdditionalProperties': residual.length,
    }),
  );
}

void _collectMixedObjects(
  Object? value,
  List<String> path,
  Map<String, Map<String, dynamic>> result,
) {
  if (value is List) {
    for (var index = 0; index < value.length; index++) {
      _collectMixedObjects(value[index], [...path, '$index'], result);
    }
    return;
  }
  if (value is! Map<String, dynamic>) return;

  final properties = value['properties'];
  final additionalProperties = value['additionalProperties'];
  if (properties is Map &&
      properties.isNotEmpty &&
      additionalProperties is Map) {
    result[_jsonPointer(path)] = value;
  }
  for (final entry in value.entries) {
    _collectMixedObjects(entry.value, [...path, entry.key], result);
  }
}

String _jsonPointer(List<String> path) =>
    '#/${path.map((part) => part.replaceAll('~', '~0').replaceAll('/', '~1')).join('/')}';

Future<void> _preserveUnknownProperties(
  File file,
  _GeneratedMixedObject model,
  List<String> propertyNames,
) async {
  var source = await file.readAsString();
  if (source.contains('get additionalProperties => _additionalProperties;')) {
    throw StateError('${model.className} was already augmented.');
  }

  final constructorStart = source.indexOf('  ${model.className}({');
  final constructorEnd = source.indexOf('  });', constructorStart);
  if (constructorStart < 0 || constructorEnd < 0) {
    throw StateError('Could not locate ${model.className} constructor.');
  }
  final valueType = model.valueClassName ?? 'Object?';
  source = source.replaceRange(
    constructorEnd,
    constructorEnd + '  });'.length,
    '    Map<String, $valueType> additionalProperties = const {},\n'
    '  }) : _additionalProperties = Map.unmodifiable(additionalProperties);',
  );

  final equalityStart = source.indexOf('  bool operator ==', constructorEnd);
  if (equalityStart < 0) {
    throw StateError('Could not locate ${model.className} equality operator.');
  }
  source = source.replaceRange(
    equalityStart,
    equalityStart,
    '  Map<String, $valueType> _additionalProperties;\n\n'
    '  @JsonKey(includeFromJson: false, includeToJson: false)\n'
    '  Map<String, $valueType> get additionalProperties => '
    '_additionalProperties;\n\n',
  );

  final factoryStart = source.indexOf(
    '  factory ${model.className}.fromJson(',
    equalityStart,
  );
  final stringStart = source.indexOf('  String toString()', factoryStart);
  if (factoryStart < 0 || stringStart < 0) {
    throw StateError('Could not locate ${model.className} JSON methods.');
  }
  final knownKeys = propertyNames.map(_dartString).join(', ');
  final deserializeValue = model.valueClassName == null
      ? 'entry.value'
      : '${model.valueClassName}.fromJson(entry.value as Map<String, dynamic>)';
  final serializeValue = model.valueClassName == null
      ? 'entry.value'
      : 'entry.value.toJson()';
  source = source.replaceRange(
    factoryStart,
    stringStart,
    '''  factory ${model.className}.fromJson(Map<String, dynamic> json) {
    final value = _\$${model.className}FromJson(json);
    const knownKeys = <String>{$knownKeys};
    value._additionalProperties = Map.unmodifiable({
      for (final entry in json.entries)
        if (!knownKeys.contains(entry.key)) entry.key: $deserializeValue,
    });
    return value;
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    for (final entry in additionalProperties.entries)
      entry.key: $serializeValue,
    ..._\$${model.className}ToJson(this),
  };

''',
  );
  await file.writeAsString(source, flush: true);
}

String _dartString(String value) => "r'${value.replaceAll("'", "\\'")}'";
