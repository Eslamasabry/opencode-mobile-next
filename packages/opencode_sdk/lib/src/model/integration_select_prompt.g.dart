// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_select_prompt.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationSelectPrompt _$IntegrationSelectPromptFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationSelectPrompt', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'key', 'message', 'options']);
  final val = IntegrationSelectPrompt(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$IntegrationSelectPromptTypeEnumEnumMap,
        v,
        unknownValue: IntegrationSelectPromptTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    key: $checkedConvert('key', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
    options: $checkedConvert(
      'options',
      (v) => (v as List<dynamic>)
          .map(
            (e) => IntegrationSelectPromptOptionsInner.fromJson(
              e as Map<String, dynamic>,
            ),
          )
          .toList(),
    ),
    when_: $checkedConvert(
      'when',
      (v) => v == null
          ? null
          : IntegrationWhen.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'when_': 'when'});

Map<String, dynamic> _$IntegrationSelectPromptToJson(
  IntegrationSelectPrompt instance,
) => <String, dynamic>{
  'type': _$IntegrationSelectPromptTypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'message': instance.message,
  'options': instance.options.map((e) => e.toJson()).toList(),
  'when': ?instance.when_?.toJson(),
};

const _$IntegrationSelectPromptTypeEnumEnumMap = {
  IntegrationSelectPromptTypeEnum.select: 'select',
  IntegrationSelectPromptTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
