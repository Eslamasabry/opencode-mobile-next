// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_rejected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionRejected _$QuestionRejectedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionRejected', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'requestID']);
      final val = QuestionRejected(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        requestID: $checkedConvert('requestID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$QuestionRejectedToJson(QuestionRejected instance) =>
    <String, dynamic>{
      'sessionID': instance.sessionID,
      'requestID': instance.requestID,
    };
