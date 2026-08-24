//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_update_request_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionUpdateRequestTime {
  /// Returns a new [SessionUpdateRequestTime] instance.
  SessionUpdateRequestTime({this.archived});

  @JsonKey(name: r'archived', required: false, includeIfNull: false)
  final num? archived;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionUpdateRequestTime &&
            runtimeType == other.runtimeType &&
            equals([archived], [other.archived]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([archived]);

  factory SessionUpdateRequestTime.fromJson(Map<String, dynamic> json) =>
      _$SessionUpdateRequestTimeFromJson(json);

  Map<String, dynamic> toJson() => _$SessionUpdateRequestTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
