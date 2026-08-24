// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'prompt_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PromptSource _$PromptSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PromptSource', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['start', 'end', 'text']);
      final val = PromptSource(
        start: $checkedConvert('start', (v) => v as num),
        end: $checkedConvert('end', (v) => v as num),
        text: $checkedConvert('text', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$PromptSourceToJson(PromptSource instance) =>
    <String, dynamic>{
      'start': instance.start,
      'end': instance.end,
      'text': instance.text,
    };
