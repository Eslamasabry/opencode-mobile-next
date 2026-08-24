// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionNotFoundError _$QuestionNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionNotFoundError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'requestID', 'message']);
  final val = QuestionNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$QuestionNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: QuestionNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    requestID: $checkedConvert('requestID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$QuestionNotFoundErrorToJson(
  QuestionNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$QuestionNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'requestID': instance.requestID,
  'message': instance.message,
};

const _$QuestionNotFoundErrorTagEnumEnumMap = {
  QuestionNotFoundErrorTagEnum.questionNotFoundError: 'QuestionNotFoundError',
  QuestionNotFoundErrorTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
