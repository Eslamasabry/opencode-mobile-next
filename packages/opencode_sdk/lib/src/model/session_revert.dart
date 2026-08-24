//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_revert.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionRevert {
  /// Returns a new [SessionRevert] instance.
  SessionRevert({
    required this.messageID,

    this.partID,

    this.snapshot,

    this.diff,
  });

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'partID', required: false, includeIfNull: false)
  final String? partID;

  @JsonKey(name: r'snapshot', required: false, includeIfNull: false)
  final String? snapshot;

  @JsonKey(name: r'diff', required: false, includeIfNull: false)
  final String? diff;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionRevert &&
            runtimeType == other.runtimeType &&
            equals(
              [messageID, partID, snapshot, diff],
              [other.messageID, other.partID, other.snapshot, other.diff],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([messageID, partID, snapshot, diff]);

  factory SessionRevert.fromJson(Map<String, dynamic> json) =>
      _$SessionRevertFromJson(json);

  Map<String, dynamic> toJson() => _$SessionRevertToJson(this);

  String toString() {
    return toJson().toString();
  }
}
