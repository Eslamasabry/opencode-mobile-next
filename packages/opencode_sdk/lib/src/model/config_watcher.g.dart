// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_watcher.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigWatcher _$ConfigWatcherFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConfigWatcher', json, ($checkedConvert) {
      final val = ConfigWatcher(
        ignore: $checkedConvert(
          'ignore',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ConfigWatcherToJson(ConfigWatcher instance) =>
    <String, dynamic>{'ignore': ?instance.ignore};
