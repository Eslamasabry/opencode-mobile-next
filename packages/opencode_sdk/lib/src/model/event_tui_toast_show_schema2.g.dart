// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_toast_show_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiToastShowSchema2 _$EventTuiToastShowSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiToastShowSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventTuiToastShowSchema2(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiToastShowSchema2TypeEnumEnumMap,
        v,
        unknownValue: EventTuiToastShowSchema2TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => TuiShowToastRequest.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiToastShowSchema2ToJson(
  EventTuiToastShowSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventTuiToastShowSchema2TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiToastShowSchema2TypeEnumEnumMap = {
  EventTuiToastShowSchema2TypeEnum.tuiPeriodToastPeriodShow: 'tui.toast.show',
  EventTuiToastShowSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
