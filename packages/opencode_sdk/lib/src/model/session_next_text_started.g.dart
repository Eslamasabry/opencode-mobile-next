// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_text_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextTextStarted _$SessionNextTextStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextTextStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextTextStarted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextTextStartedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextTextStartedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextTextStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextTextStartedToJson(
  SessionNextTextStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextTextStartedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextTextStartedTypeEnumEnumMap = {
  SessionNextTextStartedTypeEnum.sessionPeriodNextPeriodTextPeriodStarted:
      'session.next.text.started',
  SessionNextTextStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
