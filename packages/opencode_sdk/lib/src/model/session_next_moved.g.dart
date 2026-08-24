// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_moved.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextMoved _$SessionNextMovedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionNextMoved', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionNextMoved(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionNextMovedTypeEnumEnumMap,
            v,
            unknownValue: SessionNextMovedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventSessionNextMovedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionNextMovedToJson(SessionNextMoved instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionNextMovedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionNextMovedTypeEnumEnumMap = {
  SessionNextMovedTypeEnum.sessionPeriodNextPeriodMoved: 'session.next.moved',
  SessionNextMovedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
