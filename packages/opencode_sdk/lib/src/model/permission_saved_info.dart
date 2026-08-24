//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_saved_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionSavedInfo {
  /// Returns a new [PermissionSavedInfo] instance.
  PermissionSavedInfo({
    required this.id,

    required this.projectID,

    required this.action,

    required this.resource,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'projectID', required: true, includeIfNull: false)
  final String projectID;

  @JsonKey(name: r'action', required: true, includeIfNull: false)
  final String action;

  @JsonKey(name: r'resource', required: true, includeIfNull: false)
  final String resource;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionSavedInfo &&
            runtimeType == other.runtimeType &&
            equals(
              [id, projectID, action, resource],
              [other.id, other.projectID, other.action, other.resource],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, projectID, action, resource]);

  factory PermissionSavedInfo.fromJson(Map<String, dynamic> json) =>
      _$PermissionSavedInfoFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionSavedInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
