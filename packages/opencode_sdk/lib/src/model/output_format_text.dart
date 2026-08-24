//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'output_format_text.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OutputFormatText {
  /// Returns a new [OutputFormatText] instance.
  OutputFormatText({required this.type});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OutputFormatTextTypeEnum.unknownDefaultOpenApi,
  )
  final OutputFormatTextTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OutputFormatText &&
            runtimeType == other.runtimeType &&
            equals([type], [other.type]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type]);

  factory OutputFormatText.fromJson(Map<String, dynamic> json) =>
      _$OutputFormatTextFromJson(json);

  Map<String, dynamic> toJson() => _$OutputFormatTextToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OutputFormatTextTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OutputFormatTextTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
