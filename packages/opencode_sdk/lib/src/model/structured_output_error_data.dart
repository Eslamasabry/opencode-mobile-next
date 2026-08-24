//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'structured_output_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class StructuredOutputErrorData {
  /// Returns a new [StructuredOutputErrorData] instance.
  StructuredOutputErrorData({required this.message, required this.retries});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  // minimum: 0
  @JsonKey(name: r'retries', required: true, includeIfNull: false)
  final int retries;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is StructuredOutputErrorData &&
            runtimeType == other.runtimeType &&
            equals([message, retries], [other.message, other.retries]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([message, retries]);

  factory StructuredOutputErrorData.fromJson(Map<String, dynamic> json) =>
      _$StructuredOutputErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$StructuredOutputErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
