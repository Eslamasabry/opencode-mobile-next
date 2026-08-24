//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'content_filter_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ContentFilterError {
  /// Returns a new [ContentFilterError] instance.
  ContentFilterError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ContentFilterErrorNameEnum.unknownDefaultOpenApi,
  )
  final ContentFilterErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final MoveSessionErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ContentFilterError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory ContentFilterError.fromJson(Map<String, dynamic> json) =>
      _$ContentFilterErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ContentFilterErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ContentFilterErrorNameEnum {
  @JsonValue(r'ContentFilterError')
  contentFilterError(r'ContentFilterError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ContentFilterErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
