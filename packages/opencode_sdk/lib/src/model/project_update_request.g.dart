// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_update_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectUpdateRequest _$ProjectUpdateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProjectUpdateRequest', json, ($checkedConvert) {
  final val = ProjectUpdateRequest(
    name: $checkedConvert('name', (v) => v as String?),
    icon: $checkedConvert(
      'icon',
      (v) => v == null ? null : ProjectIcon.fromJson(v as Map<String, dynamic>),
    ),
    commands: $checkedConvert(
      'commands',
      (v) => v == null
          ? null
          : ProjectCommands.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$ProjectUpdateRequestToJson(
  ProjectUpdateRequest instance,
) => <String, dynamic>{
  'name': ?instance.name,
  'icon': ?instance.icon?.toJson(),
  'commands': ?instance.commands?.toJson(),
};
