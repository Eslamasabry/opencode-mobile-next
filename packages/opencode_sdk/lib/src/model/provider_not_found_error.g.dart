// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderNotFoundError _$ProviderNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderNotFoundError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'providerID', 'message']);
  final val = ProviderNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$ProviderNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: ProviderNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    providerID: $checkedConvert('providerID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$ProviderNotFoundErrorToJson(
  ProviderNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$ProviderNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'providerID': instance.providerID,
  'message': instance.message,
};

const _$ProviderNotFoundErrorTagEnumEnumMap = {
  ProviderNotFoundErrorTagEnum.providerNotFoundError: 'ProviderNotFoundError',
  ProviderNotFoundErrorTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
