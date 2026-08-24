//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_workspace_adapter_list200_response_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalWorkspaceAdapterList200ResponseInner {
  /// Returns a new [ExperimentalWorkspaceAdapterList200ResponseInner] instance.
  ExperimentalWorkspaceAdapterList200ResponseInner({
    required this.type,

    required this.name,

    required this.description,
  });

  @JsonKey(name: r'type', required: true, includeIfNull: false)
  final String type;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'description', required: true, includeIfNull: false)
  final String description;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalWorkspaceAdapterList200ResponseInner &&
            runtimeType == other.runtimeType &&
            equals(
              [type, name, description],
              [other.type, other.name, other.description],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, name, description]);

  factory ExperimentalWorkspaceAdapterList200ResponseInner.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalWorkspaceAdapterList200ResponseInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalWorkspaceAdapterList200ResponseInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
