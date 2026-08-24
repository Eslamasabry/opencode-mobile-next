//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'assistant_message_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class AssistantMessageTime {
  /// Returns a new [AssistantMessageTime] instance.
  AssistantMessageTime({required this.created, this.completed});

  // minimum: 0
  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final int created;

  // minimum: 0
  @JsonKey(name: r'completed', required: false, includeIfNull: false)
  final int? completed;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is AssistantMessageTime &&
            runtimeType == other.runtimeType &&
            equals([created, completed], [other.created, other.completed]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([created, completed]);

  factory AssistantMessageTime.fromJson(Map<String, dynamic> json) =>
      _$AssistantMessageTimeFromJson(json);

  Map<String, dynamic> toJson() => _$AssistantMessageTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
