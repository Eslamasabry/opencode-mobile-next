// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_idle.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionIdle _$SessionIdleFromJson(Map<String, dynamic> json) => $checkedCreate(
  'SessionIdle',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = SessionIdle(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$SessionIdleTypeEnumEnumMap,
          v,
          unknownValue: SessionIdleTypeEnum.unknownDefaultOpenApi,
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
        (v) => SyncStealRequest.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$SessionIdleToJson(SessionIdle instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionIdleTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionIdleTypeEnumEnumMap = {
  SessionIdleTypeEnum.sessionPeriodIdle: 'session.idle',
  SessionIdleTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
