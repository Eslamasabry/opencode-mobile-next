//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/integration_select_prompt_options_inner.dart';
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union017_any_of_when.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union017_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion017AnyOf1 {
  /// Returns a new [OpencodeSdkRawUnion017AnyOf1] instance.
  OpencodeSdkRawUnion017AnyOf1({
    required this.type,

    required this.key,

    required this.message,

    required this.options,

    this.when_,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion017AnyOf1TypeEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion017AnyOf1TypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'options', required: true, includeIfNull: false)
  final List<IntegrationSelectPromptOptionsInner> options;

  @JsonKey(name: r'when', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion017AnyOfWhen? when_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion017AnyOf1 &&
            runtimeType == other.runtimeType &&
            equals(
              [type, key, message, options, when_],
              [
                other.type,
                other.key,
                other.message,
                other.options,
                other.when_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, key, message, options, when_]);

  factory OpencodeSdkRawUnion017AnyOf1.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion017AnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion017AnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion017AnyOf1TypeEnum {
  @JsonValue(r'select')
  select(r'select'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion017AnyOf1TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
