//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/event_workspace_status_properties.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_workspace_status.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventWorkspaceStatus {
  /// Returns a new [EventWorkspaceStatus] instance.
  EventWorkspaceStatus({
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
    unknownEnumValue: EventWorkspaceStatusTypeEnum.unknownDefaultOpenApi,
  )
  final EventWorkspaceStatusTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final EventWorkspaceStatusProperties properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventWorkspaceStatus &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventWorkspaceStatus.fromJson(Map<String, dynamic> json) =>
      _$EventWorkspaceStatusFromJson(json);

  Map<String, dynamic> toJson() => _$EventWorkspaceStatusToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventWorkspaceStatusTypeEnum {
  @JsonValue(r'workspace.status')
  workspacePeriodStatus(r'workspace.status'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventWorkspaceStatusTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
