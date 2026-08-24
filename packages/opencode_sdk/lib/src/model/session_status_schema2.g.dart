// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusSchema2 _$SessionStatusSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionStatusSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionStatusSchema2(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionStatusSchema2TypeEnumEnumMap,
        v,
        unknownValue: SessionStatusSchema2TypeEnum.unknownDefaultOpenApi,
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
      (v) => SessionStatusSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionStatusSchema2ToJson(
  SessionStatusSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionStatusSchema2TypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionStatusSchema2TypeEnumEnumMap = {
  SessionStatusSchema2TypeEnum.sessionPeriodStatus: 'session.status',
  SessionStatusSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
