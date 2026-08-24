//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'reference_git_source.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ReferenceGitSource {
  /// Returns a new [ReferenceGitSource] instance.
  ReferenceGitSource({
    required this.type,

    required this.repository,

    this.branch,

    this.description,

    this.hidden,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ReferenceGitSourceTypeEnum.unknownDefaultOpenApi,
  )
  final ReferenceGitSourceTypeEnum type;

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
        other is ReferenceGitSource &&
            runtimeType == other.runtimeType &&
            equals(
              [type, repository, branch, description, hidden],
              [
                other.type,
                other.repository,
                other.branch,
                other.description,
                other.hidden,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, repository, branch, description, hidden]);

  factory ReferenceGitSource.fromJson(Map<String, dynamic> json) =>
      _$ReferenceGitSourceFromJson(json);

  Map<String, dynamic> toJson() => _$ReferenceGitSourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ReferenceGitSourceTypeEnum {
  @JsonValue(r'git')
  git(r'git'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ReferenceGitSourceTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
