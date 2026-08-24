//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/snapshot_file_diff.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'user_message_summary.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UserMessageSummary {
  /// Returns a new [UserMessageSummary] instance.
  UserMessageSummary({this.title, this.body, required this.diffs});

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'body', required: false, includeIfNull: false)
  final String? body;

  @JsonKey(name: r'diffs', required: true, includeIfNull: false)
  final List<SnapshotFileDiff> diffs;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UserMessageSummary &&
            runtimeType == other.runtimeType &&
            equals(
              [title, body, diffs],
              [other.title, other.body, other.diffs],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([title, body, diffs]);

  factory UserMessageSummary.fromJson(Map<String, dynamic> json) =>
      _$UserMessageSummaryFromJson(json);

  Map<String, dynamic> toJson() => _$UserMessageSummaryToJson(this);

  String toString() {
    return toJson().toString();
  }
}
