// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_session_select_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiSessionSelectSchema2 _$EventTuiSessionSelectSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiSessionSelectSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventTuiSessionSelectSchema2(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiSessionSelectSchema2TypeEnumEnumMap,
        v,
        unknownValue:
            EventTuiSessionSelectSchema2TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => TuiSelectSessionRequest.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiSessionSelectSchema2ToJson(
  EventTuiSessionSelectSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventTuiSessionSelectSchema2TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiSessionSelectSchema2TypeEnumEnumMap = {
  EventTuiSessionSelectSchema2TypeEnum.tuiPeriodSessionPeriodSelect:
      'tui.session.select',
  EventTuiSessionSelectSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
