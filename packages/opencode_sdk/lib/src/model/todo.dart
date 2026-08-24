//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'todo.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Todo {
  /// Returns a new [Todo] instance.
  Todo({required this.content, required this.status, required this.priority});

  /// Brief description of the task
  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final String content;

  /// Current status of the task: pending, in_progress, completed, cancelled
  @JsonKey(name: r'status', required: true, includeIfNull: false)
  final String status;

  /// Priority level of the task: high, medium, low
  @JsonKey(name: r'priority', required: true, includeIfNull: false)
  final String priority;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Todo &&
            runtimeType == other.runtimeType &&
            equals(
              [content, status, priority],
              [other.content, other.status, other.priority],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([content, status, priority]);

  factory Todo.fromJson(Map<String, dynamic> json) => _$TodoFromJson(json);

  Map<String, dynamic> toJson() => _$TodoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
