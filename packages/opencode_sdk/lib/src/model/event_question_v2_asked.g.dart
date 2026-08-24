// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_question_v2_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventQuestionV2Asked _$EventQuestionV2AskedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventQuestionV2Asked', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventQuestionV2Asked(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventQuestionV2AskedTypeEnumEnumMap,
        v,
        unknownValue: EventQuestionV2AskedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => QuestionV2AskedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventQuestionV2AskedToJson(
  EventQuestionV2Asked instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventQuestionV2AskedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventQuestionV2AskedTypeEnumEnumMap = {
  EventQuestionV2AskedTypeEnum.questionPeriodV2PeriodAsked: 'question.v2.asked',
  EventQuestionV2AskedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
