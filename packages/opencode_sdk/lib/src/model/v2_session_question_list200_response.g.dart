// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_question_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionQuestionList200Response _$V2SessionQuestionList200ResponseFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('V2SessionQuestionList200Response', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['data']);
      final val = V2SessionQuestionList200Response(
        data: $checkedConvert(
          'data',
          (v) => (v as List<dynamic>)
              .map((e) => QuestionV2Request.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2SessionQuestionList200ResponseToJson(
  V2SessionQuestionList200Response instance,
) => <String, dynamic>{'data': instance.data.map((e) => e.toJson()).toList()};
