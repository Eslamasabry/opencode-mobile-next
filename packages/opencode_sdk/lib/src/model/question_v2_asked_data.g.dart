// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_asked_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2AskedData _$QuestionV2AskedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2AskedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'sessionID', 'questions']);
      final val = QuestionV2AskedData(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        questions: $checkedConvert(
          'questions',
          (v) => (v as List<dynamic>)
              .map((e) => QuestionV2Info.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
        tool: $checkedConvert(
          'tool',
          (v) => v == null
              ? null
              : QuestionV2Tool.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2AskedDataToJson(
  QuestionV2AskedData instance,
) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'questions': instance.questions.map((e) => e.toJson()).toList(),
  'tool': ?instance.tool?.toJson(),
};
