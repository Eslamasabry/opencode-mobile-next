//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/structured_output_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'structured_output_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class StructuredOutputError {
  /// Returns a new [StructuredOutputError] instance.
  StructuredOutputError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: StructuredOutputErrorNameEnum.unknownDefaultOpenApi,
  )
  final StructuredOutputErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final StructuredOutputErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is StructuredOutputError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory StructuredOutputError.fromJson(Map<String, dynamic> json) =>
      _$StructuredOutputErrorFromJson(json);

  Map<String, dynamic> toJson() => _$StructuredOutputErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum StructuredOutputErrorNameEnum {
  @JsonValue(r'StructuredOutputError')
  structuredOutputError(r'StructuredOutputError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const StructuredOutputErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
