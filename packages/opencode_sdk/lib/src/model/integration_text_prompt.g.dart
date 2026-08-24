// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_text_prompt.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationTextPrompt _$IntegrationTextPromptFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationTextPrompt', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'key', 'message']);
  final val = IntegrationTextPrompt(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$IntegrationTextPromptTypeEnumEnumMap,
        v,
        unknownValue: IntegrationTextPromptTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    key: $checkedConvert('key', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
    placeholder: $checkedConvert('placeholder', (v) => v as String?),
    when_: $checkedConvert(
      'when',
      (v) => v == null
          ? null
          : IntegrationWhen.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'when_': 'when'});

Map<String, dynamic> _$IntegrationTextPromptToJson(
  IntegrationTextPrompt instance,
) => <String, dynamic>{
  'type': _$IntegrationTextPromptTypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'message': instance.message,
  'placeholder': ?instance.placeholder,
  'when': ?instance.when_?.toJson(),
};

const _$IntegrationTextPromptTypeEnumEnumMap = {
  IntegrationTextPromptTypeEnum.text: 'text',
  IntegrationTextPromptTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
