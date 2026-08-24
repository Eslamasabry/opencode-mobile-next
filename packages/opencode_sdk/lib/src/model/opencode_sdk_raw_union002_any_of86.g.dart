// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of86.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf86 _$OpencodeSdkRawUnion002AnyOf86FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf86', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf86(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf86TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf86TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf86ToJson(
  OpencodeSdkRawUnion002AnyOf86 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf86TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$OpencodeSdkRawUnion002AnyOf86TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf86TypeEnum.serverPeriodConnected:
      'server.connected',
  OpencodeSdkRawUnion002AnyOf86TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
