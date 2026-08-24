//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'workspace_status_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorkspaceStatusData {
  /// Returns a new [WorkspaceStatusData] instance.
  WorkspaceStatusData({required this.workspaceID, required this.status});

  @JsonKey(name: r'workspaceID', required: true, includeIfNull: true)
  final String? workspaceID;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: WorkspaceStatusDataStatusEnum.unknownDefaultOpenApi,
  )
  final WorkspaceStatusDataStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorkspaceStatusData &&
            runtimeType == other.runtimeType &&
            equals([workspaceID, status], [other.workspaceID, other.status]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([workspaceID, status]);

  factory WorkspaceStatusData.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceStatusDataFromJson(json);

  Map<String, dynamic> toJson() => _$WorkspaceStatusDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum WorkspaceStatusDataStatusEnum {
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

  const WorkspaceStatusDataStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
