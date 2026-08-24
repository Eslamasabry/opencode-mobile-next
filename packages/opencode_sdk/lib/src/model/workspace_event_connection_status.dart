//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'workspace_event_connection_status.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorkspaceEventConnectionStatus {
  /// Returns a new [WorkspaceEventConnectionStatus] instance.
  WorkspaceEventConnectionStatus({
    required this.workspaceID,

    required this.status,
  });

  @JsonKey(name: r'workspaceID', required: true, includeIfNull: true)
  final String? workspaceID;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        WorkspaceEventConnectionStatusStatusEnum.unknownDefaultOpenApi,
  )
  final WorkspaceEventConnectionStatusStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorkspaceEventConnectionStatus &&
            runtimeType == other.runtimeType &&
            equals([workspaceID, status], [other.workspaceID, other.status]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([workspaceID, status]);

  factory WorkspaceEventConnectionStatus.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceEventConnectionStatusFromJson(json);

  Map<String, dynamic> toJson() => _$WorkspaceEventConnectionStatusToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WorkspaceEventConnectionStatusStatusEnum {
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

  const WorkspaceEventConnectionStatusStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
