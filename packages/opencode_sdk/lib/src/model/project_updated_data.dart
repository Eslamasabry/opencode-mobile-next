//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/project_commands.dart';
import 'package:opencode_sdk/src/model/project_icon.dart';
import 'package:opencode_sdk/src/model/project_time.dart';
import 'package:opencode_sdk/src/model/project_vcs.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'project_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProjectUpdatedData {
  /// Returns a new [ProjectUpdatedData] instance.
  ProjectUpdatedData({
    required this.id,

    required this.worktree,

    this.vcs,

    this.name,

    this.icon,

    this.commands,

    required this.time,

    required this.sandboxes,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'worktree', required: true, includeIfNull: false)
  final String worktree;

  @JsonKey(
    name: r'vcs',
    required: false,
    includeIfNull: false,
    unknownEnumValue: ProjectVcs.unknownDefaultOpenApi,
  )
  final ProjectVcs? vcs;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  @JsonKey(name: r'icon', required: false, includeIfNull: false)
  final ProjectIcon? icon;

  @JsonKey(name: r'commands', required: false, includeIfNull: false)
  final ProjectCommands? commands;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final ProjectTime time;

  @JsonKey(name: r'sandboxes', required: true, includeIfNull: false)
  final List<String> sandboxes;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProjectUpdatedData &&
            runtimeType == other.runtimeType &&
            equals(
              [id, worktree, vcs, name, icon, commands, time, sandboxes],
              [
                other.id,
                other.worktree,
                other.vcs,
                other.name,
                other.icon,
                other.commands,
                other.time,
                other.sandboxes,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        worktree,
        vcs,
        name,
        icon,
        commands,
        time,
        sandboxes,
      ]);

  factory ProjectUpdatedData.fromJson(Map<String, dynamic> json) =>
      _$ProjectUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$ProjectUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
