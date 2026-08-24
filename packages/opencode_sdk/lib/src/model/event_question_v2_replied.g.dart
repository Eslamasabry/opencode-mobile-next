// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_question_v2_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventQuestionV2Replied _$EventQuestionV2RepliedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventQuestionV2Replied', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventQuestionV2Replied(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventQuestionV2RepliedTypeEnumEnumMap,
        v,
        unknownValue: EventQuestionV2RepliedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => QuestionV2RepliedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventQuestionV2RepliedToJson(
  EventQuestionV2Replied instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventQuestionV2RepliedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventQuestionV2RepliedTypeEnumEnumMap = {
  EventQuestionV2RepliedTypeEnum.questionPeriodV2PeriodReplied:
      'question.v2.replied',
  EventQuestionV2RepliedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
