// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_toast_show.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiToastShow _$EventTuiToastShowFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventTuiToastShow', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'properties']);
      final val = EventTuiToastShow(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventTuiToastShowTypeEnumEnumMap,
            v,
            unknownValue: EventTuiToastShowTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => TuiShowToastRequest.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventTuiToastShowToJson(EventTuiToastShow instance) =>
    <String, dynamic>{
      'type': _$EventTuiToastShowTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventTuiToastShowTypeEnumEnumMap = {
  EventTuiToastShowTypeEnum.tuiPeriodToastPeriodShow: 'tui.toast.show',
  EventTuiToastShowTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
