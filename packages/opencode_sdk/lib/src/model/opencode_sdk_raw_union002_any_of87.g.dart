// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of87.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf87 _$OpencodeSdkRawUnion002AnyOf87FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf87', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf87(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf87TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf87TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf87ToJson(
  OpencodeSdkRawUnion002AnyOf87 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf87TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$OpencodeSdkRawUnion002AnyOf87TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf87TypeEnum.globalPeriodDisposed: 'global.disposed',
  OpencodeSdkRawUnion002AnyOf87TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
