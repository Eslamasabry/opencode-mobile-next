//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/todo_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_todo_updated.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventTodoUpdated {
  /// Returns a new [EventTodoUpdated] instance.
  EventTodoUpdated({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventTodoUpdatedTypeEnum.unknownDefaultOpenApi,
  )
  final EventTodoUpdatedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final TodoUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventTodoUpdated &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventTodoUpdated.fromJson(Map<String, dynamic> json) =>
      _$EventTodoUpdatedFromJson(json);

  Map<String, dynamic> toJson() => _$EventTodoUpdatedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventTodoUpdatedTypeEnum {
  @JsonValue(r'todo.updated')
  todoPeriodUpdated(r'todo.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventTodoUpdatedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
