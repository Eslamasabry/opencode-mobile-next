//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'output_format1_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OutputFormat1AnyOf {
  /// Returns a new [OutputFormat1AnyOf] instance.
  OutputFormat1AnyOf({required this.type});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OutputFormat1AnyOfTypeEnum.unknownDefaultOpenApi,
  )
  final OutputFormat1AnyOfTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OutputFormat1AnyOf &&
            runtimeType == other.runtimeType &&
            equals([type], [other.type]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type]);

  factory OutputFormat1AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OutputFormat1AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OutputFormat1AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OutputFormat1AnyOfTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OutputFormat1AnyOfTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
