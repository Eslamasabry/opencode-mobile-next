//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/api_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'api_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class APIError {
  /// Returns a new [APIError] instance.
  APIError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: APIErrorNameEnum.unknownDefaultOpenApi,
  )
  final APIErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final APIErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is APIError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory APIError.fromJson(Map<String, dynamic> json) =>
      _$APIErrorFromJson(json);

  Map<String, dynamic> toJson() => _$APIErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum APIErrorNameEnum {
  @JsonValue(r'APIError')
  aPIError(r'APIError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const APIErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
