//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_update_request_size.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyUpdateRequestSize {
  /// Returns a new [PtyUpdateRequestSize] instance.
  PtyUpdateRequestSize({required this.rows, required this.cols});

  @JsonKey(name: r'rows', required: true, includeIfNull: false)
  final int rows;

  @JsonKey(name: r'cols', required: true, includeIfNull: false)
  final int cols;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyUpdateRequestSize &&
            runtimeType == other.runtimeType &&
            equals([rows, cols], [other.rows, other.cols]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([rows, cols]);

  factory PtyUpdateRequestSize.fromJson(Map<String, dynamic> json) =>
      _$PtyUpdateRequestSizeFromJson(json);

  Map<String, dynamic> toJson() => _$PtyUpdateRequestSizeToJson(this);

  String toString() {
    return toJson().toString();
  }
}
