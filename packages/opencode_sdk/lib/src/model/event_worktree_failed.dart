//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_worktree_failed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventWorktreeFailed {
  /// Returns a new [EventWorktreeFailed] instance.
  EventWorktreeFailed({
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
    unknownEnumValue: EventWorktreeFailedTypeEnum.unknownDefaultOpenApi,
  )
  final EventWorktreeFailedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final MoveSessionErrorData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventWorktreeFailed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventWorktreeFailed.fromJson(Map<String, dynamic> json) =>
      _$EventWorktreeFailedFromJson(json);

  Map<String, dynamic> toJson() => _$EventWorktreeFailedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventWorktreeFailedTypeEnum {
  @JsonValue(r'worktree.failed')
  worktreePeriodFailed(r'worktree.failed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventWorktreeFailedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
