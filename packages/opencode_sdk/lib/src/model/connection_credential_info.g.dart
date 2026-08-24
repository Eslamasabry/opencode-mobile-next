// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'connection_credential_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConnectionCredentialInfo _$ConnectionCredentialInfoFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ConnectionCredentialInfo', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'label']);
  final val = ConnectionCredentialInfo(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$ConnectionCredentialInfoTypeEnumEnumMap,
        v,
        unknownValue: ConnectionCredentialInfoTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    label: $checkedConvert('label', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ConnectionCredentialInfoToJson(
  ConnectionCredentialInfo instance,
) => <String, dynamic>{
  'type': _$ConnectionCredentialInfoTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'label': instance.label,
};

const _$ConnectionCredentialInfoTypeEnumEnumMap = {
  ConnectionCredentialInfoTypeEnum.credential: 'credential',
  ConnectionCredentialInfoTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
