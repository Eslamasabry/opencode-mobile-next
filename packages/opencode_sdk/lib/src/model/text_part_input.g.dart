// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'text_part_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TextPartInput _$TextPartInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TextPartInput', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'text']);
      final val = TextPartInput(
        id: $checkedConvert('id', (v) => v as String?),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$TextPartInputTypeEnumEnumMap,
            v,
            unknownValue: TextPartInputTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        text: $checkedConvert('text', (v) => v as String),
        synthetic: $checkedConvert('synthetic', (v) => v as bool?),
        ignored: $checkedConvert('ignored', (v) => v as bool?),
        time: $checkedConvert(
          'time',
          (v) => v == null
              ? null
              : TextPartTime.fromJson(v as Map<String, dynamic>),
        ),
        metadata: $checkedConvert('metadata', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$TextPartInputToJson(TextPartInput instance) =>
    <String, dynamic>{
      'id': ?instance.id,
      'type': _$TextPartInputTypeEnumEnumMap[instance.type]!,
      'text': instance.text,
      'synthetic': ?instance.synthetic,
      'ignored': ?instance.ignored,
      'time': ?instance.time?.toJson(),
      'metadata': ?instance.metadata,
    };

const _$TextPartInputTypeEnumEnumMap = {
  TextPartInputTypeEnum.text: 'text',
  TextPartInputTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
