//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/find_text200_response_inner_path.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'find_text200_response_inner_submatches_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FindText200ResponseInnerSubmatchesInner {
  /// Returns a new [FindText200ResponseInnerSubmatchesInner] instance.
  FindText200ResponseInnerSubmatchesInner({
    required this.match,

    required this.start,

    required this.end,
  });

  @JsonKey(name: r'match', required: true, includeIfNull: false)
  final FindText200ResponseInnerPath match;

  // minimum: 0
  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final int start;

  // minimum: 0
  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final int end;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FindText200ResponseInnerSubmatchesInner &&
            runtimeType == other.runtimeType &&
            equals([match, start, end], [other.match, other.start, other.end]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([match, start, end]);

  factory FindText200ResponseInnerSubmatchesInner.fromJson(
    Map<String, dynamic> json,
  ) => _$FindText200ResponseInnerSubmatchesInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$FindText200ResponseInnerSubmatchesInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
