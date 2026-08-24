// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'output_format_text.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OutputFormatText _$OutputFormatTextFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OutputFormatText', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type']);
      final val = OutputFormatText(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$OutputFormatTextTypeEnumEnumMap,
            v,
            unknownValue: OutputFormatTextTypeEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$OutputFormatTextToJson(OutputFormatText instance) =>
    <String, dynamic>{
      'type': _$OutputFormatTextTypeEnumEnumMap[instance.type]!,
    };

const _$OutputFormatTextTypeEnumEnumMap = {
  OutputFormatTextTypeEnum.text: 'text',
  OutputFormatTextTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
