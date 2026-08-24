// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolFailed _$SessionNextToolFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolFailed(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolFailedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolFailedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextToolFailedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolFailedToJson(
  SessionNextToolFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolFailedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolFailedTypeEnumEnumMap = {
  SessionNextToolFailedTypeEnum.sessionPeriodNextPeriodToolPeriodFailed:
      'session.next.tool.failed',
  SessionNextToolFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
