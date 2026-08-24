// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionReplied _$QuestionRepliedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionReplied', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['sessionID', 'requestID', 'answers'],
      );
      final val = QuestionReplied(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        requestID: $checkedConvert('requestID', (v) => v as String),
        answers: $checkedConvert(
          'answers',
          (v) => (v as List<dynamic>)
              .map((e) => (e as List<dynamic>).map((e) => e as String).toList())
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionRepliedToJson(QuestionReplied instance) =>
    <String, dynamic>{
      'sessionID': instance.sessionID,
      'requestID': instance.requestID,
      'answers': instance.answers,
    };
