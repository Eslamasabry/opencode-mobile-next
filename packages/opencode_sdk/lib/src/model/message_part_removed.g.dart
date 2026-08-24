// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_part_removed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessagePartRemoved _$MessagePartRemovedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MessagePartRemoved', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = MessagePartRemoved(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$MessagePartRemovedTypeEnumEnumMap,
            v,
            unknownValue: MessagePartRemovedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventMessagePartRemovedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MessagePartRemovedToJson(MessagePartRemoved instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$MessagePartRemovedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$MessagePartRemovedTypeEnumEnumMap = {
  MessagePartRemovedTypeEnum.messagePeriodPartPeriodRemoved:
      'message.part.removed',
  MessagePartRemovedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
