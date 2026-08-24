// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionAsked _$QuestionAskedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionAsked', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = QuestionAsked(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$QuestionAskedTypeEnumEnumMap,
            v,
            unknownValue: QuestionAskedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => QuestionAskedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionAskedToJson(QuestionAsked instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$QuestionAskedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$QuestionAskedTypeEnumEnumMap = {
  QuestionAskedTypeEnum.questionPeriodAsked: 'question.asked',
  QuestionAskedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
