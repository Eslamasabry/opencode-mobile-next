//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/api_error.dart';
import 'package:opencode_sdk/src/model/provider_auth_error.dart';
import 'package:opencode_sdk/src/model/context_overflow_error.dart';
import 'package:opencode_sdk/src/model/message_aborted_error.dart';
import 'package:opencode_sdk/src/model/message_output_length_error.dart';
import 'package:opencode_sdk/src/model/content_filter_error.dart';
import 'package:opencode_sdk/src/model/api_error_data.dart';
import 'package:opencode_sdk/src/model/structured_output_error.dart';
import 'package:opencode_sdk/src/model/unknown_error.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of45_properties_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf45PropertiesError {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf45PropertiesError] instance.
  OpencodeSdkRawUnion002AnyOf45PropertiesError({
    required this.name,

    required this.data,
  });

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum
        .unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final APIErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf45PropertiesError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory OpencodeSdkRawUnion002AnyOf45PropertiesError.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion002AnyOf45PropertiesErrorFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion002AnyOf45PropertiesErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum {
  @JsonValue(r'APIError')
  aPIError(r'APIError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
