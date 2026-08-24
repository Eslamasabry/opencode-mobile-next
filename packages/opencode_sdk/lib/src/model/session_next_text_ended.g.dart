// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_text_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextTextEnded _$SessionNextTextEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextTextEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextTextEnded(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextTextEndedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextTextEndedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextTextEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextTextEndedToJson(
  SessionNextTextEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextTextEndedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextTextEndedTypeEnumEnumMap = {
  SessionNextTextEndedTypeEnum.sessionPeriodNextPeriodTextPeriodEnded:
      'session.next.text.ended',
  SessionNextTextEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
