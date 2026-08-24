// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_v2_info_limit.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelV2InfoLimit _$ModelV2InfoLimitFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelV2InfoLimit', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['context', 'output']);
      final val = ModelV2InfoLimit(
        context: $checkedConvert('context', (v) => (v as num).toInt()),
        input: $checkedConvert('input', (v) => (v as num?)?.toInt()),
        output: $checkedConvert('output', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$ModelV2InfoLimitToJson(ModelV2InfoLimit instance) =>
    <String, dynamic>{
      'context': instance.context,
      'input': ?instance.input,
      'output': instance.output,
    };
