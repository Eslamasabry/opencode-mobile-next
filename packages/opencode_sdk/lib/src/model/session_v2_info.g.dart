// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionV2Info _$SessionV2InfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionV2Info', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'projectID',
          'cost',
          'tokens',
          'time',
          'title',
          'location',
        ],
      );
      final val = SessionV2Info(
        id: $checkedConvert('id', (v) => v as String),
        parentID: $checkedConvert('parentID', (v) => v as String?),
        projectID: $checkedConvert('projectID', (v) => v as String),
        agent: $checkedConvert('agent', (v) => v as String?),
        model: $checkedConvert(
          'model',
          (v) =>
              v == null ? null : ModelRef.fromJson(v as Map<String, dynamic>),
        ),
        cost: $checkedConvert('cost', (v) => v as num),
        tokens: $checkedConvert(
          'tokens',
          (v) => SessionTokens.fromJson(v as Map<String, dynamic>),
        ),
        time: $checkedConvert(
          'time',
          (v) => SessionV2InfoTime.fromJson(v as Map<String, dynamic>),
        ),
        title: $checkedConvert('title', (v) => v as String),
        location: $checkedConvert(
          'location',
          (v) => LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        subpath: $checkedConvert('subpath', (v) => v as String?),
        revert: $checkedConvert(
          'revert',
          (v) => v == null
              ? null
              : RevertState.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionV2InfoToJson(SessionV2Info instance) =>
    <String, dynamic>{
      'id': instance.id,
      'parentID': ?instance.parentID,
      'projectID': instance.projectID,
      'agent': ?instance.agent,
      'model': ?instance.model?.toJson(),
      'cost': instance.cost,
      'tokens': instance.tokens.toJson(),
      'time': instance.time.toJson(),
      'title': instance.title,
      'location': instance.location.toJson(),
      'subpath': ?instance.subpath,
      'revert': ?instance.revert?.toJson(),
    };
