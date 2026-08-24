//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_rule.dart';
import 'package:opencode_sdk/src/model/session_update_request_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_update_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionUpdateRequest {
  /// Returns a new [SessionUpdateRequest] instance.
  SessionUpdateRequest({this.title, this.metadata, this.permission, this.time});

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'permission', required: false, includeIfNull: false)
  final List<PermissionRule>? permission;

  @JsonKey(name: r'time', required: false, includeIfNull: false)
  final SessionUpdateRequestTime? time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionUpdateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [title, metadata, permission, time],
              [other.title, other.metadata, other.permission, other.time],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([title, metadata, permission, time]);

  factory SessionUpdateRequest.fromJson(Map<String, dynamic> json) =>
      _$SessionUpdateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$SessionUpdateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
