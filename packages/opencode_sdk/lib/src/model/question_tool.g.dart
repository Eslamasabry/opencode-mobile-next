// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_tool.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionTool _$QuestionToolFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionTool', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['messageID', 'callID']);
      final val = QuestionTool(
        messageID: $checkedConvert('messageID', (v) => v as String),
        callID: $checkedConvert('callID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$QuestionToolToJson(QuestionTool instance) =>
    <String, dynamic>{
      'messageID': instance.messageID,
      'callID': instance.callID,
    };
