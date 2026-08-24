// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderV2Info _$ProviderV2InfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderV2Info', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'name', 'api', 'request']);
      final val = ProviderV2Info(
        id: $checkedConvert('id', (v) => v as String),
        integrationID: $checkedConvert('integrationID', (v) => v as String?),
        name: $checkedConvert('name', (v) => v as String),
        disabled: $checkedConvert('disabled', (v) => v as bool?),
        api: $checkedConvert('api', (v) => ProviderApiModel.fromJson(v)),
        request: $checkedConvert(
          'request',
          (v) => ProviderRequest.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProviderV2InfoToJson(ProviderV2Info instance) =>
    <String, dynamic>{
      'id': instance.id,
      'integrationID': ?instance.integrationID,
      'name': instance.name,
      'disabled': ?instance.disabled,
      'api': instance.api.toJson(),
      'request': instance.request.toJson(),
    };
