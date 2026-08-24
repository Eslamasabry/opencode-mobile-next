// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_session_select.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiSessionSelect _$EventTuiSessionSelectFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiSessionSelect', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'properties']);
  final val = EventTuiSessionSelect(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiSessionSelectTypeEnumEnumMap,
        v,
        unknownValue: EventTuiSessionSelectTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => TuiSelectSessionRequest.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiSessionSelectToJson(
  EventTuiSessionSelect instance,
) => <String, dynamic>{
  'type': _$EventTuiSessionSelectTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiSessionSelectTypeEnumEnumMap = {
  EventTuiSessionSelectTypeEnum.tuiPeriodSessionPeriodSelect:
      'tui.session.select',
  EventTuiSessionSelectTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
