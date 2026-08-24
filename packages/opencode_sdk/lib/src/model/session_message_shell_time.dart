//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_shell_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageShellTime {
  /// Returns a new [SessionMessageShellTime] instance.
  SessionMessageShellTime({required this.created, this.completed});

  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final num created;

  @JsonKey(name: r'completed', required: false, includeIfNull: false)
  final num? completed;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageShellTime &&
            runtimeType == other.runtimeType &&
            equals([created, completed], [other.created, other.completed]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, completed]);

  factory SessionMessageShellTime.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageShellTimeFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageShellTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
