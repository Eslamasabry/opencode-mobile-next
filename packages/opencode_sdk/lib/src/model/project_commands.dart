//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_commands.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectCommands {
  /// Returns a new [ProjectCommands] instance.
  ProjectCommands({this.start});

  /// Startup script to run when creating a new workspace (worktree)
  @JsonKey(name: r'start', required: false, includeIfNull: false)
  final String? start;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectCommands &&
            runtimeType == other.runtimeType &&
            equals([start], [other.start]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([start]);

  factory ProjectCommands.fromJson(Map<String, dynamic> json) =>
      _$ProjectCommandsFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectCommandsToJson(this);

  String toString() {
    return toJson().toString();
  }
}
