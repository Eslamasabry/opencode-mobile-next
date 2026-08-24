// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'service_unavailable_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ServiceUnavailableError _$ServiceUnavailableErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ServiceUnavailableError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'message']);
  final val = ServiceUnavailableError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$ServiceUnavailableErrorTagEnumEnumMap,
        v,
        unknownValue: ServiceUnavailableErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    message: $checkedConvert('message', (v) => v as String),
    service: $checkedConvert('service', (v) => v as String?),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$ServiceUnavailableErrorToJson(
  ServiceUnavailableError instance,
) => <String, dynamic>{
  '_tag': _$ServiceUnavailableErrorTagEnumEnumMap[instance.tag]!,
  'message': instance.message,
  'service': ?instance.service,
};

const _$ServiceUnavailableErrorTagEnumEnumMap = {
  ServiceUnavailableErrorTagEnum.serviceUnavailableError:
      'ServiceUnavailableError',
  ServiceUnavailableErrorTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
