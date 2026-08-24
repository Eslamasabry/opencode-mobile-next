//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union016_any_of_when.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union016_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion016AnyOf {
  /// Returns a new [OpencodeSdkRawUnion016AnyOf] instance.
  OpencodeSdkRawUnion016AnyOf({
    required this.type,

    required this.key,

    required this.message,

    this.placeholder,

    this.when_,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: OpencodeSdkRawUnion016AnyOfTypeEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion016AnyOfTypeEnum type;

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'placeholder', required: false, includeIfNull: false)
  final String? placeholder;

  @JsonKey(name: r'when', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion016AnyOfWhen? when_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion016AnyOf &&
            runtimeType == other.runtimeType &&
            equals(
              [type, key, message, placeholder, when_],
              [
                other.type,
                other.key,
                other.message,
                other.placeholder,
                other.when_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, key, message, placeholder, when_]);

  factory OpencodeSdkRawUnion016AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion016AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion016AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion016AnyOfTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion016AnyOfTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
