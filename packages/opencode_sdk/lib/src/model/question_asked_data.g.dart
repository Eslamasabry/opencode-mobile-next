// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_asked_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionAskedData _$QuestionAskedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionAskedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'sessionID', 'questions']);
      final val = QuestionAskedData(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        questions: $checkedConvert(
          'questions',
          (v) => (v as List<dynamic>)
              .map((e) => QuestionInfo.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
        tool: $checkedConvert(
          'tool',
          (v) => v == null
              ? null
              : QuestionTool.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionAskedDataToJson(QuestionAskedData instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'questions': instance.questions.map((e) => e.toJson()).toList(),
      'tool': ?instance.tool?.toJson(),
    };
