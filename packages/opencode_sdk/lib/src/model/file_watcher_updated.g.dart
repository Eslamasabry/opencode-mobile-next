// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_watcher_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileWatcherUpdated _$FileWatcherUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FileWatcherUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = FileWatcherUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$FileWatcherUpdatedTypeEnumEnumMap,
            v,
            unknownValue: FileWatcherUpdatedTypeEnum.unknownDefaultOpenApi,
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
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => FileWatcherUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$FileWatcherUpdatedToJson(FileWatcherUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$FileWatcherUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$FileWatcherUpdatedTypeEnumEnumMap = {
  FileWatcherUpdatedTypeEnum.filePeriodWatcherPeriodUpdated:
      'file.watcher.updated',
  FileWatcherUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
