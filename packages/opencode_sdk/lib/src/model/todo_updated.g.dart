// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'todo_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TodoUpdated _$TodoUpdatedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'TodoUpdated',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = TodoUpdated(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$TodoUpdatedTypeEnumEnumMap,
          v,
          unknownValue: TodoUpdatedTypeEnum.unknownDefaultOpenApi,
        ),
      ),
      durable: $checkedConvert(
        'durable',
        (v) => v == null
            ? null
            : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
      ),
      location: $checkedConvert(
        'location',
        (v) =>
            v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
      ),
      data: $checkedConvert(
        'data',
        (v) => TodoUpdatedData.fromJson(v as Map<String, dynamic>),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$TodoUpdatedToJson(TodoUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$TodoUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$TodoUpdatedTypeEnumEnumMap = {
  TodoUpdatedTypeEnum.todoPeriodUpdated: 'todo.updated',
  TodoUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
