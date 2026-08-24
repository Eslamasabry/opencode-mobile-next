// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_synthetic.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextSynthetic _$SessionNextSyntheticFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextSynthetic', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextSynthetic(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextSyntheticTypeEnumEnumMap,
        v,
        unknownValue: SessionNextSyntheticTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SessionNextSyntheticToJson(
  SessionNextSynthetic instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextSyntheticTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextSyntheticTypeEnumEnumMap = {
  SessionNextSyntheticTypeEnum.sessionPeriodNextPeriodSynthetic:
      'session.next.synthetic',
  SessionNextSyntheticTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
