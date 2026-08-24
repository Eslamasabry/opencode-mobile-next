//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant_snapshot.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistantSnapshot {
  /// Returns a new [SessionMessageAssistantSnapshot] instance.
  SessionMessageAssistantSnapshot({this.start, this.end, this.files});

  @JsonKey(name: r'start', required: false, includeIfNull: false)
  final String? start;

  @JsonKey(name: r'end', required: false, includeIfNull: false)
  final String? end;

  @JsonKey(name: r'files', required: false, includeIfNull: false)
  final List<String>? files;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistantSnapshot &&
            runtimeType == other.runtimeType &&
            equals([start, end, files], [other.start, other.end, other.files]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([start, end, files]);

  factory SessionMessageAssistantSnapshot.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAssistantSnapshotFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessageAssistantSnapshotToJson(this);

  String toString() {
    return toJson().toString();
  }
}
