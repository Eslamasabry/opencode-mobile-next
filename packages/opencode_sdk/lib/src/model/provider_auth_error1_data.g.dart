// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_auth_error1_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAuthError1Data _$ProviderAuthError1DataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderAuthError1Data', json, ($checkedConvert) {
  final val = ProviderAuthError1Data(
    providerID: $checkedConvert('providerID', (v) => v as String?),
    field: $checkedConvert('field', (v) => v as String?),
    message: $checkedConvert('message', (v) => v as String?),
    kind: $checkedConvert('kind', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$ProviderAuthError1DataToJson(
  ProviderAuthError1Data instance,
) => <String, dynamic>{
  'providerID': ?instance.providerID,
  'field': ?instance.field,
  'message': ?instance.message,
  'kind': ?instance.kind,
};
