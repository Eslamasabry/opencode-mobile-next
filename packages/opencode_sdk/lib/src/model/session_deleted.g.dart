// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_deleted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionDeleted _$SessionDeletedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionDeleted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionDeleted(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionDeletedTypeEnumEnumMap,
            v,
            unknownValue: SessionDeletedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SessionDeletedToJson(SessionDeleted instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionDeletedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionDeletedTypeEnumEnumMap = {
  SessionDeletedTypeEnum.sessionPeriodDeleted: 'session.deleted',
  SessionDeletedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
