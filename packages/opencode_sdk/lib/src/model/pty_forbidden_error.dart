//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_forbidden_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyForbiddenError {
  /// Returns a new [PtyForbiddenError] instance.
  PtyForbiddenError({required this.tag, required this.message});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PtyForbiddenErrorTagEnum.unknownDefaultOpenApi,
  )
  final PtyForbiddenErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyForbiddenError &&
            runtimeType == other.runtimeType &&
            equals([tag, message], [other.tag, other.message]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag, message]);

  factory PtyForbiddenError.fromJson(Map<String, dynamic> json) =>
      _$PtyForbiddenErrorFromJson(json);

  Map<String, dynamic> toJson() => _$PtyForbiddenErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PtyForbiddenErrorTagEnum {
  @JsonValue(r'PtyForbiddenError')
  ptyForbiddenError(r'PtyForbiddenError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PtyForbiddenErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
