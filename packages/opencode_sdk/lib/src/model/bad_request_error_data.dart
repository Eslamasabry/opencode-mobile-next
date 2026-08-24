//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'bad_request_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class BadRequestErrorData {
  /// Returns a new [BadRequestErrorData] instance.
  BadRequestErrorData({required this.message, this.kind});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(
    name: r'kind',
    required: false,
    includeIfNull: false,
    unknownEnumValue: BadRequestErrorDataKindEnum.unknownDefaultOpenApi,
  )
  final BadRequestErrorDataKindEnum? kind;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is BadRequestErrorData &&
            runtimeType == other.runtimeType &&
            equals([message, kind], [other.message, other.kind]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([message, kind]);

  factory BadRequestErrorData.fromJson(Map<String, dynamic> json) =>
      _$BadRequestErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$BadRequestErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum BadRequestErrorDataKindEnum {
  @JsonValue(r'Params')
  params(r'Params'),
  @JsonValue(r'Headers')
  headers(r'Headers'),
  @JsonValue(r'Query')
  query(r'Query'),
  @JsonValue(r'Body')
  body(r'Body'),
  @JsonValue(r'Payload')
  payload(r'Payload'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const BadRequestErrorDataKindEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
