// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_history_list200_response_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncHistoryList200ResponseInner _$SyncHistoryList200ResponseInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncHistoryList200ResponseInner', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['id', 'aggregate_id', 'seq', 'type', 'data'],
  );
  final val = SyncHistoryList200ResponseInner(
    id: $checkedConvert('id', (v) => v as String),
    aggregateId: $checkedConvert('aggregate_id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => (v as num).toInt()),
    type: $checkedConvert('type', (v) => v as String),
    data: $checkedConvert('data', (v) => v as Object),
  );
  return val;
}, fieldKeyMap: const {'aggregateId': 'aggregate_id'});

Map<String, dynamic> _$SyncHistoryList200ResponseInnerToJson(
  SyncHistoryList200ResponseInner instance,
) => <String, dynamic>{
  'id': instance.id,
  'aggregate_id': instance.aggregateId,
  'seq': instance.seq,
  'type': instance.type,
  'data': instance.data,
};
