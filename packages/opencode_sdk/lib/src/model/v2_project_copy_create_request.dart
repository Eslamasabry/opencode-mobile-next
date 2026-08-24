//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_project_copy_create_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2ProjectCopyCreateRequest {
  /// Returns a new [V2ProjectCopyCreateRequest] instance.
  V2ProjectCopyCreateRequest({
    required this.strategy,

    required this.directory,

    this.name,
  });

  @JsonKey(name: r'strategy', required: true, includeIfNull: false)
  final String strategy;

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2ProjectCopyCreateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [strategy, directory, name],
              [other.strategy, other.directory, other.name],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([strategy, directory, name]);

  factory V2ProjectCopyCreateRequest.fromJson(Map<String, dynamic> json) =>
      _$V2ProjectCopyCreateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2ProjectCopyCreateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
