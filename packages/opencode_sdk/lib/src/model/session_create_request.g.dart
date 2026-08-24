// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_create_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionCreateRequest _$SessionCreateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionCreateRequest', json, ($checkedConvert) {
  final val = SessionCreateRequest(
    parentID: $checkedConvert('parentID', (v) => v as String?),
    title: $checkedConvert('title', (v) => v as String?),
    agent: $checkedConvert('agent', (v) => v as String?),
    model: $checkedConvert(
      'model',
      (v) => v == null
          ? null
          : SessionCreateRequestModel.fromJson(v as Map<String, dynamic>),
    ),
    metadata: $checkedConvert('metadata', (v) => v),
    permission: $checkedConvert(
      'permission',
      (v) => (v as List<dynamic>?)
          ?.map((e) => PermissionRule.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    workspaceID: $checkedConvert('workspaceID', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$SessionCreateRequestToJson(
  SessionCreateRequest instance,
) => <String, dynamic>{
  'parentID': ?instance.parentID,
  'title': ?instance.title,
  'agent': ?instance.agent,
  'model': ?instance.model?.toJson(),
  'metadata': ?instance.metadata,
  'permission': ?instance.permission?.map((e) => e.toJson()).toList(),
  'workspaceID': ?instance.workspaceID,
};
