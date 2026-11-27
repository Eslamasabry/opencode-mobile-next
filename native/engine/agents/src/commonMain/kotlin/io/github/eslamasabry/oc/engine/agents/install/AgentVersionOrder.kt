package io.github.eslamasabry.oc.engine.agents.install

/** How an installed version stands to this app's pin. */
public enum class AgentVersionOrder {
    /** The installed copy is before the pin: the one case that is an update. */
    Older,

    /** The same version (a leading "v" and build text after "+" make no difference). */
    Same,

    /** The installed copy is after the pin: installing the pin would move it back. */
    Newer,

    /** One of the two is not a version (blank, "unknown", other text): nothing is known about their order. */
    Unparsable,
}

/**
 * The order of two versions, by their numbers and never by their text ("0.10.0" is after "0.9.9"). The update offer
 * compared them as text, "is it another version", so a copy newer than the pin (the agent updated outside the app, a
 * release moved a pin back, a side build was installed) was offered as "Version 0.172.0 → 0.171.0 · Update".
 *
 * A version is `major.minor.patch` with an optional leading "v"; a missing minor or patch counts as 0. A pre-release
 * after "-" is before its release and orders as semantic versioning says (numbers as numbers and before words, a
 * longer list after its own start); build text after "+" is not part of the order.
 */
public object AgentVersions {
    private const val MAX_LENGTH = 64
    private const val CORE_PARTS = 3
    private const val CORE = "[0-9]+(?:\\.[0-9]+){0,2}"
    private const val WORDS = "[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*"
    private val shape = Regex("^[vV]?($CORE)(?:-($WORDS))?(?:\\+$WORDS)?$")

    private class Version(val core: List<String>, val pre: List<String>?)

    /** Where [installed] stands to [pin]; [AgentVersionOrder.Unparsable] when either is not a version. */
    public fun order(installed: String?, pin: String?): AgentVersionOrder {
        val have = parse(installed)
        val want = parse(pin)
        if (have == null || want == null) return AgentVersionOrder.Unparsable
        val step = compare(have, want)
        return when {
            step < 0 -> AgentVersionOrder.Older
            step > 0 -> AgentVersionOrder.Newer
            else -> AgentVersionOrder.Same
        }
    }

    private fun parse(text: String?): Version? {
        val short = text?.trim()?.takeIf { it.length <= MAX_LENGTH }
        val match = short?.let { shape.matchEntire(it) } ?: return null
        val numbers = match.groupValues[1].split('.')
        return Version(
            core = numbers + List(CORE_PARTS - numbers.size) { "0" },
            pre = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.'),
        )
    }

    private fun compare(a: Version, b: Version): Int {
        val core = a.core.indices.map { number(a.core[it], b.core[it]) }.firstOrNull { it != 0 }
        return core ?: preRelease(a.pre, b.pre)
    }

    /** A release (no pre-release words) is after every pre-release of it. */
    private fun preRelease(a: List<String>?, b: List<String>?): Int {
        if (a == null || b == null) return (a == null).compareTo(b == null)
        val shared = minOf(a.size, b.size)
        val word = (0 until shared).map { word(a[it], b[it]) }.firstOrNull { it != 0 }
        return word ?: a.size.compareTo(b.size)
    }

    private fun word(a: String, b: String): Int {
        val aNumber = a.all { it in '0'..'9' }
        val bNumber = b.all { it in '0'..'9' }
        return when {
            aNumber && bNumber -> number(a, b)
            aNumber -> -1
            bNumber -> 1
            else -> a.compareTo(b)
        }
    }

    /** Two runs of digits as numbers of any length: the longer one (zeros in front dropped) is the larger. */
    private fun number(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        return if (x.length != y.length) x.length.compareTo(y.length) else x.compareTo(y)
    }
}

/** What this app's pin asks of one installed copy. */
public enum class AgentPinMove {
    /** The copy stays as it is: it is the pin, it is newer and runs, or its version cannot be read. */
    None,

    /** The copy is older than the pin (or the pin from another artifact): offered and run as an update. */
    Update,

    /**
     * The copy is newer than the pin and this app cannot run it: the pin is needed for the app to work. Its own case,
     * told in its own words; never an "Update" and never counted as one.
     */
    Required,
}

/**
 * The one rule for moving an installed copy to this app's pin: forward only. A copy newer than the pin is left alone,
 * and so is one whose version cannot be read (nothing says the pin is newer), so a downgrade is never offered or run
 * as an update.
 */
public object AgentPinRule {
    /**
     * [runsAsIs] false: this app cannot run a copy that is not the pin (the agent helper outside its tested versions),
     * so a newer one is [AgentPinMove.Required]. [reinstall]: the pin's own version is installed from another artifact
     * than the pin's (issue 147, `AgentInstallRecord`); it is installed again, a newer or unreadable copy never is.
     */
    public fun move(
        installed: String?,
        pin: String?,
        runsAsIs: Boolean = true,
        reinstall: Boolean = false,
    ): AgentPinMove =
        when (AgentVersions.order(installed, pin)) {
            AgentVersionOrder.Older -> AgentPinMove.Update
            AgentVersionOrder.Same -> if (reinstall) AgentPinMove.Update else AgentPinMove.None
            AgentVersionOrder.Newer -> if (runsAsIs) AgentPinMove.None else AgentPinMove.Required
            AgentVersionOrder.Unparsable -> AgentPinMove.None
        }

    /**
     * Whether an offer may hold [item]. An agent's row: only a copy older than the pin, or the pin's own version (the
     * reinstall the inventory found). A needed part: one that is not there yet, an older one, or a newer one only under
     * [AgentNeedRule.Always] (the app cannot run it: the required case, in the needed group and never a choice).
     */
    public fun offerable(item: AgentUpdateItem): Boolean =
        when (item.group) {
            AgentUpdateGroup.Optional -> move(item.fromVersion, item.toVersion, reinstall = true) == AgentPinMove.Update
            AgentUpdateGroup.Needed ->
                item.fromVersion == null ||
                    move(item.fromVersion, item.toVersion, runsAsIs = item.neededWhen != AgentNeedRule.Always) !=
                    AgentPinMove.None
        }
}
