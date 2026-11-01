/// Words and glyphs for AI Team role voices (lib/state/team_voice.dart):
/// the role names, the sentence each voiced line says, and the short "who's
/// doing what now" phrase for a task's top bar.
library;

import 'package:flutter/widgets.dart';

import '../../../../l10n/app_localizations.dart';
import '../../../../state/team_voice.dart';
import '../../../app_iconography.dart';
import 'team_refusal.dart' show teamTimelineWords;

/// The role's name; a developer wearing a persona reads "Developer ·
/// Frontend".
String teamVoiceName(AppLocalizations l, TeamVoice voice, {String? persona}) =>
    switch (voice) {
      TeamVoice.productOwner => l.teamVoiceProductOwner,
      TeamVoice.architect => l.teamVoiceArchitect,
      TeamVoice.manager => l.teamVoiceManager,
      TeamVoice.developer =>
        persona == null
            ? l.teamVoiceDeveloper
            : l.teamVoiceDeveloperAs(persona),
      TeamVoice.reviewer => l.teamVoiceReviewer,
    };

/// The role's glyph (decorative; the name is the words).
IconData teamVoiceIcon(TeamVoice voice) => switch (voice) {
  TeamVoice.productOwner => AppIconography.checklist,
  TeamVoice.architect => AppIconography.layers,
  TeamVoice.manager => AppIconography.kanban,
  TeamVoice.developer => AppIconography.code,
  TeamVoice.reviewer => AppIconography.checkCircle,
};

/// What [line] says, in the speaker's words.
String teamVoiceSentence(AppLocalizations l, TeamVoiceLine line) =>
    switch (line.says) {
      TeamVoiceSays.planning => l.teamVoiceSaysPlanning,
      TeamVoiceSays.planReady => l.teamVoiceSaysPlanReady,
      TeamVoiceSays.starting => l.teamVoiceSaysStarting,
      TeamVoiceSays.working => l.teamVoiceSaysWorking,
      TeamVoiceSays.checking => l.teamVoiceSaysChecking,
      TeamVoiceSays.foundProblems => l.teamVoiceSaysFoundProblems,
      TeamVoiceSays.merging => l.teamVoiceSaysMerging,
      TeamVoiceSays.merged => l.teamVoiceSaysMerged,
      TeamVoiceSays.queued =>
        line.planner ? l.teamVoiceSaysPlanQueued : l.teamVoiceSaysQueued,
      TeamVoiceSays.waitingForLane => l.teamVoiceSaysWaitingForLane,
      TeamVoiceSays.waitingForTask => l.teamVoiceSaysWaitingForTask,
      TeamVoiceSays.waitingForChat => l.teamVoiceSaysWaitingForChat,
      TeamVoiceSays.waitingForCheck => l.teamVoiceSaysWaitingForCheck,
      TeamVoiceSays.paused => l.teamVoiceSaysPaused,
      TeamVoiceSays.stopped => l.teamVoiceSaysStopped,
      TeamVoiceSays.interrupted => l.teamVoiceSaysInterrupted,
      TeamVoiceSays.resuming => l.teamVoiceSaysResuming,
      TeamVoiceSays.other => teamTimelineWords(l, line.text),
    };

/// Who is doing what now, for a task's top bar ("Reviewer is checking").
String teamVoiceNowPhrase(
  AppLocalizations l,
  TeamVoiceLine line, {
  String? persona,
}) {
  final developer = teamVoiceName(l, TeamVoice.developer, persona: persona);
  return switch (line.says) {
    TeamVoiceSays.planning => l.teamVoiceNowPlanning,
    TeamVoiceSays.starting ||
    TeamVoiceSays.working => l.teamVoiceNowWorking(developer),
    TeamVoiceSays.checking => l.teamVoiceNowChecking,
    TeamVoiceSays.foundProblems ||
    TeamVoiceSays.planReady ||
    TeamVoiceSays.interrupted => l.teamVoiceNowNeedsYou,
    TeamVoiceSays.merging => l.teamVoiceNowMerging,
    TeamVoiceSays.merged => l.teamVoiceNowMerged,
    TeamVoiceSays.queued ||
    TeamVoiceSays.waitingForLane ||
    TeamVoiceSays.waitingForTask ||
    TeamVoiceSays.waitingForChat ||
    TeamVoiceSays.waitingForCheck => l.teamVoiceNowWaiting,
    TeamVoiceSays.paused => l.teamVoiceNowPaused,
    TeamVoiceSays.stopped => l.teamVoiceNowStopped,
    TeamVoiceSays.resuming => l.teamVoiceNowResuming,
    TeamVoiceSays.other => l.teamVoiceNowManager,
  };
}
