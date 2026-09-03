//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union007_any_of_field.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union007_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion007AnyOf {
  /// Returns a new [OpencodeSdkRawUnion007AnyOf] instance.
  OpencodeSdkRawUnion007AnyOf({required this.field});

  @JsonKey(name: r'field', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion007AnyOfField field;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion007AnyOf &&
            runtimeType == other.runtimeType &&
            equals([field], [other.field]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([field]);

  factory OpencodeSdkRawUnion007AnyOf.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion007AnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion007AnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}
