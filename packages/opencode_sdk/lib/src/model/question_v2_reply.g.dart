// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_reply.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Reply _$QuestionV2ReplyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Reply', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['answers']);
      final val = QuestionV2Reply(
        answers: $checkedConvert(
          'answers',
          (v) => (v as List<dynamic>)
              .map((e) => (e as List<dynamic>).map((e) => e as String).toList())
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2ReplyToJson(QuestionV2Reply instance) =>
    <String, dynamic>{'answers': instance.answers};
