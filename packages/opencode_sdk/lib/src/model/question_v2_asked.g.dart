// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_asked.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Asked _$QuestionV2AskedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Asked', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = QuestionV2Asked(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$QuestionV2AskedTypeEnumEnumMap,
            v,
            unknownValue: QuestionV2AskedTypeEnum.unknownDefaultOpenApi,
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
          (v) => QuestionV2AskedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2AskedToJson(QuestionV2Asked instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$QuestionV2AskedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$QuestionV2AskedTypeEnumEnumMap = {
  QuestionV2AskedTypeEnum.questionPeriodV2PeriodAsked: 'question.v2.asked',
  QuestionV2AskedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
