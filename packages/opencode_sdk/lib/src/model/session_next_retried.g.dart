// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_retried.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextRetried _$SessionNextRetriedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionNextRetried', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionNextRetried(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionNextRetriedTypeEnumEnumMap,
            v,
            unknownValue: SessionNextRetriedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventSessionNextRetriedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionNextRetriedToJson(SessionNextRetried instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionNextRetriedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionNextRetriedTypeEnumEnumMap = {
  SessionNextRetriedTypeEnum.sessionPeriodNextPeriodRetried:
      'session.next.retried',
  SessionNextRetriedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
