// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'output_format1_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OutputFormat1AnyOf _$OutputFormat1AnyOfFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OutputFormat1AnyOf', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type']);
      final val = OutputFormat1AnyOf(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$OutputFormat1AnyOfTypeEnumEnumMap,
            v,
            unknownValue: OutputFormat1AnyOfTypeEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$OutputFormat1AnyOfToJson(OutputFormat1AnyOf instance) =>
    <String, dynamic>{
      'type': _$OutputFormat1AnyOfTypeEnumEnumMap[instance.type]!,
    };

const _$OutputFormat1AnyOfTypeEnumEnumMap = {
  OutputFormat1AnyOfTypeEnum.text: 'text',
  OutputFormat1AnyOfTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
