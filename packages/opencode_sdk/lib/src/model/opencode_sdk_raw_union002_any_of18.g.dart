// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of18.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf18 _$OpencodeSdkRawUnion002AnyOf18FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf18', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf18(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf18TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf18TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextShellStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf18ToJson(
  OpencodeSdkRawUnion002AnyOf18 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf18TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$OpencodeSdkRawUnion002AnyOf18TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf18TypeEnum
          .sessionPeriodNextPeriodShellPeriodStarted:
      'session.next.shell.started',
  OpencodeSdkRawUnion002AnyOf18TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
