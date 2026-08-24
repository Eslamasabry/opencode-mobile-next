// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_question_rejected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventQuestionRejected _$EventQuestionRejectedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventQuestionRejected', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventQuestionRejected(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventQuestionRejectedTypeEnumEnumMap,
        v,
        unknownValue: EventQuestionRejectedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => QuestionRejectedSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventQuestionRejectedToJson(
  EventQuestionRejected instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventQuestionRejectedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventQuestionRejectedTypeEnumEnumMap = {
  EventQuestionRejectedTypeEnum.questionPeriodRejected: 'question.rejected',
  EventQuestionRejectedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
