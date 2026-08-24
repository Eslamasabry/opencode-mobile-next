//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_source.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_v2_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionV2Request {
  /// Returns a new [PermissionV2Request] instance.
  PermissionV2Request({
    required this.id,

    required this.sessionID,

    required this.action,

    required this.resources,

    this.save,

    this.metadata,

    this.source_,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

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

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionV2Request &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, action, resources, save, metadata, source_],
              [
                other.id,
                other.sessionID,
                other.action,
                other.resources,
                other.save,
                other.metadata,
                other.source_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        action,
        resources,
        save,
        metadata,
        source_,
      ]);

  factory PermissionV2Request.fromJson(Map<String, dynamic> json) =>
      _$PermissionV2RequestFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionV2RequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
