// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_called.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolCalled _$EventSessionNextToolCalledFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextToolCalled', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextToolCalled(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextToolCalledTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextToolCalledTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextToolCalledSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextToolCalledToJson(
  EventSessionNextToolCalled instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolCalledTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolCalledTypeEnumEnumMap = {
  EventSessionNextToolCalledTypeEnum.sessionPeriodNextPeriodToolPeriodCalled:
      'session.next.tool.called',
  EventSessionNextToolCalledTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
