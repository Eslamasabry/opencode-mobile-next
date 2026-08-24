//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/snapshot_file_diff.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_summary.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionSummary {
  /// Returns a new [SessionSummary] instance.
  SessionSummary({
    required this.additions,

    required this.deletions,

    required this.files,

    this.diffs,
  });

  @JsonKey(name: r'additions', required: true, includeIfNull: false)
  final num additions;

  @JsonKey(name: r'deletions', required: true, includeIfNull: false)
  final num deletions;

  @JsonKey(name: r'files', required: true, includeIfNull: false)
  final num files;

  @JsonKey(name: r'diffs', required: false, includeIfNull: false)
  final List<SnapshotFileDiff>? diffs;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionSummary &&
            runtimeType == other.runtimeType &&
            equals(
              [additions, deletions, files, diffs],
              [other.additions, other.deletions, other.files, other.diffs],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([additions, deletions, files, diffs]);

  factory SessionSummary.fromJson(Map<String, dynamic> json) =>
      _$SessionSummaryFromJson(json);

  Map<String, dynamic> toJson() => _$SessionSummaryToJson(this);

  String toString() {
    return toJson().toString();
  }
}
