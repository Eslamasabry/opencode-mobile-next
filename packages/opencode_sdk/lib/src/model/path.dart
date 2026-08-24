//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'path.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Path {
  /// Returns a new [Path] instance.
  Path({
    required this.home,

    required this.state,

    required this.config,

    required this.worktree,

    required this.directory,
  });

  @JsonKey(name: r'home', required: true, includeIfNull: false)
  final String home;

  @JsonKey(name: r'state', required: true, includeIfNull: false)
  final String state;

  @JsonKey(name: r'config', required: true, includeIfNull: false)
  final String config;

  @JsonKey(name: r'worktree', required: true, includeIfNull: false)
  final String worktree;

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Path &&
            runtimeType == other.runtimeType &&
            equals(
              [home, state, config, worktree, directory],
              [
                other.home,
                other.state,
                other.config,
                other.worktree,
                other.directory,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([home, state, config, worktree, directory]);

  factory Path.fromJson(Map<String, dynamic> json) => _$PathFromJson(json);

  Map<String, dynamic> toJson() => _$PathToJson(this);

  String toString() {
    return toJson().toString();
  }
}
