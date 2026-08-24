// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolFailed _$EventSessionNextToolFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextToolFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextToolFailed(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextToolFailedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextToolFailedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextToolFailedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextToolFailedToJson(
  EventSessionNextToolFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolFailedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolFailedTypeEnumEnumMap = {
  EventSessionNextToolFailedTypeEnum.sessionPeriodNextPeriodToolPeriodFailed:
      'session.next.tool.failed',
  EventSessionNextToolFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
