//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'worktree_reset_input.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorktreeResetInput {
  /// Returns a new [WorktreeResetInput] instance.
  WorktreeResetInput({required this.directory});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorktreeResetInput &&
            runtimeType == other.runtimeType &&
            equals([directory], [other.directory]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([directory]);

  factory WorktreeResetInput.fromJson(Map<String, dynamic> json) =>
      _$WorktreeResetInputFromJson(json);

  Map<String, dynamic> toJson() => _$WorktreeResetInputToJson(this);

  String toString() {
    return toJson().toString();
  }
}
