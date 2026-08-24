// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_part_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessagePartUpdated _$MessagePartUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MessagePartUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = MessagePartUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$MessagePartUpdatedTypeEnumEnumMap,
            v,
            unknownValue: MessagePartUpdatedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventMessagePartUpdatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MessagePartUpdatedToJson(MessagePartUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$MessagePartUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$MessagePartUpdatedTypeEnumEnumMap = {
  MessagePartUpdatedTypeEnum.messagePeriodPartPeriodUpdated:
      'message.part.updated',
  MessagePartUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
