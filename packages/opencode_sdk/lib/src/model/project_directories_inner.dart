//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_directories_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectDirectoriesInner {
  /// Returns a new [ProjectDirectoriesInner] instance.
  ProjectDirectoriesInner({required this.directory, this.strategy});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  @JsonKey(name: r'strategy', required: false, includeIfNull: false)
  final String? strategy;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectDirectoriesInner &&
            runtimeType == other.runtimeType &&
            equals([directory, strategy], [other.directory, other.strategy]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([directory, strategy]);

  factory ProjectDirectoriesInner.fromJson(Map<String, dynamic> json) =>
      _$ProjectDirectoriesInnerFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectDirectoriesInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
