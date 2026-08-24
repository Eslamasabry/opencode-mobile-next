// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_auth_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAuthErrorData _$ProviderAuthErrorDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderAuthErrorData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['providerID', 'message']);
  final val = ProviderAuthErrorData(
    providerID: $checkedConvert('providerID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ProviderAuthErrorDataToJson(
  ProviderAuthErrorData instance,
) => <String, dynamic>{
  'providerID': instance.providerID,
  'message': instance.message,
};
