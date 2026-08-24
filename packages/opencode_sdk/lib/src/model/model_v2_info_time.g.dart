// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_v2_info_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelV2InfoTime _$ModelV2InfoTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelV2InfoTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['released']);
      final val = ModelV2InfoTime(
        released: $checkedConvert('released', (v) => v as num),
      );
      return val;
    });

Map<String, dynamic> _$ModelV2InfoTimeToJson(ModelV2InfoTime instance) =>
    <String, dynamic>{'released': instance.released};
