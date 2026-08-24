// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_select_prompt_options_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationSelectPromptOptionsInner
_$IntegrationSelectPromptOptionsInnerFromJson(Map<String, dynamic> json) =>
    $checkedCreate('IntegrationSelectPromptOptionsInner', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['label', 'value']);
      final val = IntegrationSelectPromptOptionsInner(
        label: $checkedConvert('label', (v) => v as String),
        value: $checkedConvert('value', (v) => v as String),
        hint: $checkedConvert('hint', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$IntegrationSelectPromptOptionsInnerToJson(
  IntegrationSelectPromptOptionsInner instance,
) => <String, dynamic>{
  'label': instance.label,
  'value': instance.value,
  'hint': ?instance.hint,
};
