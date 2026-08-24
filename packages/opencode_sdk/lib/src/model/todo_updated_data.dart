//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/todo.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'todo_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TodoUpdatedData {
  /// Returns a new [TodoUpdatedData] instance.
  TodoUpdatedData({required this.sessionID, required this.todos});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'todos', required: true, includeIfNull: false)
  final List<Todo> todos;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TodoUpdatedData &&
            runtimeType == other.runtimeType &&
            equals([sessionID, todos], [other.sessionID, other.todos]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, todos]);

  factory TodoUpdatedData.fromJson(Map<String, dynamic> json) =>
      _$TodoUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$TodoUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
