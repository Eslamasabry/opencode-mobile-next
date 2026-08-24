// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_created.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionCreated _$SessionCreatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionCreated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionCreated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionCreatedTypeEnumEnumMap,
            v,
            unknownValue: SessionCreatedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventSessionCreatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionCreatedToJson(SessionCreated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionCreatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionCreatedTypeEnumEnumMap = {
  SessionCreatedTypeEnum.sessionPeriodCreated: 'session.created',
  SessionCreatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
