//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_workspace_warp_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalWorkspaceWarpRequest {
  /// Returns a new [ExperimentalWorkspaceWarpRequest] instance.
  ExperimentalWorkspaceWarpRequest({
    required this.id,

    required this.sessionID,

    this.copyChanges,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: true)
  final String? id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'copyChanges', required: false, includeIfNull: false)
  final bool? copyChanges;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalWorkspaceWarpRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, copyChanges],
              [other.id, other.sessionID, other.copyChanges],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, sessionID, copyChanges]);

  factory ExperimentalWorkspaceWarpRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalWorkspaceWarpRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalWorkspaceWarpRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
