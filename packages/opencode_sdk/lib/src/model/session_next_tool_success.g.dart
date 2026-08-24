// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_success.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolSuccess _$SessionNextToolSuccessFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolSuccess', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolSuccess(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolSuccessTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolSuccessTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextToolSuccessSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolSuccessToJson(
  SessionNextToolSuccess instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolSuccessTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolSuccessTypeEnumEnumMap = {
  SessionNextToolSuccessTypeEnum.sessionPeriodNextPeriodToolPeriodSuccess:
      'session.next.tool.success',
  SessionNextToolSuccessTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
