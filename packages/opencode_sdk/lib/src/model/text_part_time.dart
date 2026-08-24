//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'text_part_time.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TextPartTime {
  /// Returns a new [TextPartTime] instance.
  TextPartTime({required this.start, this.end});

  // minimum: 0
  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final int start;

  // minimum: 0
  @JsonKey(name: r'end', required: false, includeIfNull: false)
  final int? end;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TextPartTime &&
            runtimeType == other.runtimeType &&
            equals([start, end], [other.start, other.end]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([start, end]);

  factory TextPartTime.fromJson(Map<String, dynamic> json) =>
      _$TextPartTimeFromJson(json);

  Map<String, dynamic> toJson() => _$TextPartTimeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
