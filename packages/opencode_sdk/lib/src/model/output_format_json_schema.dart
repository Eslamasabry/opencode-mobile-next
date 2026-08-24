//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'output_format_json_schema.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OutputFormatJsonSchema {
  /// Returns a new [OutputFormatJsonSchema] instance.
  OutputFormatJsonSchema({
    required this.type,

    required this.schema,

    this.retryCount,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OutputFormatJsonSchemaTypeEnum.unknownDefaultOpenApi,
  )
  final OutputFormatJsonSchemaTypeEnum type;

  @JsonKey(name: r'schema', required: true, includeIfNull: false)
  final Object schema;

  // minimum: 0
  @JsonKey(name: r'retryCount', required: false, includeIfNull: false)
  final int? retryCount;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OutputFormatJsonSchema &&
            runtimeType == other.runtimeType &&
            equals(
              [type, schema, retryCount],
              [other.type, other.schema, other.retryCount],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, schema, retryCount]);

  factory OutputFormatJsonSchema.fromJson(Map<String, dynamic> json) =>
      _$OutputFormatJsonSchemaFromJson(json);

  Map<String, dynamic> toJson() => _$OutputFormatJsonSchemaToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OutputFormatJsonSchemaTypeEnum {
  @JsonValue(r'json_schema')
  jsonSchema(r'json_schema'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OutputFormatJsonSchemaTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
