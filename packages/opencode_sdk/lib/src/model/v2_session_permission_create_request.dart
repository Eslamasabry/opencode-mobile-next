//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_permission_create_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPermissionCreateRequest {
  /// Returns a new [V2SessionPermissionCreateRequest] instance.
  V2SessionPermissionCreateRequest({
    this.id,

    required this.action,

    required this.resources,

    this.save,

    this.metadata,

    this.source_,

    this.agent,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(name: r'action', required: true, includeIfNull: false)
  final String action;

  @JsonKey(name: r'resources', required: true, includeIfNull: false)
  final List<String> resources;

  @JsonKey(name: r'save', required: false, includeIfNull: false)
  final List<String>? save;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'source', required: false, includeIfNull: false)
  final PermissionV2Source? source_;

  @JsonKey(name: r'agent', required: false, includeIfNull: false)
  final String? agent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPermissionCreateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, action, resources, save, metadata, source_, agent],
              [
                other.id,
                other.action,
                other.resources,
                other.save,
                other.metadata,
                other.source_,
                other.agent,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        action,
        resources,
        save,
        metadata,
        source_,
        agent,
      ]);

  factory V2SessionPermissionCreateRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$V2SessionPermissionCreateRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2SessionPermissionCreateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
