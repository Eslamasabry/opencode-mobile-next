// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_replied_schema2_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionRepliedSchema2Data _$QuestionRepliedSchema2DataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionRepliedSchema2Data', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'requestID', 'answers']);
  final val = QuestionRepliedSchema2Data(
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

Map<String, dynamic> _$QuestionRepliedSchema2DataToJson(
  QuestionRepliedSchema2Data instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'requestID': instance.requestID,
  'answers': instance.answers,
};
