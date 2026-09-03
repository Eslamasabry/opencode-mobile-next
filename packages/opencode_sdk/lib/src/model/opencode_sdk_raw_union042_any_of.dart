//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union042_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion042AnyOf {
  /// Returns a new [OpencodeSdkRawUnion042AnyOf] instance.
  OpencodeSdkRawUnion042AnyOf({required this.success, required this.version});

  @JsonKey(
    name: r'success',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion042AnyOfSuccessEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion042AnyOfSuccessEnum success;

  @JsonKey(name: r'version', required: true, includeIfNull: false)
  final String version;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion042AnyOf &&
            runtimeType == other.runtimeType &&
            equals([success, version], [other.success, other.version]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([success, version]);

  factory OpencodeSdkRawUnion042AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion042AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion042AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion042AnyOfSuccessEnum {
  @JsonValue('true')
  true_('true'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const OpencodeSdkRawUnion042AnyOfSuccessEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
