// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_compacted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionCompacted _$SessionCompactedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionCompacted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionCompacted(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionCompactedTypeEnumEnumMap,
            v,
            unknownValue: SessionCompactedTypeEnum.unknownDefaultOpenApi,
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
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => SyncStealRequest.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionCompactedToJson(SessionCompacted instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionCompactedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionCompactedTypeEnumEnumMap = {
  SessionCompactedTypeEnum.sessionPeriodCompacted: 'session.compacted',
  SessionCompactedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
