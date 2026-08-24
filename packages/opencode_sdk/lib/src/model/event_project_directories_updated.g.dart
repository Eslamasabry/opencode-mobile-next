// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_project_directories_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventProjectDirectoriesUpdated _$EventProjectDirectoriesUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventProjectDirectoriesUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventProjectDirectoriesUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventProjectDirectoriesUpdatedTypeEnumEnumMap,
        v,
        unknownValue:
            EventProjectDirectoriesUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => ProjectDirectoriesUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventProjectDirectoriesUpdatedToJson(
  EventProjectDirectoriesUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventProjectDirectoriesUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventProjectDirectoriesUpdatedTypeEnumEnumMap = {
  EventProjectDirectoriesUpdatedTypeEnum.projectPeriodDirectoriesPeriodUpdated:
      'project.directories.updated',
  EventProjectDirectoriesUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
