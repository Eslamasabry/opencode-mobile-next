// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_permission_create_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPermissionCreateRequest _$V2SessionPermissionCreateRequestFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('V2SessionPermissionCreateRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['action', 'resources']);
      final val = V2SessionPermissionCreateRequest(
        id: $checkedConvert('id', (v) => v as String?),
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
        agent: $checkedConvert('agent', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$V2SessionPermissionCreateRequestToJson(
  V2SessionPermissionCreateRequest instance,
) => <String, dynamic>{
  'id': ?instance.id,
  'action': instance.action,
  'resources': instance.resources,
  'save': ?instance.save,
  'metadata': ?instance.metadata,
  'source': ?instance.source_?.toJson(),
  'agent': ?instance.agent,
};
