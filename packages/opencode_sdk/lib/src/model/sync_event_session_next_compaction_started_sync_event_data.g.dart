// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_compaction_started_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextCompactionStartedSyncEventData
_$SyncEventSessionNextCompactionStartedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate(
  'SyncEventSessionNextCompactionStartedSyncEventData',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const ['timestamp', 'sessionID', 'messageID', 'reason'],
    );
    final val = SyncEventSessionNextCompactionStartedSyncEventData(
      timestamp: $checkedConvert('timestamp', (v) => v as num),
      sessionID: $checkedConvert('sessionID', (v) => v as String),
      messageID: $checkedConvert('messageID', (v) => v as String),
      reason: $checkedConvert(
        'reason',
        (v) => $enumDecode(
          _$SyncEventSessionNextCompactionStartedSyncEventDataReasonEnumEnumMap,
          v,
          unknownValue:
              SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum
                  .unknownDefaultOpenApi,
        ),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$SyncEventSessionNextCompactionStartedSyncEventDataToJson(
  SyncEventSessionNextCompactionStartedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'reason':
      _$SyncEventSessionNextCompactionStartedSyncEventDataReasonEnumEnumMap[instance
          .reason]!,
};

const _$SyncEventSessionNextCompactionStartedSyncEventDataReasonEnumEnumMap = {
  SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum.auto: 'auto',
  SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum.manual: 'manual',
  SyncEventSessionNextCompactionStartedSyncEventDataReasonEnum
          .unknownDefaultOpenApi:
      'unknown_default_open_api',
};
