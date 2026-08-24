// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'assistant_message_path.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AssistantMessagePath _$AssistantMessagePathFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('AssistantMessagePath', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['cwd', 'root']);
  final val = AssistantMessagePath(
    cwd: $checkedConvert('cwd', (v) => v as String),
    root: $checkedConvert('root', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$AssistantMessagePathToJson(
  AssistantMessagePath instance,
) => <String, dynamic>{'cwd': instance.cwd, 'root': instance.root};
