// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of38.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf38 _$OpencodeSdkRawUnion002AnyOf38FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf38', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = OpencodeSdkRawUnion002AnyOf38(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf38TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion002AnyOf38TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf38ToJson(
  OpencodeSdkRawUnion002AnyOf38 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$OpencodeSdkRawUnion002AnyOf38TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$OpencodeSdkRawUnion002AnyOf38TypeEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf38TypeEnum
          .sessionPeriodNextPeriodCompactionPeriodDelta:
      'session.next.compaction.delta',
  OpencodeSdkRawUnion002AnyOf38TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
