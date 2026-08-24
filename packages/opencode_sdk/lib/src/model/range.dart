//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/range_start.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'range.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Range {
  /// Returns a new [Range] instance.
  Range({required this.start, required this.end});

  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final RangeStart start;

  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final RangeStart end;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Range &&
            runtimeType == other.runtimeType &&
            equals([start, end], [other.start, other.end]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([start, end]);

  factory Range.fromJson(Map<String, dynamic> json) => _$RangeFromJson(json);

  Map<String, dynamic> toJson() => _$RangeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
