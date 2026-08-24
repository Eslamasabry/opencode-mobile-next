// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_prompted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextPrompted _$SessionNextPromptedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionNextPrompted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionNextPrompted(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionNextPromptedTypeEnumEnumMap,
            v,
            unknownValue: SessionNextPromptedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventSessionNextPromptedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionNextPromptedToJson(
  SessionNextPrompted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextPromptedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextPromptedTypeEnumEnumMap = {
  SessionNextPromptedTypeEnum.sessionPeriodNextPeriodPrompted:
      'session.next.prompted',
  SessionNextPromptedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
