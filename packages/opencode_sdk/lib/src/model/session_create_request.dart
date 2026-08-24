//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_rule.dart';
import 'package:opencode_sdk/src/model/session_create_request_model.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_create_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionCreateRequest {
  /// Returns a new [SessionCreateRequest] instance.
  SessionCreateRequest({
    this.parentID,

    this.title,

    this.agent,

    this.model,

    this.metadata,

    this.permission,

    this.workspaceID,
  });

  @JsonKey(name: r'parentID', required: false, includeIfNull: false)
  final String? parentID;

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  @JsonKey(name: r'model', required: false, includeIfNull: false)
  final SessionCreateRequestModel? model;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'permission', required: false, includeIfNull: false)
  final List<PermissionRule>? permission;

  @JsonKey(name: r'workspaceID', required: false, includeIfNull: false)
  final String? workspaceID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionCreateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [
                parentID,
                title,
                agent,
                model,
                metadata,
                permission,
                workspaceID,
              ],
              [
                other.parentID,
                other.title,
                other.agent,
                other.model,
                other.metadata,
                other.permission,
                other.workspaceID,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        parentID,
        title,
        agent,
        model,
        metadata,
        permission,
        workspaceID,
      ]);

  factory SessionCreateRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionCreateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionCreateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
