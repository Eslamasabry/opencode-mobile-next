//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'unknown_error1.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UnknownError1 {
  /// Returns a new [UnknownError1] instance.
  UnknownError1({required this.tag, required this.message, this.ref});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: UnknownError1TagEnum.unknownDefaultOpenApi,
  )
  final UnknownError1TagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'ref', required: false, includeIfNull: false)
  final String? ref;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UnknownError1 &&
            runtimeType == other.runtimeType &&
            equals([tag, message, ref], [other.tag, other.message, other.ref]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, message, ref]);

  factory UnknownError1.fromJson(Map<String, dynamic> json) =>
      _$UnknownError1FromJson(json);

  Map<String, dynamic> toJson() => _$UnknownError1ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum UnknownError1TagEnum {
  @JsonValue(r'UnknownError')
  unknownError(r'UnknownError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const UnknownError1TagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
