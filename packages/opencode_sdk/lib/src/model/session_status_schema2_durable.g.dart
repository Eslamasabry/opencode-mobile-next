// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_schema2_durable.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusSchema2Durable _$SessionStatusSchema2DurableFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionStatusSchema2Durable', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['aggregateID', 'seq', 'version']);
  final val = SessionStatusSchema2Durable(
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    seq: $checkedConvert('seq', (v) => (v as num).toInt()),
    version: $checkedConvert('version', (v) => (v as num).toInt()),
  );
  return val;
});

Map<String, dynamic> _$SessionStatusSchema2DurableToJson(
  SessionStatusSchema2Durable instance,
) => <String, dynamic>{
  'aggregateID': instance.aggregateID,
  'seq': instance.seq,
  'version': instance.version,
};
