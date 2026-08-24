// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_compaction_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextCompactionDelta _$EventSessionNextCompactionDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextCompactionDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextCompactionDelta(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextCompactionDeltaTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextCompactionDeltaTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextCompactionDeltaToJson(
  EventSessionNextCompactionDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextCompactionDeltaTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextCompactionDeltaTypeEnumEnumMap = {
  EventSessionNextCompactionDeltaTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodDelta:
      'session.next.compaction.delta',
  EventSessionNextCompactionDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
