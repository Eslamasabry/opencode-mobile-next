//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_compaction.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigCompaction {
  /// Returns a new [ConfigCompaction] instance.
  ConfigCompaction({
    this.auto,

    this.prune,

    this.tailTurns,

    this.preserveRecentTokens,

    this.reserved,
  });

  @JsonKey(name: r'auto', required: false, includeIfNull: false)
  final bool? auto;

  @JsonKey(name: r'prune', required: false, includeIfNull: false)
  final bool? prune;

  // minimum: 0
  @JsonKey(name: r'tail_turns', required: false, includeIfNull: false)
  final int? tailTurns;

  // minimum: 0
  @JsonKey(
    name: r'preserve_recent_tokens',
    required: false,
    includeIfNull: false,
  )
  final int? preserveRecentTokens;

  // minimum: 0
  @JsonKey(name: r'reserved', required: false, includeIfNull: false)
  final int? reserved;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigCompaction &&
            runtimeType == other.runtimeType &&
            equals(
              [auto, prune, tailTurns, preserveRecentTokens, reserved],
              [
                other.auto,
                other.prune,
                other.tailTurns,
                other.preserveRecentTokens,
                other.reserved,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        auto,
        prune,
        tailTurns,
        preserveRecentTokens,
        reserved,
      ]);

  factory ConfigCompaction.fromJson(Map<String, dynamic> json) =>
      _$ConfigCompactionFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigCompactionToJson(this);

  String toString() {
    return toJson().toString();
  }
}
