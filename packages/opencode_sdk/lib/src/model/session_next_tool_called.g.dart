// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_tool_called.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextToolCalled _$SessionNextToolCalledFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextToolCalled', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextToolCalled(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextToolCalledTypeEnumEnumMap,
        v,
        unknownValue: SessionNextToolCalledTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextToolCalledSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextToolCalledToJson(
  SessionNextToolCalled instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextToolCalledTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextToolCalledTypeEnumEnumMap = {
  SessionNextToolCalledTypeEnum.sessionPeriodNextPeriodToolPeriodCalled:
      'session.next.tool.called',
  SessionNextToolCalledTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
