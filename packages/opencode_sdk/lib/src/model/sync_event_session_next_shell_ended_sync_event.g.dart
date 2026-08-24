// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_shell_ended_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextShellEndedSyncEvent
_$SyncEventSessionNextShellEndedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextShellEndedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextShellEndedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextShellEndedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextShellEndedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextShellEndedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextShellEndedSyncEventToJson(
  SyncEventSessionNextShellEndedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextShellEndedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextShellEndedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextShellEndedSyncEventTypeEnum
          .sessionPeriodNextPeriodShellPeriodEndedPeriod1:
      'session.next.shell.ended.1',
  SyncEventSessionNextShellEndedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
