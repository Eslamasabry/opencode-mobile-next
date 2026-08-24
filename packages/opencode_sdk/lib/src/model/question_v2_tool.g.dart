// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_tool.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Tool _$QuestionV2ToolFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Tool', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['messageID', 'callID']);
      final val = QuestionV2Tool(
        messageID: $checkedConvert('messageID', (v) => v as String),
        callID: $checkedConvert('callID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2ToolToJson(QuestionV2Tool instance) =>
    <String, dynamic>{
      'messageID': instance.messageID,
      'callID': instance.callID,
    };
