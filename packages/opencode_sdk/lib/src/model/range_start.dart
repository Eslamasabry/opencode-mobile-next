//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'range_start.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class RangeStart {
  /// Returns a new [RangeStart] instance.
  RangeStart({required this.line, required this.character});

  // minimum: 0
  @JsonKey(name: r'line', required: true, includeIfNull: false)
  final int line;

  // minimum: 0
  @JsonKey(name: r'character', required: true, includeIfNull: false)
  final int character;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is RangeStart &&
            runtimeType == other.runtimeType &&
            equals([line, character], [other.line, other.character]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([line, character]);

  factory RangeStart.fromJson(Map<String, dynamic> json) =>
      _$RangeStartFromJson(json);

  Map<String, dynamic> toJson() => _$RangeStartToJson(this);

  String toString() {
    return toJson().toString();
  }
}
