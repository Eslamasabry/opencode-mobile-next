// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_todo_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTodoUpdated _$EventTodoUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventTodoUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventTodoUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventTodoUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventTodoUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => TodoUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventTodoUpdatedToJson(EventTodoUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventTodoUpdatedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventTodoUpdatedTypeEnumEnumMap = {
  EventTodoUpdatedTypeEnum.todoPeriodUpdated: 'todo.updated',
  EventTodoUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
