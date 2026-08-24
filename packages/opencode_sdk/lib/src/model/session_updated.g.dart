// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionUpdated _$SessionUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionUpdatedTypeEnumEnumMap,
            v,
            unknownValue: SessionUpdatedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SessionUpdatedToJson(SessionUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionUpdatedTypeEnumEnumMap = {
  SessionUpdatedTypeEnum.sessionPeriodUpdated: 'session.updated',
  SessionUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
