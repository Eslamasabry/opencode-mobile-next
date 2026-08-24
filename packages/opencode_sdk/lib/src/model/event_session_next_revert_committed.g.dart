// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_revert_committed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextRevertCommitted _$EventSessionNextRevertCommittedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextRevertCommitted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextRevertCommitted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextRevertCommittedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextRevertCommittedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextRevertCommittedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextRevertCommittedToJson(
  EventSessionNextRevertCommitted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextRevertCommittedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextRevertCommittedTypeEnumEnumMap = {
  EventSessionNextRevertCommittedTypeEnum
          .sessionPeriodNextPeriodRevertPeriodCommitted:
      'session.next.revert.committed',
  EventSessionNextRevertCommittedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
