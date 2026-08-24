//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'retry_part_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class RetryPartTime {
  /// Returns a new [RetryPartTime] instance.
  RetryPartTime({required this.created});

  // minimum: 0
  @JsonKey(name: r'created', required: true, includeIfNull: false)
  final int created;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is RetryPartTime &&
            runtimeType == other.runtimeType &&
            equals([created], [other.created]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([created]);

  factory RetryPartTime.fromJson(Map<String, dynamic> json) =>
      _$RetryPartTimeFromJson(json);

  Map<String, dynamic> toJson() => _$RetryPartTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
