// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_rejected_schema2_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionRejectedSchema2Data _$QuestionRejectedSchema2DataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionRejectedSchema2Data', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['sessionID', 'requestID']);
  final val = QuestionRejectedSchema2Data(
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    requestID: $checkedConvert('requestID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$QuestionRejectedSchema2DataToJson(
  QuestionRejectedSchema2Data instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'requestID': instance.requestID,
};
