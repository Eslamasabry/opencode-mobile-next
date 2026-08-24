// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'todo_updated_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TodoUpdatedData _$TodoUpdatedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TodoUpdatedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'todos']);
      final val = TodoUpdatedData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        todos: $checkedConvert(
          'todos',
          (v) => (v as List<dynamic>)
              .map((e) => Todo.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$TodoUpdatedDataToJson(TodoUpdatedData instance) =>
    <String, dynamic>{
      'sessionID': instance.sessionID,
      'todos': instance.todos.map((e) => e.toJson()).toList(),
    };
