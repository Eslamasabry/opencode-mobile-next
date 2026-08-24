// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_compaction.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageCompaction _$SessionMessageCompactionFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageCompaction', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'reason', 'summary', 'recent', 'id', 'time'],
  );
  final val = SessionMessageCompaction(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageCompactionTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageCompactionTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    reason: $checkedConvert(
      'reason',
      (v) => $enumDecode(
        _$SessionMessageCompactionReasonEnumEnumMap,
        v,
        unknownValue: SessionMessageCompactionReasonEnum.unknownDefaultOpenApi,
      ),
    ),
    summary: $checkedConvert('summary', (v) => v as String),
    recent: $checkedConvert('recent', (v) => v as String),
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) =>
          SessionMessageAgentSwitchedTime.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageCompactionToJson(
  SessionMessageCompaction instance,
) => <String, dynamic>{
  'type': _$SessionMessageCompactionTypeEnumEnumMap[instance.type]!,
  'reason': _$SessionMessageCompactionReasonEnumEnumMap[instance.reason]!,
  'summary': instance.summary,
  'recent': instance.recent,
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
};

const _$SessionMessageCompactionTypeEnumEnumMap = {
  SessionMessageCompactionTypeEnum.compaction: 'compaction',
  SessionMessageCompactionTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};

const _$SessionMessageCompactionReasonEnumEnumMap = {
  SessionMessageCompactionReasonEnum.auto: 'auto',
  SessionMessageCompactionReasonEnum.manual: 'manual',
  SessionMessageCompactionReasonEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
