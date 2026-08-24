//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tool_text_content.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ToolTextContent {
  /// Returns a new [ToolTextContent] instance.
  ToolTextContent({required this.type, required this.text});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ToolTextContentTypeEnum.unknownDefaultOpenApi,
  )
  final ToolTextContentTypeEnum type;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ToolTextContent &&
            runtimeType == other.runtimeType &&
            equals([type, text], [other.type, other.text]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type, text]);

  factory ToolTextContent.fromJson(Map<String, dynamic> json) =>
      _$ToolTextContentFromJson(json);

  Map<String, dynamic> toJson() => _$ToolTextContentToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ToolTextContentTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ToolTextContentTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
