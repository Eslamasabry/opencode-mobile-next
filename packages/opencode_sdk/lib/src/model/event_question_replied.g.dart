// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_question_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventQuestionReplied _$EventQuestionRepliedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventQuestionReplied', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventQuestionReplied(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventQuestionRepliedTypeEnumEnumMap,
        v,
        unknownValue: EventQuestionRepliedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => QuestionRepliedSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventQuestionRepliedToJson(
  EventQuestionReplied instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventQuestionRepliedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventQuestionRepliedTypeEnumEnumMap = {
  EventQuestionRepliedTypeEnum.questionPeriodReplied: 'question.replied',
  EventQuestionRepliedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
