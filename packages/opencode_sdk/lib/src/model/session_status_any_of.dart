//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_any_of.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusAnyOf {
  /// Returns a new [SessionStatusAnyOf] instance.
  SessionStatusAnyOf({required this.type});

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionStatusAnyOfTypeEnum.unknownDefaultOpenApi,
  )
  final SessionStatusAnyOfTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusAnyOf &&
            runtimeType == other.runtimeType &&
            equals([type], [other.type]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([type]);

  factory SessionStatusAnyOf.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusAnyOfFromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusAnyOfToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionStatusAnyOfTypeEnum {
  @JsonValue(r'idle')
  idle(r'idle'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionStatusAnyOfTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
