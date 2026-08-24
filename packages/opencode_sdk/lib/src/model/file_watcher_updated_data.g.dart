// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_watcher_updated_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FileWatcherUpdatedData _$FileWatcherUpdatedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('FileWatcherUpdatedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['file', 'event']);
  final val = FileWatcherUpdatedData(
    file: $checkedConvert('file', (v) => v as String),
    event: $checkedConvert(
      'event',
      (v) => $enumDecode(
        _$FileWatcherUpdatedDataEventEnumEnumMap,
        v,
        unknownValue: FileWatcherUpdatedDataEventEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$FileWatcherUpdatedDataToJson(
  FileWatcherUpdatedData instance,
) => <String, dynamic>{
  'file': instance.file,
  'event': _$FileWatcherUpdatedDataEventEnumEnumMap[instance.event]!,
};

const _$FileWatcherUpdatedDataEventEnumEnumMap = {
  FileWatcherUpdatedDataEventEnum.add: 'add',
  FileWatcherUpdatedDataEventEnum.change: 'change',
  FileWatcherUpdatedDataEventEnum.unlink: 'unlink',
  FileWatcherUpdatedDataEventEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
