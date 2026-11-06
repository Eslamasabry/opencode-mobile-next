package io.github.eslamasabry.oc.feature.onboarding.presentation

/**
 * The device-wide flag the local agents offer keeps (bound over `:core:data`'s prefs store by the app graph, the way
 * [FirstRunPreferences] is). A Bool, as Flutter's `prefs.setBool` wrote it, so a migrated value reads unchanged.
 */
public interface LocalAgentsOfferPreferences {
    /** The stored Bool, or null when absent or not a Bool. */
    public suspend fun readBool(key: String): Boolean?

    public suspend fun writeBool(key: String, value: Boolean)
}

/** What the local agents block shows while no agent is installed on this phone. */
public enum class LocalAgentsOfferView {
    /** The offer: what it installs, its size, Set up and Not now. */
    Offer,

    /** Nothing: the person said Not now. The Agents page still has its visible way to set them up. */
    Hidden,
}

/**
 * "Set up agents on this phone" asked once (Dart `LocalAgentOnboardingBlock`, `lib/ui/widgets/local_agent_onboarding.dart`):
 * Not now is remembered under [SKIPPED_KEY] in the Flutter preferences file, which native adopts in place, so a
 * person who said no in the Flutter app is not asked again after the update (E00-S06).
 *
 * Only the offer itself is hidden. Once agents are installing, installed, need Ubuntu or failed, the block shows that
 * state whatever the flag says, as Flutter does; [showOffer] is the decision for the "nothing installed" state.
 */
public class LocalAgentsOffer(private val prefs: LocalAgentsOfferPreferences) {
    /** True after Not now, here or in the Flutter app before the update. */
    public suspend fun skipped(): Boolean = prefs.readBool(SKIPPED_KEY) == true

    /**
     * Dart `_apply` for `LocalAgentPhase.absent`: the offer shows unless it was skipped. [autoStart] (the person just
     * asked for agents on this phone, for example from setup) shows it even after Not now.
     */
    public suspend fun showOffer(autoStart: Boolean = false): LocalAgentsOfferView =
        if (autoStart || !skipped()) LocalAgentsOfferView.Offer else LocalAgentsOfferView.Hidden

    /** Not now: remembered on this device, same key and type as Flutter, so a rollback build reads it too. */
    public suspend fun skip() {
        prefs.writeBool(SKIPPED_KEY, true)
    }

    public companion object {
        /** Dart `localAgentOfferSkippedKey` (logical name; stored as `flutter.local_agents_offer_skipped`). */
        public const val SKIPPED_KEY: String = "local_agents_offer_skipped"
    }
}
