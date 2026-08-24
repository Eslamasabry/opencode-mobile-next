//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_any_of2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusAnyOf2 {
  /// Returns a new [SessionStatusAnyOf2] instance.
  SessionStatusAnyOf2({required this.type});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionStatusAnyOf2TypeEnum.unknownDefaultOpenApi,
  )
  final SessionStatusAnyOf2TypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusAnyOf2 &&
            runtimeType == other.runtimeType &&
            equals([type], [other.type]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type]);

  factory SessionStatusAnyOf2.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusAnyOf2FromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusAnyOf2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionStatusAnyOf2TypeEnum {
  @JsonValue(r'busy')
  busy(r'busy'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionStatusAnyOf2TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
