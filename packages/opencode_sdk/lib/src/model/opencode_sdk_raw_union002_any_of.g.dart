// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf _$OpencodeSdkRawUnion002AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOfTypeEnumEnumMap,
        v,
        unknownValue: OpencodeSdkRawUnion002AnyOfTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOfToJson(
  OpencodeSdkRawUnion002AnyOf instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOfTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$OpencodeSdkRawUnion002AnyOfTypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOfTypeEnum.modelsDevPeriodRefreshed:
      'models-dev.refreshed',
  OpencodeSdkRawUnion002AnyOfTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
