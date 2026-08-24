// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_v2_info_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelV2InfoRequest _$ModelV2InfoRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelV2InfoRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['headers', 'body']);
      final val = ModelV2InfoRequest(
        headers: $checkedConvert(
          'headers',
          (v) => Map<String, String>.from(v as Map),
        ),
        body: $checkedConvert('body', (v) => v as Object),
        variant: $checkedConvert('variant', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$ModelV2InfoRequestToJson(ModelV2InfoRequest instance) =>
    <String, dynamic>{
      'headers': instance.headers,
      'body': instance.body,
      'variant': ?instance.variant,
    };
