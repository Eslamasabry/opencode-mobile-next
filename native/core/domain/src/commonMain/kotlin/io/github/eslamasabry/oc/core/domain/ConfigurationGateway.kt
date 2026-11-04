package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation

/**
 * A read-only, structurally masked configuration snapshot. Raw source values never cross this
 * boundary.
 */
public class ConfigurationPreview(public val text: String) {
    override fun toString(): String = "ConfigurationPreview(<masked>)"
}

/**
 * Optional setup inspection facet. Location is captured by the caller and cannot follow a later
 * selection.
 */
public interface ConfigurationGateway {
    public suspend fun configurationPreview(location: ServerLocation): Outcome<ConfigurationPreview>
}
