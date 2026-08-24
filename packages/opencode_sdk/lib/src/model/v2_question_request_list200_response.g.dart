// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_question_request_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2QuestionRequestList200Response _$V2QuestionRequestList200ResponseFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('V2QuestionRequestList200Response', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['location', 'data']);
      final val = V2QuestionRequestList200Response(
        location: $checkedConvert(
          'location',
          (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => (v as List<dynamic>)
              .map((e) => QuestionV2Request.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2QuestionRequestList200ResponseToJson(
  V2QuestionRequestList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
