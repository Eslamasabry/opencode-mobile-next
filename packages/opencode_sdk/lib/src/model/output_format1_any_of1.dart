//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'output_format1_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OutputFormat1AnyOf1 {
  /// Returns a new [OutputFormat1AnyOf1] instance.
  OutputFormat1AnyOf1({
    required this.type,

    required this.schema,

    this.retryCount,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OutputFormat1AnyOf1TypeEnum.unknownDefaultOpenApi,
  )
  final OutputFormat1AnyOf1TypeEnum type;

  @JsonKey(name: r'schema', required: true, includeIfNull: false)
  final Object schema;

  // minimum: 0
  @JsonKey(name: r'retryCount', required: false, includeIfNull: false)
  final int? retryCount;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OutputFormat1AnyOf1 &&
            runtimeType == other.runtimeType &&
            equals(
              [type, schema, retryCount],
              [other.type, other.schema, other.retryCount],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, schema, retryCount]);

  factory OutputFormat1AnyOf1.fromJson(Map<String, dynamic> json) =>
      _$OutputFormat1AnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$OutputFormat1AnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OutputFormat1AnyOf1TypeEnum {
  @JsonValue(r'json_schema')
  jsonSchema(r'json_schema'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OutputFormat1AnyOf1TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
