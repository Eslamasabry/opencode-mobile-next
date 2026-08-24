// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_text_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextTextDelta _$SessionNextTextDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextTextDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextTextDelta(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextTextDeltaTypeEnumEnumMap,
        v,
        unknownValue: SessionNextTextDeltaTypeEnum.unknownDefaultOpenApi,
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
      (v) => SessionNextTextDeltaData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextTextDeltaToJson(
  SessionNextTextDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextTextDeltaTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextTextDeltaTypeEnumEnumMap = {
  SessionNextTextDeltaTypeEnum.sessionPeriodNextPeriodTextPeriodDelta:
      'session.next.text.delta',
  SessionNextTextDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
