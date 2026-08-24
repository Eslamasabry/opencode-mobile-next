// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionInfo _$QuestionInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionInfo', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['question', 'header', 'options']);
      final val = QuestionInfo(
        question: $checkedConvert('question', (v) => v as String),
        header: $checkedConvert('header', (v) => v as String),
        options: $checkedConvert(
          'options',
          (v) => (v as List<dynamic>)
              .map((e) => QuestionOption.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
        multiple: $checkedConvert('multiple', (v) => v as bool?),
        custom: $checkedConvert('custom', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$QuestionInfoToJson(QuestionInfo instance) =>
    <String, dynamic>{
      'question': instance.question,
      'header': instance.header,
      'options': instance.options.map((e) => e.toJson()).toList(),
      'multiple': ?instance.multiple,
      'custom': ?instance.custom,
    };
