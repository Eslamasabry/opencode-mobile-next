// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Info _$QuestionV2InfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Info', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['question', 'header', 'options']);
      final val = QuestionV2Info(
        question: $checkedConvert('question', (v) => v as String),
        header: $checkedConvert('header', (v) => v as String),
        options: $checkedConvert(
          'options',
          (v) => (v as List<dynamic>)
              .map((e) => QuestionV2Option.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
        multiple: $checkedConvert('multiple', (v) => v as bool?),
        custom: $checkedConvert('custom', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2InfoToJson(QuestionV2Info instance) =>
    <String, dynamic>{
      'question': instance.question,
      'header': instance.header,
      'options': instance.options.map((e) => e.toJson()).toList(),
      'multiple': ?instance.multiple,
      'custom': ?instance.custom,
    };
