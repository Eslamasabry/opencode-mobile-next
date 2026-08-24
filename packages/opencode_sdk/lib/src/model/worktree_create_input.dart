//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'worktree_create_input.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorktreeCreateInput {
  /// Returns a new [WorktreeCreateInput] instance.
  WorktreeCreateInput({this.name, this.startCommand});

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  /// Additional startup script to run after the project's start command
  @JsonKey(name: r'startCommand', required: false, includeIfNull: false)
  final String? startCommand;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorktreeCreateInput &&
            runtimeType == other.runtimeType &&
            equals([name, startCommand], [other.name, other.startCommand]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([name, startCommand]);

  factory WorktreeCreateInput.fromJson(Map<String, dynamic> json) =>
      _$WorktreeCreateInputFromJson(json);

  Map<String, dynamic> toJson() => _$WorktreeCreateInputToJson(this);

  String toString() {
    return toJson().toString();
  }
}
