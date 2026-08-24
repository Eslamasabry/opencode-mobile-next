// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_credential_update_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2CredentialUpdateRequest _$V2CredentialUpdateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2CredentialUpdateRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['label']);
  final val = V2CredentialUpdateRequest(
    label: $checkedConvert('label', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$V2CredentialUpdateRequestToJson(
  V2CredentialUpdateRequest instance,
) => <String, dynamic>{'label': instance.label};
