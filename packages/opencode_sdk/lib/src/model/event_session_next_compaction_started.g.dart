// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_compaction_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextCompactionStarted _$EventSessionNextCompactionStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextCompactionStarted', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextCompactionStarted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextCompactionStartedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextCompactionStartedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextCompactionStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextCompactionStartedToJson(
  EventSessionNextCompactionStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextCompactionStartedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextCompactionStartedTypeEnumEnumMap = {
  EventSessionNextCompactionStartedTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodStarted:
      'session.next.compaction.started',
  EventSessionNextCompactionStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
