//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'worktree_remove_input.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorktreeRemoveInput {
  /// Returns a new [WorktreeRemoveInput] instance.
  WorktreeRemoveInput({required this.directory});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorktreeRemoveInput &&
            runtimeType == other.runtimeType &&
            equals([directory], [other.directory]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([directory]);

  factory WorktreeRemoveInput.fromJson(Map<String, dynamic> json) =>
      _$WorktreeRemoveInputFromJson(json);

  Map<String, dynamic> toJson() => _$WorktreeRemoveInputToJson(this);

  String toString() {
    return toJson().toString();
  }
}
