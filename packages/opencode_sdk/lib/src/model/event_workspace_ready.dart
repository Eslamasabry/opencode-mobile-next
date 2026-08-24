//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/experimental_project_copy_generate_name200_response.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_workspace_ready.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventWorkspaceReady {
  /// Returns a new [EventWorkspaceReady] instance.
  EventWorkspaceReady({
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
    unknownEnumValue: EventWorkspaceReadyTypeEnum.unknownDefaultOpenApi,
  )
  final EventWorkspaceReadyTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final ExperimentalProjectCopyGenerateName200Response properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventWorkspaceReady &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventWorkspaceReady.fromJson(Map<String, dynamic> json) =>
      _$EventWorkspaceReadyFromJson(json);

  Map<String, dynamic> toJson() => _$EventWorkspaceReadyToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventWorkspaceReadyTypeEnum {
  @JsonValue(r'workspace.ready')
  workspacePeriodReady(r'workspace.ready'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventWorkspaceReadyTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
