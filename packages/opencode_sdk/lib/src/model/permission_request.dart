//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_request_tool.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionRequest {
  /// Returns a new [PermissionRequest] instance.
  PermissionRequest({
    required this.id,

    required this.sessionID,

    required this.permission,

    required this.patterns,

    required this.metadata,

    required this.always,

    this.tool,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'permission', required: true, includeIfNull: false)
  final String permission;

  @JsonKey(name: r'patterns', required: true, includeIfNull: false)
  final List<String> patterns;

  @JsonKey(name: r'metadata', required: true, includeIfNull: false)
  final Object metadata;

  @JsonKey(name: r'always', required: true, includeIfNull: false)
  final List<String> always;

  @JsonKey(name: r'tool', required: false, includeIfNull: false)
  final PermissionRequestTool? tool;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, permission, patterns, metadata, always, tool],
              [
                other.id,
                other.sessionID,
                other.permission,
                other.patterns,
                other.metadata,
                other.always,
                other.tool,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        permission,
        patterns,
        metadata,
        always,
        tool,
      ]);

  factory PermissionRequest.fromJson(Map<String, dynamic> json) =>
      _$PermissionRequestFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
