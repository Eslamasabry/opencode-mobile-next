//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_diff.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'revert_state.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class RevertState {
  /// Returns a new [RevertState] instance.
  RevertState({
    required this.messageID,

    this.partID,

    this.snapshot,

    this.diff,

    this.files,
  });

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'partID', required: false, includeIfNull: false)
  final String? partID;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final String? snapshot;

  @JsonKey(name: r'diff', required: false, includeIfNull: false)
  final String? diff;

  @JsonKey(name: r'files', required: false, includeIfNull: false)
  final List<FileDiff>? files;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is RevertState &&
            runtimeType == other.runtimeType &&
            equals(
              [messageID, partID, snapshot, diff, files],
              [
                other.messageID,
                other.partID,
                other.snapshot,
                other.diff,
                other.files,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([messageID, partID, snapshot, diff, files]);

  factory RevertState.fromJson(Map<String, dynamic> json) =>
      _$RevertStateFromJson(json);

  Map<String, dynamic> toJson() => _$RevertStateToJson(this);

  String toString() {
    return toJson().toString();
  }
}
