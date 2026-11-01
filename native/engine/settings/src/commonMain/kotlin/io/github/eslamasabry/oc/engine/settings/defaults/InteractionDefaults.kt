package io.github.eslamasabry.oc.engine.settings.defaults

// Defaults instead of questions (Flutter `lib/state/interaction_defaults.dart`, programme P6.6 / AUTO-8). A pure
// policy: callers pass fresh, capability-filtered options and the existing explicit choice; nothing here performs I/O
// or changes a setting.

/** Stable notice identities ([wire] is the stored name in `oc.defaultNotices.<profileId>`). */
public enum class DefaultKind(public val wire: String) {
    Project("project"), Model("model"), VoicePack("voicePack"), Voice("voice"), Delivery("delivery"), Review("review"),
}

public enum class DefaultReason {
    ExplicitChoice, OnlyOption, LastUsed, FirstProject, ServerDefault, DeviceMemory, Locale, Queue, ChangedScope,
    Unresolved,
}

public enum class DefaultReviewScope { Session, WorkingTree, Branch }

/** How a prompt reaches a running turn (same names as `:core:model` `PromptDelivery`; settings may not depend on it). */
public enum class DeliveryChoice { Steer, Queue }

/**
 * A selection, not an instruction to act or open a picker. [label] is the option's own name (data, not copy); the
 * notice store masks it before it is ever announced.
 */
public data class DefaultChoice<out T>(
    val value: T?,
    val label: String,
    val reason: DefaultReason,
    val optionCount: Int,
) {
    /** Zero options must show an unavailable / create state, never an empty picker. */
    public val skipPicker: Boolean get() = optionCount <= 1 || value != null
    public val canChange: Boolean get() = optionCount > 1
    public val isAutomatic: Boolean get() = value != null && reason != DefaultReason.ExplicitChoice
}

/** A project option. */
public data class ProjectOption(val id: String, val name: String)

/** A missing [project] means a proposed creation name, not an existing project. */
public data class DefaultProject(val name: String, val project: ProjectOption? = null) {
    public val needsCreation: Boolean get() = project == null
}

/** A catalog model as the policy needs it (match by ids; [name] is shown). */
public data class ModelOption(val providerId: String, val id: String, val name: String, val enabled: Boolean = true)

/** A voice model pack supported on this device (ABI and storage already filtered). */
public data class VoicePackOption(val id: String, val label: String, val minimumMemoryMb: Int)

/** An available offline voice. */
public data class VoiceOption(val id: String, val label: String, val locale: String)

public object InteractionDefaults {
    /** The proposed name when there is no project at all. */
    public const val FIRST_PROJECT_NAME: String = "my-app"

    public fun <T> picker(options: List<T>, label: (T) -> String, explicitChoice: T? = null): DefaultChoice<T> = when {
        explicitChoice != null && explicitChoice in options -> choice(explicitChoice, label, DefaultReason.ExplicitChoice, options.size)
        options.size == 1 -> choice(options.single(), label, DefaultReason.OnlyOption, options.size)
        else -> choice(null, label, DefaultReason.Unresolved, options.size)
    }

    /** The only project, the last used one, or the explicit one; a gone last-used gives none; none → `my-app`. */
    public fun project(projects: List<ProjectOption>, lastUsedId: String? = null, explicitId: String? = null): DefaultChoice<DefaultProject> {
        if (projects.isEmpty()) {
            return DefaultChoice(DefaultProject(FIRST_PROJECT_NAME), FIRST_PROJECT_NAME, DefaultReason.FirstProject, 0)
        }
        val explicit = projects.firstOrNull { it.id == explicitId }
        val last = projects.firstOrNull { it.id == lastUsedId }
        val selected = explicit ?: if (projects.size == 1) projects.single() else last
        val reason = when {
            explicit != null -> DefaultReason.ExplicitChoice
            projects.size == 1 -> DefaultReason.OnlyOption
            last != null -> DefaultReason.LastUsed
            else -> DefaultReason.Unresolved
        }
        return DefaultChoice(selected?.let { DefaultProject(it.name, it) }, selected?.name.orEmpty(), reason, projects.size)
    }

