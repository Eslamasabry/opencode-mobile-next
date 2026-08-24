// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessageUpdated _$MessageUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MessageUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = MessageUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$MessageUpdatedTypeEnumEnumMap,
            v,
            unknownValue: MessageUpdatedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventMessageUpdatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MessageUpdatedToJson(MessageUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$MessageUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$MessageUpdatedTypeEnumEnumMap = {
  MessageUpdatedTypeEnum.messagePeriodUpdated: 'message.updated',
  MessageUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
