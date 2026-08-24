//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union014_any_of_value_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion014AnyOfValueAnyOf {
  /// Returns a new [OpencodeSdkRawUnion014AnyOfValueAnyOf] instance.
  OpencodeSdkRawUnion014AnyOfValueAnyOf({required this.disabled});

  @JsonKey(
    name: r'disabled',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum disabled;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion014AnyOfValueAnyOf &&
            runtimeType == other.runtimeType &&
            equals([disabled], [other.disabled]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([disabled]);

  factory OpencodeSdkRawUnion014AnyOfValueAnyOf.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion014AnyOfValueAnyOfFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion014AnyOfValueAnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum {
  @JsonValue('true')
  true_('true'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
