// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_compaction_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextCompactionEnded _$EventSessionNextCompactionEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextCompactionEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextCompactionEnded(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextCompactionEndedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextCompactionEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextCompactionEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextCompactionEndedToJson(
  EventSessionNextCompactionEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextCompactionEndedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextCompactionEndedTypeEnumEnumMap = {
  EventSessionNextCompactionEndedTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodEnded:
      'session.next.compaction.ended',
  EventSessionNextCompactionEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
