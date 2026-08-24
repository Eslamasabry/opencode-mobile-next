//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_any_of1_action.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusAnyOf1Action {
  /// Returns a new [SessionStatusAnyOf1Action] instance.
  SessionStatusAnyOf1Action({
    required this.reason,

    required this.provider,

    required this.title,

    required this.message,

    required this.label,

    this.link,
  });

  @JsonKey(name: r'reason', required: true, includeIfNull: false)
  final String reason;

  @JsonKey(name: r'provider', required: true, includeIfNull: false)
  final String provider;

  @JsonKey(name: r'title', required: true, includeIfNull: false)
  final String title;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  @JsonKey(name: r'link', required: false, includeIfNull: false)
  final String? link;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusAnyOf1Action &&
            runtimeType == other.runtimeType &&
            equals(
              [reason, provider, title, message, label, link],
              [
                other.reason,
                other.provider,
                other.title,
                other.message,
                other.label,
                other.link,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([reason, provider, title, message, label, link]);

  factory SessionStatusAnyOf1Action.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusAnyOf1ActionFromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusAnyOf1ActionToJson(this);

  String toString() {
    return toJson().toString();
  }
}
