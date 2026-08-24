// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of67.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf67 _$OpencodeSdkRawUnion002AnyOf67FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf67', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf67(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf67TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf67TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => OpencodeSdkRawUnion002AnyOf67Properties.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf67ToJson(
  OpencodeSdkRawUnion002AnyOf67 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf67TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$OpencodeSdkRawUnion002AnyOf67TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf67TypeEnum.tuiPeriodCommandPeriodExecute:
      'tui.command.execute',
  OpencodeSdkRawUnion002AnyOf67TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
