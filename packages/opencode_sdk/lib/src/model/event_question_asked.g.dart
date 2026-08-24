// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_question_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventQuestionAsked _$EventQuestionAskedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventQuestionAsked', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventQuestionAsked(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventQuestionAskedTypeEnumEnumMap,
            v,
            unknownValue: EventQuestionAskedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => QuestionAskedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventQuestionAskedToJson(EventQuestionAsked instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventQuestionAskedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventQuestionAskedTypeEnumEnumMap = {
  EventQuestionAskedTypeEnum.questionPeriodAsked: 'question.asked',
  EventQuestionAskedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
