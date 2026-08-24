// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_input_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolInputEnded _$EventSessionNextToolInputEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextToolInputEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextToolInputEnded(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextToolInputEndedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextToolInputEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextToolInputEndedToJson(
  EventSessionNextToolInputEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolInputEndedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolInputEndedTypeEnumEnumMap = {
  EventSessionNextToolInputEndedTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodEnded:
      'session.next.tool.input.ended',
  EventSessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
