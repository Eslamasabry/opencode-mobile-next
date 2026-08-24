// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_file_watcher_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventFileWatcherUpdated _$EventFileWatcherUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventFileWatcherUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventFileWatcherUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventFileWatcherUpdatedTypeEnumEnumMap,
        v,
        unknownValue: EventFileWatcherUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => FileWatcherUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventFileWatcherUpdatedToJson(
  EventFileWatcherUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventFileWatcherUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventFileWatcherUpdatedTypeEnumEnumMap = {
  EventFileWatcherUpdatedTypeEnum.filePeriodWatcherPeriodUpdated:
      'file.watcher.updated',
  EventFileWatcherUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
