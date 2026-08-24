// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_success_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolSuccessSyncEvent
_$SyncEventSessionNextToolSuccessSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextToolSuccessSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextToolSuccessSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextToolSuccessSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextToolSuccessSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextToolSuccessSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextToolSuccessSyncEventToJson(
  SyncEventSessionNextToolSuccessSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextToolSuccessSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextToolSuccessSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextToolSuccessSyncEventTypeEnum
          .sessionPeriodNextPeriodToolPeriodSuccessPeriod1:
      'session.next.tool.success.1',
  SyncEventSessionNextToolSuccessSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
