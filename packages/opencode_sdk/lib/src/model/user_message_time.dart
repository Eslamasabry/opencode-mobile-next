//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'user_message_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UserMessageTime {
  /// Returns a new [UserMessageTime] instance.
  UserMessageTime({required this.created});

  // minimum: 0
  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final num created;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UserMessageTime &&
            runtimeType == other.runtimeType &&
            equals([created], [other.created]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([created]);

  factory UserMessageTime.fromJson(Map<String, dynamic> json) =>
      _$UserMessageTimeFromJson(json);

  Map<String, dynamic> toJson() => _$UserMessageTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
