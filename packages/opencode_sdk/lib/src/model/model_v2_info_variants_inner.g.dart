// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_v2_info_variants_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelV2InfoVariantsInner _$ModelV2InfoVariantsInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ModelV2InfoVariantsInner', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'headers', 'body']);
  final val = ModelV2InfoVariantsInner(
    id: $checkedConvert('id', (v) => v as String),
    headers: $checkedConvert(
      'headers',
      (v) => Map<String, String>.from(v as Map),
    ),
    body: $checkedConvert('body', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$ModelV2InfoVariantsInnerToJson(
  ModelV2InfoVariantsInner instance,
) => <String, dynamic>{
  'id': instance.id,
  'headers': instance.headers,
  'body': instance.body,
};
