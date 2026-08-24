// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_rejected_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionRejectedSchema2 _$QuestionRejectedSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('QuestionRejectedSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = QuestionRejectedSchema2(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$QuestionRejectedSchema2TypeEnumEnumMap,
        v,
        unknownValue: QuestionRejectedSchema2TypeEnum.unknownDefaultOpenApi,
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
      (v) => QuestionRejectedSchema2Data.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$QuestionRejectedSchema2ToJson(
  QuestionRejectedSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$QuestionRejectedSchema2TypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$QuestionRejectedSchema2TypeEnumEnumMap = {
  QuestionRejectedSchema2TypeEnum.questionPeriodRejected: 'question.rejected',
  QuestionRejectedSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
