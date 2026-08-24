//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/snapshot_file_diff.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_diff_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionDiffData {
  /// Returns a new [SessionDiffData] instance.
  SessionDiffData({required this.sessionID, required this.diff});

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'diff', required: true, includeIfNull: false)
  final List<SnapshotFileDiff> diff;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionDiffData &&
            runtimeType == other.runtimeType &&
            equals([sessionID, diff], [other.sessionID, other.diff]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, diff]);

  factory SessionDiffData.fromJson(Map<String, dynamic> json) =>
      _$SessionDiffDataFromJson(json);

  Map<String, dynamic> toJson() => _$SessionDiffDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
