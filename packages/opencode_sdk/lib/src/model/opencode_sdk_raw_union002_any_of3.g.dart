// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of3.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf3 _$OpencodeSdkRawUnion002AnyOf3FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf3', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf3(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf3TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf3TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf3ToJson(
  OpencodeSdkRawUnion002AnyOf3 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf3TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$OpencodeSdkRawUnion002AnyOf3TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf3TypeEnum.catalogPeriodUpdated: 'catalog.updated',
  OpencodeSdkRawUnion002AnyOf3TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
