//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'worktree.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Worktree {
  /// Returns a new [Worktree] instance.
  Worktree({required this.name, this.branch, required this.directory});

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Worktree &&
            runtimeType == other.runtimeType &&
            equals(
              [name, branch, directory],
              [other.name, other.branch, other.directory],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([name, branch, directory]);

  factory Worktree.fromJson(Map<String, dynamic> json) =>
      _$WorktreeFromJson(json);

  Map<String, dynamic> toJson() => _$WorktreeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
