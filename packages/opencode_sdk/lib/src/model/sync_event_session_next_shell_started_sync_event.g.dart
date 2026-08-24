// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_shell_started_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextShellStartedSyncEvent
_$SyncEventSessionNextShellStartedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextShellStartedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextShellStartedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextShellStartedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextShellStartedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextShellStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextShellStartedSyncEventToJson(
  SyncEventSessionNextShellStartedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextShellStartedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextShellStartedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextShellStartedSyncEventTypeEnum
          .sessionPeriodNextPeriodShellPeriodStartedPeriod1:
      'session.next.shell.started.1',
  SyncEventSessionNextShellStartedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
