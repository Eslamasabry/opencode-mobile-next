// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'effect_http_api_error_bad_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EffectHttpApiErrorBadRequest _$EffectHttpApiErrorBadRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EffectHttpApiErrorBadRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag']);
  final val = EffectHttpApiErrorBadRequest(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$EffectHttpApiErrorBadRequestTagEnumEnumMap,
        v,
        unknownValue: EffectHttpApiErrorBadRequestTagEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$EffectHttpApiErrorBadRequestToJson(
  EffectHttpApiErrorBadRequest instance,
) => <String, dynamic>{
  '_tag': _$EffectHttpApiErrorBadRequestTagEnumEnumMap[instance.tag]!,
};

const _$EffectHttpApiErrorBadRequestTagEnumEnumMap = {
  EffectHttpApiErrorBadRequestTagEnum.badRequest: 'BadRequest',
  EffectHttpApiErrorBadRequestTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
