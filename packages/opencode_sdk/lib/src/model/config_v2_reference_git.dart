//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_v2_reference_git.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigV2ReferenceGit {
  /// Returns a new [ConfigV2ReferenceGit] instance.
  ConfigV2ReferenceGit({
    required this.repository,

    this.branch,

    this.description,

    this.hidden,
  });

  @JsonKey(name: r'repository', required: true, includeIfNull: false)
  final String repository;

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'hidden', required: false, includeIfNull: false)
  final bool? hidden;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigV2ReferenceGit &&
            runtimeType == other.runtimeType &&
            equals(
              [repository, branch, description, hidden],
              [other.repository, other.branch, other.description, other.hidden],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([repository, branch, description, hidden]);

  factory ConfigV2ReferenceGit.fromJson(Map<String, dynamic> json) =>
      _$ConfigV2ReferenceGitFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigV2ReferenceGitToJson(this);

  String toString() {
    return toJson().toString();
  }
}
