// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_connect_key_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationConnectKeyRequest _$V2IntegrationConnectKeyRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2IntegrationConnectKeyRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['key']);
  final val = V2IntegrationConnectKeyRequest(
    key: $checkedConvert('key', (v) => v as String),
    label: $checkedConvert('label', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$V2IntegrationConnectKeyRequestToJson(
  V2IntegrationConnectKeyRequest instance,
) => <String, dynamic>{'key': instance.key, 'label': ?instance.label};
