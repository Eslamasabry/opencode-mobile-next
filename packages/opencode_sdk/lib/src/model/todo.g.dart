// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'todo.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Todo _$TodoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Todo', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['content', 'status', 'priority']);
      final val = Todo(
        content: $checkedConvert('content', (v) => v as String),
        status: $checkedConvert('status', (v) => v as String),
        priority: $checkedConvert('priority', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$TodoToJson(Todo instance) => <String, dynamic>{
  'content': instance.content,
  'status': instance.status,
  'priority': instance.priority,
};
