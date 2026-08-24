// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'question_v2_option.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

QuestionV2Option _$QuestionV2OptionFromJson(Map<String, dynamic> json) =>
    $checkedCreate('QuestionV2Option', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['label', 'description']);
      final val = QuestionV2Option(
        label: $checkedConvert('label', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$QuestionV2OptionToJson(QuestionV2Option instance) =>
    <String, dynamic>{
      'label': instance.label,
      'description': instance.description,
    };
