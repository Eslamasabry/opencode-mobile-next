// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of49.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf49 _$OpencodeSdkRawUnion002AnyOf49FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf49', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf49(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf49TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf49TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf49ToJson(
  OpencodeSdkRawUnion002AnyOf49 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf49TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$OpencodeSdkRawUnion002AnyOf49TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf49TypeEnum.referencePeriodUpdated:
      'reference.updated',
  OpencodeSdkRawUnion002AnyOf49TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