    /**
     * After sign-in / catalog loading. Matches the server's default by ids and shows its name; never guesses by a
     * human-readable name; never overrides an explicit choice; disabled models do not count; none when signed out.
     */
    public fun model(
        models: List<ModelOption>,
        signedIn: Boolean,
        serverProviderId: String? = null,
        serverModelId: String? = null,
        explicitChoice: ModelOption? = null,
    ): DefaultChoice<ModelOption> {
        val options = if (signedIn) models.filter { it.enabled } else emptyList()
        val explicit = options.firstOrNull { it.providerId == explicitChoice?.providerId && it.id == explicitChoice.id }
        if (explicit != null || options.size <= 1) return picker(options, ModelOption::name, explicit)
        val server = options.firstOrNull { it.providerId == serverProviderId && it.id == serverModelId }
        return choice(server, ModelOption::name, DefaultReason.ServerDefault, options.size)
    }

    /** Physical RAM, never the heap class: the largest eligible pack; unknown RAM gets the smallest eligible one. */
    public fun voicePack(totalMemoryMb: Int?, supportedPacks: List<VoicePackOption>, explicitId: String? = null): DefaultChoice<VoicePackOption> {
        val options = supportedPacks
            .filter { totalMemoryMb == null || it.minimumMemoryMb <= totalMemoryMb }
            .sortedWith(compareBy<VoicePackOption> { it.minimumMemoryMb }.thenBy { it.id })
        val explicit = options.firstOrNull { it.id == explicitId }
        if (explicit != null || options.size <= 1) return picker(options, VoicePackOption::label, explicit)
        return choice(if (totalMemoryMb == null) options.first() else options.last(), VoicePackOption::label, DefaultReason.DeviceMemory, options.size)
    }

    /** The exact locale first, then the language; no foreign-language guess. */
    public fun voice(voices: List<VoiceOption>, locale: String, explicitId: String? = null): DefaultChoice<VoiceOption> {
        val explicit = voices.firstOrNull { it.id == explicitId }
        if (explicit != null || voices.size <= 1) return picker(voices, VoiceOption::label, explicit)
        fun normalize(value: String) = value.replace('_', '-').lowercase()
        val target = normalize(locale)
        val selected = voices.firstOrNull { normalize(it.locale) == target }
            ?: voices.firstOrNull { normalize(it.locale).substringBefore('-') == target.substringBefore('-') }
        return choice(selected, VoiceOption::label, DefaultReason.Locale, voices.size)
    }

    /** Send defaults to Queue; an explicit choice is kept. */
    public fun delivery(explicitChoice: DeliveryChoice? = null): DefaultChoice<DeliveryChoice> = choice(
        explicitChoice ?: DeliveryChoice.Queue,
        { it.name.lowercase() },
        if (explicitChoice == null) DefaultReason.Queue else DefaultReason.ExplicitChoice,
        DeliveryChoice.entries.size,
    )

    /**
     * A null count is unknown (loading), zero is confirmed empty. With changes in several scopes: session, then working
     * tree, then branch. Pass only the scopes the session supports.
     */
    public fun review(changeCounts: Map<DefaultReviewScope, Int?>, explicitChoice: DefaultReviewScope? = null): DefaultChoice<DefaultReviewScope> {
        val options = DefaultReviewScope.entries.filter { it in changeCounts }
        if (explicitChoice != null && explicitChoice in options) return picker(options, { it.name }, explicitChoice)
        val changed = options.firstOrNull { (changeCounts[it] ?: 0) > 0 }
        return choice(changed, { it.name }, DefaultReason.ChangedScope, options.size)
    }

    private fun <T> choice(value: T?, label: (T) -> String, reason: DefaultReason, count: Int): DefaultChoice<T> =
        DefaultChoice(value, value?.let(label).orEmpty(), if (value == null) DefaultReason.Unresolved else reason, count)
}
