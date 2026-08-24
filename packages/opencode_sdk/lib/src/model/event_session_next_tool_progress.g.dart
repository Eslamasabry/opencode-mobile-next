// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_progress.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolProgress _$EventSessionNextToolProgressFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextToolProgress', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextToolProgress(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextToolProgressTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextToolProgressTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextToolProgressSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextToolProgressToJson(
  EventSessionNextToolProgress instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolProgressTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolProgressTypeEnumEnumMap = {
  EventSessionNextToolProgressTypeEnum
          .sessionPeriodNextPeriodToolPeriodProgress:
      'session.next.tool.progress',
  EventSessionNextToolProgressTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
