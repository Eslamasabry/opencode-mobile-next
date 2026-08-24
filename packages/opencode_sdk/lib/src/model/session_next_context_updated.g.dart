// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_context_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextContextUpdated _$SessionNextContextUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextContextUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextContextUpdated(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextContextUpdatedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextContextUpdatedToJson(
  SessionNextContextUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextContextUpdatedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextContextUpdatedTypeEnumEnumMap = {
  SessionNextContextUpdatedTypeEnum.sessionPeriodNextPeriodContextPeriodUpdated:
      'session.next.context.updated',
  SessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
