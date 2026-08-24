//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'tui_show_toast_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TuiShowToastRequest {
  /// Returns a new [TuiShowToastRequest] instance.
  TuiShowToastRequest({
    this.title,

    required this.message,

    required this.variant,

    this.duration,
  });

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(
    name: r'variant',
    required: true,
    includeIfNull: false,
    unknownEnumValue: TuiShowToastRequestVariantEnum.unknownDefaultOpenApi,
  )
  final TuiShowToastRequestVariantEnum variant;

  @JsonKey(name: r'duration', required: false, includeIfNull: false)
  final int? duration;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TuiShowToastRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [title, message, variant, duration],
              [other.title, other.message, other.variant, other.duration],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([title, message, variant, duration]);

  factory TuiShowToastRequest.fromJson(Map<String, dynamic> json) =>
      _$TuiShowToastRequestFromJson(json);

  Map<String, dynamic> toJson() => _$TuiShowToastRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum TuiShowToastRequestVariantEnum {
  @JsonValue(r'info')
  info(r'info'),
  @JsonValue(r'success')
  success(r'success'),
  @JsonValue(r'warning')
  warning(r'warning'),
  @JsonValue(r'error')
  error(r'error'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const TuiShowToastRequestVariantEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
