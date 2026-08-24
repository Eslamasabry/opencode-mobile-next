// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'global_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

GlobalEvent _$GlobalEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('GlobalEvent', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory', 'payload']);
      final val = GlobalEvent(
        directory: $checkedConvert('directory', (v) => v as String),
        project: $checkedConvert('project', (v) => v as String?),
        workspace: $checkedConvert('workspace', (v) => v as String?),
        payload: $checkedConvert(
          'payload',
          (v) => OpencodeSdkRawUnion002.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$GlobalEventToJson(GlobalEvent instance) =>
    <String, dynamic>{
      'directory': instance.directory,
      'project': ?instance.project,
      'workspace': ?instance.workspace,
      'payload': instance.payload.toJson(),
    };
