// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_removed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessageRemoved _$MessageRemovedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MessageRemoved', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = MessageRemoved(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$MessageRemovedTypeEnumEnumMap,
            v,
            unknownValue: MessageRemovedTypeEnum.unknownDefaultOpenApi,
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
          (v) => SyncEventMessageRemovedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MessageRemovedToJson(MessageRemoved instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$MessageRemovedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$MessageRemovedTypeEnumEnumMap = {
  MessageRemovedTypeEnum.messagePeriodRemoved: 'message.removed',
  MessageRemovedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
