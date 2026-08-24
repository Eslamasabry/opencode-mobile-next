// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_replied.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Replied _$QuestionV2RepliedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Replied', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = QuestionV2Replied(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$QuestionV2RepliedTypeEnumEnumMap,
            v,
            unknownValue: QuestionV2RepliedTypeEnum.unknownDefaultOpenApi,
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
          (v) => QuestionV2RepliedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2RepliedToJson(QuestionV2Replied instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$QuestionV2RepliedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$QuestionV2RepliedTypeEnumEnumMap = {
  QuestionV2RepliedTypeEnum.questionPeriodV2PeriodReplied:
      'question.v2.replied',
  QuestionV2RepliedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
