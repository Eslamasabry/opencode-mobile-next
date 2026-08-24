// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'effect_http_api_error_forbidden.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EffectHttpApiErrorForbidden _$EffectHttpApiErrorForbiddenFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EffectHttpApiErrorForbidden', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag']);
  final val = EffectHttpApiErrorForbidden(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$EffectHttpApiErrorForbiddenTagEnumEnumMap,
        v,
        unknownValue: EffectHttpApiErrorForbiddenTagEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$EffectHttpApiErrorForbiddenToJson(
  EffectHttpApiErrorForbidden instance,
) => <String, dynamic>{
  '_tag': _$EffectHttpApiErrorForbiddenTagEnumEnumMap[instance.tag]!,
};

const _$EffectHttpApiErrorForbiddenTagEnumEnumMap = {
  EffectHttpApiErrorForbiddenTagEnum.forbidden: 'Forbidden',
  EffectHttpApiErrorForbiddenTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
