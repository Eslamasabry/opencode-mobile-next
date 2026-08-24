// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_log_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AppLogRequest _$AppLogRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AppLogRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['service', 'level', 'message']);
      final val = AppLogRequest(
        service: $checkedConvert('service', (v) => v as String),
        level: $checkedConvert(
          'level',
          (v) => $enumDecode(
            _$AppLogRequestLevelEnumEnumMap,
            v,
            unknownValue: AppLogRequestLevelEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
        extra: $checkedConvert('extra', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$AppLogRequestToJson(AppLogRequest instance) =>
    <String, dynamic>{
      'service': instance.service,
      'level': _$AppLogRequestLevelEnumEnumMap[instance.level]!,
      'message': instance.message,
      'extra': ?instance.extra,
    };

const _$AppLogRequestLevelEnumEnumMap = {
  AppLogRequestLevelEnum.debug: 'debug',
  AppLogRequestLevelEnum.info: 'info',
  AppLogRequestLevelEnum.error: 'error',
  AppLogRequestLevelEnum.warn: 'warn',
  AppLogRequestLevelEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
