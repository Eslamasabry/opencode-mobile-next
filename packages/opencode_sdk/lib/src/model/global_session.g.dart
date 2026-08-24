// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'global_session.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

GlobalSession _$GlobalSessionFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('GlobalSession', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'id',
      'slug',
      'projectID',
      'directory',
      'title',
      'version',
      'time',
      'project',
    ],
  );
  final val = GlobalSession(
    id: $checkedConvert('id', (v) => v as String),
    slug: $checkedConvert('slug', (v) => v as String),
    projectID: $checkedConvert('projectID', (v) => v as String),
    workspaceID: $checkedConvert('workspaceID', (v) => v as String?),
    directory: $checkedConvert('directory', (v) => v as String),
    path: $checkedConvert('path', (v) => v as String?),
    parentID: $checkedConvert('parentID', (v) => v as String?),
    summary: $checkedConvert(
      'summary',
      (v) =>
          v == null ? null : SessionSummary.fromJson(v as Map<String, dynamic>),
    ),
    cost: $checkedConvert('cost', (v) => v as num?),
    tokens: $checkedConvert(
      'tokens',
      (v) =>
          v == null ? null : SessionTokens.fromJson(v as Map<String, dynamic>),
    ),
    share: $checkedConvert(
      'share',
      (v) =>
          v == null ? null : SessionShare.fromJson(v as Map<String, dynamic>),
    ),
    title: $checkedConvert('title', (v) => v as String),
    agent: $checkedConvert('agent', (v) => v as String?),
    model: $checkedConvert(
      'model',
      (v) => v == null
          ? null
          : SessionCreateRequestModel.fromJson(v as Map<String, dynamic>),
    ),
    version: $checkedConvert('version', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) => SessionTime.fromJson(v as Map<String, dynamic>),
    ),
    permission: $checkedConvert(
      'permission',
      (v) => (v as List<dynamic>?)
          ?.map((e) => PermissionRule.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    revert: $checkedConvert(
      'revert',
      (v) =>
          v == null ? null : SessionRevert.fromJson(v as Map<String, dynamic>),
    ),
    project: $checkedConvert(
      'project',
      (v) =>
          v == null ? null : ProjectSummary.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$GlobalSessionToJson(GlobalSession instance) =>
    <String, dynamic>{
      'id': instance.id,
      'slug': instance.slug,
      'projectID': instance.projectID,
      'workspaceID': ?instance.workspaceID,
      'directory': instance.directory,
      'path': ?instance.path,
      'parentID': ?instance.parentID,
      'summary': ?instance.summary?.toJson(),
      'cost': ?instance.cost,
      'tokens': ?instance.tokens?.toJson(),
      'share': ?instance.share?.toJson(),
      'title': instance.title,
      'agent': ?instance.agent,
      'model': ?instance.model?.toJson(),
      'version': instance.version,
      'metadata': ?instance.metadata,
      'time': instance.time.toJson(),
      'permission': ?instance.permission?.map((e) => e.toJson()).toList(),
      'revert': ?instance.revert?.toJson(),
      'project': instance.project?.toJson(),
    };
