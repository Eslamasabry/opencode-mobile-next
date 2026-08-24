//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_summary.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectSummary {
  /// Returns a new [ProjectSummary] instance.
  ProjectSummary({required this.id, this.name, required this.worktree});

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  @JsonKey(name: r'worktree', required: true, includeIfNull: false)
  final String worktree;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectSummary &&
            runtimeType == other.runtimeType &&
            equals(
              [id, name, worktree],
              [other.id, other.name, other.worktree],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, name, worktree]);

  factory ProjectSummary.fromJson(Map<String, dynamic> json) =>
      _$ProjectSummaryFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectSummaryToJson(this);

  String toString() {
    return toJson().toString();
  }
}
