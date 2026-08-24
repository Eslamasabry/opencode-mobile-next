//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'api_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class APIErrorData {
  /// Returns a new [APIErrorData] instance.
  APIErrorData({
    required this.message,

    this.statusCode,

    required this.isRetryable,

    this.responseHeaders,

    this.responseBody,

    this.metadata,
  });

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  // minimum: 0
  @JsonKey(name: r'statusCode', required: false, includeIfNull: false)
  final int? statusCode;

  @JsonKey(name: r'isRetryable', required: true, includeIfNull: false)
  final bool isRetryable;

  @JsonKey(name: r'responseHeaders', required: false, includeIfNull: false)
  final Map<String, String>? responseHeaders;

  @JsonKey(name: r'responseBody', required: false, includeIfNull: false)
  final String? responseBody;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Map<String, String>? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is APIErrorData &&
            runtimeType == other.runtimeType &&
            equals(
              [
                message,
                statusCode,
                isRetryable,
                responseHeaders,
                responseBody,
                metadata,
              ],
              [
                other.message,
                other.statusCode,
                other.isRetryable,
                other.responseHeaders,
                other.responseBody,
                other.metadata,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        message,
        statusCode,
        isRetryable,
        responseHeaders,
        responseBody,
        metadata,
      ]);

  factory APIErrorData.fromJson(Map<String, dynamic> json) =>
      _$APIErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$APIErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
