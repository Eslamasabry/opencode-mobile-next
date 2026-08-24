//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'context_overflow_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ContextOverflowErrorData {
  /// Returns a new [ContextOverflowErrorData] instance.
  ContextOverflowErrorData({required this.message, this.responseBody});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'responseBody', required: false, includeIfNull: false)
  final String? responseBody;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ContextOverflowErrorData &&
            runtimeType == other.runtimeType &&
            equals(
              [message, responseBody],
              [other.message, other.responseBody],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([message, responseBody]);

  factory ContextOverflowErrorData.fromJson(Map<String, dynamic> json) =>
      _$ContextOverflowErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$ContextOverflowErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
