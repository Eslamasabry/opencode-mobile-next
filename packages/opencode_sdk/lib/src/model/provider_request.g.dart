// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderRequest _$ProviderRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['headers', 'body']);
      final val = ProviderRequest(
        headers: $checkedConvert(
          'headers',
          (v) => Map<String, String>.from(v as Map),
        ),
        body: $checkedConvert('body', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$ProviderRequestToJson(ProviderRequest instance) =>
    <String, dynamic>{'headers': instance.headers, 'body': instance.body};
