// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_input_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolInputStarted _$SessionNextToolInputStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolInputStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolInputStarted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolInputStartedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolInputStartedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextToolInputStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolInputStartedToJson(
  SessionNextToolInputStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolInputStartedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolInputStartedTypeEnumEnumMap = {
  SessionNextToolInputStartedTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodStarted:
      'session.next.tool.input.started',
  SessionNextToolInputStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
