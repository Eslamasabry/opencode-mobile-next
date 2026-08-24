// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_connect_oauth_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationConnectOauthRequest _$V2IntegrationConnectOauthRequestFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('V2IntegrationConnectOauthRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['methodID', 'inputs']);
      final val = V2IntegrationConnectOauthRequest(
        methodID: $checkedConvert('methodID', (v) => v as String),
        inputs: $checkedConvert(
          'inputs',
          (v) => Map<String, String>.from(v as Map),
        ),
        label: $checkedConvert('label', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$V2IntegrationConnectOauthRequestToJson(
  V2IntegrationConnectOauthRequest instance,
) => <String, dynamic>{
  'methodID': instance.methodID,
  'inputs': instance.inputs,
  'label': ?instance.label,
};
