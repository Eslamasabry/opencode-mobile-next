// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_input_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolInputEnded _$SessionNextToolInputEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolInputEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolInputEnded(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolInputEndedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    durable: $checkedConvert(
      'durable',
      (v) => v == null
          ? null
          : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
    ),
    location: $checkedConvert(
      'location',
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextToolInputEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolInputEndedToJson(
  SessionNextToolInputEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolInputEndedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolInputEndedTypeEnumEnumMap = {
  SessionNextToolInputEndedTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodEnded:
      'session.next.tool.input.ended',
  SessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
