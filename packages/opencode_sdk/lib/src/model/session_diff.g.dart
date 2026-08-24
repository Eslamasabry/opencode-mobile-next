// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_diff.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionDiff _$SessionDiffFromJson(Map<String, dynamic> json) => $checkedCreate(
  'SessionDiff',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = SessionDiff(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$SessionDiffTypeEnumEnumMap,
          v,
          unknownValue: SessionDiffTypeEnum.unknownDefaultOpenApi,
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
        (v) =>
            v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
      ),
      data: $checkedConvert(
        'data',
        (v) => SessionDiffData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$SessionDiffToJson(SessionDiff instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionDiffTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionDiffTypeEnumEnumMap = {
  SessionDiffTypeEnum.sessionPeriodDiff: 'session.diff',
  SessionDiffTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
