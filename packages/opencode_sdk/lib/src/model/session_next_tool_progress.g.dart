// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_progress.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolProgress _$SessionNextToolProgressFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolProgress', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolProgress(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolProgressTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolProgressTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextToolProgressSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolProgressToJson(
  SessionNextToolProgress instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolProgressTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolProgressTypeEnumEnumMap = {
  SessionNextToolProgressTypeEnum.sessionPeriodNextPeriodToolPeriodProgress:
      'session.next.tool.progress',
  SessionNextToolProgressTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
