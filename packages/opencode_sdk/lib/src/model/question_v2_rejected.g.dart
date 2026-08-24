// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_rejected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Rejected _$QuestionV2RejectedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Rejected', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = QuestionV2Rejected(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$QuestionV2RejectedTypeEnumEnumMap,
            v,
            unknownValue: QuestionV2RejectedTypeEnum.unknownDefaultOpenApi,
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
          (v) =>
              QuestionRejectedSchema2Data.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2RejectedToJson(QuestionV2Rejected instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$QuestionV2RejectedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$QuestionV2RejectedTypeEnumEnumMap = {
  QuestionV2RejectedTypeEnum.questionPeriodV2PeriodRejected:
      'question.v2.rejected',
  QuestionV2RejectedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
