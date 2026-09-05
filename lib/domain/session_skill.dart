abstract interface class SessionSkillGateway {
  bool get sessionSkillsSupported;
  Future<void> activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
  });
}

enum SessionSkillFailure { unsupported, changed, staged, busy, uncertain }

class SessionSkillException implements Exception {
  const SessionSkillException(this.failure);
  final SessionSkillFailure failure;
}
