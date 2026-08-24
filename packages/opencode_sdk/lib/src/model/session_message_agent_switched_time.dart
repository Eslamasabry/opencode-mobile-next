//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_agent_switched_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageAgentSwitchedTime {
  /// Returns a new [SessionMessageAgentSwitchedTime] instance.
  SessionMessageAgentSwitchedTime({required this.created});

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final num created;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageAgentSwitchedTime &&
            runtimeType == other.runtimeType &&
            equals([created], [other.created]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([created]);

  factory SessionMessageAgentSwitchedTime.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageAgentSwitchedTimeFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SessionMessageAgentSwitchedTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
