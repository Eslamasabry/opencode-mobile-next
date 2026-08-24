//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_assistant_tool_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAssistantToolTime {
  /// Returns a new [SessionMessageAssistantToolTime] instance.
  SessionMessageAssistantToolTime({
    required this.created,

    this.ran,

    this.completed,

    this.pruned,
  });

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final num created;

  @JsonKey(name: r'ran', required: false, includeIfNull: false)
  final num? ran;

  @JsonKey(name: r'completed', required: false, includeIfNull: false)
  final num? completed;

  @JsonKey(name: r'pruned', required: false, includeIfNull: false)
  final num? pruned;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAssistantToolTime &&
            runtimeType == other.runtimeType &&
            equals(
              [created, ran, completed, pruned],
              [other.created, other.ran, other.completed, other.pruned],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([created, ran, completed, pruned]);

  factory SessionMessageAssistantToolTime.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAssistantToolTimeFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessageAssistantToolTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
