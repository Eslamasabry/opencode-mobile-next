// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of85.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf85 _$OpencodeSdkRawUnion002AnyOf85FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf85', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf85(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf85TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf85TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf85ToJson(
  OpencodeSdkRawUnion002AnyOf85 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf85TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$OpencodeSdkRawUnion002AnyOf85TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf85TypeEnum.worktreePeriodFailed: 'worktree.failed',
  OpencodeSdkRawUnion002AnyOf85TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
