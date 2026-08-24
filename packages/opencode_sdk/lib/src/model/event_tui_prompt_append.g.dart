// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_prompt_append.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiPromptAppend _$EventTuiPromptAppendFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiPromptAppend', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'properties']);
  final val = EventTuiPromptAppend(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiPromptAppendTypeEnumEnumMap,
        v,
        unknownValue: EventTuiPromptAppendTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => FindText200ResponseInnerPath.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiPromptAppendToJson(
  EventTuiPromptAppend instance,
) => <String, dynamic>{
  'type': _$EventTuiPromptAppendTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiPromptAppendTypeEnumEnumMap = {
  EventTuiPromptAppendTypeEnum.tuiPeriodPromptPeriodAppend: 'tui.prompt.append',
  EventTuiPromptAppendTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
