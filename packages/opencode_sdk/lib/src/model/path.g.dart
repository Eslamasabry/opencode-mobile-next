// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'path.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Path _$PathFromJson(Map<String, dynamic> json) => $checkedCreate('Path', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['home', 'state', 'config', 'worktree', 'directory'],
  );
  final val = Path(
    home: $checkedConvert('home', (v) => v as String),
    state: $checkedConvert('state', (v) => v as String),
    config: $checkedConvert('config', (v) => v as String),
    worktree: $checkedConvert('worktree', (v) => v as String),
    directory: $checkedConvert('directory', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$PathToJson(Path instance) => <String, dynamic>{
  'home': instance.home,
  'state': instance.state,
  'config': instance.config,
  'worktree': instance.worktree,
  'directory': instance.directory,
};
