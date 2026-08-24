// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'output_format_json_schema.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OutputFormatJsonSchema _$OutputFormatJsonSchemaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OutputFormatJsonSchema', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'schema']);
  final val = OutputFormatJsonSchema(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OutputFormatJsonSchemaTypeEnumEnumMap,
        v,
        unknownValue: OutputFormatJsonSchemaTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    schema: $checkedConvert('schema', (v) => v as Object),
    retryCount: $checkedConvert('retryCount', (v) => (v as num?)?.toInt()),
  );
  return val;
});

Map<String, dynamic> _$OutputFormatJsonSchemaToJson(
  OutputFormatJsonSchema instance,
) => <String, dynamic>{
  'type': _$OutputFormatJsonSchemaTypeEnumEnumMap[instance.type]!,
  'schema': instance.schema,
  'retryCount': ?instance.retryCount,
};

const _$OutputFormatJsonSchemaTypeEnumEnumMap = {
  OutputFormatJsonSchemaTypeEnum.jsonSchema: 'json_schema',
  OutputFormatJsonSchemaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
