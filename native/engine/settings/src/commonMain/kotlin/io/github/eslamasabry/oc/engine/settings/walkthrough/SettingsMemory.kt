package io.github.eslamasabry.oc.engine.settings.walkthrough

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Device-wide walkthrough data. The platform waits for migration before opening the preference
 * store.
 */
public interface SettingsMemory {
    public suspend fun read(key: String): Outcome<String?>

    public suspend fun write(key: String, value: String): Outcome<Unit>
}
