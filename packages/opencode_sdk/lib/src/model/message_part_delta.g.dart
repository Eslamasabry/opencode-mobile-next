// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'message_part_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MessagePartDelta _$MessagePartDeltaFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MessagePartDelta', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = MessagePartDelta(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$MessagePartDeltaTypeEnumEnumMap,
            v,
            unknownValue: MessagePartDeltaTypeEnum.unknownDefaultOpenApi,
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
          (v) => MessagePartDeltaData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MessagePartDeltaToJson(MessagePartDelta instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$MessagePartDeltaTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$MessagePartDeltaTypeEnumEnumMap = {
  MessagePartDeltaTypeEnum.messagePeriodPartPeriodDelta: 'message.part.delta',
  MessagePartDeltaTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
