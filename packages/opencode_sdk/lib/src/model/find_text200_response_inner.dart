//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/find_text200_response_inner_submatches_inner.dart';
import 'package:opencode_sdk/src/model/find_text200_response_inner_path.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'find_text200_response_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FindText200ResponseInner {
  /// Returns a new [FindText200ResponseInner] instance.
  FindText200ResponseInner({
    required this.path,

    required this.lines,

    required this.lineNumber,

    required this.absoluteOffset,

    required this.submatches,
  });

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final FindText200ResponseInnerPath path;

  @JsonKey(name: r'lines', required: true, includeIfNull: false)
  final FindText200ResponseInnerPath lines;

  // minimum: 0
  @JsonKey(name: r'line_number', required: true, includeIfNull: false)
  final int lineNumber;

  // minimum: 0
  @JsonKey(name: r'absolute_offset', required: true, includeIfNull: false)
  final int absoluteOffset;

  @JsonKey(name: r'submatches', required: true, includeIfNull: false)
  final List<FindText200ResponseInnerSubmatchesInner> submatches;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FindText200ResponseInner &&
            runtimeType == other.runtimeType &&
            equals(
              [path, lines, lineNumber, absoluteOffset, submatches],
              [
                other.path,
                other.lines,
                other.lineNumber,
                other.absoluteOffset,
                other.submatches,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([path, lines, lineNumber, absoluteOffset, submatches]);

  factory FindText200ResponseInner.fromJson(Map<String, dynamic> json) =>
      _$FindText200ResponseInnerFromJson(json);

  Map<String, dynamic> toJson() => _$FindText200ResponseInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
