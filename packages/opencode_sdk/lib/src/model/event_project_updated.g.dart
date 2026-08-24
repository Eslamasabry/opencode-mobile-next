// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_project_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventProjectUpdated _$EventProjectUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventProjectUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventProjectUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventProjectUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventProjectUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => ProjectUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventProjectUpdatedToJson(
  EventProjectUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventProjectUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventProjectUpdatedTypeEnumEnumMap = {
  EventProjectUpdatedTypeEnum.projectPeriodUpdated: 'project.updated',
  EventProjectUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
