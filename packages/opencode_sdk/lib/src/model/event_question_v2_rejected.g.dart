// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_question_v2_rejected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventQuestionV2Rejected _$EventQuestionV2RejectedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventQuestionV2Rejected', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventQuestionV2Rejected(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventQuestionV2RejectedTypeEnumEnumMap,
        v,
        unknownValue: EventQuestionV2RejectedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => QuestionRejectedSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventQuestionV2RejectedToJson(
  EventQuestionV2Rejected instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventQuestionV2RejectedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventQuestionV2RejectedTypeEnumEnumMap = {
  EventQuestionV2RejectedTypeEnum.questionPeriodV2PeriodRejected:
      'question.v2.rejected',
  EventQuestionV2RejectedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
