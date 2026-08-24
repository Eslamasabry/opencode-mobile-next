// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'effect_http_api_error_internal_server_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EffectHttpApiErrorInternalServerError
_$EffectHttpApiErrorInternalServerErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EffectHttpApiErrorInternalServerError', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['_tag']);
      final val = EffectHttpApiErrorInternalServerError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$EffectHttpApiErrorInternalServerErrorTagEnumEnumMap,
            v,
            unknownValue: EffectHttpApiErrorInternalServerErrorTagEnum
                .unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$EffectHttpApiErrorInternalServerErrorToJson(
  EffectHttpApiErrorInternalServerError instance,
) => <String, dynamic>{
  '_tag': _$EffectHttpApiErrorInternalServerErrorTagEnumEnumMap[instance.tag]!,
};

const _$EffectHttpApiErrorInternalServerErrorTagEnumEnumMap = {
  EffectHttpApiErrorInternalServerErrorTagEnum.internalServerError:
      'InternalServerError',
  EffectHttpApiErrorInternalServerErrorTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
