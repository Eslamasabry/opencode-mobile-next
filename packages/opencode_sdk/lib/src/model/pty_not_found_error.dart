//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyNotFoundError {
  /// Returns a new [PtyNotFoundError] instance.
  PtyNotFoundError({
    required this.tag,

    required this.ptyID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PtyNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final PtyNotFoundErrorTagEnum tag;

  @JsonKey(name: r'ptyID', required: true, includeIfNull: false)
  final String ptyID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, ptyID, message],
              [other.tag, other.ptyID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, ptyID, message]);

  factory PtyNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$PtyNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$PtyNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PtyNotFoundErrorTagEnum {
  @JsonValue(r'PtyNotFoundError')
  ptyNotFoundError(r'PtyNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PtyNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
