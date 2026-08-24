// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_replay_request_events_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncReplayRequestEventsInner _$SyncReplayRequestEventsInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncReplayRequestEventsInner', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['id', 'aggregateID', 'seq', 'type', 'data'],
  );
  final val = SyncReplayRequestEventsInner(
    id: $checkedConvert('id', (v) => v as String),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    seq: $checkedConvert('seq', (v) => (v as num).toInt()),
    type: $checkedConvert('type', (v) => v as String),
    data: $checkedConvert('data', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$SyncReplayRequestEventsInnerToJson(
  SyncReplayRequestEventsInner instance,
) => <String, dynamic>{
  'id': instance.id,
  'aggregateID': instance.aggregateID,
  'seq': instance.seq,
  'type': instance.type,
  'data': instance.data,
};
