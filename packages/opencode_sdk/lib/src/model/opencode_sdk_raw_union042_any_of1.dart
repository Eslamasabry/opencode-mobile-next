//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union042_any_of1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion042AnyOf1 {
  /// Returns a new [OpencodeSdkRawUnion042AnyOf1] instance.
  OpencodeSdkRawUnion042AnyOf1({required this.success, required this.error});

  @JsonKey(
    name: r'success',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion042AnyOf1SuccessEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion042AnyOf1SuccessEnum success;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final String error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion042AnyOf1 &&
            runtimeType == other.runtimeType &&
            equals([success, error], [other.success, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([success, error]);

  factory OpencodeSdkRawUnion042AnyOf1.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion042AnyOf1FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion042AnyOf1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion042AnyOf1SuccessEnum {
  @JsonValue('false')
  false_('false'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const OpencodeSdkRawUnion042AnyOf1SuccessEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
