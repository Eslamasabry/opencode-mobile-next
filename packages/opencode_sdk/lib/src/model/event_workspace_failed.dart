//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_workspace_failed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventWorkspaceFailed {
  /// Returns a new [EventWorkspaceFailed] instance.
  EventWorkspaceFailed({
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
    unknownEnumValue: EventWorkspaceFailedTypeEnum.unknownDefaultOpenApi,
  )
  final EventWorkspaceFailedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final MoveSessionErrorData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventWorkspaceFailed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventWorkspaceFailed.fromJson(Map<String, dynamic> json) =>
      _$EventWorkspaceFailedFromJson(json);

  Map<String, dynamic> toJson() => _$EventWorkspaceFailedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventWorkspaceFailedTypeEnum {
  @JsonValue(r'workspace.failed')
  workspacePeriodFailed(r'workspace.failed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventWorkspaceFailedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
