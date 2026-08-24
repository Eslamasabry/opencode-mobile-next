//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_workspace_status_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventWorkspaceStatusProperties {
  /// Returns a new [EventWorkspaceStatusProperties] instance.
  EventWorkspaceStatusProperties({
    required this.workspaceID,

    required this.status,
  });

  @JsonKey(name: r'workspaceID', required: true, includeIfNull: false)
  final String workspaceID;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        EventWorkspaceStatusPropertiesStatusEnum.unknownDefaultOpenApi,
  )
  final EventWorkspaceStatusPropertiesStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventWorkspaceStatusProperties &&
            runtimeType == other.runtimeType &&
            equals([workspaceID, status], [other.workspaceID, other.status]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([workspaceID, status]);

  factory EventWorkspaceStatusProperties.fromJson(Map<String, dynamic> json) =>
      _$EventWorkspaceStatusPropertiesFromJson(json);

  Map<String, dynamic> toJson() => _$EventWorkspaceStatusPropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventWorkspaceStatusPropertiesStatusEnum {
  @JsonValue(r'connected')
  connected(r'connected'),
  @JsonValue(r'connecting')
  connecting(r'connecting'),
  @JsonValue(r'disconnected')
  disconnected(r'disconnected'),
  @JsonValue(r'error')
  error(r'error'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventWorkspaceStatusPropertiesStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
