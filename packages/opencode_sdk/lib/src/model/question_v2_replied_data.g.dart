// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_replied_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2RepliedData _$QuestionV2RepliedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionV2RepliedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'requestID', 'answers']);
  final val = QuestionV2RepliedData(
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

Map<String, dynamic> _$QuestionV2RepliedDataToJson(
  QuestionV2RepliedData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'requestID': instance.requestID,
  'answers': instance.answers,
};
