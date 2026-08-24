// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_reply_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionReplyRequest _$QuestionReplyRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionReplyRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['answers']);
  final val = QuestionReplyRequest(
    answers: $checkedConvert(
      'answers',
      (v) => (v as List<dynamic>)
          .map((e) => (e as List<dynamic>).map((e) => e as String).toList())
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$QuestionReplyRequestToJson(
  QuestionReplyRequest instance,
) => <String, dynamic>{'answers': instance.answers};
