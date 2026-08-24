// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_replied_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionRepliedSchema2 _$QuestionRepliedSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionRepliedSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = QuestionRepliedSchema2(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$QuestionRepliedSchema2TypeEnumEnumMap,
        v,
        unknownValue: QuestionRepliedSchema2TypeEnum.unknownDefaultOpenApi,
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
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => QuestionRepliedSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$QuestionRepliedSchema2ToJson(
  QuestionRepliedSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$QuestionRepliedSchema2TypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$QuestionRepliedSchema2TypeEnumEnumMap = {
  QuestionRepliedSchema2TypeEnum.questionPeriodReplied: 'question.replied',
  QuestionRepliedSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
