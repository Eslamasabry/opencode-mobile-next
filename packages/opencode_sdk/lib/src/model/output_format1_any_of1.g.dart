// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'output_format1_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OutputFormat1AnyOf1 _$OutputFormat1AnyOf1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('OutputFormat1AnyOf1', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'schema']);
      final val = OutputFormat1AnyOf1(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$OutputFormat1AnyOf1TypeEnumEnumMap,
            v,
            unknownValue: OutputFormat1AnyOf1TypeEnum.unknownDefaultOpenApi,
          ),
        ),
        schema: $checkedConvert('schema', (v) => v as Object),
        retryCount: $checkedConvert('retryCount', (v) => (v as num?)?.toInt()),
      );
      return val;
    });

Map<String, dynamic> _$OutputFormat1AnyOf1ToJson(
  OutputFormat1AnyOf1 instance,
) => <String, dynamic>{
  'type': _$OutputFormat1AnyOf1TypeEnumEnumMap[instance.type]!,
  'schema': instance.schema,
  'retryCount': ?instance.retryCount,
};

const _$OutputFormat1AnyOf1TypeEnumEnumMap = {
  OutputFormat1AnyOf1TypeEnum.jsonSchema: 'json_schema',
  OutputFormat1AnyOf1TypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
