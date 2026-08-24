// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'reasoning_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReasoningPart _$ReasoningPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ReasoningPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'text',
          'time',
        ],
      );
      final val = ReasoningPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ReasoningPartTypeEnumEnumMap,
            v,
            unknownValue: ReasoningPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        text: $checkedConvert('text', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        time: $checkedConvert(
          'time',
          (v) => TextPartTime.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ReasoningPartToJson(ReasoningPart instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'messageID': instance.messageID,
      'type': _$ReasoningPartTypeEnumEnumMap[instance.type]!,
      'text': instance.text,
      'metadata': ?instance.metadata,
      'time': instance.time.toJson(),
    };

const _$ReasoningPartTypeEnumEnumMap = {
  ReasoningPartTypeEnum.reasoning: 'reasoning',
  ReasoningPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
