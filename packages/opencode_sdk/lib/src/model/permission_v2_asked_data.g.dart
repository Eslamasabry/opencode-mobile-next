// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_v2_asked_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionV2AskedData _$PermissionV2AskedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionV2AskedData', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['id', 'sessionID', 'action', 'resources'],
  );
  final val = PermissionV2AskedData(
    id: $checkedConvert('id', (v) => v as String),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    action: $checkedConvert('action', (v) => v as String),
    resources: $checkedConvert(
      'resources',
      (v) => (v as List<dynamic>).map((e) => e as String).toList(),
    ),
    save: $checkedConvert(
      'save',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
    metadata: $checkedConvert('metadata', (v) => v),
    source_: $checkedConvert(
      'source',
      (v) => v == null
          ? null
          : PermissionV2Source.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$PermissionV2AskedDataToJson(
  PermissionV2AskedData instance,
) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'action': instance.action,
  'resources': instance.resources,
  'save': ?instance.save,
  'metadata': ?instance.metadata,
  'source': ?instance.source_?.toJson(),
};
