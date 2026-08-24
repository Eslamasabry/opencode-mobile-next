//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_workspace_create_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalWorkspaceCreateRequest {
  /// Returns a new [ExperimentalWorkspaceCreateRequest] instance.
  ExperimentalWorkspaceCreateRequest({
    this.id,

    required this.type,

    this.branch,

    this.extra,
  });

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(name: r'type', required: true, includeIfNull: false)
  final String type;

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  @JsonKey(name: r'extra', required: false, includeIfNull: false)
  final Object? extra;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalWorkspaceCreateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, branch, extra],
              [other.id, other.type, other.branch, other.extra],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, branch, extra]);

  factory ExperimentalWorkspaceCreateRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalWorkspaceCreateRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalWorkspaceCreateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
