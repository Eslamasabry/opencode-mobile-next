// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_prompt_append_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiPromptAppendSchema2 _$EventTuiPromptAppendSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiPromptAppendSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventTuiPromptAppendSchema2(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiPromptAppendSchema2TypeEnumEnumMap,
        v,
        unknownValue: EventTuiPromptAppendSchema2TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => FindText200ResponseInnerPath.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiPromptAppendSchema2ToJson(
  EventTuiPromptAppendSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventTuiPromptAppendSchema2TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiPromptAppendSchema2TypeEnumEnumMap = {
  EventTuiPromptAppendSchema2TypeEnum.tuiPeriodPromptPeriodAppend:
      'tui.prompt.append',
  EventTuiPromptAppendSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
