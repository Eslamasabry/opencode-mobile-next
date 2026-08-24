// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Request _$QuestionV2RequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Request', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'sessionID', 'questions']);
      final val = QuestionV2Request(
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

Map<String, dynamic> _$QuestionV2RequestToJson(QuestionV2Request instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'questions': instance.questions.map((e) => e.toJson()).toList(),
      'tool': ?instance.tool?.toJson(),
    };
